package com.gatefit.ui.feature.saved

import com.gatefit.domain.repository.FacilityQuery
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedPlacesViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()

    private fun TestScope.viewModel(kind: String): SavedPlacesViewModel {
        val viewModel = SavedPlacesViewModel(
            kind = kind,
            savedPlaces = environment.savedPlaces,
            getRecentFacilities = GetRecentFacilitiesUseCase(environment.savedPlaces, environment.facilities),
            getFavoriteFacilities = GetFavoriteFacilitiesUseCase(environment.savedPlaces, environment.facilities),
        )
        backgroundScope.launch { viewModel.places.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `recent list can be cleared without touching favorites`() = runTest(mainRule.dispatcher) {
        environment.facilities.ensureSeeded()
        val facilityId = environment.facilities.search(FacilityQuery(limit = 1)).first().facility.id
        environment.savedPlaces.recordRecent(facilityId)
        environment.savedPlaces.setFavorite(facilityId, true)

        val recents = viewModel(SavedPlacesViewModel.KIND_RECENT)
        assertEquals(listOf(facilityId), recents.places.first { it.isNotEmpty() }.map { it.facility.id })

        recents.clearRecents()
        advanceUntilIdle()
        assertTrue(recents.places.first { it.isEmpty() }.isEmpty())
        assertEquals(listOf(facilityId), environment.savedPlaces.favoriteIds.first())
    }
}
