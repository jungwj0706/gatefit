package com.gatefit.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gatefit.R
import com.gatefit.ui.components.GatefitScreen
import com.gatefit.ui.components.SectionCard
import com.gatefit.ui.theme.GatefitTheme

@Composable
fun DataPolicyScreen(onBack: () -> Unit) {
    GatefitScreen(title = stringResource(R.string.data_policy_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GatefitTheme.dimens.sectionSpacing),
        ) {
            PolicySection(R.string.data_policy_vehicle_title, R.string.data_policy_vehicle_body)
            PolicySection(R.string.data_policy_entrance_title, R.string.data_policy_entrance_body)
            PolicySection(R.string.data_policy_report_title, R.string.data_policy_report_body)
            PolicySection(R.string.data_policy_safety_title, R.string.data_policy_safety_body)
        }
    }
}

@Composable
private fun PolicySection(titleRes: Int, bodyRes: Int) {
    SectionCard(title = stringResource(titleRes)) {
        Text(
            stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
