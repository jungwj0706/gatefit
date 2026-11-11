package com.gatefit.data.repository

import com.gatefit.core.result.DataError
import com.gatefit.core.result.DataResult
import com.gatefit.data.local.AssetPaths
import com.gatefit.data.local.BundledData
import com.gatefit.data.local.PlaceLocalSource
import com.gatefit.data.local.TextAssetSource
import com.gatefit.data.mapper.toDomain
import com.gatefit.data.remote.PlaceRemoteSource
import com.gatefit.data.remote.RemoteDataset
import com.gatefit.domain.analysis.FreshnessPolicy
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.repository.DatasetInfo
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.SyncSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class OfflineFirstFacilityRepository(
    private val local: PlaceLocalSource,
    private val assets: TextAssetSource,
    private val remote: PlaceRemoteSource?,
    private val demoEnabled: () -> Boolean,
    private val seedVersion: String,
    private val clock: () -> Instant,
    private val io: CoroutineDispatcher,
    private val freshness: FreshnessPolicy = FreshnessPolicy(),
) : FacilityRepository {

    private val version = MutableStateFlow(0L)
    override val dataVersion: StateFlow<Long> = version.asStateFlow()

    private val writeLock = Mutex()

    @Volatile
    private var seeded = false

    // DEMO 표시 설정처럼 조회 결과에 영향을 주는 조건이 바뀌었을 때 호출
    fun invalidate() {
        version.update { it + 1 }
    }

    override suspend fun ensureSeeded() {
        if (seeded) return
        writeLock.withLock {
            if (seeded) return
            withContext(io) {
                seedBaselineIfNeeded()
                seedDemo()
            }
            seeded = true
        }
        invalidate()
    }

    private fun seedBaselineIfNeeded() {
        val neverSynced = local.getMeta(META_LAST_SYNC) == null
        val bundledChanged = local.getMeta(META_SEED_VERSION) != seedVersion
        // 서버 동기화 이력이 있으면 내장 스냅샷보다 최신이므로 덮어쓰지 않음
        if (local.facilityCount(DataOrigin.LIVE) > 0 && !(neverSynced && bundledChanged)) return

        val snapshot = BundledData.parseSnapshot(assets.read(AssetPaths.BASELINE_SNAPSHOT), DataOrigin.LIVE)
        local.replaceAll(DataOrigin.LIVE, snapshot.sources, snapshot.facilities, snapshot.entrances)
        local.putMeta(META_SEED_VERSION, seedVersion)
    }

    private fun seedDemo() {
        val demo = BundledData.parseDemoSnapshot(
            assets.read(AssetPaths.DEMO_PLACES),
            verifiedAt = clock().minus(DEMO_VERIFICATION_AGE),
        )
        local.replaceAll(DataOrigin.DEMO, demo.sources, demo.facilities, demo.entrances)
    }

    override suspend fun search(query: FacilityQuery): List<FacilitySummary> = read {
        local.search(query, demoEnabled(), freshSince())
    }

    override suspend fun getSummaries(facilityIds: List<String>): List<FacilitySummary> = read {
        local.summaries(facilityIds, demoEnabled(), freshSince())
    }

    override suspend fun getFacilityWithEntrances(facilityId: String): FacilityWithEntrances? = read {
        local.facilityWithEntrances(facilityId, demoEnabled())
    }

    override suspend fun getEntrance(entranceId: String): EntranceWithFacility? = read {
        local.entrance(entranceId, demoEnabled())
    }

    override suspend fun categoryCounts(): Map<FacilityCategory, Int> = read {
        local.categoryCounts(demoEnabled())
    }

    override suspend fun datasetInfo(): DatasetInfo = read {
        val counts = local.liveCounts(freshSince())
        DatasetInfo(
            facilityCount = counts.facilities,
            entranceCount = counts.entrances,
            verifiedEntranceCount = counts.verifiedEntrances,
            sources = local.sources(),
            lastSyncedAt = local.getMeta(META_LAST_SYNC)?.toLongOrNull()?.let(Instant::ofEpochMilli),
            remoteConfigured = remote != null,
        )
    }

    override suspend fun refreshFromRemote(): DataResult<SyncSummary> {
        val source = remote ?: return DataResult.Failure(DataError.NotConfigured)
        ensureSeeded()
        val result = when (val fetched = source.fetchAll()) {
            is DataResult.Failure -> fetched
            is DataResult.Success -> writeLock.withLock { withContext(io) { store(fetched.value) } }
        }
        if (result is DataResult.Success) invalidate()
        return result
    }

    private fun store(dataset: RemoteDataset): DataResult<SyncSummary> {
        val sources = dataset.sources.map { it.toDomain() }
        val sourcesById = sources.associateBy { it.id }
        val facilities = dataset.facilities.map { it.toDomain(DataOrigin.LIVE) }.distinctBy { it.id }
        val facilityIds = facilities.mapTo(HashSet()) { it.id }
        val entrances = dataset.entrances
            .map { it.toDomain(DataOrigin.LIVE, sourcesById) }
            .filter { it.facilityId in facilityIds }
            .distinctBy { it.id }

        // 비어 있는 응답으로 기기에 있는 데이터를 지우지 않도록 보호
        if (facilities.isEmpty() || entrances.isEmpty()) {
            return DataResult.Failure(DataError.Invalid("empty_remote_dataset"))
        }

        val syncedAt = clock()
        local.replaceAll(DataOrigin.LIVE, sources, facilities, entrances)
        local.putMeta(META_LAST_SYNC, syncedAt.toEpochMilli().toString())
        return DataResult.Success(SyncSummary(facilities.size, entrances.size, syncedAt))
    }

    private suspend fun <T> read(block: () -> T): T {
        ensureSeeded()
        return withContext(io) { block() }
    }

    private fun freshSince(): Long = clock().minus(Duration.ofDays(freshness.maxVerifiedAgeDays)).toEpochMilli()

    companion object {
        const val META_LAST_SYNC = "last_sync_at"
        const val META_SEED_VERSION = "seed_version"
        private val DEMO_VERIFICATION_AGE: Duration = Duration.ofDays(7)
    }
}
