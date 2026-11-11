package com.gatefit.data.repository

import com.gatefit.core.result.DataError
import com.gatefit.core.result.DataResult
import com.gatefit.data.dto.DataSourceDto
import com.gatefit.data.dto.EntranceDto
import com.gatefit.data.dto.FacilityDto
import com.gatefit.data.local.AssetPaths
import com.gatefit.data.remote.RemoteDataset
import com.gatefit.data.testing.BundledAssets
import com.gatefit.data.testing.CountingAssetSource
import com.gatefit.data.testing.FakePlaceRemoteSource
import com.gatefit.data.testing.InMemoryPlaceLocalSource
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.repository.FacilityQuery
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFirstFacilityRepositoryTest {

    private val now = Instant.parse("2026-11-11T04:00:00Z")
    private val local = InMemoryPlaceLocalSource()
    private val assets = CountingAssetSource(BundledAssets)
    private var demoEnabled = false

    private val remoteDataset = RemoteDataset(
        sources = listOf(DataSourceDto(id = "official", sourceName = "시설 공식 안내", sourceType = "OFFICIAL")),
        facilities = listOf(
            FacilityDto(id = "remote-mall", name = "원격 쇼핑몰", category = "SHOPPING"),
            FacilityDto(id = "remote-empty", name = "원격 공원", category = "PARK"),
        ),
        entrances = listOf(
            EntranceDto(
                id = "remote-gate",
                facilityId = "remote-mall",
                name = "정문",
                maxHeightMm = 2300,
                entranceType = "ENTRY",
                dataSourceId = "official",
                trustLevel = "VERIFIED_OFFICIAL",
                verifiedAt = "2026-11-01T00:00:00Z",
            ),
            EntranceDto(id = "orphan", facilityId = "deleted-facility", name = "고아 출입구"),
        ),
    )

    private fun repository(
        remote: FakePlaceRemoteSource? = null,
        seedVersion: String = "1",
    ) = OfflineFirstFacilityRepository(
        local = local,
        assets = assets,
        remote = remote,
        demoEnabled = { demoEnabled },
        seedVersion = seedVersion,
        clock = { now },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun `first launch works offline from the bundled snapshot`() = runTest {
        val repository = repository()

        val info = repository.datasetInfo()

        assertEquals(3603, info.facilityCount)
        assertEquals(2269, info.entranceCount)
        assertEquals(0, info.verifiedEntranceCount)
        assertEquals(2, info.sources.size)
        assertNull(info.lastSyncedAt)
        assertFalse(info.remoteConfigured)
        assertEquals(1L, repository.dataVersion.value)
    }

    @Test
    fun `bundled snapshot is imported only once`() = runTest {
        repository().search(FacilityQuery("병원"))
        repository().search(FacilityQuery("병원"))

        assertEquals(1, assets.reads.count { it == AssetPaths.BASELINE_SNAPSHOT })
    }

    @Test
    fun `newer bundled snapshot replaces an old one that was never synced`() = runTest {
        repository(seedVersion = "1").ensureSeeded()

        repository(seedVersion = "2").ensureSeeded()

        assertEquals(2, assets.reads.count { it == AssetPaths.BASELINE_SNAPSHOT })
    }

    @Test
    fun `search finds real facilities by name and filters by category`() = runTest {
        val repository = repository()

        val hospitals = repository.search(FacilityQuery("병원", FacilityCategory.HOSPITAL, limit = 10))

        assertTrue(hospitals.isNotEmpty() && hospitals.size <= 10)
        assertTrue(hospitals.all { it.facility.category == FacilityCategory.HOSPITAL })
        assertTrue(
            hospitals.all { summary ->
                val facility = summary.facility
                (listOf(facility.name, facility.roadAddress, facility.lotAddress) + facility.aliases)
                    .any { "병원" in it.orEmpty() }
            }
        )
        assertTrue(repository.search(FacilityQuery("존재하지않는시설이름")).isEmpty())
    }

    @Test
    fun `demo facilities are hidden until the user turns demo data on`() = runTest {
        val repository = repository()

        assertTrue(repository.search(FacilityQuery("DEMO")).isEmpty())
        assertNull(repository.getFacilityWithEntrances("demo-facility-mall"))
        assertNull(repository.getEntrance("demo-entrance-mall-b"))

        demoEnabled = true

        val demo = repository.search(FacilityQuery("DEMO"))
        assertEquals(2, demo.size)
        assertTrue(demo.all { it.facility.origin == DataOrigin.DEMO })
        assertEquals(4, repository.getFacilityWithEntrances("demo-facility-mall")!!.entrances.size)
    }

    @Test
    fun `demo data never counts towards the real dataset`() = runTest {
        demoEnabled = true

        val info = repository().datasetInfo()

        assertEquals(3603, info.facilityCount)
        assertEquals(2269, info.entranceCount)
        assertEquals(0, info.verifiedEntranceCount)
    }

    @Test
    fun `entrance lookup returns its facility`() = runTest {
        val repository = repository()
        val facility = repository.search(FacilityQuery("")).first { it.entranceCount > 0 }
        val entrance = repository.getFacilityWithEntrances(facility.facility.id)!!.entrances.first()

        val found = repository.getEntrance(entrance.id)!!

        assertEquals(facility.facility.id, found.facility.id)
        assertEquals(TrustLevel.PUBLIC_BASELINE, found.entrance.trustLevel)
    }

    @Test
    fun `sync is unavailable without remote configuration and keeps local data`() = runTest {
        val repository = repository(remote = null)

        assertEquals(DataResult.Failure(DataError.NotConfigured), repository.refreshFromRemote())
        assertEquals(3603, repository.datasetInfo().facilityCount)
    }

    @Test
    fun `successful sync replaces live data and records the sync time`() = runTest {
        val repository = repository(FakePlaceRemoteSource(DataResult.Success(remoteDataset)))
        repository.ensureSeeded()
        val versionBefore = repository.dataVersion.value

        val summary = (repository.refreshFromRemote() as DataResult.Success).value

        assertEquals(2, summary.facilityCount)
        assertEquals(1, summary.entranceCount)
        val info = repository.datasetInfo()
        assertEquals(2, info.facilityCount)
        assertEquals(1, info.entranceCount)
        assertEquals(1, info.verifiedEntranceCount)
        assertEquals(now, info.lastSyncedAt)
        assertTrue(info.remoteConfigured)
        assertEquals(listOf("시설 공식 안내"), info.sources.map { it.name })
        assertTrue(repository.dataVersion.value > versionBefore)

        val gate = repository.getEntrance("remote-gate")!!.entrance
        assertEquals("official", gate.dataSource?.id)
        assertNull(repository.getEntrance("orphan"))
    }

    @Test
    fun `synced data is not overwritten by the bundled snapshot after an app update`() = runTest {
        repository(FakePlaceRemoteSource(DataResult.Success(remoteDataset))).refreshFromRemote()

        val info = repository(seedVersion = "2").datasetInfo()

        assertEquals(2, info.facilityCount)
        assertEquals(1, assets.reads.count { it == AssetPaths.BASELINE_SNAPSHOT })
    }

    @Test
    fun `failed sync keeps local data untouched`() = runTest {
        val repository = repository(FakePlaceRemoteSource(DataResult.Failure(DataError.Network)))
        repository.ensureSeeded()
        val replaceCount = local.replaceCount

        assertEquals(DataResult.Failure(DataError.Network), repository.refreshFromRemote())
        assertEquals(replaceCount, local.replaceCount)
        assertEquals(3603, repository.datasetInfo().facilityCount)
    }

    @Test
    fun `empty remote response never wipes local data`() = runTest {
        val empty = RemoteDataset(emptyList(), emptyList(), emptyList())
        val onlyFacilities = remoteDataset.copy(entrances = emptyList())

        for (dataset in listOf(empty, onlyFacilities)) {
            val repository = repository(FakePlaceRemoteSource(DataResult.Success(dataset)))

            val result = repository.refreshFromRemote()

            assertTrue(result is DataResult.Failure && result.error is DataError.Invalid)
            assertEquals(3603, repository.datasetInfo().facilityCount)
            assertNull(repository.datasetInfo().lastSyncedAt)
        }
    }

    @Test
    fun `demo data stays available after a sync`() = runTest {
        demoEnabled = true
        val repository = repository(FakePlaceRemoteSource(DataResult.Success(remoteDataset)))

        repository.refreshFromRemote()

        assertNotNull(repository.getFacilityWithEntrances("demo-facility-mall"))
        assertNotNull(repository.getFacilityWithEntrances("remote-mall"))
    }

    @Test
    fun `summaries keep the requested order and skip unknown ids`() = runTest {
        val repository = repository()
        val ids = repository.search(FacilityQuery("", limit = 3)).map { it.facility.id }

        val summaries = repository.getSummaries(listOf(ids[2], "missing", ids[0]))

        assertEquals(listOf(ids[2], ids[0]), summaries.map { it.facility.id })
    }

    @Test
    fun `category counts only include facilities that have vehicle entrances`() = runTest {
        val counts = repository().categoryCounts()

        assertEquals(1442, counts.values.sum())
        assertFalse(FacilityCategory.OTHER in counts)
    }
}
