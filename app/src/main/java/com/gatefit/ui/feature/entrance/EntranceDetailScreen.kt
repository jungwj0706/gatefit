package com.gatefit.ui.feature.entrance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.DataFreshness
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.DayType
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.ui.components.CheckLine
import com.gatefit.ui.components.DemoBadge
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.LabeledValue
import com.gatefit.ui.components.LoadingPanel
import com.gatefit.ui.components.MessagePanel
import com.gatefit.ui.components.NoteText
import com.gatefit.ui.components.PrimaryButton
import com.gatefit.ui.components.SecondaryButton
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.components.StatusBadge
import com.gatefit.ui.components.TrustBadge
import com.gatefit.ui.format.Formatters
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.format.kgText
import com.gatefit.ui.format.mmText
import com.gatefit.ui.format.text
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun EntranceDetailScreen(
    entranceId: String,
    onNavigate: (String) -> Unit,
    onReport: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = gatefitViewModel { container ->
        EntranceDetailViewModel(
            entranceId = entranceId,
            profiles = container.vehicleProfileRepository,
            facilities = container.facilityRepository,
            getEntranceDetails = container.getEntranceDetails,
            analyzeEntrance = container.analyzeEntranceCompatibility,
            clock = container.clock,
        )
    }
    val content by viewModel.content.collectAsStateWithLifecycle()

    GatefitScreen(
        title = stringResource(R.string.entrance_detail_title),
        onBack = onBack,
    ) {
        when (val current = content) {
            EntranceDetailContent.Loading -> LoadingPanel(stringResource(R.string.entrance_detail_loading))
            EntranceDetailContent.NotFound -> MessagePanel(
                icon = Icons.Filled.SearchOff,
                title = stringResource(R.string.entrance_detail_not_found_title),
                message = stringResource(R.string.entrance_detail_not_found_message),
            )
            is EntranceDetailContent.Ready -> EntranceDetailBody(
                content = current,
                onNavigate = { onNavigate(current.details.entrance.id) },
                onReport = { onReport(current.details.entrance.id) },
            )
        }
    }
}

@Composable
private fun EntranceDetailBody(
    content: EntranceDetailContent.Ready,
    onNavigate: () -> Unit,
    onReport: () -> Unit,
) {
    val entrance = content.details.entrance
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.itemSpacing),
        contentPadding = PaddingValues(bottom = GatefitTheme.dimens.sectionSpacing),
    ) {
        item { EntranceHeader(content.details, content.analysis.status) }
        item { LimitsCard(entrance) }
        item { HoursCard(entrance) }
        item { CompatibilityCard(content) }
        item { SourceCard(entrance, content.analysis.freshness) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.entrance_navigate_action),
                    onClick = onNavigate,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Navigation,
                )
                SecondaryButton(
                    text = stringResource(R.string.entrance_report_action),
                    onClick = onReport,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Flag,
                    enabled = entrance.origin != DataOrigin.DEMO,
                )
            }
        }
    }
}

@Composable
private fun EntranceHeader(details: EntranceWithFacility, status: CompatibilityStatus) {
    val entrance = details.entrance
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(details.facility.name, style = MaterialTheme.typography.labelMedium)
                Text(entrance.name, style = MaterialTheme.typography.titleLarge)
            }
            StatusBadge(status)
        }
        LabeledValue(stringResource(R.string.entrance_direction), stringResource(entrance.direction.labelRes))
        entrance.entranceClass?.let {
            LabeledValue(stringResource(R.string.entrance_class), stringResource(it.labelRes))
        }
        LabeledValue(stringResource(R.string.entrance_status), stringResource(entrance.status.labelRes))
        LabeledValue(stringResource(R.string.entrance_location), coordinateText(entrance))
        if (entrance.origin == DataOrigin.DEMO) DemoBadge()
    }
}

@Composable
private fun LimitsCard(entrance: VehicleEntrance) {
    SectionCard(title = stringResource(R.string.entrance_limits_title)) {
        LabeledValue(stringResource(R.string.entrance_height_limit), mmText(entrance.maxHeightMm))
        LabeledValue(stringResource(R.string.entrance_width_limit), mmText(entrance.maxWidthMm))
        LabeledValue(stringResource(R.string.entrance_length_limit), mmText(entrance.maxLengthMm))
        LabeledValue(stringResource(R.string.entrance_weight_limit), kgText(entrance.maxWeightKg))
        LabeledValue(
            stringResource(R.string.entrance_permitted_vehicles),
            permittedVehicleText(entrance),
        )
        entrance.restrictionNote?.takeIf { it.isNotBlank() }?.let {
            NoteText(stringResource(R.string.entrance_restriction_note, it))
        }
    }
}

@Composable
private fun HoursCard(entrance: VehicleEntrance) {
    SectionCard(title = stringResource(R.string.entrance_hours_title)) {
        DayType.entries.forEach { day ->
            LabeledValue(stringResource(day.labelRes), entrance.hours.of(day).text())
        }
        entrance.hoursExceptionNote?.takeIf { it.isNotBlank() }?.let {
            NoteText(stringResource(R.string.entrance_hours_exception, it))
        }
    }
}

@Composable
private fun CompatibilityCard(content: EntranceDetailContent.Ready) {
    SectionCard(title = stringResource(R.string.entrance_compatibility_title)) {
        content.analysis.checks.forEach { CheckLine(it, detailed = true) }
        NoteText(
            stringResource(
                R.string.entrance_compatibility_disclaimer,
                Formatters.date(content.analysis.evaluatedAt.toLocalDate()),
                Formatters.time(content.analysis.evaluatedAt.toLocalTime()),
            ),
        )
    }
}

@Composable
private fun SourceCard(entrance: VehicleEntrance, freshness: DataFreshness) {
    SectionCard(title = stringResource(R.string.entrance_source_title)) {
        TrustBadge(entrance.trustLevel, freshness)
        LabeledValue(
            stringResource(R.string.entrance_source_name),
            entrance.dataSource?.name ?: stringResource(R.string.value_not_provided),
        )
        LabeledValue(
            stringResource(R.string.entrance_verified_at),
            entrance.verifiedAt?.let(Formatters::date) ?: stringResource(R.string.value_not_provided),
        )
        entrance.dataSource?.originalUpdatedAt?.let {
            LabeledValue(stringResource(R.string.entrance_source_updated_at), Formatters.date(it))
        }
        entrance.dataSource?.notes?.takeIf { it.isNotBlank() }?.let { NoteText(it) }
    }
}

@Composable
private fun permittedVehicleText(entrance: VehicleEntrance): String {
    val types = entrance.permittedVehicleTypes ?: return stringResource(R.string.value_not_provided)
    val labels = mutableListOf<String>()
    for (type in types) labels += stringResource(type.labelRes)
    return labels.joinToString()
}

@Composable
private fun coordinateText(entrance: VehicleEntrance): String {
    val latitude = entrance.latitude
    val longitude = entrance.longitude
    return if (latitude != null && longitude != null) {
        "${Formatters.coordinate(latitude)}, ${Formatters.coordinate(longitude)}"
    } else {
        stringResource(R.string.value_not_provided)
    }
}
