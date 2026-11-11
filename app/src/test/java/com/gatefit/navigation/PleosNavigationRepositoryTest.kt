package com.gatefit.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import com.gatefit.domain.model.RouteFailure
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTarget
import com.gatefit.domain.model.RouteTargetType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosNavigationRepositoryTest {

    private class FakeNaviClient(
        var installed: Boolean = true,
        var connectResult: NaviConnectResult = NaviConnectResult.Connected,
        var requestError: Exception? = null,
    ) : NaviClient {
        var listener: ((NaviEvent) -> Unit)? = null
        var connectGate: CompletableDeferred<Unit>? = null
        var connectCount = 0
        var releaseCount = 0
        var cancelCount = 0
        val routeRequests = mutableListOf<RouteTarget>()

        override fun isServiceInstalled(): Boolean = installed

        override suspend fun connect(): NaviConnectResult {
            connectCount++
            connectGate?.await()
            return connectResult
        }

        override fun setEventListener(listener: ((NaviEvent) -> Unit)?) {
            this.listener = listener
        }

        override fun requestRoute(target: RouteTarget) {
            requestError?.let { throw it }
            routeRequests += target
        }

        override fun cancelRoute() {
            cancelCount++
        }

        override fun release() {
            releaseCount++
        }

        fun emit(event: NaviEvent) {
            listener?.invoke(event)
        }
    }

    private val target = RouteTarget(
        entranceId = "e1",
        latitude = 37.5012,
        longitude = 127.0034,
        name = "가상 쇼핑센터 B게이트",
        address = null,
        targetType = RouteTargetType.ENTRANCE_POINT,
    )
    private val otherTarget = target.copy(entranceId = "e2", name = "가상 쇼핑센터 C게이트")

    private fun TestScope.repository(client: FakeNaviClient) =
        PleosNavigationRepository(client, backgroundScope, resultTimeoutMs = 30_000L)

    private fun failureOf(state: RouteRequestState): RouteFailure? = (state as? RouteRequestState.Failed)?.failure

    @Test
    fun `calling request route alone is not treated as success`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)

        repository.requestRoute(target)

        assertEquals(RouteRequestState.Requested(target), repository.state.value)
        assertEquals(listOf(target), client.routeRequests)
    }

    @Test
    fun `route is confirmed only after the search completed callback`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)

        client.emit(NaviEvent.RouteSearchStarted)
        assertEquals(RouteRequestState.Searching(target), repository.state.value)

        client.emit(NaviEvent.RouteSearchCompleted)
        assertEquals(RouteRequestState.Confirmed(target), repository.state.value)
    }

    @Test
    fun `completion without a start callback still confirms the pending request`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)

        client.emit(NaviEvent.RouteSearchCompleted)

        assertEquals(RouteRequestState.Confirmed(target), repository.state.value)
    }

    @Test
    fun `navigation events without a pending request are ignored`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)

        client.emit(NaviEvent.RouteSearchStarted)
        client.emit(NaviEvent.RouteSearchCompleted)
        client.emit(NaviEvent.Error(RouteFailure.ROUTE_SEARCH_FAILED))

        assertEquals(RouteRequestState.Idle, repository.state.value)
    }

    @Test
    fun `navigation error fails the pending request with its cause`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)
        client.emit(NaviEvent.RouteSearchStarted)

        client.emit(NaviEvent.Error(RouteFailure.ROUTE_TOO_SHORT))

        assertEquals(RouteRequestState.Failed(target, RouteFailure.ROUTE_TOO_SHORT), repository.state.value)
    }

    @Test
    fun `missing navigation service fails without connecting`() = runTest {
        val client = FakeNaviClient(installed = false)
        val repository = repository(client)

        repository.requestRoute(target)

        assertEquals(RouteFailure.NAVI_SERVICE_UNAVAILABLE, failureOf(repository.state.value))
        assertEquals(0, client.connectCount)
    }

    @Test
    fun `connection failures are reported with their cause`() = runTest {
        val failures = mapOf(
            NaviConnectFailure.CONNECTION_FAILED to RouteFailure.CONNECTION_FAILED,
            NaviConnectFailure.PERMISSION_DENIED to RouteFailure.PERMISSION_DENIED,
            NaviConnectFailure.TIMEOUT to RouteFailure.NO_RESPONSE,
        )

        failures.forEach { (connectFailure, expected) ->
            val client = FakeNaviClient(connectResult = NaviConnectResult.Failed(connectFailure))
            val repository = repository(client)

            repository.requestRoute(target)

            assertEquals(expected, failureOf(repository.state.value))
            assertTrue(client.routeRequests.isEmpty())
        }
    }

    @Test
    fun `rejected request is reported and forces a reconnect next time`() = runTest {
        val client = FakeNaviClient(requestError = IllegalStateException("not connected"))
        val repository = repository(client)

        repository.requestRoute(target)

        assertEquals(RouteFailure.REQUEST_REJECTED, failureOf(repository.state.value))
        assertEquals(1, client.releaseCount)
    }

    @Test
    fun `permission error on request is reported as permission denied`() = runTest {
        val client = FakeNaviClient(requestError = SecurityException("NAVI_ROUTE_SEARCH"))
        val repository = repository(client)

        repository.requestRoute(target)

        assertEquals(RouteFailure.PERMISSION_DENIED, failureOf(repository.state.value))
    }

    @Test
    fun `request without any callback times out instead of succeeding`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)

        advanceTimeBy(29_000L)
        assertEquals(RouteRequestState.Requested(target), repository.state.value)

        advanceTimeBy(2_000L)
        assertEquals(RouteFailure.NO_RESPONSE, failureOf(repository.state.value))
        assertEquals(1, client.releaseCount)
    }

    @Test
    fun `timeout does not override a confirmed route`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)
        client.emit(NaviEvent.RouteSearchCompleted)

        advanceTimeBy(60_000L)

        assertEquals(RouteRequestState.Confirmed(target), repository.state.value)
        assertEquals(0, client.releaseCount)
    }

    @Test
    fun `cancellation from the navigation app fails the pending request`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)

        client.emit(NaviEvent.RouteCancelled)

        assertEquals(RouteFailure.CANCELLED, failureOf(repository.state.value))
    }

    @Test
    fun `user cancellation is forwarded to the navigation app`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)
        client.emit(NaviEvent.RouteSearchCompleted)

        repository.cancelRoute()

        assertEquals(RouteRequestState.Idle, repository.state.value)
        assertEquals(1, client.cancelCount)
    }

    @Test
    fun `cancelling while connecting prevents the route request`() = runTest {
        val client = FakeNaviClient().apply { connectGate = CompletableDeferred() }
        val repository = repository(client)
        val request = launch { repository.requestRoute(target) }
        runCurrent()
        assertEquals(RouteRequestState.Connecting(target), repository.state.value)

        repository.cancelRoute()
        client.connectGate?.complete(Unit)
        request.join()

        assertEquals(RouteRequestState.Idle, repository.state.value)
        assertTrue(client.routeRequests.isEmpty())
    }

    @Test
    fun `new request replaces the previous destination`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)
        client.emit(NaviEvent.RouteSearchCompleted)

        repository.requestRoute(otherTarget)
        advanceTimeBy(29_000L)
        client.emit(NaviEvent.RouteSearchCompleted)

        assertEquals(RouteRequestState.Confirmed(otherTarget), repository.state.value)
        assertEquals(listOf(target, otherTarget), client.routeRequests)
    }

    @Test
    fun `clearing a result keeps the destination set in the navigation app`() = runTest {
        val client = FakeNaviClient()
        val repository = repository(client)
        repository.requestRoute(target)
        client.emit(NaviEvent.RouteSearchCompleted)

        repository.clearResult()

        assertEquals(RouteRequestState.Idle, repository.state.value)
        assertEquals(0, client.cancelCount)
    }

    @Test
    fun `sdk error codes map to distinct route failures`() {
        assertEquals(RouteFailure.LOW_BATTERY, NaviHelperManager.failureOf(NaviErrorCode.CAUSE_LOW_BATTERY))
        assertEquals(RouteFailure.ROUTE_TOO_FAR, NaviHelperManager.failureOf(NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE))
        assertEquals(
            RouteFailure.ROUTE_TOO_SHORT,
            NaviHelperManager.failureOf(NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE),
        )
        assertEquals(RouteFailure.ROUTE_SEARCH_FAILED, NaviHelperManager.failureOf(NaviErrorCode.CAUSE_SEARCH_ROUTE))
        assertEquals(
            NaviConnectFailure.PERMISSION_DENIED,
            NaviHelperManager.connectFailureOf(SecurityException("denied")),
        )
        assertEquals(NaviConnectFailure.CONNECTION_FAILED, NaviHelperManager.connectFailureOf(RuntimeException()))
    }
}
