package com.gatefit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 차량 실내 환경 기준으로 다크 테마만 제공
private val GatefitColors = darkColorScheme(
    primary = Color(0xFF8FA8FF),
    onPrimary = Color(0xFF0A1024),
    primaryContainer = Color(0xFF23346B),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFB4C0DC),
    onSecondary = Color(0xFF10182A),
    secondaryContainer = Color(0xFF263247),
    onSecondaryContainer = Color(0xFFE1E8F7),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFF1F4FA),
    surface = Color(0xFF121925),
    onSurface = Color(0xFFF1F4FA),
    surfaceVariant = Color(0xFF1B2433),
    onSurfaceVariant = Color(0xFFB7C0D1),
    outline = Color(0xFF55607A),
    outlineVariant = Color(0xFF2B3649),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF2B0A07),
    errorContainer = Color(0xFF4A1C18),
    onErrorContainer = Color(0xFFFFDAD5),
)

@Immutable
data class StatusColors(
    val match: Color = Color(0xFF5BE08F),
    val matchContainer: Color = Color(0xFF123222),
    val caution: Color = Color(0xFFFFC857),
    val cautionContainer: Color = Color(0xFF3A2C0C),
    val mismatch: Color = Color(0xFFFF7B72),
    val mismatchContainer: Color = Color(0xFF431A17),
    val neutral: Color = Color(0xFFB7C0D1),
    val neutralContainer: Color = Color(0xFF232D3F),
    val demo: Color = Color(0xFFD7A8FF),
    val demoContainer: Color = Color(0xFF35204D),
)

@Immutable
data class Dimens(
    // 주행 환경에서 누르기 쉬운 최소 터치 영역
    val touchTarget: Dp = 76.dp,
    val screenPadding: Dp = 32.dp,
    val sectionSpacing: Dp = 24.dp,
    val itemSpacing: Dp = 16.dp,
    val cardPadding: Dp = 24.dp,
    val iconSize: Dp = 36.dp,
    val contentMaxWidth: Dp = 1280.dp,
)

val LocalStatusColors = staticCompositionLocalOf { StatusColors() }
val LocalDimens = staticCompositionLocalOf { Dimens() }

private val GatefitTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 26.sp, lineHeight = 38.sp),
    bodyMedium = TextStyle(fontSize = 24.sp, lineHeight = 34.sp),
    bodySmall = TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
    labelLarge = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
)

private val GatefitShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun GatefitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GatefitColors,
        typography = GatefitTypography,
        shapes = GatefitShapes,
        content = content,
    )
}

object GatefitTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current

    val dimens: Dimens
        @Composable get() = LocalDimens.current
}
