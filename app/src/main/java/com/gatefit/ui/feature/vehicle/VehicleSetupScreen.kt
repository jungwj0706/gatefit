package com.gatefit.ui.feature.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.HeightBasis
import com.gatefit.domain.model.RestrictedFeature
import com.gatefit.domain.model.VehicleModelSpec
import com.gatefit.ui.components.ChoiceChip
import com.gatefit.ui.components.DemoBadge
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.GatefitTextField
import com.gatefit.ui.components.LabeledValue
import com.gatefit.ui.components.LargeIconButton
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.components.MessagePanel
import com.gatefit.ui.components.NumberField
import com.gatefit.ui.components.PrimaryButton
import com.gatefit.ui.components.SecondaryButton
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.components.SwitchRow
import com.gatefit.ui.components.VehicleLinkRow
import com.gatefit.ui.format.Formatters
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.format.mmText
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme
import java.util.UUID

@Composable
fun VehicleSetupScreen(
    onboarding: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val viewModel = gatefitViewModel { container ->
        VehicleSetupViewModel(
            profiles = container.vehicleProfileRepository,
            specs = container.vehicleSpecRepository,
            settings = container.settingsRepository,
            vehicleInfo = container.vehicleInfoRepository,
            getVehicleInformation = container.getVehicleInformation,
            saveProfile = container.saveVehicleProfile,
            applySpec = container.applyVehicleModelSpec,
            calculateEffectiveDimensions = container.calculateEffectiveDimensions,
            newId = { UUID.randomUUID().toString() },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    val editable = LocalDrivingRestrictions.current.allows(RestrictedFeature.VEHICLE_PROFILE_EDIT)
    GatefitScreen(
        title = stringResource(if (onboarding) R.string.vehicle_setup_title_onboarding else R.string.vehicle_setup_title),
        onBack = if (onboarding) null else onBack,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 1000.dp
            val spacing = GatefitTheme.dimens.sectionSpacing
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    Column(
                        modifier = Modifier
                            .weight(1.35f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        if (editable) VehicleFormSections(state, viewModel) else RestrictedPanel()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = spacing),
                    ) {
                        VehicleSummary(state, onboarding, editable, viewModel::save, onSkip)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    if (editable) VehicleFormSections(state, viewModel) else RestrictedPanel()
                    VehicleSummary(state, onboarding, editable, viewModel::save, onSkip)
                }
            }
        }
    }
}

@Composable
private fun RestrictedPanel() {
    MessagePanel(
        icon = Icons.Filled.DirectionsCar,
        title = stringResource(R.string.driving_restricted_title),
        message = stringResource(R.string.driving_restricted_message),
        tint = GatefitTheme.status.caution,
    )
}

@Composable
private fun VehicleFormSections(state: VehicleSetupUiState, viewModel: VehicleSetupViewModel) {
    VehicleLinkSection(state, onRead = viewModel::readFromVehicle)
    ModelSection(state, viewModel)
    AccessorySection(state.form.accessories, viewModel)
    ExtraSection(state.form, viewModel)
    SafetyMarginSection(state.form.safetyMarginMm, viewModel::onSafetyMarginChange)
}

@Composable
private fun VehicleLinkSection(state: VehicleSetupUiState, onRead: () -> Unit) {
    SectionCard(title = stringResource(R.string.vehicle_section_link)) {
        VehicleLinkRow(state.vehicleLink)
        if (state.canReadFromVehicle) {
            SecondaryButton(
                text = stringResource(R.string.vehicle_read_action),
                onClick = onRead,
                enabled = state.readState != VehicleReadState.Reading,
            )
            Note(stringResource(R.string.vehicle_read_privacy))
        }
        when (val read = state.readState) {
            VehicleReadState.Idle -> Unit
            VehicleReadState.Reading -> Note(stringResource(R.string.vehicle_read_reading))
            VehicleReadState.Unsupported -> Note(stringResource(R.string.vehicle_read_unsupported))
            is VehicleReadState.Failed -> Note(stringResource(read.failure.labelRes))
            is VehicleReadState.Hint -> {
                val unknown = stringResource(R.string.value_unknown)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            R.string.vehicle_read_hint,
                            read.hint.manufacturer ?: unknown,
                            read.hint.modelYear?.toString() ?: unknown,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (read.hint.demo) DemoBadge()
                }
                Note(stringResource(R.string.vehicle_read_hint_note))
            }
        }
        Note(stringResource(R.string.vehicle_dimension_not_from_sdk))
    }
}

