package com.gatefit.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.RestrictedFeature
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.LocalDrivingRestrictions
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.components.SwitchRow
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun SettingsScreen(
    onOpenVehicle: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDataPolicy: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = gatefitViewModel { container -> SettingsViewModel(container.settingsRepository) }
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val canEdit = LocalDrivingRestrictions.current.allows(RestrictedFeature.VEHICLE_PROFILE_EDIT)

    GatefitScreen(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.sectionSpacing),
        ) {
            SettingsLink(Icons.Filled.DirectionsCar, stringResource(R.string.settings_vehicle), onOpenVehicle, canEdit)
            SettingsLink(Icons.Filled.History, stringResource(R.string.saved_recents_title), onOpenRecents)
            SettingsLink(Icons.Filled.Bookmark, stringResource(R.string.saved_favorites_title), onOpenFavorites)
            SectionCard(title = stringResource(R.string.settings_data_title)) {
                SwitchRow(
                    title = stringResource(R.string.settings_demo_title),
                    description = stringResource(R.string.settings_demo_note),
                    checked = settings.demoDataEnabled,
                    onCheckedChange = viewModel::setDemoDataEnabled,
                    enabled = canEdit,
                )
            }
            SettingsLink(Icons.Filled.PrivacyTip, stringResource(R.string.settings_data_policy), onOpenDataPolicy)
        }
    }
}

@Composable
private fun SettingsLink(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    SectionCard(onClick = if (enabled) onClick else null) {
        Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
