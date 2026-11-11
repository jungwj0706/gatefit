package com.gatefit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gatefit.R
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.theme.GatefitTheme

// 시설 목록 항목, 출입구 수와 확인된 제한 정보 범위를 함께 요약
@Composable
fun FacilitySummaryCard(summary: FacilitySummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val facility = summary.facility
    SectionCard(modifier = modifier.heightIn(min = GatefitTheme.dimens.touchTarget), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        facility.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (facility.origin == DataOrigin.DEMO) DemoBadge()
                }
                Text(
                    stringResource(facility.category.labelRes) + " · " +
                        (facility.displayAddress ?: stringResource(R.string.facility_address_missing)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                EntranceCoverage(summary)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(GatefitTheme.dimens.iconSize),
            )
        }
    }
}

@Composable
private fun EntranceCoverage(summary: FacilitySummary) {
    if (summary.entranceCount == 0) {
        Text(
            stringResource(R.string.facility_no_entrances),
            style = MaterialTheme.typography.bodySmall,
            color = GatefitTheme.status.neutral,
        )
        return
    }
    Text(
        stringResource(R.string.facility_entrance_count, summary.entranceCount),
        style = MaterialTheme.typography.bodyMedium,
    )
    val heights = if (summary.heightUnknownCount == 0) {
        stringResource(R.string.facility_height_known, summary.heightKnownCount)
    } else {
        stringResource(R.string.facility_height_known_unknown, summary.heightKnownCount, summary.heightUnknownCount)
    }
    val verification = if (summary.freshEntranceCount == 0) {
        stringResource(R.string.facility_not_reverified)
    } else {
        stringResource(R.string.facility_reverified_count, summary.freshEntranceCount)
    }
    Text(
        "$heights · $verification",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
