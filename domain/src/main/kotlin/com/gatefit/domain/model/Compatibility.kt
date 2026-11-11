package com.gatefit.domain.model

import java.time.LocalDateTime

enum class CompatibilityStatus {
    // 확인된 필수 조건 충족, 실제 진입 가능성 보증 아님
    CONDITION_MATCH,
    NEEDS_CONFIRMATION,
    CONDITION_MISMATCH,
}

enum class ConstraintType {
    HEIGHT,
    WIDTH,
    LENGTH,
    WEIGHT,
    DIRECTION,
    OPERATING_HOURS,
    VEHICLE_TYPE,
    ENTRANCE_STATUS,
    DATA_TRUST,
}

enum class CheckOutcome {
    PASSED,
    FAILED,
    UNKNOWN,
    // 출입구 측 제한 정보 자체가 없는 선택 조건, 충족으로 표시 금지
    NOT_PROVIDED,
}

enum class CheckReason {
    WITHIN_LIMIT,
    EXCEEDS_LIMIT,
    MARGIN_INSUFFICIENT,
    VEHICLE_VALUE_MISSING,
    VEHICLE_VALUE_UNCONFIRMED,
    LIMIT_NOT_PROVIDED,
    HEIGHT_STRUCTURE_NOT_INSTALLED,

    DIRECTION_ENTRY,
    DIRECTION_BIDIRECTIONAL,
    DIRECTION_EXIT_ONLY,
    DIRECTION_UNKNOWN,

    HOURS_OPEN,
    HOURS_ALL_DAY,
    HOURS_OUTSIDE,
    HOURS_CLOSED_DAY,
    HOURS_NOT_PROVIDED,
    HOLIDAY_HOURS_NOT_PROVIDED,
    HOLIDAY_STATUS_UNKNOWN,

    VEHICLE_TYPE_PERMITTED,
    VEHICLE_TYPE_NOT_PERMITTED,
    VEHICLE_TYPE_NOT_PROVIDED,

    ENTRANCE_ACTIVE,
    ENTRANCE_CLOSED,
    ENTRANCE_STATUS_UNKNOWN,

    TRUST_FRESH,
    TRUST_STALE_VERIFICATION,
    TRUST_VERIFICATION_DATE_MISSING,
    TRUST_BASELINE_NOT_REVERIFIED,
    TRUST_COMMUNITY_UNVERIFIED,
    TRUST_SOURCE_UNKNOWN,
}

data class ConstraintCheck(
    val type: ConstraintType,
    val outcome: CheckOutcome,
    val reason: CheckReason,
    val required: Boolean,
    val vehicleValue: Int? = null,
    val limitValue: Int? = null,
    val marginValue: Int? = null,
)

enum class DataFreshness {
    FRESH,
    NEEDS_RECHECK,
    STALE_BASELINE,
    UNTRUSTED,
}

data class CompatibilityResult(
    val entranceId: String,
    val status: CompatibilityStatus,
    val checks: List<ConstraintCheck>,
    val freshness: DataFreshness,
    val evaluatedAt: LocalDateTime,
    val demo: Boolean = false,
) {
    fun check(type: ConstraintType): ConstraintCheck? = checks.firstOrNull { it.type == type }

    val failedChecks: List<ConstraintCheck> get() = checks.filter { it.outcome == CheckOutcome.FAILED }

    val unknownChecks: List<ConstraintCheck> get() = checks.filter { it.outcome == CheckOutcome.UNKNOWN }

    val notProvidedChecks: List<ConstraintCheck> get() = checks.filter { it.outcome == CheckOutcome.NOT_PROVIDED }

    val heightPassed: Boolean get() = check(ConstraintType.HEIGHT)?.outcome == CheckOutcome.PASSED
}

data class EntranceAnalysis(
    val entrance: VehicleEntrance,
    val result: CompatibilityResult,
)

data class FacilityAnalysis(
    val facility: Facility,
    val vehicle: EffectiveDimensions,
    val safetyMarginMm: Int,
    val entrances: List<EntranceAnalysis>,
    val evaluatedAt: LocalDateTime,
) {
    val matchCount: Int get() = entrances.count { it.result.status == CompatibilityStatus.CONDITION_MATCH }
    val needsConfirmationCount: Int get() = entrances.count { it.result.status == CompatibilityStatus.NEEDS_CONFIRMATION }
    val mismatchCount: Int get() = entrances.count { it.result.status == CompatibilityStatus.CONDITION_MISMATCH }
    val hasNoMatch: Boolean get() = entrances.isNotEmpty() && matchCount == 0
}
