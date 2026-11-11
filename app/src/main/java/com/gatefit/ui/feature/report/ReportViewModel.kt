package com.gatefit.ui.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceReportDraft
import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.model.ReportType
import com.gatefit.domain.usecase.ReportIssue
import com.gatefit.domain.usecase.SubmitEntranceReportUseCase
import com.gatefit.domain.usecase.SubmitReportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportForm(
    val type: ReportType = ReportType.WRONG_LOCATION,
    val description: String = "",
    val proposedHeight: String = "",
    val proposedDirection: EntranceDirection? = null,
)

data class ReportUiState(
    val form: ReportForm = ReportForm(),
    val issues: List<ReportIssue> = emptyList(),
    val submitting: Boolean = false,
    val delivery: ReportDelivery? = null,
)

class ReportViewModel(
    private val entranceId: String,
    private val submitReport: SubmitEntranceReportUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = mutableState.asStateFlow()

    fun updateForm(transform: (ReportForm) -> ReportForm) {
        mutableState.update { current -> current.copy(form = transform(current.form), issues = emptyList(), delivery = null) }
    }

    fun submit() {
        val form = state.value.form
        val draft = EntranceReportDraft(
            entranceId = entranceId,
            type = form.type,
            description = form.description,
            proposedHeightMm = form.proposedHeight.toIntOrNull(),
            proposedDirection = form.proposedDirection,
        )
        val validationIssues = submitReport.validate(draft)
        if (validationIssues.isNotEmpty()) {
            mutableState.update { it.copy(issues = validationIssues) }
            return
        }
        mutableState.update { it.copy(submitting = true, issues = emptyList(), delivery = null) }
        viewModelScope.launch {
            when (val result = submitReport(draft)) {
                is SubmitReportResult.Accepted -> {
                    mutableState.update { it.copy(submitting = false, delivery = result.report.delivery) }
                }
                is SubmitReportResult.Rejected -> {
                    mutableState.update { it.copy(submitting = false, issues = result.issues) }
                }
            }
        }
    }
}
