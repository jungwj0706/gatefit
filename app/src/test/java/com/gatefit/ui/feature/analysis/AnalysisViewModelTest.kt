package com.gatefit.ui.feature.analysis

import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.usecase.ResolveRouteTargetUseCase
import com.gatefit.domain.usecase.ToggleFavoriteFacilityUseCase
import com.gatefit.ui.testing.MainDispatcherRule
import com.gatefit.ui.testing.TestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnalysisViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()

    private fun TestScope.viewModel(facilityId: String): AnalysisViewModel {
        val viewModel = AnalysisViewModel(
            facilityId = facilityId,
            profiles = environment.profiles,
            facilities = environment.facilities,
            savedPlaces = environment.savedPlaces,
            getFacilityEntrances = environment.getFacilityEntrances,
            resolveRouteTarget = ResolveRouteTargetUseCase(),
            toggleFavorite = ToggleFavoriteFacilityUseCase(environment.savedPlaces),
            clock = environment.clock,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `unknown facility is not treated as a successful analysis`() = runTest(mainRule.dispatcher) {
        val state = viewModel("missing").state.first { it.content !is AnalysisContent.Loading }
        assertEquals(AnalysisContent.NotFound, state.content)
    }

    @Test
    fun `public baseline data never claims a confirmed match by itself`() = runTest(mainRule.dispatcher) {
        environment.profiles.save(
            VehicleProfile(
                id = "p1",
                baseDimensions = VehicleDimensions(heightMm = 1500),
                specConfidence = com.gatefit.domain.model.SpecConfidence.USER_CONFIRMED,
            ),
        )
        environment.facilities.ensureSeeded()
        val facilityId = environment.facilities.search(FacilityQuery(limit = 20))
            .first { it.entranceCount > 0 }
            .facility.id

        val state = viewModel(facilityId).state.first { it.content is AnalysisContent.Ready }
        val ready = state.content as AnalysisContent.Ready

        assertTrue(ready.entrances.isNotEmpty())
        assertEquals(0, ready.analysis.matchCount)
        assertTrue(ready.entrances.none { it.analysis.result.status == CompatibilityStatus.CONDITION_MATCH })
        assertEquals(listOf(facilityId), environment.savedPlaces.recentIds.first())
    }

    @Test
    fun `favorite can be toggled from the analysis screen`() = runTest(mainRule.dispatcher) {
        environment.facilities.ensureSeeded()
        val facilityId = environment.facilities.search(FacilityQuery(limit = 1)).first().facility.id
        val viewModel = viewModel(facilityId)
        viewModel.state.first { it.content is AnalysisContent.Ready }

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.favorite)
        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.favorite)
    }
}
