package com.gatefit.domain.model

enum class FacilityCategory {
    HOSPITAL,
    SHOPPING,
    HOTEL,
    PUBLIC_OFFICE,
    CULTURE,
    PARKING,
    PARK,
    OTHER,
}

enum class FacilityStatus { OPERATING, CLOSED, UNDER_CONSTRUCTION, UNKNOWN }

// DEMO 데이터는 실제 시설 데이터와 섞이지 않도록 항상 출처 구분
enum class DataOrigin { LIVE, DEMO }

data class Facility(
    val id: String,
    val name: String,
    val category: FacilityCategory,
    val roadAddress: String?,
    val lotAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val aliases: List<String> = emptyList(),
    val status: FacilityStatus = FacilityStatus.UNKNOWN,
    val statusNote: String? = null,
    val origin: DataOrigin = DataOrigin.LIVE,
    val updatedAtEpochMs: Long = 0L,
) {
    val displayAddress: String?
        get() = roadAddress?.takeIf { it.isNotBlank() } ?: lotAddress?.takeIf { it.isNotBlank() }
}

data class FacilitySummary(
    val facility: Facility,
    val entranceCount: Int,
    val heightKnownCount: Int,
    val entryCapableCount: Int,
    val freshEntranceCount: Int,
) {
    val heightUnknownCount: Int get() = entranceCount - heightKnownCount
}

data class FacilityWithEntrances(
    val facility: Facility,
    val entrances: List<VehicleEntrance>,
)
