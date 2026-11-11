package com.gatefit.data.local

import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.DataSource
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.domain.repository.FacilityQuery

data class DatasetCounts(
    val facilities: Int,
    val entrances: Int,
    val verifiedEntrances: Int,
)

// 모든 함수는 블로킹 호출, 리포지토리에서 IO 디스패처로 감싸 사용
interface PlaceLocalSource {
    fun facilityCount(origin: DataOrigin): Int

    fun replaceAll(
        origin: DataOrigin,
        sources: List<DataSource>,
        facilities: List<Facility>,
        entrances: List<VehicleEntrance>,
    )

    fun clear(origin: DataOrigin)

    fun search(query: FacilityQuery, includeDemo: Boolean, freshSinceEpochMs: Long): List<FacilitySummary>

    fun summaries(facilityIds: List<String>, includeDemo: Boolean, freshSinceEpochMs: Long): List<FacilitySummary>

    fun facilityWithEntrances(facilityId: String, includeDemo: Boolean): FacilityWithEntrances?

    fun entrance(entranceId: String, includeDemo: Boolean): EntranceWithFacility?

    fun categoryCounts(includeDemo: Boolean): Map<FacilityCategory, Int>

    fun liveCounts(freshSinceEpochMs: Long): DatasetCounts

    fun sources(): List<DataSource>

    fun getMeta(key: String): String?

    fun putMeta(key: String, value: String)
}
