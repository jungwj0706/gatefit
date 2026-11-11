package com.gatefit.domain.model

data class RouteTarget(
    val entranceId: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String?,
    val targetType: RouteTargetType,
)

enum class RouteBlockReason {
    COORDINATES_MISSING,
    COORDINATES_INVALID,
    FACILITY_CENTROID_ONLY,
    DEMO_DATA,
    ENTRANCE_CLOSED,
}

enum class RouteFailure {
    NAVI_SERVICE_UNAVAILABLE,
    CONNECTION_FAILED,
    PERMISSION_DENIED,
    REQUEST_REJECTED,
    ROUTE_SEARCH_FAILED,
    ROUTE_TOO_FAR,
    ROUTE_TOO_SHORT,
    LOW_BATTERY,
    NO_RESPONSE,
    CANCELLED,
}

sealed interface RouteRequestState {
    data object Idle : RouteRequestState
    data class Connecting(val target: RouteTarget) : RouteRequestState

    // requestRoute 호출 완료 상태, 경로 탐색 완료와 구분
    data class Requested(val target: RouteTarget) : RouteRequestState
    data class Searching(val target: RouteTarget) : RouteRequestState
    data class Guiding(val target: RouteTarget, val distanceMeters: Int?, val durationSeconds: Int?) : RouteRequestState
    data class Failed(val target: RouteTarget, val failure: RouteFailure, val detail: String? = null) : RouteRequestState
}
