package com.gatefit.ui.feature.navigation

import com.gatefit.domain.model.RouteBlockReason
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTargetResolution
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.usecase.GetEntranceDetailsUseCase
import com.gatefit.domain.usecase.RequestEntranceNavigationUseCase
import com.gatefit.domain.usecase.ResolveRouteTargetUseCase
import com.gatefit.ui.testing.MainDispatcherRule
import com.gatefit.ui.testing.TestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationHandoffViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()
    private val resolve = ResolveRouteTargetUseCase()

    private fun TestScope.viewModel(entranceId: String): NavigationHandoffViewModel {
        val viewModel = NavigationHandoffViewModel(
            entranceId = entranceId,
            facilities = environment.facilities,
            getEntranceDetails = GetEntranceDetailsUseCase(environment.facilities),
            requestNavigation = RequestEntranceNavigationUseCase(environment.navigation, resolve),
            navigation = environment.navigation,
            resolveRouteTarget = resolve,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `missing entrance cannot start a route request`() = runTest(mainRule.dispatcher) {
        val state = viewModel("missing").state.first { it.content !is NavigationHandoffContent.Loading }
        assertEquals(NavigationHandoffContent.NotFound, state.content)
        assertEquals(RouteRequestState.Idle, state.routeState)
    }

    @Test
    fun `demo entrance is blocked instead of sending a fake destination`() = runTest(mainRule.dispatcher) {
        environment.settings.update { it.copy(demoDataEnabled = true) }
        val viewModel = viewModel("demo-entrance-mall-a")

        val ready = viewModel.state.first { it.content is NavigationHandoffContent.Ready }
            .content as NavigationHandoffContent.Ready
        val blocked = ready.resolution as RouteTargetResolution.Blocked
        assertEquals(RouteBlockReason.DEMO_DATA, blocked.reason)

        viewModel.requestRoute()
        advanceUntilIdle()
        assertTrue(environment.navigation.requested.isEmpty())
        assertEquals(RouteRequestState.Idle, viewModel.state.value.routeState)
    }

    @Test
    fun `requesting a route does not mark navigation as confirmed`() = runTest(mainRule.dispatcher) {
        environment.facilities.ensureSeeded()
        var navigable = null as com.gatefit.domain.model.VehicleEntrance?
        for (summary in environment.facilities.search(FacilityQuery(limit = 50))) {
            val facility = environment.facilities.getFacilityWithEntrances(summary.facility.id) ?: continue
            navigable = facility.entrances.firstOrNull { resolve(facility.facility, it) is RouteTargetResolution.Ready }
            if (navigable != null) break
        }
        val entrance = checkNotNull(navigable)

        val viewModel = viewModel(entrance.id)
        viewModel.state.first { it.content is NavigationHandoffContent.Ready }

        viewModel.requestRoute()
        advanceUntilIdle()

        val routeState = viewModel.state.value.routeState
        assertTrue(routeState is RouteRequestState.Requested)
        assertTrue(routeState !is RouteRequestState.Confirmed)
        assertEquals(1, environment.navigation.requested.size)
    }
}
