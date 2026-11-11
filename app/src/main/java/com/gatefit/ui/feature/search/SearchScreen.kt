package com.gatefit.ui.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.RestrictedFeature
import com.gatefit.ui.components.ChoiceChip
import com.gatefit.ui.components.FacilitySummaryCard
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.GatefitTextField
import com.gatefit.ui.components.LargeIconButton
import com.gatefit.ui.components.LoadingPanel
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.components.MessagePanel
import com.gatefit.ui.components.NoteText
import com.gatefit.ui.components.SectionTitle
import com.gatefit.ui.format.Formatters
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun SearchScreen(
    initialCategory: FacilityCategory?,
    onOpenFacility: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = gatefitViewModel { container ->
        SearchViewModel(
            initialCategory = initialCategory,
            facilities = container.facilityRepository,
            searchFacilities = container.searchFacilities,
            getRecentFacilities = container.getRecentFacilities,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inputAllowed = LocalDrivingRestrictions.current.allows(RestrictedFeature.FACILITY_SEARCH_INPUT)
    val spacing = GatefitTheme.dimens.itemSpacing

    GatefitScreen(title = stringResource(R.string.search_title), onBack = onBack) {
        if (inputAllowed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
                GatefitTextField(
                    label = stringResource(R.string.search_field_label),
                    value = state.criteria.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.weight(1f),
                    leadingIcon = Icons.Filled.Search,
                    imeAction = ImeAction.Search,
                )
                if (state.criteria.query.isNotEmpty()) {
                    LargeIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        onClick = { viewModel.onQueryChange("") },
                    )
                }
            }
            CategoryRow(state, viewModel::onCategoryChange)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 520.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = spacing, bottom = GatefitTheme.dimens.sectionSpacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            val results = state.results
            val showRecents = !inputAllowed || state.criteria.isEmpty
            when {
                showRecents -> {
                    if (!inputAllowed) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            MessagePanel(
                                icon = Icons.Filled.DirectionsCar,
                                title = stringResource(R.string.home_search_restricted),
                                message = stringResource(R.string.driving_restricted_message),
                                tint = GatefitTheme.status.caution,
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(stringResource(R.string.home_recents))
                    }
                    if (state.recents.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NoteText(stringResource(R.string.home_recents_empty))
                        }
                    }
                    items(state.recents, key = { it.facility.id }) { summary ->
                        FacilitySummaryCard(summary, onClick = { onOpenFacility(summary.facility.id) })
                    }
                    if (inputAllowed) {
                        item(span = { GridItemSpan(maxLineSpan) }) { ScopeNote(state.registeredFacilityCount) }
                    }
                }
                results == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadingPanel(stringResource(R.string.search_loading))
                }
                results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    MessagePanel(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.search_empty_title),
                        message = stringResource(R.string.search_empty_message),
                    )
                }
                else -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NoteText(
                            stringResource(
                                if (state.truncated) R.string.search_result_truncated else R.string.search_result_count,
                                results.size,
                            )
                        )
                    }
                    items(results, key = { it.facility.id }) { summary ->
                        FacilitySummaryCard(summary, onClick = { onOpenFacility(summary.facility.id) })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { ScopeNote(state.registeredFacilityCount) }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(state: SearchUiState, onSelect: (FacilityCategory?) -> Unit) {
    if (state.categories.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChoiceChip(
            text = stringResource(R.string.category_all),
            selected = state.criteria.category == null,
            onClick = { onSelect(null) },
        )
        state.categories.forEach { item ->
            ChoiceChip(
                text = stringResource(item.category.labelRes),
                selected = state.criteria.category == item.category,
                onClick = { onSelect(item.category) },
            )
        }
    }
}

// 전국의 모든 시설이 검색되는 것처럼 보이지 않도록 등록 범위를 명시
@Composable
private fun ScopeNote(registeredFacilityCount: Int?) {
    if (registeredFacilityCount == null) return
    NoteText(stringResource(R.string.search_scope_note, Formatters.number(registeredFacilityCount)))
}
