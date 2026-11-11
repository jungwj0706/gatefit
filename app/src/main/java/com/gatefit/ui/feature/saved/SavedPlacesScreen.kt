package com.gatefit.ui.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.ui.components.FacilitySummaryCard
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.MessagePanel
import com.gatefit.ui.components.SecondaryButton
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun SavedPlacesScreen(
    kind: String,
    onOpenFacility: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = gatefitViewModel { container ->
        SavedPlacesViewModel(
            kind = kind,
            savedPlaces = container.savedPlacesRepository,
            getRecentFacilities = container.getRecentFacilities,
            getFavoriteFacilities = container.getFavoriteFacilities,
        )
    }
    val places by viewModel.places.collectAsStateWithLifecycle()
    val isRecent = viewModel.isRecent
    val title = stringResource(if (isRecent) R.string.saved_recents_title else R.string.saved_favorites_title)

    GatefitScreen(
        title = title,
        onBack = onBack,
        actions = {
            if (isRecent && places.isNotEmpty()) {
                SecondaryButton(stringResource(R.string.saved_clear_recents), viewModel::clearRecents)
            }
        },
    ) {
        if (places.isEmpty()) {
            MessagePanel(
                icon = if (isRecent) Icons.Filled.History else Icons.Filled.BookmarkBorder,
                title = stringResource(if (isRecent) R.string.home_recents_empty else R.string.home_favorites_empty),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.itemSpacing),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GatefitTheme.dimens.sectionSpacing),
            ) {
                items(places, key = { it.facility.id }) { summary ->
                    FacilitySummaryCard(summary, onClick = { onOpenFacility(summary.facility.id) })
                }
            }
        }
    }
}
