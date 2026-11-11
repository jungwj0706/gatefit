package com.gatefit.ui.feature.entrance

import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.usecase.GetEntranceDetailsUseCase
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

class EntranceDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()

    private fun TestScope.viewModel(entranceId: String): EntranceDetailViewModel {
        val viewModel = EntranceDetailViewModel(
            entranceId = entranceId,
            profiles = environment.profiles,
            facilities = environment.facilities,
            getEntranceDetails = GetEntranceDetailsUseCase(environment.facilities),
            analyzeEntrance = environment.analyzeEntrance,
            clock = environment.clock,
        )
        backgroundScope.launch { viewModel.content.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `missing entrance is not shown as a successful detail page`() = runTest(mainRule.dispatcher) {
        val content = viewModel("missing").content.first { it !is EntranceDetailContent.Loading }
        assertEquals(EntranceDetailContent.NotFound, content)
    }

    @Test
    fun `detail reuses the same analysis engine as the facility list`() = runTest(mainRule.dispatcher) {
        environment.profiles.save(
            VehicleProfile(
                id = "p1",
                baseDimensions = VehicleDimensions(heightMm = 1500),
                specConfidence = SpecConfidence.USER_CONFIRMED,
            ),
        )
        environment.facilities.ensureSeeded()
        val entrance = environment.facilities.search(FacilityQuery(limit = 20))
            .first { it.entranceCount > 0 }
            .let { environment.facilities.getFacilityWithEntrances(it.facility.id)!!.entrances.first() }

        val content = viewModel(entrance.id).content.first { it is EntranceDetailContent.Ready } as EntranceDetailContent.Ready

        assertEquals(entrance.id, content.details.entrance.id)
        assertTrue(content.analysis.status != CompatibilityStatus.CONDITION_MATCH)
    }
}
