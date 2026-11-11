package com.gatefit.navigation

import com.gatefit.domain.model.RouteFailure
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTarget
import com.gatefit.domain.repository.NavigationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PleosNavigationRepository(
    private val client: NaviClient,
    private val scope: CoroutineScope,
    private val resultTimeoutMs: Long = 30_000L,
) : NavigationRepository {

    private val mutableState = MutableStateFlow<RouteRequestState>(RouteRequestState.Idle)
    override val state: StateFlow<RouteRequestState> = mutableState.asStateFlow()

    private val requestLock = Mutex()

    @Volatile
    private var watchdog: Job? = null

    init {
        client.setEventListener(::onEvent)
    }

    override suspend fun requestRoute(target: RouteTarget) = requestLock.withLock {
        watchdog?.cancel()
        mutableState.value = RouteRequestState.Connecting(target)

        if (!client.isServiceInstalled()) {
            fail(target, RouteFailure.NAVI_SERVICE_UNAVAILABLE)
            return@withLock
        }
        when (val connection = client.connect()) {
            NaviConnectResult.Connected -> Unit
            is NaviConnectResult.Failed -> {
                fail(target, connection.failure.toRouteFailure())
                return@withLock
            }
        }

        // 요청 직후 도착하는 콜백을 놓치지 않도록 SDK 호출 전에 상태를 먼저 전환,
        // 연결을 기다리는 동안 사용자가 취소했으면 요청하지 않음
        if (!mutableState.compareAndSet(RouteRequestState.Connecting(target), RouteRequestState.Requested(target))) {
            return@withLock
        }
        try {
            client.requestRoute(target)
        } catch (_: SecurityException) {
            fail(target, RouteFailure.PERMISSION_DENIED)
            return@withLock
        } catch (_: Exception) {
            // 끊어진 연결일 수 있으므로 다음 요청에서 SDK를 다시 초기화
            client.release()
            fail(target, RouteFailure.REQUEST_REJECTED)
            return@withLock
        }
        watchdog = scope.launch {
            delay(resultTimeoutMs)
            expire(target)
        }
    }

    // 콜백 없이 시간이 지나면 성공으로 간주하지 않고 응답 없음으로 처리
    private fun expire(target: RouteTarget) {
        while (true) {
            val pending = mutableState.value
            if (!pending.isPending || pending.targetOrNull() != target) return
            if (mutableState.compareAndSet(pending, RouteRequestState.Failed(target, RouteFailure.NO_RESPONSE))) {
                client.release()
                return
            }
        }
    }

    override fun cancelRoute() {
        val current = mutableState.value
        if (current is RouteRequestState.Idle || current is RouteRequestState.Failed) {
            clearResult()
            return
        }
        watchdog?.cancel()
        mutableState.value = RouteRequestState.Idle
        try {
            client.cancelRoute()
        } catch (_: Exception) {
            // 취소 요청 실패 시 내비게이션 쪽 목적지는 사용자가 내비게이션에서 직접 해제
        }
    }

    override fun clearResult() {
        mutableState.update { current -> if (current.isPending) current else RouteRequestState.Idle }
    }

    override fun release() {
        watchdog?.cancel()
        client.release()
        mutableState.update { current ->
            val target = current.targetOrNull()
            if (current.isPending && target != null) {
                RouteRequestState.Failed(target, RouteFailure.CONNECTION_FAILED)
            } else {
                current
            }
        }
    }

    // 다른 앱이나 사용자가 내비게이션에서 만든 이벤트일 수 있으므로 요청 대기 중일 때만 결과로 반영
    private fun onEvent(event: NaviEvent) {
        mutableState.update { current ->
            when (event) {
                NaviEvent.RouteSearchStarted ->
                    if (current is RouteRequestState.Requested) RouteRequestState.Searching(current.target) else current

                NaviEvent.RouteSearchCompleted -> when (current) {
                    is RouteRequestState.Requested -> RouteRequestState.Confirmed(current.target)
                    is RouteRequestState.Searching -> RouteRequestState.Confirmed(current.target)
                    else -> current
                }

                NaviEvent.RouteCancelled -> when (current) {
                    is RouteRequestState.Requested -> RouteRequestState.Failed(current.target, RouteFailure.CANCELLED)
                    is RouteRequestState.Searching -> RouteRequestState.Failed(current.target, RouteFailure.CANCELLED)
                    is RouteRequestState.Confirmed -> RouteRequestState.Idle
                    else -> current
                }

                is NaviEvent.Error -> when (current) {
                    is RouteRequestState.Requested -> RouteRequestState.Failed(current.target, event.failure)
                    is RouteRequestState.Searching -> RouteRequestState.Failed(current.target, event.failure)
                    else -> current
                }
            }
        }
        if (!mutableState.value.isPending) watchdog?.cancel()
    }

    private fun fail(target: RouteTarget, failure: RouteFailure) {
        mutableState.value = RouteRequestState.Failed(target, failure)
    }

    private fun RouteRequestState.targetOrNull(): RouteTarget? = when (this) {
        RouteRequestState.Idle -> null
        is RouteRequestState.Connecting -> target
        is RouteRequestState.Requested -> target
        is RouteRequestState.Searching -> target
        is RouteRequestState.Confirmed -> target
        is RouteRequestState.Failed -> target
    }

    private fun NaviConnectFailure.toRouteFailure(): RouteFailure = when (this) {
        NaviConnectFailure.PERMISSION_DENIED -> RouteFailure.PERMISSION_DENIED
        NaviConnectFailure.CONNECTION_FAILED -> RouteFailure.CONNECTION_FAILED
        NaviConnectFailure.TIMEOUT -> RouteFailure.NO_RESPONSE
    }
}
