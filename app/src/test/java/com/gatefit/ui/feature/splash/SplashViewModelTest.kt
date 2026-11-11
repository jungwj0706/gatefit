package com.gatefit.ui.feature.splash

import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.ui.testing.FakeVehicleInfoRepository
import com.gatefit.ui.testing.MainDispatcherRule
import com.gatefit.ui.testing.TestEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SplashViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()

    private fun viewModel(scope: CoroutineScope) = SplashViewModel(
        facilities = environment.facilities,
        vehicleInfo = environment.vehicleInfo,
        settings = environment.settings,
        profiles = environment.profiles,
        reports = environment.reports,
        appScope = scope,
    )

    @Test
    fun `first launch goes to vehicle setup after data is seeded`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel(backgroundScope)

        val finished = viewModel.state.first { it.destination != null }

        assertEquals(SplashDestination.VEHICLE_SETUP, finished.destination)
        assertEquals(DataPreparation.READY, finished.data)
        assertTrue(environment.facilities.datasetInfo().facilityCount > 0)
    }

    @Test
    fun `returning user with a saved profile goes straight to home`() = runTest(mainRule.dispatcher) {
        environment.profiles.save(VehicleProfile(id = "p1", baseDimensions = VehicleDimensions(heightMm = 1700)))
        environment.settings.update { it.copy(onboardingCompleted = true) }

        val finished = viewModel(backgroundScope).state.first { it.destination != null }

        assertEquals(SplashDestination.HOME, finished.destination)
    }

    @Test
    fun `completed onboarding without a profile returns to vehicle setup`() = runTest(mainRule.dispatcher) {
        environment.settings.update { it.copy(onboardingCompleted = true) }

        val finished = viewModel(backgroundScope).state.first { it.destination != null }

        assertEquals(SplashDestination.VEHICLE_SETUP, finished.destination)
    }

    @Test
    fun `unavailable vehicle sdk does not block the app`() = runTest(mainRule.dispatcher) {
        val finished = viewModel(backgroundScope).state.first { it.destination != null }

        assertEquals(VehicleLinkState.SERVICE_UNAVAILABLE, finished.vehicleLink.state)
        assertEquals(1, environment.vehicleInfo.connectCount)
    }

    @Test
    fun `connected vehicle status is shown`() = runTest(mainRule.dispatcher) {
        environment.vehicleInfo.connectResult = FakeVehicleInfoRepository.connected()

        val finished = viewModel(backgroundScope).state.first { it.destination != null }

        assertEquals(VehicleLinkState.CONNECTED, finished.vehicleLink.state)
    }
}
