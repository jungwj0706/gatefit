package com.gatefit.ui.feature.report

import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.model.ReportType
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.usecase.ReportIssue
import com.gatefit.domain.usecase.SubmitEntranceReportUseCase
import com.gatefit.ui.testing.MainDispatcherRule
import com.gatefit.ui.testing.TestEnvironment
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()

    private fun viewModel(entranceId: String) = ReportViewModel(
        entranceId = entranceId,
        submitReport = SubmitEntranceReportUseCase(environment.reports, environment.facilities),
    )

    @Test
    fun `height reports require a proposed limit before submit`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel("any")
        viewModel.updateForm { it.copy(type = ReportType.HEIGHT_CHANGED) }
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf(ReportIssue.PROPOSED_HEIGHT_REQUIRED), viewModel.state.value.issues)
        assertEquals(null, viewModel.state.value.delivery)
    }

    @Test
    fun `demo entrances are not accepted as reports`() = runTest(mainRule.dispatcher) {
        environment.settings.update { it.copy(demoDataEnabled = true) }
        environment.facilities.ensureSeeded()
        val viewModel = viewModel("demo-entrance-mall-a")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf(ReportIssue.DEMO_ENTRANCE), viewModel.state.value.issues)
    }

    @Test
    fun `valid report is queued locally without claiming server review is done`() = runTest(mainRule.dispatcher) {
        environment.facilities.ensureSeeded()
        val entranceId = environment.facilities.search(FacilityQuery(limit = 20))
            .first { it.entranceCount > 0 }
            .let { environment.facilities.getFacilityWithEntrances(it.facility.id)!!.entrances.first().id }

        val viewModel = viewModel(entranceId)
        viewModel.updateForm { it.copy(type = ReportType.WRONG_LOCATION, description = "입구 위치가 다릅니다") }
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.issues.isEmpty())
        assertEquals(ReportDelivery.QUEUED, viewModel.state.value.delivery)
    }
}
