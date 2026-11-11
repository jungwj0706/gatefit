package com.gatefit.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import com.gatefit.domain.model.RouteFailure
import com.gatefit.domain.model.RouteTarget
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class NaviHelperManager(
    context: Context,
    private val connectTimeoutMs: Long = 10_000L,
) : NaviClient {

    private val appContext = context.applicationContext

    @Volatile
    private var helper: NaviHelper? = null

    @Volatile
    private var eventListener: ((NaviEvent) -> Unit)? = null

    private val sdkListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            eventListener?.invoke(NaviEvent.RouteSearchStarted)
        }

        override fun onRoutingCompleted() {
            eventListener?.invoke(NaviEvent.RouteSearchCompleted)
        }

        override fun onRouteCancelled() {
            eventListener?.invoke(NaviEvent.RouteCancelled)
        }

        override fun onError(errorCode: NaviErrorCode) {
            eventListener?.invoke(NaviEvent.Error(failureOf(errorCode)))
        }
    }

    override fun isServiceInstalled(): Boolean =
        appContext.packageManager.queryIntentServices(Intent(NAVI_SERVICE_ACTION), 0).isNotEmpty()

    override suspend fun connect(): NaviConnectResult {
        if (helper != null) return NaviConnectResult.Connected

        val candidate = try {
            NaviHelper(appContext)
        } catch (error: Exception) {
            return NaviConnectResult.Failed(connectFailureOf(error))
        }
        val result: NaviConnectResult = withTimeoutOrNull(connectTimeoutMs) {
            suspendCancellableCoroutine<NaviConnectResult> { continuation ->
                val settled = AtomicBoolean(false)
                fun settle(result: NaviConnectResult) {
                    if (settled.compareAndSet(false, true) && continuation.isActive) continuation.resume(result)
                }
                try {
                    candidate.initialize(
                        { settle(NaviConnectResult.Connected) },
                        { error -> settle(NaviConnectResult.Failed(connectFailureOf(error))) },
                    )
                } catch (error: Exception) {
                    settle(NaviConnectResult.Failed(connectFailureOf(error)))
                }
            }
        } ?: NaviConnectResult.Failed(NaviConnectFailure.TIMEOUT)

        if (result is NaviConnectResult.Connected) {
            candidate.addListener(sdkListener)
            helper = candidate
        } else {
            releaseQuietly(candidate)
        }
        return result
    }

    override fun setEventListener(listener: ((NaviEvent) -> Unit)?) {
        eventListener = listener
    }

    override fun requestRoute(target: RouteTarget) {
        val connected = checkNotNull(helper) { "navi helper is not connected" }
        connected.requestRoute(
            RouteInfo(
                latitude = target.latitude,
                longitude = target.longitude,
                poiName = target.name,
                poiId = NO_POI,
                address = target.address.orEmpty(),
                poiSubId = NO_POI,
            )
        )
    }

    override fun cancelRoute() {
        helper?.cancelRoute()
    }

    // release 이후에는 SDK 객체를 재사용할 수 없으므로 다음 연결에서 새로 생성
    override fun release() {
        val connected = helper ?: return
        helper = null
        releaseQuietly(connected)
    }

    private fun releaseQuietly(target: NaviHelper) {
        try {
            target.removeListener(sdkListener)
            target.release()
        } catch (_: Exception) {
            // 이미 끊어진 연결을 해제할 때의 오류는 복구할 대상이 없음
        }
    }

    companion object {
        const val NAVI_SERVICE_ACTION = "ai.pleos.playground.intent.action.NAVI_START"

        // 시설 POI를 지정하면 시설 대표 위치로 안내될 수 있으므로 POI 없이 출입구 좌표만 전달
        private const val NO_POI = ""

        fun failureOf(errorCode: NaviErrorCode): RouteFailure = when (errorCode) {
            NaviErrorCode.CAUSE_LOW_BATTERY -> RouteFailure.LOW_BATTERY
            NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> RouteFailure.ROUTE_TOO_FAR
            NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> RouteFailure.ROUTE_TOO_SHORT
            NaviErrorCode.CAUSE_SEARCH_ROUTE -> RouteFailure.ROUTE_SEARCH_FAILED
            else -> RouteFailure.ROUTE_SEARCH_FAILED
        }

        fun connectFailureOf(error: Throwable): NaviConnectFailure =
            if (error is SecurityException) NaviConnectFailure.PERMISSION_DENIED else NaviConnectFailure.CONNECTION_FAILED
    }
}
