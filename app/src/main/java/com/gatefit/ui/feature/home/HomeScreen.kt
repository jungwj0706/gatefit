package com.gatefit.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.RestrictedFeature
import com.gatefit.domain.repository.DatasetInfo
import com.gatefit.ui.components.ChoiceChip
import com.gatefit.ui.components.DemoBadge
import com.gatefit.ui.components.FacilitySummaryCard
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.LabeledValue
import com.gatefit.ui.components.LargeIconButton
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.components.NoteText
import com.gatefit.ui.components.PrimaryButton
import com.gatefit.ui.components.SecondaryButton
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.components.SectionTitle
import com.gatefit.ui.components.VehicleLinkRow
import com.gatefit.ui.format.Formatters
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.format.mmText
import com.gatefit.ui.model.CategoryCount
import com.gatefit.ui.navigation.Routes
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun HomeScreen(
    onSearch: (FacilityCategory?) -> Unit,
    onOpenFacility: (String) -> Unit,
    onOpenVehicleSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSavedPlaces: (String) -> Unit,
) {
    val viewModel = gatefitViewModel { container ->
        HomeViewModel(
            profiles = container.vehicleProfileRepository,
            vehicleInfo = container.vehicleInfoRepository,
            facilities = container.facilityRepository,
            getRecentFacilities = container.getRecentFacilities,
            getFavoriteFacilities = container.getFavoriteFacilities,
            calculateEffectiveDimensions = container.calculateEffectiveDimensions,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restrictions = LocalDrivingRestrictions.current

    GatefitScreen(
        title = stringResource(R.string.app_name),
        actions = {
            LargeIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                onClick = onOpenSettings,
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val spacing = GatefitTheme.dimens.sectionSpacing
            val vehicle: @Composable () -> Unit = {
                VehicleCard(
                    state = state,
                    editable = restrictions.allows(RestrictedFeature.VEHICLE_PROFILE_EDIT),
                    onOpenVehicleSetup = onOpenVehicleSetup,
                )
            }
            val places: @Composable () -> Unit = {
                SearchEntry(
                    dataset = state.dataset,
                    categories = state.categories,
                    searchAllowed = restrictions.allows(RestrictedFeature.FACILITY_SEARCH_INPUT),
                    onSearch = onSearch,
                )
                PlaceSection(
                    title = stringResource(R.string.home_favorites),
                    emptyText = stringResource(R.string.home_favorites_empty),
                    places = state.favorites,
                    onOpenFacility = onOpenFacility,
                    onShowAll = { onOpenSavedPlaces(Routes.KIND_FAVORITE) },
                )
                PlaceSection(
                    title = stringResource(R.string.home_recents),
                    emptyText = stringResource(R.string.home_recents_empty),
                    places = state.recents,
                    onOpenFacility = onOpenFacility,
                    onShowAll = { onOpenSavedPlaces(Routes.KIND_RECENT) },
                )
            }
            if (maxWidth >= 1000.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = spacing),
                    ) { vehicle() }
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) { places() }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    places()
                    vehicle()
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(state: HomeUiState, editable: Boolean, onOpenVehicleSetup: () -> Unit) {
    val profile = state.profile
    val dimensions = state.dimensions
    SectionCard(title = stringResource(R.string.vehicle_summary_title)) {
        if (profile == null || dimensions == null) {
            Text(stringResource(R.string.vehicle_model_unregistered), style = MaterialTheme.typography.titleLarge)
            NoteText(stringResource(R.string.home_vehicle_unregistered_note))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.displayName ?: stringResource(R.string.vehicle_model_unregistered),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (profile.demo) DemoBadge()
            }
            Text(
                stringResource(R.string.vehicle_summary_required_height),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                mmText(dimensions.requiredHeightMm(profile.safetyMarginMm)),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            LabeledValue(stringResource(R.string.vehicle_summary_current_height), mmText(dimensions.heightMm))
            LabeledValue(
                stringResource(R.string.field_safety_margin),
                stringResource(R.string.format_mm, Formatters.number(profile.safetyMarginMm)),
            )
            LabeledValue(
                stringResource(R.string.vehicle_summary_confidence),
                stringResource(profile.specConfidence.labelRes),
            )
            if (profile.accessories.isNotEmpty()) {
                LabeledValue(
                    stringResource(R.string.vehicle_section_accessories),
                    profile.accessories.map { stringResource(it.type.labelRes) }.joinToString(", "),
                )
            }
            if (dimensions.heightMm != null && !dimensions.usableForAnalysis) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = GatefitTheme.status.caution)
                    NoteText(stringResource(R.string.vehicle_summary_unconfirmed), color = GatefitTheme.status.caution)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        VehicleLinkRow(state.vehicleLink)
        if (editable) {
            val label = if (profile == null) R.string.home_register_vehicle else R.string.home_edit_vehicle
            if (profile == null) {
                PrimaryButton(stringResource(label), onOpenVehicleSetup, Modifier.fillMaxWidth())
            } else {
                SecondaryButton(stringResource(label), onOpenVehicleSetup, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SearchEntry(
    dataset: DatasetInfo?,
    categories: List<CategoryCount>,
    searchAllowed: Boolean,
    onSearch: (FacilityCategory?) -> Unit,
) {
    SectionCard(
        onClick = if (searchAllowed) ({ onSearch(null) }) else null,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.heightIn(min = GatefitTheme.dimens.touchTarget),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (searchAllowed) R.string.home_search_title else R.string.home_search_restricted),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (dataset != null) {
                    NoteText(
                        stringResource(
                            R.string.home_search_scope,
                            Formatters.number(dataset.facilityCount),
                            Formatters.number(dataset.entranceCount),
                        )
                    )
                }
            }
        }
    }
    if (searchAllowed && categories.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            categories.forEach { item ->
                ChoiceChip(
                    text = stringResource(item.category.labelRes),
                    selected = false,
                    onClick = { onSearch(item.category) },
                )
            }
        }
    }
}

@Composable
private fun PlaceSection(
    title: String,
    emptyText: String,
    places: List<FacilitySummary>,
    onOpenFacility: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.itemSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title, Modifier.weight(1f))
            if (places.isNotEmpty()) {
                SecondaryButton(stringResource(R.string.home_show_all), onShowAll)
            }
        }
        if (places.isEmpty()) {
            NoteText(emptyText)
        } else {
            places.forEach { summary ->
                FacilitySummaryCard(summary, onClick = { onOpenFacility(summary.facility.id) })
            }
        }
    }
}