@Composable
private fun ModelSection(state: VehicleSetupUiState, viewModel: VehicleSetupViewModel) {
    val form = state.form
    SectionCard(title = stringResource(R.string.vehicle_section_model)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChip(
                text = stringResource(R.string.vehicle_mode_catalog),
                selected = form.mode == SpecMode.CATALOG,
                onClick = viewModel::switchToCatalog,
            )
            ChoiceChip(
                text = stringResource(R.string.vehicle_mode_manual),
                selected = form.mode == SpecMode.MANUAL,
                onClick = viewModel::switchToManual,
            )
        }
        when (form.mode) {
            SpecMode.CATALOG -> CatalogPicker(state, viewModel)
            SpecMode.MANUAL -> ManualSpecFields(form, viewModel)
        }
    }
}

@Composable
private fun CatalogPicker(state: VehicleSetupUiState, viewModel: VehicleSetupViewModel) {
    val form = state.form
    if (state.hasCatalogSelection) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = GatefitTheme.status.match,
                modifier = Modifier.size(GatefitTheme.dimens.iconSize),
            )
            Column {
                Text(
                    listOfNotNull(form.manufacturer, form.modelName, form.trimName).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                )
                Note(
                    stringResource(
                        R.string.vehicle_spec_dimensions,
                        form.length.ifEmpty { "-" },
                        form.width.ifEmpty { "-" },
                        form.height.ifEmpty { "-" },
                    )
                )
                form.specSourceName?.let { Note(stringResource(R.string.vehicle_spec_source, it)) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    GatefitTextField(
        label = stringResource(R.string.vehicle_model_search_label),
        value = state.query,
        onValueChange = viewModel::onQueryChange,
        leadingIcon = Icons.Filled.Search,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.specs.isEmpty()) {
        Note(stringResource(R.string.vehicle_model_search_empty))
    } else {
        Column {
            state.specs.forEach { spec ->
                SpecRow(spec, selected = spec.id == form.selectedSpec?.id, onClick = { viewModel.selectSpec(spec) })
            }
        }
    }
    Note(stringResource(R.string.vehicle_catalog_note))
}

@Composable
private fun SpecRow(spec: VehicleModelSpec, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = GatefitTheme.dimens.touchTarget)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${spec.manufacturer} ${spec.modelName}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            spec.trimName?.let { Note(it) }
        }
        Text(
            stringResource(R.string.vehicle_spec_height, mmText(spec.dimensions.heightMm)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManualSpecFields(form: VehicleForm, viewModel: VehicleSetupViewModel) {
    val mm = stringResource(R.string.unit_mm)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GatefitTextField(
            label = stringResource(R.string.vehicle_manufacturer_label),
            value = form.manufacturer,
            onValueChange = viewModel::onManufacturerChange,
            modifier = Modifier.weight(1f),
        )
        GatefitTextField(
            label = stringResource(R.string.vehicle_model_label),
            value = form.modelName,
            onValueChange = viewModel::onModelNameChange,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NumberField(
            label = stringResource(R.string.field_height),
            value = form.height,
            onValueChange = viewModel::onHeightChange,
            suffix = mm,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.field_width),
            value = form.width,
            onValueChange = viewModel::onWidthChange,
            suffix = mm,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.field_length),
            value = form.length,
            onValueChange = viewModel::onLengthChange,
            suffix = mm,
            modifier = Modifier.weight(1f),
        )
    }
    SwitchRow(
        title = stringResource(R.string.vehicle_manual_confirm_title),
        description = stringResource(R.string.vehicle_manual_confirm_description),
        checked = form.manualConfirmed,
        onCheckedChange = viewModel::onManualConfirmedChange,
        enabled = form.height.isNotEmpty(),
    )
}

