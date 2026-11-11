package com.gatefit.domain.repository

import com.gatefit.core.result.DataResult
import com.gatefit.domain.model.DataSource
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.VehicleEntrance
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

data class FacilityQuery(
    val text: String = "",
    val category: FacilityCategory? = null,
    val limit: Int = 50,
)

data class EntranceWithFacility(
    val facility: Facility,
    val entrance: VehicleEntrance,
)

data class DatasetInfo(
    val facilityCount: Int,
    val entranceCount: Int,
    val verifiedEntranceCount: Int,
    val sources: List<DataSource>,
    val lastSyncedAt: Instant?,
    val remoteConfigured: Boolean,
)

data class SyncSummary(
    val facilityCount: Int,
    val entranceCount: Int,
    val syncedAt: Instant,
)

interface FacilityRepository {
    // 저장된 시설 데이터가 바뀔 때마다 증가, 화면 재조회 트리거 용도
    val dataVersion: StateFlow<Long>

    suspend fun ensureSeeded()

    suspend fun search(query: FacilityQuery): List<FacilitySummary>

    suspend fun getSummaries(facilityIds: List<String>): List<FacilitySummary>

    suspend fun getFacilityWithEntrances(facilityId: String): FacilityWithEntrances?

    suspend fun getEntrance(entranceId: String): EntranceWithFacility?

    suspend fun categoryCounts(): Map<FacilityCategory, Int>

    suspend fun datasetInfo(): DatasetInfo

    suspend fun refreshFromRemote(): DataResult<SyncSummary>
}
