package com.gatefit.ui.feature.search

import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.usecase.GetRecentFacilitiesUseCase
import com.gatefit.domain.usecase.SearchFacilitiesUseCase
import com.gatefit.ui.testing.MainDispatcherRule
import com.gatefit.ui.testing.TestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()

    private fun TestScope.viewModel(category: FacilityCategory? = null): SearchViewModel {
        val viewModel = SearchViewModel(
            initialCategory = category,
            facilities = environment.facilities,
            searchFacilities = SearchFacilitiesUseCase(environment.facilities),
            getRecentFacilities = GetRecentFacilitiesUseCase(environment.savedPlaces, environment.facilities),
            debounceMs = 0L,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `empty query does not search the catalog`() = runTest(mainRule.dispatcher) {
        val state = viewModel().state.first { it.registeredFacilityCount != null }

        assertTrue(state.criteria.isEmpty)
        assertEquals(emptyList<Any>(), state.results)
        assertEquals(3603, state.registeredFacilityCount)
        assertTrue(state.categories.isNotEmpty())
    }

    @Test
    fun `query returns registered facilities only`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("병원")
        advanceUntilIdle()

        val state = viewModel.state.first { it.results?.isNotEmpty() == true }
        assertTrue(state.results!!.isNotEmpty())
        assertTrue(state.results!!.none { it.facility.origin == com.gatefit.domain.model.DataOrigin.DEMO })
    }

    @Test
    fun `unknown query shows an empty result list`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.onQueryChange("존재하지않는시설명칭xyz")
        advanceUntilIdle()

        val state = viewModel.state.first { it.results != null && !it.criteria.isEmpty }
        assertTrue(state.results!!.isEmpty())
    }
}
