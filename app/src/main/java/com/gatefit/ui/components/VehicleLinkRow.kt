package com.gatefit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gatefit.R
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun VehicleLinkRow(status: VehicleLinkStatus, modifier: Modifier = Modifier) {
    val colors = GatefitTheme.status
    val (icon, tint) = when (status.state) {
        VehicleLinkState.CONNECTED -> Icons.Filled.CheckCircle to colors.match
        VehicleLinkState.CONNECTING -> Icons.Filled.Sync to colors.neutral
        VehicleLinkState.IDLE -> Icons.Filled.Info to colors.neutral
        VehicleLinkState.SERVICE_UNAVAILABLE, VehicleLinkState.FAILED -> Icons.Filled.Info to colors.caution
    }
    val title = when (status.state) {
        VehicleLinkState.CONNECTED ->
            stringResource(if (status.demo) R.string.vehicle_link_connected_demo else R.string.vehicle_link_connected)
        VehicleLinkState.CONNECTING -> stringResource(R.string.vehicle_link_connecting)
        VehicleLinkState.IDLE -> stringResource(R.string.vehicle_link_idle)
        VehicleLinkState.SERVICE_UNAVAILABLE, VehicleLinkState.FAILED ->
            stringResource((status.failure ?: VehicleLinkFailure.CONNECTION_FAILED).labelRes)
    }
    val manualFallback = status.state == VehicleLinkState.SERVICE_UNAVAILABLE || status.state == VehicleLinkState.FAILED

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(GatefitTheme.dimens.iconSize))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (status.demo) DemoBadge()
            }
            if (manualFallback) {
                Text(
                    stringResource(R.string.vehicle_manual_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
