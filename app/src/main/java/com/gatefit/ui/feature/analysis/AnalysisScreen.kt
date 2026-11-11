package com.gatefit.ui.feature.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.ConstraintType
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EffectiveDimensions
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityAnalysis
import com.gatefit.domain.model.FacilityStatus
import com.gatefit.domain.model.RestrictedFeature
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.ui.components.CheckLine
import com.gatefit.ui.components.DemoBadge
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.LabeledValue
import com.gatefit.ui.components.LargeIconButton
import com.gatefit.ui.components.LoadingPanel
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.components.MessagePanel
import com.gatefit.ui.components.NoteText
import com.gatefit.ui.components.Pill
import com.gatefit.ui.components.PrimaryButton
import com.gatefit.ui.components.SecondaryButton
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.components.StatusBadge
import com.gatefit.ui.components.TrustBadge
import com.gatefit.ui.components.visual
import com.gatefit.ui.format.Formatters
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.format.mmText
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun AnalysisScreen(
    facilityId: String,
    onOpenEntrance: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenVehicleSetup: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = gatefitViewModel { container ->
        AnalysisViewModel(
            facilityId = facilityId,
            profiles = container.vehicleProfileRepository,
            facilities = container.facilityRepository,
            savedPlaces = container.savedPlacesRepository,
            getFacilityEntrances = container.getFacilityEntrances,
            resolveRouteTarget = container.resolveRouteTarget,
            toggleFavorite = container.toggleFavoriteFacility,
            clock = container.clock,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 출입 시간 판정은 현재 시각에 따라 달라지므로 화면 복귀 시 재분석
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val content = state.content
    GatefitScreen(
        title = (content as? AnalysisContent.Ready)?.analysis?.facility?.name
            ?: stringResource(R.string.analysis_title),
        onBack = onBack,
        actions = {
            if (content is AnalysisContent.Ready) {
                LargeIconButton(
                    icon = if (state.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(
                        if (state.favorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    onClick = viewModel::toggleFavorite,
                )
            }
        },
    ) {
        when (content) {
            AnalysisContent.Loading -> LoadingPanel(stringResource(R.string.analysis_loading))
            AnalysisContent.NotFound -> MessagePanel(
                icon = Icons.Filled.SearchOff,
                title = stringResource(R.string.analysis_not_found_title),
                message = stringResource(R.string.analysis_not_found_message),
            )
            is AnalysisContent.Ready -> AnalysisBody(
                content = content,
                profile = state.profile,
                onOpenEntrance = onOpenEntrance,
                onNavigate = onNavigate,
                onOpenVehicleSetup = onOpenVehicleSetup,
            )
        }
    }
}

@Composable
private fun AnalysisBody(
    content: AnalysisContent.Ready,
    profile: VehicleProfile?,
    onOpenEntrance: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onOpenVehicleSetup: () -> Unit,
) {
    val analysis = content.analysis
    val restrictions = LocalDrivingRestrictions.current
    val browsing = restrictions.allows(RestrictedFeature.DETAIL_BROWSING)
    val spacing = GatefitTheme.dimens.sectionSpacing
    val overview: @Composable () -> Unit = {
        FacilityCard(analysis.facility)
        VehicleBasisCard(
            profile = profile,
            vehicle = analysis.vehicle,
            safetyMarginMm = analysis.safetyMarginMm,
            editable = restrictions.allows(RestrictedFeature.VEHICLE_PROFILE_EDIT),
            onOpenVehicleSetup = onOpenVehicleSetup,
        )
    }
    val entrances: LazyListScope.() -> Unit = {
        item { ResultSummary(analysis) }
        if (analysis.entrances.isEmpty()) {
            item {
                MessagePanel(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.analysis_no_entrances_title),
                    message = stringResource(R.string.analysis_no_entrances_message),
                )
            }
        }
        if (analysis.hasNoMatch) item { NoMatchNotice(analysis) }
        items(content.entrances, key = { it.analysis.entrance.id }) { item ->
            EntranceCard(item, browsing, onOpenEntrance, onNavigate)
        }
        if (analysis.entrances.isNotEmpty()) {
            item {
                NoteText(
                    stringResource(
                        R.string.analysis_disclaimer,
                        Formatters.date(analysis.evaluatedAt.toLocalDate()),
                        Formatters.time(analysis.evaluatedAt.toLocalTime()),
                    )
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 1000.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                Column(
                    modifier = Modifier
                        .weight(0.75f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) { overview() }
                LazyColumn(
                    modifier = Modifier.weight(1.25f),
                    contentPadding = PaddingValues(bottom = spacing),
                    verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.itemSpacing),
                    content = entrances,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = spacing),
                verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.itemSpacing),
            ) {
                item { Column(verticalArrangement = Arrangement.spacedBy(spacing)) { overview() } }
                entrances()
            }
        }
    }
}

@Composable
private fun FacilityCard(facility: Facility) {
    SectionCard(title = stringResource(facility.category.labelRes)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                facility.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (facility.origin == DataOrigin.DEMO) DemoBadge()
        }
        Text(
            facility.displayAddress ?: stringResource(R.string.facility_address_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val statusText = when (facility.status) {
            FacilityStatus.CLOSED -> stringResource(R.string.facility_status_closed)
            FacilityStatus.UNDER_CONSTRUCTION -> stringResource(R.string.facility_status_construction)
            FacilityStatus.OPERATING, FacilityStatus.UNKNOWN -> null
        }
        if (statusText != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = GatefitTheme.status.caution)
                NoteText(
                    listOfNotNull(statusText, facility.statusNote).joinToString(" · "),
                    color = GatefitTheme.status.caution,
                )
            }
        }
        if (facility.origin == DataOrigin.DEMO) NoteText(stringResource(R.string.demo_notice))
    }
}

@Composable
private fun VehicleBasisCard(
    profile: VehicleProfile?,
    vehicle: EffectiveDimensions,
    safetyMarginMm: Int,
    editable: Boolean,
    onOpenVehicleSetup: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.analysis_vehicle_basis)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                profile?.displayName ?: stringResource(R.string.vehicle_model_unregistered),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (profile?.demo == true) DemoBadge()
        }
        Text(
            stringResource(R.string.vehicle_summary_required_height),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            mmText(vehicle.requiredHeightMm(safetyMarginMm)),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LabeledValue(stringResource(R.string.vehicle_summary_current_height), mmText(vehicle.heightMm))
        LabeledValue(
            stringResource(R.string.field_safety_margin),
            stringResource(R.string.format_mm, Formatters.number(safetyMarginMm)),
        )
        if (profile != null) {
            LabeledValue(
                stringResource(R.string.vehicle_summary_confidence),
                stringResource(profile.specConfidence.labelRes),
            )
        }
        if (!vehicle.usableForAnalysis) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = GatefitTheme.status.caution)
                NoteText(
                    stringResource(
                        if (vehicle.heightMm == null) R.string.analysis_vehicle_missing
                        else R.string.vehicle_summary_unconfirmed
                    ),
                    color = GatefitTheme.status.caution,
                )
            }
            if (editable) {
                SecondaryButton(
                    text = stringResource(
                        if (profile == null) R.string.home_register_vehicle else R.string.home_edit_vehicle
                    ),
                    onClick = onOpenVehicleSetup,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ResultSummary(analysis: FacilityAnalysis) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CountPill(CompatibilityStatus.CONDITION_MATCH, analysis.matchCount)
        CountPill(CompatibilityStatus.NEEDS_CONFIRMATION, analysis.needsConfirmationCount)
        CountPill(CompatibilityStatus.CONDITION_MISMATCH, analysis.mismatchCount)
    }
}

@Composable
private fun CountPill(status: CompatibilityStatus, count: Int) {
    val visual = status.visual()
    Pill(
        text = stringResource(R.string.analysis_status_count, stringResource(status.labelRes), count),
        icon = visual.icon,
        color = visual.color,
        containerColor = visual.containerColor,
    )
}

// 조건을 충족하는 입구가 없을 때 검증되지 않은 입구를 대신 추천하지 않고 이유만 안내
@Composable
private fun NoMatchNotice(analysis: FacilityAnalysis) {
    SectionCard(containerColor = GatefitTheme.status.cautionContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = GatefitTheme.status.caution)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.analysis_no_match_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (analysis.vehicle.usableForAnalysis) R.string.analysis_no_match_message
                        else R.string.analysis_no_match_vehicle_message
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EntranceCard(
    item: EntranceItem,
    browsing: Boolean,
    onOpenEntrance: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val entrance = item.analysis.entrance
    val result = item.analysis.result
    val heightCheck = result.check(ConstraintType.HEIGHT)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(
                        entrance.entranceClass?.let { stringResource(it.labelRes) },
                        stringResource(entrance.direction.labelRes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entrance.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(result.status)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Fact(
                label = stringResource(R.string.entrance_height_limit),
                value = mmText(entrance.maxHeightMm),
                modifier = Modifier.weight(1f),
                emphasized = true,
            )
            Fact(
                label = stringResource(R.string.entrance_height_margin),
                value = heightCheck?.marginValue
                    ?.let { stringResource(R.string.format_mm, Formatters.signedNumber(it)) }
                    ?: stringResource(R.string.value_unknown),
                modifier = Modifier.weight(1f),
                emphasized = true,
            )
        }

        // 부적합 사유를 먼저, 그다음 확인이 필요한 항목 순으로 표시
        val reasons = result.failedChecks + result.unknownChecks
        val visibleReasons = reasons.take(if (browsing) MAX_VISIBLE_REASONS else 1)
        visibleReasons.forEach { CheckLine(it) }
        if (reasons.size > visibleReasons.size) {
            NoteText(stringResource(R.string.analysis_more_reasons, reasons.size - visibleReasons.size))
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrustBadge(entrance.trustLevel, result.freshness)
            if (result.demo) DemoBadge()
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (browsing) {
                SecondaryButton(
                    text = stringResource(R.string.entrance_detail_action),
                    onClick = { onOpenEntrance(entrance.id) },
                    modifier = Modifier.weight(1f),
                )
            }
            when {
                item.routeBlock != null -> Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = GatefitTheme.status.neutral)
                    NoteText(stringResource(item.routeBlock.labelRes))
                }
                result.status == CompatibilityStatus.CONDITION_MATCH -> PrimaryButton(
                    text = stringResource(R.string.entrance_navigate_action),
                    onClick = { onNavigate(entrance.id) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Navigation,
                )
                // 조건 충족이 아닌 입구는 강조하지 않으며, 다음 화면에서 확인 절차를 거침
                else -> SecondaryButton(
                    text = stringResource(R.string.entrance_navigate_action),
                    onClick = { onNavigate(entrance.id) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Navigation,
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
        )
    }
}

private const val MAX_VISIBLE_REASONS = 3
