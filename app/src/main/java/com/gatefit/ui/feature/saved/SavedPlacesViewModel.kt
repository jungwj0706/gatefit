package com.gatefit.ui.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.repository.SavedPlacesRepository
import com.gatefit.domain.usecase.GetFavoriteFacilitiesUseCase
import com.gatefit.domain.usecase.GetRecentFacilitiesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedPlacesViewModel(
    kind: String,
    private val savedPlaces: SavedPlacesRepository,
    getRecentFacilities: GetRecentFacilitiesUseCase,
    getFavoriteFacilities: GetFavoriteFacilitiesUseCase,
) : ViewModel() {

    val isRecent: Boolean = kind == KIND_RECENT
    val places: StateFlow<List<FacilitySummary>> = (if (isRecent) getRecentFacilities() else getFavoriteFacilities())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun clearRecents() {
        if (!isRecent) return
        viewModelScope.launch { savedPlaces.clearRecents() }
    }

    companion object {
        const val KIND_RECENT = "recent"
    }
}
