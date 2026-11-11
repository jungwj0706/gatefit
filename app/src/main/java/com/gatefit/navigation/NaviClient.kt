package com.gatefit.navigation

import com.gatefit.domain.model.RouteFailure
import com.gatefit.domain.model.RouteTarget

enum class NaviConnectFailure { PERMISSION_DENIED, CONNECTION_FAILED, TIMEOUT }

sealed interface NaviConnectResult {
    data object Connected : NaviConnectResult
    data class Failed(val failure: NaviConnectFailure) : NaviConnectResult
}

sealed interface NaviEvent {
    data object RouteSearchStarted : NaviEvent
    data object RouteSearchCompleted : NaviEvent
    data object RouteCancelled : NaviEvent
    data class Error(val failure: RouteFailure) : NaviEvent
}

// NaviHelper SDK 호출을 감싸는 경계, 단위 테스트에서는 가짜 구현으로 대체
interface NaviClient {
    fun isServiceInstalled(): Boolean

    suspend fun connect(): NaviConnectResult

    fun setEventListener(listener: ((NaviEvent) -> Unit)?)

    fun requestRoute(target: RouteTarget)

    fun cancelRoute()

    fun release()
}
