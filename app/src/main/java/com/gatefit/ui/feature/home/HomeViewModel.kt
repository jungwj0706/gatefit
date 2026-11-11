package com.gatefit.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.EffectiveDimensions
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.DatasetInfo
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.VehicleInfoRepository
import com.gatefit.domain.repository.VehicleProfileRepository
import com.gatefit.domain.usecase.CalculateEffectiveVehicleDimensionsUseCase
import com.gatefit.domain.usecase.GetFavoriteFacilitiesUseCase
import com.gatefit.domain.usecase.GetRecentFacilitiesUseCase
import com.gatefit.ui.model.CategoryCount
import com.gatefit.ui.model.registeredCategories
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val profile: VehicleProfile? = null,
    val dimensions: EffectiveDimensions? = null,
    val vehicleLink: VehicleLinkStatus = VehicleLinkStatus(),
    val recents: List<FacilitySummary> = emptyList(),
    val favorites: List<FacilitySummary> = emptyList(),
    val categories: List<CategoryCount> = emptyList(),
    val dataset: DatasetInfo? = null,
)

class HomeViewModel(
    profiles: VehicleProfileRepository,
    vehicleInfo: VehicleInfoRepository,
    private val facilities: FacilityRepository,
    getRecentFacilities: GetRecentFacilitiesUseCase,
    getFavoriteFacilities: GetFavoriteFacilitiesUseCase,
    private val calculateEffectiveDimensions: CalculateEffectiveVehicleDimensionsUseCase,
) : ViewModel() {

    private val catalog = facilities.dataVersion.map {
        facilities.ensureSeeded()
        facilities.registeredCategories() to facilities.datasetInfo()
    }

    val state: StateFlow<HomeUiState> = combine(
        profiles.profile,
        vehicleInfo.status,
        getRecentFacilities(),
        getFavoriteFacilities(),
        catalog,
    ) { profile, link, recents, favorites, (categories, dataset) ->
        HomeUiState(
            loading = false,
            profile = profile,
            dimensions = profile?.let { calculateEffectiveDimensions(it) },
            vehicleLink = link,
            recents = recents.take(PREVIEW_COUNT),
            favorites = favorites.take(PREVIEW_COUNT),
            categories = categories,
            dataset = dataset,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HomeUiState())

    companion object {
        const val PREVIEW_COUNT = 3
    }
}
