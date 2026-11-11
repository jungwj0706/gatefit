package com.gatefit.ui.feature.entrance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.CompatibilityResult
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.VehicleProfileRepository
import com.gatefit.domain.usecase.AnalyzeEntranceCompatibilityUseCase
import com.gatefit.domain.usecase.GetEntranceDetailsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.ZonedDateTime

sealed interface EntranceDetailContent {
    data object Loading : EntranceDetailContent
    data object NotFound : EntranceDetailContent
    data class Ready(
        val details: EntranceWithFacility,
        val analysis: CompatibilityResult,
    ) : EntranceDetailContent
}

@OptIn(ExperimentalCoroutinesApi::class)
class EntranceDetailViewModel(
    entranceId: String,
    profiles: VehicleProfileRepository,
    facilities: FacilityRepository,
    getEntranceDetails: GetEntranceDetailsUseCase,
    analyzeEntrance: AnalyzeEntranceCompatibilityUseCase,
    clock: () -> ZonedDateTime,
) : ViewModel() {

    // 차량·시설 정보가 변경되면 상세 화면의 판정도 같은 기준으로 다시 계산
    val content: StateFlow<EntranceDetailContent> = combine(profiles.profile, facilities.dataVersion) { profile, _ ->
        profile ?: VehicleProfile.UNREGISTERED
    }.mapLatest { profile ->
        facilities.ensureSeeded()
        val details = getEntranceDetails(entranceId) ?: return@mapLatest EntranceDetailContent.NotFound
        EntranceDetailContent.Ready(
            details = details,
            analysis = analyzeEntrance(profile, details.entrance, clock()),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        EntranceDetailContent.Loading,
    )
}
