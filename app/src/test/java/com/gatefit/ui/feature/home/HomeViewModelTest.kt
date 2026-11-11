package com.gatefit.ui.feature.home

import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.usecase.GetFavoriteFacilitiesUseCase
import com.gatefit.domain.usecase.GetRecentFacilitiesUseCase
import com.gatefit.ui.testing.MainDispatcherRule
import com.gatefit.ui.testing.TestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()

    private fun TestScope.viewModel(): HomeViewModel {
        val viewModel = HomeViewModel(
            profiles = environment.profiles,
            vehicleInfo = environment.vehicleInfo,
            facilities = environment.facilities,
            getRecentFacilities = GetRecentFacilitiesUseCase(environment.savedPlaces, environment.facilities),
            getFavoriteFacilities = GetFavoriteFacilitiesUseCase(environment.savedPlaces, environment.facilities),
            calculateEffectiveDimensions = environment.calculateEffectiveDimensions,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `home shows seeded seoul dataset without inventing empty categories`() = runTest(mainRule.dispatcher) {
        val state = viewModel().state.first { !it.loading }

        assertTrue(state.categories.isNotEmpty())
        assertTrue(state.categories.all { it.count > 0 })
        assertEquals(3603, state.dataset?.facilityCount)
        assertEquals(2269, state.dataset?.entranceCount)
        assertNull(state.profile)
        assertTrue(state.recents.isEmpty())
        assertTrue(state.favorites.isEmpty())
    }

    @Test
    fun `registered vehicle height is summarised for analysis`() = runTest(mainRule.dispatcher) {
        environment.profiles.save(
            VehicleProfile(id = "p1", baseDimensions = VehicleDimensions(heightMm = 1700)),
        )

        val state = viewModel().state.first { it.profile != null }

        assertEquals(1700, state.dimensions?.heightMm)
        assertFalse(state.dimensions?.usableForAnalysis == true)
    }
}
