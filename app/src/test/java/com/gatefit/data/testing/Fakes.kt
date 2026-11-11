package com.gatefit.data.testing

import com.gatefit.core.result.DataResult
import com.gatefit.data.dto.ReportRequestDto
import com.gatefit.data.local.DatasetCounts
import com.gatefit.data.local.KeyValueStore
import com.gatefit.data.local.PlaceLocalSource
import com.gatefit.data.local.ReportLocalSource
import com.gatefit.data.local.SavedPlacesLocalSource
import com.gatefit.data.local.SearchText
import com.gatefit.data.local.TextAssetSource
import com.gatefit.data.remote.PlaceRemoteSource
import com.gatefit.data.remote.RemoteDataset
import com.gatefit.data.remote.ReportRemoteSource
import com.gatefit.data.remote.ReportSendOutcome
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.DataSource
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceReport
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.domain.repository.FacilityQuery
import java.io.File

// 단위 테스트는 모듈 디렉터리에서 실행되므로 앱에 실제로 포함되는 파일을 그대로 읽어 검증
object BundledAssets : TextAssetSource {
    override fun read(path: String): String = File("src/main/assets", path).readText(Charsets.UTF_8)
}

class CountingAssetSource(private val delegate: TextAssetSource) : TextAssetSource {
    val reads = mutableListOf<String>()

    override fun read(path: String): String {
        reads += path
        return delegate.read(path)
    }
}

class InMemoryPlaceLocalSource : PlaceLocalSource {
    private val facilities = LinkedHashMap<String, Facility>()
    private val entrances = LinkedHashMap<String, VehicleEntrance>()
    private var sources = emptyList<DataSource>()
    private val meta = HashMap<String, String>()
    var replaceCount = 0
        private set

    override fun facilityCount(origin: DataOrigin): Int = facilities.values.count { it.origin == origin }

    override fun replaceAll(
        origin: DataOrigin,
        sources: List<DataSource>,
        facilities: List<Facility>,
        entrances: List<VehicleEntrance>,
    ) {
        replaceCount++
        clear(origin)
        if (origin == DataOrigin.LIVE) this.sources = sources
        facilities.forEach { this.facilities[it.id] = it.copy(origin = origin) }
        entrances.filter { it.facilityId in this.facilities }.forEach { this.entrances[it.id] = it.copy(origin = origin) }
    }

    override fun clear(origin: DataOrigin) {
        facilities.values.removeAll { it.origin == origin }
        entrances.values.removeAll { it.facilityId !in facilities }
    }

    override fun search(query: FacilityQuery, includeDemo: Boolean, freshSinceEpochMs: Long): List<FacilitySummary> {
        val tokens = SearchText.tokens(query.text)
        return visible(includeDemo)
            .filter { query.category == null || it.category == query.category }
            .filter { facility ->
                val text = SearchText.searchable(facility.name, facility.aliases, facility.roadAddress, facility.lotAddress)
                tokens.all { it in text }
            }
            .map { summaryOf(it, freshSinceEpochMs) }
            .sortedWith(compareByDescending<FacilitySummary> { it.entranceCount > 0 }.thenBy { it.facility.name })
            .take(query.limit)
    }

    override fun summaries(
        facilityIds: List<String>,
        includeDemo: Boolean,
        freshSinceEpochMs: Long,
    ): List<FacilitySummary> {
        val byId = visible(includeDemo).associateBy { it.id }
        return facilityIds.mapNotNull(byId::get).map { summaryOf(it, freshSinceEpochMs) }
    }

    override fun facilityWithEntrances(facilityId: String, includeDemo: Boolean): FacilityWithEntrances? {
        val facility = visible(includeDemo).firstOrNull { it.id == facilityId } ?: return null
        return FacilityWithEntrances(facility, entrances.values.filter { it.facilityId == facilityId })
    }

    override fun entrance(entranceId: String, includeDemo: Boolean): EntranceWithFacility? {
        val entrance = entrances[entranceId] ?: return null
        val facility = visible(includeDemo).firstOrNull { it.id == entrance.facilityId } ?: return null
        return EntranceWithFacility(facility, entrance)
    }

