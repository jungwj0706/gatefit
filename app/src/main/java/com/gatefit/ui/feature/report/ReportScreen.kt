package com.gatefit.ui.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.ReportType
import com.gatefit.domain.model.RestrictedFeature
import com.gatefit.ui.components.ChoiceChip
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.GatefitTextField
import com.gatefit.ui.components.MessagePanel
import com.gatefit.ui.components.NoteText
import com.gatefit.ui.components.NumberField
import com.gatefit.ui.components.PrimaryButton
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.components.SectionTitle
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun ReportScreen(entranceId: String, onBack: () -> Unit) {
    val viewModel = gatefitViewModel { container ->
        ReportViewModel(entranceId, container.submitEntranceReport)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val writable = LocalDrivingRestrictions.current.allows(RestrictedFeature.REPORT_WRITING)

    GatefitScreen(title = stringResource(R.string.report_title), onBack = onBack) {
        if (!writable) {
            MessagePanel(
                icon = Icons.Filled.DirectionsCar,
                title = stringResource(R.string.driving_restricted_title),
                message = stringResource(R.string.driving_restricted_message),
            )
        } else if (state.delivery != null) {
            ReportAccepted(state.delivery.labelRes, onBack)
        } else {
            ReportFormBody(state, viewModel, onBack)
        }
    }
}

@Composable
private fun ReportFormBody(state: ReportUiState, viewModel: ReportViewModel, onBack: () -> Unit) {
    val form = state.form
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.sectionSpacing),
    ) {
        SectionCard(title = stringResource(R.string.report_type_title)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReportType.entries.forEach { type ->
                    ChoiceChip(
                        text = stringResource(type.labelRes),
                        selected = form.type == type,
                        onClick = { viewModel.updateForm { it.copy(type = type) } },
                    )
                }
            }
        }
        if (form.type == ReportType.HEIGHT_CHANGED) {
            NumberField(
                label = stringResource(R.string.report_height_label),
                value = form.proposedHeight,
                onValueChange = { value -> viewModel.updateForm { it.copy(proposedHeight = value) } },
                modifier = Modifier.fillMaxWidth(),
                suffix = stringResource(R.string.unit_mm),
                error = state.issueTextForHeight(),
            )
        }
        if (form.type == ReportType.WRONG_DIRECTION) {
            SectionCard(title = stringResource(R.string.report_direction_title)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(EntranceDirection.ENTRY, EntranceDirection.EXIT, EntranceDirection.BIDIRECTIONAL).forEach { direction ->
                        ChoiceChip(
                            text = stringResource(direction.labelRes),
                            selected = form.proposedDirection == direction,
                            onClick = { viewModel.updateForm { it.copy(proposedDirection = direction) } },
                        )
                    }
                }
                state.issueTextForDirection()?.let { NoteText(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        }
        GatefitTextField(
            label = stringResource(R.string.report_description_label),
            value = form.description,
            onValueChange = { value -> viewModel.updateForm { it.copy(description = value.take(500)) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            error = state.issueTextForDescription(),
            supportingText = stringResource(R.string.report_description_note),
        )
        NoteText(stringResource(R.string.report_privacy_note))
        PrimaryButton(
            text = stringResource(if (state.submitting) R.string.report_submitting else R.string.report_submit_action),
            onClick = viewModel::submit,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.submitting,
        )
    }
}

@Composable
private fun ReportAccepted(deliveryLabelRes: Int, onBack: () -> Unit) {
    MessagePanel(
        icon = Icons.Filled.CheckCircle,
        title = stringResource(R.string.report_complete_title),
        message = stringResource(R.string.report_complete_message, stringResource(deliveryLabelRes)),
        tint = GatefitTheme.status.match,
        action = { PrimaryButton(stringResource(R.string.action_confirm), onBack) },
    )
}

@Composable
private fun ReportUiState.issueTextForHeight(): String? =
    issues.firstOrNull {
        it == com.gatefit.domain.usecase.ReportIssue.PROPOSED_HEIGHT_REQUIRED ||
            it == com.gatefit.domain.usecase.ReportIssue.PROPOSED_HEIGHT_OUT_OF_RANGE
    }?.let { stringResource(it.labelRes) }

@Composable
private fun ReportUiState.issueTextForDirection(): String? =
    issues.firstOrNull { it == com.gatefit.domain.usecase.ReportIssue.PROPOSED_DIRECTION_REQUIRED }
        ?.let { stringResource(it.labelRes) }

@Composable
private fun ReportUiState.issueTextForDescription(): String? =
    issues.firstOrNull {
        it == com.gatefit.domain.usecase.ReportIssue.DESCRIPTION_REQUIRED ||
            it == com.gatefit.domain.usecase.ReportIssue.DESCRIPTION_TOO_LONG ||
            it == com.gatefit.domain.usecase.ReportIssue.ENTRANCE_NOT_FOUND ||
            it == com.gatefit.domain.usecase.ReportIssue.DEMO_ENTRANCE
    }?.let { stringResource(it.labelRes) }