@Composable
private fun AccessorySection(accessories: List<AccessoryForm>, viewModel: VehicleSetupViewModel) {
    val mm = stringResource(R.string.unit_mm)
    SectionCard(title = stringResource(R.string.vehicle_section_accessories)) {
        accessories.forEach { accessory ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(accessory.type.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                LargeIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = { viewModel.removeAccessory(accessory.id) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NumberField(
                    label = stringResource(R.string.accessory_height_label),
                    value = accessory.height,
                    onValueChange = { viewModel.updateAccessory(accessory.copy(height = it, verified = false)) },
                    suffix = mm,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.accessory_width_label),
                    value = accessory.width,
                    onValueChange = { viewModel.updateAccessory(accessory.copy(width = it, verified = false)) },
                    suffix = mm,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.accessory_length_label),
                    value = accessory.length,
                    onValueChange = { viewModel.updateAccessory(accessory.copy(length = it, verified = false)) },
                    suffix = mm,
                    modifier = Modifier.weight(1f),
                )
            }
            SwitchRow(
                title = stringResource(R.string.accessory_verified_title),
                checked = accessory.verified,
                onCheckedChange = { viewModel.updateAccessory(accessory.copy(verified = it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (accessories.size < VehicleSetupViewModel.MAX_ACCESSORIES) {
            Text(stringResource(R.string.accessory_add_title), style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccessoryType.entries.forEach { type ->
                    ChoiceChip(
                        text = stringResource(type.labelRes),
                        selected = false,
                        onClick = { viewModel.addAccessory(type) },
                    )
                }
            }
        }
        Note(stringResource(R.string.accessory_note))
    }
}

@Composable
private fun ExtraSection(form: VehicleForm, viewModel: VehicleSetupViewModel) {
    SectionCard(title = stringResource(R.string.vehicle_section_extra)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NumberField(
                label = stringResource(R.string.field_measured_height),
                value = form.measuredHeight,
                onValueChange = viewModel::onMeasuredHeightChange,
                suffix = stringResource(R.string.unit_mm),
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.field_height_correction),
                value = form.heightCorrection,
                onValueChange = viewModel::onHeightCorrectionChange,
                suffix = stringResource(R.string.unit_mm),
                allowNegative = true,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = stringResource(R.string.field_weight),
                value = form.weight,
                onValueChange = viewModel::onWeightChange,
                suffix = stringResource(R.string.unit_kg),
                modifier = Modifier.weight(1f),
            )
        }
        Note(stringResource(R.string.vehicle_measured_note))
    }
}

@Composable
private fun SafetyMarginSection(selectedMm: Int, onSelect: (Int) -> Unit) {
    SectionCard(title = stringResource(R.string.field_safety_margin)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VehicleSetupViewModel.SAFETY_MARGIN_OPTIONS_MM.forEach { option ->
                ChoiceChip(
                    text = stringResource(R.string.format_mm, Formatters.number(option)),
                    selected = option == selectedMm,
                    onClick = { onSelect(option) },
                )
            }
        }
        Note(stringResource(R.string.safety_margin_note))
    }
}

@Composable
private fun VehicleSummary(
    state: VehicleSetupUiState,
    onboarding: Boolean,
    editable: Boolean,
    onSave: () -> Unit,
    onSkip: () -> Unit,
) {
    val form = state.form
    val preview = state.preview
    SectionCard(title = stringResource(R.string.vehicle_summary_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(form.manufacturer, form.modelName).filter { it.isNotBlank() }.joinToString(" ")
                    .ifEmpty { stringResource(R.string.vehicle_model_unregistered) },
                style = MaterialTheme.typography.titleLarge,
            )
            if (form.demo) DemoBadge()
        }
        LabeledValue(stringResource(R.string.vehicle_summary_confidence), stringResource(state.previewConfidence.labelRes))
        LabeledValue(stringResource(R.string.vehicle_summary_base_height), mmText(form.height.toIntOrNull()))
        if (preview.heightBasis == HeightBasis.MEASURED_TOTAL) {
            LabeledValue(
                stringResource(R.string.vehicle_summary_height_basis),
                stringResource(R.string.vehicle_summary_measured_basis),
            )
        } else {
            LabeledValue(
                stringResource(R.string.vehicle_summary_accessory_height),
                stringResource(R.string.format_mm, Formatters.signedNumber(preview.accessoryHeightMm)),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LabeledValue(stringResource(R.string.vehicle_summary_current_height), mmText(preview.heightMm))
        LabeledValue(
            stringResource(R.string.field_safety_margin),
            stringResource(R.string.format_mm, Formatters.number(form.safetyMarginMm)),
        )
        Text(
            stringResource(R.string.vehicle_summary_required_height),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            mmText(preview.requiredHeightMm(form.safetyMarginMm)),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (preview.heightMm != null && !preview.usableForAnalysis) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = GatefitTheme.status.caution)
                Text(
                    stringResource(R.string.vehicle_summary_unconfirmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = GatefitTheme.status.caution,
                )
            }
        }
        state.issues.forEach { issue ->
            Text(
                stringResource(
                    R.string.field_range_error,
                    stringResource(issue.field.labelRes),
                    Formatters.number(issue.min),
                    Formatters.number(issue.max),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (editable) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = stringResource(if (onboarding) R.string.vehicle_save_and_start else R.string.action_save),
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.canSave) Note(stringResource(R.string.vehicle_save_requires_height))
        }
        // 최초 실행에서 등록을 미뤄도 서비스를 볼 수 있도록 허용, 이 경우 분석 결과는 확인 필요로 표시
        if (onboarding) {
            SecondaryButton(
                text = stringResource(R.string.vehicle_setup_later),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