    override fun categoryCounts(includeDemo: Boolean): Map<FacilityCategory, Int> =
        visible(includeDemo)
            .filter { facility -> entrances.values.any { it.facilityId == facility.id } }
            .groupingBy { it.category }
            .eachCount()

    override fun liveCounts(freshSinceEpochMs: Long): DatasetCounts {
        val live = entrances.values.filter { it.origin == DataOrigin.LIVE }
        return DatasetCounts(
            facilities = facilityCount(DataOrigin.LIVE),
            entrances = live.size,
            verifiedEntrances = live.count { it.isFresh(freshSinceEpochMs) },
        )
    }

    override fun sources(): List<DataSource> = sources

    override fun getMeta(key: String): String? = meta[key]

    override fun putMeta(key: String, value: String) {
        meta[key] = value
    }

    private fun visible(includeDemo: Boolean) =
        facilities.values.filter { includeDemo || it.origin == DataOrigin.LIVE }

    private fun summaryOf(facility: Facility, freshSinceEpochMs: Long): FacilitySummary {
        val own = entrances.values.filter { it.facilityId == facility.id }
        return FacilitySummary(
            facility = facility,
            entranceCount = own.size,
            heightKnownCount = own.count { it.maxHeightMm != null },
            entryCapableCount = own.count {
                it.direction == EntranceDirection.ENTRY || it.direction == EntranceDirection.BIDIRECTIONAL
            },
            freshEntranceCount = own.count { it.isFresh(freshSinceEpochMs) },
        )
    }

    private fun VehicleEntrance.isFresh(freshSinceEpochMs: Long): Boolean {
        val verified = trustLevel == TrustLevel.VERIFIED_OFFICIAL || trustLevel == TrustLevel.VERIFIED_FIELD
        return verified && (verifiedAt?.toEpochMilli() ?: Long.MIN_VALUE) >= freshSinceEpochMs
    }
}

class FakePlaceRemoteSource(var result: DataResult<RemoteDataset>) : PlaceRemoteSource {
    var fetchCount = 0
        private set

    override suspend fun fetchAll(): DataResult<RemoteDataset> {
        fetchCount++
        return result
    }
}

class InMemoryKeyValueStore : KeyValueStore {
    val values = HashMap<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

class InMemoryReportLocalSource : ReportLocalSource {
    private val reports = LinkedHashMap<String, EntranceReport>()

    override fun insert(report: EntranceReport) {
        check(report.localId !in reports)
        reports[report.localId] = report
    }

    override fun updateDelivery(localId: String, delivery: ReportDelivery, attemptAtEpochMs: Long) {
        reports.computeIfPresent(localId) { _, report ->
            report.copy(delivery = delivery, lastAttemptAtEpochMs = attemptAtEpochMs)
        }
    }

    override fun all(): List<EntranceReport> = reports.values.sortedByDescending { it.createdAtEpochMs }

    override fun undelivered(): List<EntranceReport> =
        all().filter { it.delivery == ReportDelivery.QUEUED || it.delivery == ReportDelivery.FAILED }
}

class FakeReportRemoteSource(var outcome: ReportSendOutcome = ReportSendOutcome.DELIVERED) : ReportRemoteSource {
    val sent = mutableListOf<ReportRequestDto>()

    override suspend fun send(request: ReportRequestDto): ReportSendOutcome {
        sent += request
        return outcome
    }
}

class InMemorySavedPlacesLocalSource : SavedPlacesLocalSource {
    private val favorites = LinkedHashMap<String, Long>()
    private val recents = LinkedHashMap<String, Long>()

    override fun favoriteIds(): List<String> = favorites.entries.sortedByDescending { it.value }.map { it.key }

    override fun setFavorite(facilityId: String, favorite: Boolean, atEpochMs: Long) {
        if (favorite) favorites.putIfAbsent(facilityId, atEpochMs) else favorites.remove(facilityId)
    }

    override fun recentIds(): List<String> = recents.entries.sortedByDescending { it.value }.map { it.key }

    override fun recordRecent(facilityId: String, atEpochMs: Long, keep: Int) {
        recents[facilityId] = atEpochMs
        recentIds().drop(keep).forEach(recents::remove)
    }

    override fun clearRecents() {
        recents.clear()
    }
}
