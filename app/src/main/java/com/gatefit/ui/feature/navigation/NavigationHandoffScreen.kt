package com.gatefit.ui.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gatefit.R
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTargetResolution
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.LabeledValue
import com.gatefit.ui.components.LoadingPanel
import com.gatefit.ui.components.MessagePanel
import com.gatefit.ui.components.NoteText
import com.gatefit.ui.components.PrimaryButton
import com.gatefit.ui.components.SecondaryButton
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.format.Formatters
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.navigation.gatefitViewModel
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun NavigationHandoffScreen(entranceId: String, onBack: () -> Unit) {
    val viewModel = gatefitViewModel { container ->
        NavigationHandoffViewModel(
            entranceId = entranceId,
            facilities = container.facilityRepository,
            getEntranceDetails = container.getEntranceDetails,
            requestNavigation = container.requestEntranceNavigation,
            navigation = container.navigationRepository,
            resolveRouteTarget = container.resolveRouteTarget,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    GatefitScreen(title = stringResource(R.string.navigation_title), onBack = onBack) {
        when (val content = state.content) {
            NavigationHandoffContent.Loading -> LoadingPanel(stringResource(R.string.navigation_loading))
            NavigationHandoffContent.NotFound -> MessagePanel(
                icon = Icons.Filled.SearchOff,
                title = stringResource(R.string.navigation_not_found_title),
                message = stringResource(R.string.navigation_not_found_message),
            )
            is NavigationHandoffContent.Ready -> NavigationBody(
                content = content,
                routeState = state.routeState,
                onRequest = viewModel::requestRoute,
                onCancel = viewModel::cancelRoute,
                onClear = viewModel::clearResult,
            )
        }
    }
}

@Composable
private fun NavigationBody(
    content: NavigationHandoffContent.Ready,
    routeState: RouteRequestState,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = GatefitTheme.dimens.sectionSpacing),
        verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.sectionSpacing),
    ) {
        SectionCard(title = stringResource(R.string.navigation_destination_title)) {
            Text(content.facilityName, style = MaterialTheme.typography.titleMedium)
            Text(content.entranceName, style = MaterialTheme.typography.titleLarge)
            when (val resolution = content.resolution) {
                is RouteTargetResolution.Ready -> {
                    LabeledValue(stringResource(R.string.navigation_target_type), stringResource(resolution.target.targetType.labelRes))
                    LabeledValue(
                        stringResource(R.string.navigation_coordinates),
                        "${Formatters.coordinate(resolution.target.latitude)}, ${Formatters.coordinate(resolution.target.longitude)}",
                    )
                    resolution.target.address?.let {
                        LabeledValue(stringResource(R.string.navigation_address), it)
                    }
                }
                is RouteTargetResolution.Blocked -> NoteText(stringResource(resolution.reason.labelRes))
            }
        }
        when {
            content.resolution is RouteTargetResolution.Blocked -> MessagePanel(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.navigation_blocked_title),
                message = stringResource((content.resolution as RouteTargetResolution.Blocked).reason.labelRes),
            )
            else -> NavigationStatePanel(routeState, onRequest, onCancel, onClear)
        }
    }
}

@Composable
private fun NavigationStatePanel(
    state: RouteRequestState,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    when (state) {
        RouteRequestState.Idle -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NoteText(stringResource(R.string.navigation_request_note))
                PrimaryButton(
                    text = stringResource(R.string.navigation_request_action),
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Navigation,
                )
            }
        }
        is RouteRequestState.Connecting,
        is RouteRequestState.Requested,
        is RouteRequestState.Searching -> MessagePanel(
            icon = Icons.Filled.Sync,
            title = stringResource(R.string.navigation_pending_title),
            message = stringResource(R.string.navigation_pending_message),
            action = {
                SecondaryButton(stringResource(R.string.navigation_cancel_action), onCancel)
            },
        )
        is RouteRequestState.Confirmed -> MessagePanel(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.navigation_confirmed_title),
            message = stringResource(R.string.navigation_confirmed_message),
            tint = GatefitTheme.status.match,
            action = {
                SecondaryButton(stringResource(R.string.action_close), onClear)
            },
        )
        is RouteRequestState.Failed -> MessagePanel(
            icon = Icons.Filled.ErrorOutline,
            title = stringResource(R.string.navigation_failed_title),
            message = stringResource(state.failure.labelRes),
            tint = GatefitTheme.status.mismatch,
            action = {
                PrimaryButton(stringResource(R.string.action_retry), onRequest)
            },
        )
    }
}
