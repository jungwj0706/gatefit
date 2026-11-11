package com.gatefit.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.gatefit.R
import com.gatefit.domain.model.CheckOutcome
import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.DataFreshness
import com.gatefit.domain.model.TrustLevel
import com.gatefit.ui.format.labelRes
import com.gatefit.ui.theme.GatefitTheme

data class StatusVisual(val icon: ImageVector, val color: Color, val containerColor: Color)

// 색상만으로 상태를 전달하지 않도록 항상 아이콘과 문구를 함께 사용
@Composable
fun CompatibilityStatus.visual(): StatusVisual {
    val colors = GatefitTheme.status
    return when (this) {
        CompatibilityStatus.CONDITION_MATCH -> StatusVisual(Icons.Filled.CheckCircle, colors.match, colors.matchContainer)
        CompatibilityStatus.NEEDS_CONFIRMATION -> StatusVisual(Icons.Filled.Warning, colors.caution, colors.cautionContainer)
        CompatibilityStatus.CONDITION_MISMATCH -> StatusVisual(Icons.Filled.Cancel, colors.mismatch, colors.mismatchContainer)
    }
}

@Composable
fun CheckOutcome.visual(): StatusVisual {
    val colors = GatefitTheme.status
    return when (this) {
        CheckOutcome.PASSED -> StatusVisual(Icons.Filled.CheckCircle, colors.match, colors.matchContainer)
        CheckOutcome.FAILED -> StatusVisual(Icons.Filled.Cancel, colors.mismatch, colors.mismatchContainer)
        CheckOutcome.UNKNOWN -> StatusVisual(Icons.Filled.Warning, colors.caution, colors.cautionContainer)
        CheckOutcome.NOT_PROVIDED -> StatusVisual(Icons.Filled.Help, colors.neutral, colors.neutralContainer)
    }
}

@Composable
fun StatusBadge(status: CompatibilityStatus, modifier: Modifier = Modifier) {
    val visual = status.visual()
    Pill(stringResource(status.labelRes), visual.icon, visual.color, visual.containerColor, modifier)
}

// 최신성이 확인된 검증 정보만 검증 표시, 그 외에는 주의 또는 중립 표시
@Composable
fun TrustBadge(trustLevel: TrustLevel, freshness: DataFreshness, modifier: Modifier = Modifier) {
    val colors = GatefitTheme.status
    val verified = trustLevel == TrustLevel.VERIFIED_OFFICIAL || trustLevel == TrustLevel.VERIFIED_FIELD
    val text = stringResource(trustLevel.labelRes) +
        if (verified && freshness != DataFreshness.FRESH) " · " + stringResource(freshness.labelRes) else ""
    when {
        verified && freshness == DataFreshness.FRESH ->
            Pill(text, Icons.Filled.Verified, colors.match, colors.matchContainer, modifier)
        verified || trustLevel == TrustLevel.PUBLIC_BASELINE ->
            Pill(text, Icons.Filled.Warning, colors.caution, colors.cautionContainer, modifier)
        else -> Pill(text, Icons.Filled.Help, colors.neutral, colors.neutralContainer, modifier)
    }
}

@Composable
fun DemoBadge(modifier: Modifier = Modifier) {
    val colors = GatefitTheme.status
    Pill(stringResource(R.string.badge_demo), Icons.Filled.Science, colors.demo, colors.demoContainer, modifier)
}
