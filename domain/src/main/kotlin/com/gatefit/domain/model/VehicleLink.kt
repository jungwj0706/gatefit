package com.gatefit.domain.model

enum class VehicleLinkState {
    IDLE,
    CONNECTING,
    CONNECTED,
    SERVICE_UNAVAILABLE,
    FAILED,
}

enum class VehicleLinkFailure {
    SERVICE_NOT_INSTALLED,
    CONNECTION_FAILED,
    NO_RESPONSE,
    PERMISSION_DENIED,
    NO_DATA,
    SDK_ERROR,
}

enum class CapabilitySupport { SUPPORTED, UNSUPPORTED, UNKNOWN }

// 공개 CarInfo API가 제공하는 항목은 VIN·CRN 뿐이라 외부 제원 직접 조회 불가
data class VehicleCapabilities(
    val vinInfo: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val dimensions: CapabilitySupport = CapabilitySupport.UNSUPPORTED,
)

// VIN 원문은 보관하지 않고 제조사 코드와 연식 코드 해석 결과만 전달
data class VehicleIdentityHint(
    val manufacturer: String?,
    val modelYear: Int?,
    val demo: Boolean = false,
)

data class VehicleLinkStatus(
    val state: VehicleLinkState = VehicleLinkState.IDLE,
    val capabilities: VehicleCapabilities = VehicleCapabilities(),
    val failure: VehicleLinkFailure? = null,
    val demo: Boolean = false,
)

sealed interface VehicleInfoResult {
    data class Available(val hint: VehicleIdentityHint) : VehicleInfoResult

    // 차량이 해당 정보를 제공하지 않는 상태, 오류와 구분
    data object Unsupported : VehicleInfoResult
    data class Failed(val failure: VehicleLinkFailure) : VehicleInfoResult
}
