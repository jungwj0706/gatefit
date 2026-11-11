package com.gatefit.domain.usecase

import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceReportDraft
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.ReportType
import com.gatefit.domain.testing.FakeFacilityRepository
import com.gatefit.domain.testing.FakeReportRepository
import com.gatefit.domain.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitEntranceReportUseCaseTest {

    private val reports = FakeReportRepository()
    private val facilities = FakeFacilityRepository(
        listOf(
            FacilityWithEntrances(
                FakeFacilityRepository.facility("facility"),
                listOf(
                    Fixtures.verifiedEntrance(id = "gate"),
                    Fixtures.verifiedEntrance(id = "demo-gate").copy(origin = DataOrigin.DEMO),
                ),
            )
        )
    )
    private val submit = SubmitEntranceReportUseCase(reports, facilities)

    private fun draft(
        type: ReportType = ReportType.ENTRANCE_CLOSED,
        entranceId: String = "gate",
        description: String? = null,
        height: Int? = null,
        direction: EntranceDirection? = null,
    ) = EntranceReportDraft(entranceId, type, description, height, direction)

    private suspend fun issuesOf(draft: EntranceReportDraft): List<ReportIssue> =
        (submit(draft) as SubmitReportResult.Rejected).issues

    @Test
    fun `valid report is stored with a trimmed description`() = runTest {
        val result = submit(draft(description = "  공사로 폐쇄됨  "))

        assertTrue(result is SubmitReportResult.Accepted)
        assertEquals("공사로 폐쇄됨", reports.submitted.single().description)
    }

    @Test
    fun `blank description is stored as missing`() = runTest {
        submit(draft(description = "   "))

        assertEquals(null, reports.submitted.single().description)
    }

    @Test
    fun `height change needs a proposed height`() = runTest {
        assertEquals(listOf(ReportIssue.PROPOSED_HEIGHT_REQUIRED), issuesOf(draft(ReportType.HEIGHT_CHANGED)))
        assertTrue(reports.submitted.isEmpty())
    }

    @Test
    fun `proposed height in metres is rejected`() = runTest {
        assertEquals(
            listOf(ReportIssue.PROPOSED_HEIGHT_OUT_OF_RANGE),
            issuesOf(draft(ReportType.HEIGHT_CHANGED, height = 2)),
        )
    }

    @Test
    fun `wrong direction needs a proposed direction`() = runTest {
        assertEquals(listOf(ReportIssue.PROPOSED_DIRECTION_REQUIRED), issuesOf(draft(ReportType.WRONG_DIRECTION)))

        val accepted = submit(draft(ReportType.WRONG_DIRECTION, direction = EntranceDirection.EXIT))
        assertTrue(accepted is SubmitReportResult.Accepted)
    }

    @Test
    fun `other report needs a description within the length limit`() = runTest {
        assertEquals(listOf(ReportIssue.DESCRIPTION_REQUIRED), issuesOf(draft(ReportType.OTHER)))
        assertEquals(
            listOf(ReportIssue.DESCRIPTION_TOO_LONG),
            issuesOf(draft(ReportType.OTHER, description = "가".repeat(501))),
        )
    }

    @Test
    fun `report for an unknown entrance is rejected`() = runTest {
        assertEquals(listOf(ReportIssue.ENTRANCE_NOT_FOUND), issuesOf(draft(entranceId = "missing")))
    }

    @Test
    fun `demo entrance cannot be reported`() = runTest {
        assertEquals(listOf(ReportIssue.DEMO_ENTRANCE), issuesOf(draft(entranceId = "demo-gate")))
        assertTrue(reports.submitted.isEmpty())
    }
}
