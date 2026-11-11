package com.gatefit.ui.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTargetResolution
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.NavigationRepository
import com.gatefit.domain.usecase.GetEntranceDetailsUseCase
import com.gatefit.domain.usecase.RequestEntranceNavigationUseCase
import com.gatefit.domain.usecase.ResolveRouteTargetUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NavigationHandoffContent {
    data object Loading : NavigationHandoffContent
    data object NotFound : NavigationHandoffContent
    data class Ready(
        val facilityName: String,
        val entranceName: String,
        val resolution: RouteTargetResolution,
    ) : NavigationHandoffContent
}

data class NavigationHandoffUiState(
    val content: NavigationHandoffContent = NavigationHandoffContent.Loading,
    val routeState: RouteRequestState = RouteRequestState.Idle,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationHandoffViewModel(
    private val entranceId: String,
    facilities: FacilityRepository,
    private val getEntranceDetails: GetEntranceDetailsUseCase,
    private val requestNavigation: RequestEntranceNavigationUseCase,
    private val navigation: NavigationRepository,
    private val resolveRouteTarget: ResolveRouteTargetUseCase,
) : ViewModel() {

    private val destination = facilities.dataVersion.mapLatest {
        facilities.ensureSeeded()
        val details = getEntranceDetails(entranceId) ?: return@mapLatest NavigationHandoffContent.NotFound
        NavigationHandoffContent.Ready(
            facilityName = details.facility.name,
            entranceName = details.entrance.name,
            resolution = resolveRouteTarget(details.facility, details.entrance),
        )
    }

    val state: StateFlow<NavigationHandoffUiState> = combine(destination, navigation.state) { destination, routeState ->
        NavigationHandoffUiState(destination, routeState)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        NavigationHandoffUiState(),
    )

    fun requestRoute() {
        val current = state.value.content as? NavigationHandoffContent.Ready ?: return
        if (current.resolution !is RouteTargetResolution.Ready || state.value.routeState.isPending) return
        viewModelScope.launch {
            val details = getEntranceDetails(entranceId) ?: return@launch
            requestNavigation(details.facility, details.entrance)
        }
    }

    fun cancelRoute() = navigation.cancelRoute()

    fun clearResult() = navigation.clearResult()
}
