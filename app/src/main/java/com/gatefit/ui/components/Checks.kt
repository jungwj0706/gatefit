package com.gatefit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gatefit.R
import com.gatefit.domain.model.ConstraintCheck
import com.gatefit.domain.model.ConstraintType
import com.gatefit.ui.format.Formatters
import com.gatefit.ui.format.labelRes

// 분석 항목 한 줄, 판정 결과를 아이콘과 문구로 함께 표시
@Composable
fun CheckLine(check: ConstraintCheck, modifier: Modifier = Modifier, detailed: Boolean = false) {
    val visual = check.outcome.visual()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.color, modifier = Modifier.size(30.dp))
        Column {
            Text(
                stringResource(check.type.labelRes) + " · " + stringResource(check.reason.labelRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            val values = if (detailed) check.valuesText() else null
            if (values != null) {
                Text(
                    values,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConstraintCheck.valuesText(): String? {
    val unit = stringResource(if (type == ConstraintType.WEIGHT) R.string.unit_kg else R.string.unit_mm)
    val parts = listOfNotNull(
        limitValue?.let { stringResource(R.string.check_limit_value, Formatters.number(it), unit) },
        vehicleValue?.let { stringResource(R.string.check_vehicle_value, Formatters.number(it), unit) },
        marginValue?.let { stringResource(R.string.check_margin_value, Formatters.signedNumber(it), unit) },
    )
    return parts.joinToString(" · ").ifEmpty { null }
}
