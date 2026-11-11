package com.gatefit.ui.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.EntranceAnalysis
import com.gatefit.domain.model.FacilityAnalysis
import com.gatefit.domain.model.RouteBlockReason
import com.gatefit.domain.model.RouteTargetResolution
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.SavedPlacesRepository
import com.gatefit.domain.repository.VehicleProfileRepository
import com.gatefit.domain.usecase.GetFacilityEntrancesUseCase
import com.gatefit.domain.usecase.ResolveRouteTargetUseCase
import com.gatefit.domain.usecase.ToggleFavoriteFacilityUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class EntranceItem(
    val analysis: EntranceAnalysis,
    // null이 아니면 내비게이션 목적지로 설정할 수 없는 출입구
    val routeBlock: RouteBlockReason?,
)

sealed interface AnalysisContent {
    data object Loading : AnalysisContent
    data object NotFound : AnalysisContent
    data class Ready(val analysis: FacilityAnalysis, val entrances: List<EntranceItem>) : AnalysisContent
}

data class AnalysisUiState(
    val content: AnalysisContent = AnalysisContent.Loading,
    val profile: VehicleProfile? = null,
    val favorite: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModel(
    private val facilityId: String,
    profiles: VehicleProfileRepository,
    private val facilities: FacilityRepository,
    private val savedPlaces: SavedPlacesRepository,
    private val getFacilityEntrances: GetFacilityEntrancesUseCase,
    private val resolveRouteTarget: ResolveRouteTargetUseCase,
    private val toggleFavorite: ToggleFavoriteFacilityUseCase,
    private val clock: () -> ZonedDateTime,
) : ViewModel() {

    private val refreshRequests = MutableStateFlow(0)
    private var recentRecorded = false

    // 차량 프로필·시설 데이터가 바뀌거나 화면에 다시 진입하면 현재 시각 기준으로 재분석
    private val content = combine(profiles.profile, facilities.dataVersion, refreshRequests) { profile, _, _ -> profile }
        .mapLatest { profile ->
            facilities.ensureSeeded()
            val analysis = getFacilityEntrances(facilityId, profile ?: VehicleProfile.UNREGISTERED, clock())
            if (analysis == null) {
                AnalysisContent.NotFound
            } else {
                recordRecentOnce()
                AnalysisContent.Ready(
                    analysis = analysis,
                    entrances = analysis.entrances.map { item ->
                        val resolution = resolveRouteTarget(analysis.facility, item.entrance)
                        EntranceItem(item, (resolution as? RouteTargetResolution.Blocked)?.reason)
                    },
                )
            }
        }
        .onStart<AnalysisContent> { emit(AnalysisContent.Loading) }

    val state: StateFlow<AnalysisUiState> = combine(
        content,
        profiles.profile,
        savedPlaces.favoriteIds.map { facilityId in it }.onStart { emit(false) },
    ) { current, profile, favorite ->
        AnalysisUiState(current, profile, favorite)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AnalysisUiState())

    fun refresh() {
        refreshRequests.update { it + 1 }
    }

    fun toggleFavorite() {
        val next = !state.value.favorite
        viewModelScope.launch { toggleFavorite(facilityId, next) }
    }

    private suspend fun recordRecentOnce() {
        if (recentRecorded) return
        recentRecorded = true
        savedPlaces.recordRecent(facilityId)
    }
}
