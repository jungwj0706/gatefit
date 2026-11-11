package com.gatefit.domain.model

import java.time.Instant
import java.time.LocalDate

enum class EntranceDirection { ENTRY, EXIT, BIDIRECTIONAL, UNKNOWN }

enum class EntranceClass { MAIN, SUB, COMPLEX, RESIDENT_ONLY, GROUND, UNDERGROUND, OTHER }

enum class EntranceStatus { ACTIVE, CLOSED, UNKNOWN }

enum class HeightLimitStructure { INSTALLED, NOT_INSTALLED, UNKNOWN }

enum class PermittedVehicleType { GENERAL, RESIDENT, EMERGENCY, SERVICE }

enum class TrustLevel {
    VERIFIED_OFFICIAL,
    VERIFIED_FIELD,
    PUBLIC_BASELINE,
    COMMUNITY_REPORTED,
    UNKNOWN,
}

enum class RouteTargetType {
    ENTRANCE_POINT,
    ROAD_SNAPPED_POINT,
    FACILITY_CENTROID,
}

enum class DataSourceType { PUBLIC, OFFICIAL, FIELD, COMMUNITY }

data class DataSource(
    val id: String,
    val name: String,
    val type: DataSourceType,
    val url: String? = null,
    val license: String? = null,
    val importedAt: Instant? = null,
    val originalUpdatedAt: LocalDate? = null,
    val notes: String? = null,
)

data class VehicleEntrance(
    val id: String,
    val facilityId: String,
    val name: String,
    val latitude: Double?,
    val longitude: Double?,
    val direction: EntranceDirection = EntranceDirection.UNKNOWN,
    val entranceClass: EntranceClass? = null,
    // null은 허용 차량 유형 미확인, 제한 없음으로 해석 금지
    val permittedVehicleTypes: Set<PermittedVehicleType>? = null,
    val maxHeightMm: Int? = null,
    val heightLimitStructure: HeightLimitStructure = HeightLimitStructure.UNKNOWN,
    val maxWidthMm: Int? = null,
    val maxLengthMm: Int? = null,
    val maxWeightKg: Int? = null,
    val hours: WeeklyHours = WeeklyHours(),
    // 요일별 시간표로 표현되지 않는 휴무·예외 조건 원문
    val hoursExceptionNote: String? = null,
    val restrictionNote: String? = null,
    val status: EntranceStatus = EntranceStatus.UNKNOWN,
    val dataSource: DataSource? = null,
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN,
    val verifiedAt: Instant? = null,
    val routeTargetType: RouteTargetType = RouteTargetType.ENTRANCE_POINT,
    val origin: DataOrigin = DataOrigin.LIVE,
    val updatedAtEpochMs: Long = 0L,
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null
}
