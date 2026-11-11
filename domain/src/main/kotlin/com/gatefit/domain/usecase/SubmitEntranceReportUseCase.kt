package com.gatefit.domain.usecase

import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EntranceReport
import com.gatefit.domain.model.EntranceReportDraft
import com.gatefit.domain.model.ReportType
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.ReportRepository

enum class ReportIssue {
    ENTRANCE_NOT_FOUND,
    DEMO_ENTRANCE,
    DESCRIPTION_TOO_LONG,
    DESCRIPTION_REQUIRED,
    PROPOSED_HEIGHT_REQUIRED,
    PROPOSED_HEIGHT_OUT_OF_RANGE,
    PROPOSED_DIRECTION_REQUIRED,
}

sealed interface SubmitReportResult {
    data class Accepted(val report: EntranceReport) : SubmitReportResult
    data class Rejected(val issues: List<ReportIssue>) : SubmitReportResult
}

class SubmitEntranceReportUseCase(
    private val reports: ReportRepository,
    private val facilities: FacilityRepository,
) {

    suspend operator fun invoke(draft: EntranceReportDraft): SubmitReportResult {
        val normalized = draft.copy(description = draft.description?.trim()?.ifEmpty { null })
        val issues = validate(normalized)
        if (issues.isNotEmpty()) return SubmitReportResult.Rejected(issues)

        // DEMO 출입구는 실제 시설이 아니므로 제보 접수 금지
        val target = facilities.getEntrance(normalized.entranceId)
            ?: return SubmitReportResult.Rejected(listOf(ReportIssue.ENTRANCE_NOT_FOUND))
        if (target.entrance.origin == DataOrigin.DEMO) {
            return SubmitReportResult.Rejected(listOf(ReportIssue.DEMO_ENTRANCE))
        }
        return SubmitReportResult.Accepted(reports.submit(normalized))
    }

    fun validate(draft: EntranceReportDraft): List<ReportIssue> = buildList {
        val description = draft.description
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) add(ReportIssue.DESCRIPTION_TOO_LONG)
        if (draft.type == ReportType.OTHER && description.isNullOrBlank()) add(ReportIssue.DESCRIPTION_REQUIRED)

        val height = draft.proposedHeightMm
        if (draft.type == ReportType.HEIGHT_CHANGED && height == null) add(ReportIssue.PROPOSED_HEIGHT_REQUIRED)
        if (height != null && height !in PROPOSED_HEIGHT_RANGE_MM) add(ReportIssue.PROPOSED_HEIGHT_OUT_OF_RANGE)

        if (draft.type == ReportType.WRONG_DIRECTION && draft.proposedDirection == null) {
            add(ReportIssue.PROPOSED_DIRECTION_REQUIRED)
        }
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 500
        val PROPOSED_HEIGHT_RANGE_MM = 1000..6000
    }
}
