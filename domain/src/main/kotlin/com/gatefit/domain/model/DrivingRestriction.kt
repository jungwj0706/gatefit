package com.gatefit.domain.model

enum class RestrictionSource {
    // 차량 플랫폼이 알려 준 실제 제한 상태
    CAR_UX_RESTRICTIONS,

    // 차량 환경이 아니어서 주행 제한 대상이 아닌 상태
    NOT_AUTOMOTIVE,

    // 차량 환경이지만 제한 상태를 확인하지 못한 상태
    UNAVAILABLE,
}

enum class RestrictedFeature {
    FACILITY_SEARCH_INPUT,
    VEHICLE_PROFILE_EDIT,
    REPORT_WRITING,
    DETAIL_BROWSING,
}

data class DrivingRestrictions(
    val source: RestrictionSource,
    val requiresDistractionOptimization: Boolean,
    val keyboardBlocked: Boolean,
    val setupBlocked: Boolean,
    val contentLimited: Boolean,
) {
    val restricted: Boolean
        get() = requiresDistractionOptimization

    fun allows(feature: RestrictedFeature): Boolean = when (feature) {
        RestrictedFeature.FACILITY_SEARCH_INPUT -> !requiresDistractionOptimization && !keyboardBlocked
        RestrictedFeature.VEHICLE_PROFILE_EDIT -> !requiresDistractionOptimization && !setupBlocked && !keyboardBlocked
        RestrictedFeature.REPORT_WRITING -> !requiresDistractionOptimization && !keyboardBlocked
        RestrictedFeature.DETAIL_BROWSING -> !requiresDistractionOptimization && !contentLimited
    }

    companion object {
        val UNRESTRICTED_NOT_AUTOMOTIVE = DrivingRestrictions(
            source = RestrictionSource.NOT_AUTOMOTIVE,
            requiresDistractionOptimization = false,
            keyboardBlocked = false,
            setupBlocked = false,
            contentLimited = false,
        )

        // 차량에서 제한 상태를 확인하지 못하면 플랫폼 기본값과 같이 전체 제한으로 취급
        val RESTRICTED_UNAVAILABLE = DrivingRestrictions(
            source = RestrictionSource.UNAVAILABLE,
            requiresDistractionOptimization = true,
            keyboardBlocked = true,
            setupBlocked = true,
            contentLimited = true,
        )
    }
}
