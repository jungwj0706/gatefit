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
    EXIT_ONLY,
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

    // requestRoute 호출만 끝난 상태, 경로 탐색 완료와 구분
    data class Requested(val target: RouteTarget) : RouteRequestState
    data class Searching(val target: RouteTarget) : RouteRequestState

    // 내비게이션의 경로 탐색 완료 콜백을 실제로 받은 상태
    data class Confirmed(val target: RouteTarget) : RouteRequestState
    data class Failed(val target: RouteTarget, val failure: RouteFailure) : RouteRequestState

    val isPending: Boolean
        get() = this is Connecting || this is Requested || this is Searching
}

sealed interface RouteTargetResolution {
    data class Ready(val target: RouteTarget) : RouteTargetResolution
    data class Blocked(val reason: RouteBlockReason) : RouteTargetResolution
}
