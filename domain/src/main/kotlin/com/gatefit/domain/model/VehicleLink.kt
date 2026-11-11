package com.gatefit.domain.model

enum class VehicleLinkState {
    IDLE,
    CONNECTING,
    CONNECTED,
    SERVICE_UNAVAILABLE,
    FAILED,
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
    val errorMessage: String? = null,
    val demo: Boolean = false,
)
