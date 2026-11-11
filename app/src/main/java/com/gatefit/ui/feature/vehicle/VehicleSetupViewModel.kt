package com.gatefit.ui.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.EffectiveDimensions
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.SpecSource
import com.gatefit.domain.model.VehicleAccessory
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleIdentityHint
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.model.VehicleModelSpec
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.SettingsRepository
import com.gatefit.domain.repository.VehicleInfoRepository
import com.gatefit.domain.repository.VehicleProfileRepository
import com.gatefit.domain.repository.VehicleSpecRepository
import com.gatefit.domain.usecase.ApplyVehicleModelSpecUseCase
import com.gatefit.domain.usecase.CalculateEffectiveVehicleDimensionsUseCase
import com.gatefit.domain.usecase.GetVehicleInformationUseCase
import com.gatefit.domain.usecase.ProfileIssue
import com.gatefit.domain.usecase.SaveProfileResult
import com.gatefit.domain.usecase.SaveVehicleProfileUseCase
import com.gatefit.domain.usecase.VehicleProfileValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpecMode { CATALOG, MANUAL }

data class AccessoryForm(
    val id: String,
    val type: AccessoryType,
    val height: String = "",
    val width: String = "",
    val length: String = "",
    val verified: Boolean = false,
)

sealed interface VehicleReadState {
    data object Idle : VehicleReadState
    data object Reading : VehicleReadState
    data class Hint(val hint: VehicleIdentityHint) : VehicleReadState
    data object Unsupported : VehicleReadState
    data class Failed(val failure: VehicleLinkFailure) : VehicleReadState
}

data class VehicleForm(
    val profileId: String,
    val mode: SpecMode = SpecMode.CATALOG,
    val selectedSpec: VehicleModelSpec? = null,
    // 저장된 프로필의 제조사 제원을 다시 선택하지 않고 그대로 유지하는 상태
    val keepsSavedSpec: Boolean = false,
    val manufacturer: String = "",
    val modelName: String = "",
    val trimName: String? = null,
    val specSourceName: String? = null,
    val modelYear: Int? = null,
    val height: String = "",
    val width: String = "",
    val length: String = "",
    val weight: String = "",
    // 직접 입력한 제원을 실측·등록증 등으로 확인했는지 여부
    val manualConfirmed: Boolean = false,
    val accessories: List<AccessoryForm> = emptyList(),
    val heightCorrection: String = "",
    val measuredHeight: String = "",
    val safetyMarginMm: Int = VehicleProfile.DEFAULT_SAFETY_MARGIN_MM,
    val demo: Boolean = false,
)

data class VehicleSetupUiState(
    val form: VehicleForm,
    val query: String = "",
    val specs: List<VehicleModelSpec> = emptyList(),
    val vehicleLink: VehicleLinkStatus = VehicleLinkStatus(),
    val readState: VehicleReadState = VehicleReadState.Idle,
    val preview: EffectiveDimensions,
    val previewConfidence: SpecConfidence,
    val issues: List<ProfileIssue> = emptyList(),
    val hasSavedProfile: Boolean = false,
    val saved: Boolean = false,
) {
    val canReadFromVehicle: Boolean
        get() = vehicleLink.state == VehicleLinkState.CONNECTED &&
            vehicleLink.capabilities.vinInfo != CapabilitySupport.UNSUPPORTED

    val hasCatalogSelection: Boolean
        get() = form.mode == SpecMode.CATALOG && (form.selectedSpec != null || form.keepsSavedSpec)

    val canSave: Boolean
        get() = preview.heightMm != null
}

