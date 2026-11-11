package com.gatefit.ui.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.ui.components.PrimaryButton
import com.gatefit.ui.components.VehicleLinkRow
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun SplashScreen(onFinished: (SplashDestination) -> Unit) {
    val viewModel = gatefitViewModel { container ->
        SplashViewModel(
            facilities = container.facilityRepository,
            vehicleInfo = container.vehicleInfoRepository,
            settings = container.settingsRepository,
            profiles = container.vehicleProfileRepository,
            reports = container.reportRepository,
            appScope = container.appScope,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let(onFinished)
    }
    SplashContent(state, onRetry = viewModel::start)
}

@Composable
private fun SplashContent(state: SplashUiState, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(GatefitTheme.dimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                DataRow(state.data)
                VehicleLinkRow(state.vehicleLink)
            }
            if (state.data == DataPreparation.FAILED) {
                Spacer(Modifier.height(32.dp))
                PrimaryButton(stringResource(R.string.action_retry), onClick = onRetry)
            }
        }
    }
}

@Composable
private fun DataRow(data: DataPreparation) {
    val colors = GatefitTheme.status
    val (icon, tint, text) = when (data) {
        DataPreparation.PREPARING -> Triple(Icons.Filled.Sync, colors.neutral, R.string.splash_preparing_data)
        DataPreparation.READY -> Triple(Icons.Filled.CheckCircle, colors.match, R.string.splash_data_ready)
        DataPreparation.FAILED -> Triple(Icons.Filled.ErrorOutline, colors.mismatch, R.string.splash_data_failed)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(GatefitTheme.dimens.iconSize))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}
