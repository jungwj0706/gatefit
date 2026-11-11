package com.gatefit.domain.testing

import com.gatefit.core.result.DataError
import com.gatefit.core.result.DataResult
import com.gatefit.domain.model.EntranceReport
import com.gatefit.domain.model.EntranceReportDraft
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.DatasetInfo
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.ReportRepository
import com.gatefit.domain.repository.SavedPlacesRepository
import com.gatefit.domain.repository.SyncSummary
import com.gatefit.domain.repository.VehicleProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeFacilityRepository(facilities: List<FacilityWithEntrances> = emptyList()) : FacilityRepository {
    val stored = facilities.associateBy { it.facility.id }.toMutableMap()
    val queries = mutableListOf<FacilityQuery>()

    override val dataVersion = MutableStateFlow(0L)

    override suspend fun ensureSeeded() = Unit

    override suspend fun search(query: FacilityQuery): List<FacilitySummary> {
        queries += query
        return stored.values.filter { query.text in it.facility.name }.map(::summaryOf)
    }

    override suspend fun getSummaries(facilityIds: List<String>): List<FacilitySummary> =
        facilityIds.mapNotNull { stored[it] }.map(::summaryOf)

    override suspend fun getFacilityWithEntrances(facilityId: String): FacilityWithEntrances? = stored[facilityId]

    override suspend fun getEntrance(entranceId: String): EntranceWithFacility? =
        stored.values.firstNotNullOfOrNull { facility ->
            facility.entrances.firstOrNull { it.id == entranceId }?.let { EntranceWithFacility(facility.facility, it) }
        }

    override suspend fun categoryCounts(): Map<FacilityCategory, Int> =
        stored.values.groupingBy { it.facility.category }.eachCount()

    override suspend fun datasetInfo() = DatasetInfo(stored.size, 0, 0, emptyList(), null, false)

    override suspend fun refreshFromRemote(): DataResult<SyncSummary> = DataResult.Failure(DataError.NotConfigured)

    private fun summaryOf(facility: FacilityWithEntrances) = FacilitySummary(
        facility = facility.facility,
        entranceCount = facility.entrances.size,
        heightKnownCount = facility.entrances.count { it.maxHeightMm != null },
        entryCapableCount = facility.entrances.size,
        freshEntranceCount = 0,
    )

    companion object {
        fun facility(id: String, name: String = id) = Facility(
            id = id,
            name = name,
            category = FacilityCategory.SHOPPING,
            roadAddress = null,
            lotAddress = null,
            latitude = null,
            longitude = null,
        )
    }
}

class FakeReportRepository : ReportRepository {
    val submitted = mutableListOf<EntranceReportDraft>()
    override val reports = MutableStateFlow<List<EntranceReport>>(emptyList())

    override suspend fun submit(draft: EntranceReportDraft): EntranceReport {
        submitted += draft
        return EntranceReport(
            localId = "report-${submitted.size}",
            entranceId = draft.entranceId,
            type = draft.type,
            description = draft.description,
            proposedHeightMm = draft.proposedHeightMm,
            proposedDirection = draft.proposedDirection,
            delivery = ReportDelivery.QUEUED,
            createdAtEpochMs = 0L,
            lastAttemptAtEpochMs = null,
        )
    }

    override suspend fun retryUndelivered(): Int = 0
}

class FakeVehicleProfileRepository(initial: VehicleProfile? = null) : VehicleProfileRepository {
    private val state = MutableStateFlow(initial)
    override val profile: StateFlow<VehicleProfile?> = state

    override suspend fun save(profile: VehicleProfile) {
        state.value = profile
    }

    override suspend fun clear() {
        state.value = null
    }
}

class FakeSavedPlacesRepository : SavedPlacesRepository {
    val favorites = MutableStateFlow<List<String>>(emptyList())
    val recents = MutableStateFlow<List<String>>(emptyList())

    override val favoriteIds: Flow<List<String>> = favorites
    override val recentIds: Flow<List<String>> = recents

    override suspend fun setFavorite(facilityId: String, favorite: Boolean) {
        favorites.value = if (favorite) listOf(facilityId) + (favorites.value - facilityId) else favorites.value - facilityId
    }

    override suspend fun recordRecent(facilityId: String) {
        recents.value = listOf(facilityId) + (recents.value - facilityId)
    }

    override suspend fun clearRecents() {
        recents.value = emptyList()
    }
}
