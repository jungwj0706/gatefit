package com.gatefit.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.usecase.GetRecentFacilitiesUseCase
import com.gatefit.domain.usecase.SearchFacilitiesUseCase
import com.gatefit.ui.model.CategoryCount
import com.gatefit.ui.model.registeredCategories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SearchCriteria(val query: String = "", val category: FacilityCategory? = null) {
    val isEmpty: Boolean get() = query.isBlank() && category == null
}

data class SearchUiState(
    val criteria: SearchCriteria = SearchCriteria(),
    val categories: List<CategoryCount> = emptyList(),
    val recents: List<FacilitySummary> = emptyList(),
    // null은 아직 조회하지 않았거나 조회 중인 상태
    val results: List<FacilitySummary>? = null,
    val registeredFacilityCount: Int? = null,
) {
    val searching: Boolean get() = !criteria.isEmpty && results == null
    val truncated: Boolean get() = (results?.size ?: 0) >= SearchViewModel.RESULT_LIMIT
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    initialCategory: FacilityCategory?,
    private val facilities: FacilityRepository,
    private val searchFacilities: SearchFacilitiesUseCase,
    getRecentFacilities: GetRecentFacilitiesUseCase,
    debounceMs: Long = 250L,
) : ViewModel() {

    private val criteria = MutableStateFlow(SearchCriteria(category = initialCategory))

    private data class Outcome(val criteria: SearchCriteria, val results: List<FacilitySummary>)

    private val outcome = combine(
        // 입력 중 불필요한 조회를 줄이되 카테고리 선택과 입력 지우기는 즉시 반영
        criteria.debounce { if (it.query.isBlank()) 0L else debounceMs },
        facilities.dataVersion,
    ) { current, _ -> current }
        .mapLatest { current ->
            facilities.ensureSeeded()
            val results = if (current.isEmpty) emptyList() else searchFacilities(current.query, current.category)
            Outcome(current, results)
        }
        .onStart<Outcome?> { emit(null) }

    private val catalog = facilities.dataVersion
        .map {
            facilities.ensureSeeded()
            facilities.registeredCategories() to facilities.datasetInfo().facilityCount
        }
        .onStart<Pair<List<CategoryCount>, Int>?> { emit(null) }

    val state: StateFlow<SearchUiState> = combine(
        criteria,
        outcome,
        getRecentFacilities().onStart { emit(emptyList()) },
        catalog,
    ) { current, latest, recents, info ->
        SearchUiState(
            criteria = current,
            categories = info?.first.orEmpty(),
            recents = recents,
            // 현재 조건과 다른 조건의 결과는 표시하지 않음
            results = latest?.takeIf { it.criteria == current }?.results,
            registeredFacilityCount = info?.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SearchUiState(criteria.value))

    fun onQueryChange(text: String) {
        criteria.update { it.copy(query = text.take(MAX_QUERY_LENGTH)) }
    }

    fun onCategoryChange(category: FacilityCategory?) {
        criteria.update { it.copy(category = category) }
    }

    companion object {
        const val MAX_QUERY_LENGTH = 40
        val RESULT_LIMIT = FacilityQuery().limit
    }
}