class VehicleSetupViewModel(
    private val profiles: VehicleProfileRepository,
    private val specs: VehicleSpecRepository,
    private val settings: SettingsRepository,
    vehicleInfo: VehicleInfoRepository,
    private val getVehicleInformation: GetVehicleInformationUseCase,
    private val saveProfile: SaveVehicleProfileUseCase,
    private val applySpec: ApplyVehicleModelSpecUseCase,
    private val calculateEffectiveDimensions: CalculateEffectiveVehicleDimensionsUseCase,
    private val newId: () -> String,
) : ViewModel() {

    private data class Internal(
        val form: VehicleForm,
        val query: String = "",
        val specs: List<VehicleModelSpec> = emptyList(),
        val readState: VehicleReadState = VehicleReadState.Idle,
        val issues: List<ProfileIssue> = emptyList(),
        val saved: Boolean = false,
    )

    private val existing = profiles.profile.value
    private val internal = MutableStateFlow(Internal(form = existing?.toForm() ?: VehicleForm(profileId = newId())))

    val state: StateFlow<VehicleSetupUiState> = combine(internal, vehicleInfo.status) { current, link ->
        current.toUiState(link)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), internal.value.toUiState(vehicleInfo.status.value))

    private var searchJob: Job? = null

    init {
        search("")
    }

    fun onQueryChange(text: String) {
        internal.update { it.copy(query = text) }
        search(text)
    }

    private fun search(text: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val found = runCatching { specs.search(text) }.getOrDefault(emptyList())
            internal.update { it.copy(specs = found) }
        }
    }

    fun selectSpec(spec: VehicleModelSpec) = updateForm {
        it.copy(
            mode = SpecMode.CATALOG,
            selectedSpec = spec,
            keepsSavedSpec = false,
            manufacturer = spec.manufacturer,
            modelName = spec.modelName,
            trimName = spec.trimName,
            specSourceName = spec.sourceName,
            height = spec.dimensions.heightMm?.toString().orEmpty(),
            width = spec.dimensions.widthMm?.toString().orEmpty(),
            length = spec.dimensions.lengthMm?.toString().orEmpty(),
            manualConfirmed = false,
        )
    }

    // 직접 입력으로 바꾸면 제조사 제원 등급을 유지하지 않고 사용자 입력값으로 취급
    fun switchToManual() = updateForm {
        it.copy(
            mode = SpecMode.MANUAL,
            selectedSpec = null,
            keepsSavedSpec = false,
            trimName = null,
            specSourceName = null,
            manualConfirmed = false,
        )
    }

    fun switchToCatalog() = updateForm { it.copy(mode = SpecMode.CATALOG) }

    fun onManufacturerChange(value: String) = updateForm { it.copy(manufacturer = value.take(MAX_NAME_LENGTH)) }

    fun onModelNameChange(value: String) = updateForm { it.copy(modelName = value.take(MAX_NAME_LENGTH)) }

    // 값이 바뀌면 이전 확인은 더 이상 유효하지 않으므로 다시 확인 요청
    fun onHeightChange(value: String) = updateForm { it.copy(height = value, manualConfirmed = false) }

    fun onWidthChange(value: String) = updateForm { it.copy(width = value, manualConfirmed = false) }

    fun onLengthChange(value: String) = updateForm { it.copy(length = value, manualConfirmed = false) }

    fun onWeightChange(value: String) = updateForm { it.copy(weight = value) }

    fun onManualConfirmedChange(confirmed: Boolean) = updateForm { it.copy(manualConfirmed = confirmed) }

    fun onHeightCorrectionChange(value: String) = updateForm { it.copy(heightCorrection = value) }

    fun onMeasuredHeightChange(value: String) = updateForm { it.copy(measuredHeight = value) }

    fun onSafetyMarginChange(marginMm: Int) = updateForm {
        it.copy(safetyMarginMm = marginMm.coerceIn(VehicleProfileValidator.SAFETY_MARGIN_RANGE))
    }

    fun addAccessory(type: AccessoryType) = updateForm {
        if (it.accessories.size >= MAX_ACCESSORIES) it
        else it.copy(accessories = it.accessories + AccessoryForm(id = newId(), type = type))
    }

    fun updateAccessory(accessory: AccessoryForm) = updateForm { form ->
        form.copy(accessories = form.accessories.map { if (it.id == accessory.id) accessory else it })
    }

    fun removeAccessory(id: String) = updateForm { form ->
        form.copy(accessories = form.accessories.filterNot { it.id == id })
    }

    // 사용자가 직접 요청했을 때만 차량 식별 정보를 조회, 결과는 제조사·연식 힌트로만 사용
    fun readFromVehicle() {
        if (internal.value.readState == VehicleReadState.Reading) return
        internal.update { it.copy(readState = VehicleReadState.Reading) }
        viewModelScope.launch {
            val readState = when (val result = getVehicleInformation()) {
                is VehicleInfoResult.Available -> VehicleReadState.Hint(result.hint)
                VehicleInfoResult.Unsupported -> VehicleReadState.Unsupported
                is VehicleInfoResult.Failed -> VehicleReadState.Failed(result.failure)
            }
            internal.update { current ->
                val hint = (readState as? VehicleReadState.Hint)?.hint
                current.copy(
                    readState = readState,
                    form = if (hint == null) current.form else current.form.copy(
                        modelYear = hint.modelYear ?: current.form.modelYear,
                        demo = hint.demo,
                    ),
                )
            }
            val manufacturer = (readState as? VehicleReadState.Hint)?.hint?.manufacturer
            val form = internal.value.form
            if (manufacturer != null && form.selectedSpec == null && !form.keepsSavedSpec) onQueryChange(manufacturer)
        }
    }

    fun save() {
        val profile = internal.value.form.toProfile()
        viewModelScope.launch {
            when (val result = saveProfile(profile)) {
                is SaveProfileResult.Rejected -> internal.update { it.copy(issues = result.issues) }
                is SaveProfileResult.Saved -> {
                    settings.update { it.copy(onboardingCompleted = true) }
                    internal.update { it.copy(issues = emptyList(), saved = true) }
                }
            }
        }
    }

    private fun updateForm(transform: (VehicleForm) -> VehicleForm) {
        internal.update { it.copy(form = transform(it.form), issues = emptyList()) }
    }

    private fun Internal.toUiState(link: VehicleLinkStatus): VehicleSetupUiState {
        val draft = form.toProfile()
        return VehicleSetupUiState(
            form = form,
            query = query,
            specs = specs,
            vehicleLink = link,
            readState = readState,
            preview = calculateEffectiveDimensions(draft),
            previewConfidence = draft.specConfidence,
            issues = issues,
            hasSavedProfile = existing != null,
            saved = saved,
        )
    }

    private fun VehicleForm.toProfile(): VehicleProfile {
        val base = VehicleProfile(
            id = profileId,
            modelYear = modelYear,
            accessories = accessories.map { accessory ->
                VehicleAccessory(
                    id = accessory.id,
                    type = accessory.type,
                    additionalHeightMm = accessory.height.toIntOrNull() ?: 0,
                    additionalWidthMm = accessory.width.toIntOrNull() ?: 0,
                    additionalLengthMm = accessory.length.toIntOrNull() ?: 0,
                    verified = accessory.verified,
                )
            },
            heightCorrectionMm = heightCorrection.toIntOrNull() ?: 0,
            measuredTotalHeightMm = measuredHeight.toIntOrNull(),
            weightKg = weight.toIntOrNull(),
            safetyMarginMm = safetyMarginMm,
            demo = demo,
        )
        val spec = selectedSpec
        if (mode == SpecMode.CATALOG && spec != null) return applySpec(base, spec)
        if (mode == SpecMode.CATALOG && keepsSavedSpec && existing != null) {
            return base.copy(
                manufacturer = existing.manufacturer,
                modelName = existing.modelName,
                trimName = existing.trimName,
                baseDimensions = existing.baseDimensions,
                specSource = existing.specSource,
                specConfidence = existing.specConfidence,
                specSourceName = existing.specSourceName,
                specSourceUrl = existing.specSourceUrl,
            )
        }

        val dimensions = VehicleDimensions(
            lengthMm = length.toIntOrNull(),
            widthMm = width.toIntOrNull(),
            heightMm = height.toIntOrNull(),
        )
        return base.copy(
            manufacturer = manufacturer.trim().ifEmpty { null },
            modelName = modelName.trim().ifEmpty { null },
            baseDimensions = dimensions,
            specSource = SpecSource.USER,
            // 확인하지 않은 입력값은 추정값으로 저장되어 조건 충족 판정에 쓰이지 않음
            specConfidence = when {
                dimensions.heightMm == null -> SpecConfidence.UNKNOWN
                manualConfirmed -> SpecConfidence.USER_CONFIRMED
                else -> SpecConfidence.USER_ESTIMATED
            },
        )
    }

    private fun VehicleProfile.toForm(): VehicleForm {
        val fromCatalog = specSource == SpecSource.MANUFACTURER && specConfidence == SpecConfidence.VERIFIED_BY_MANUFACTURER
        return VehicleForm(
            profileId = id,
            mode = if (fromCatalog) SpecMode.CATALOG else SpecMode.MANUAL,
            keepsSavedSpec = fromCatalog,
            manufacturer = manufacturer.orEmpty(),
            modelName = modelName.orEmpty(),
            trimName = trimName,
            specSourceName = specSourceName,
            modelYear = modelYear,
            height = baseDimensions.heightMm?.toString().orEmpty(),
            width = baseDimensions.widthMm?.toString().orEmpty(),
            length = baseDimensions.lengthMm?.toString().orEmpty(),
            weight = weightKg?.toString().orEmpty(),
            manualConfirmed = specConfidence == SpecConfidence.USER_CONFIRMED,
            accessories = accessories.map { accessory ->
                AccessoryForm(
                    id = accessory.id,
                    type = accessory.type,
                    height = accessory.additionalHeightMm.takeIf { it != 0 }?.toString().orEmpty(),
                    width = accessory.additionalWidthMm.takeIf { it != 0 }?.toString().orEmpty(),
                    length = accessory.additionalLengthMm.takeIf { it != 0 }?.toString().orEmpty(),
                    verified = accessory.verified,
                )
            },
            heightCorrection = heightCorrectionMm.takeIf { it != 0 }?.toString().orEmpty(),
            measuredHeight = measuredTotalHeightMm?.toString().orEmpty(),
            safetyMarginMm = safetyMarginMm,
            demo = demo,
        )
    }

    companion object {
        const val MAX_ACCESSORIES = 5
        const val MAX_NAME_LENGTH = 40
        val SAFETY_MARGIN_OPTIONS_MM = listOf(0, 50, 100, 150, 200, 300)
    }
}
