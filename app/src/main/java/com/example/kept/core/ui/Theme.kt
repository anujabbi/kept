package com.example.kept.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class KeptColors(
    val surface2: Color,
    val surface1: Color,
    val surface0: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val purple50: Color,
    val purple100: Color,
    val purple200: Color,
    val purple400: Color,
    val purple600: Color,
    val purple900: Color,
    val teal50: Color,
    val teal100: Color,
    val teal600: Color,
    val teal900: Color,
    val green50: Color,
    val green400: Color,
    val green600: Color,
    val amber50: Color,
    val amber800: Color,
    val blue50: Color,
    val blue100: Color,
    val blue400: Color,
    val blue800: Color,
    val pink100: Color,
    val danger: Color,
    val dangerSoft: Color,
    val ink: Color,
    val onInk: Color,
    val isDark: Boolean,
)

val LightColors = KeptColors(
    surface2 = Color(0xFFFFFFFF), surface1 = Color(0xFFF7F6F3), surface0 = Color(0xFFEFEDE8),
    textPrimary = Color(0xFF1F1E1D), textSecondary = Color(0xFF6B6A66), textMuted = Color(0xFF9A9892),
    border = Color(0xFFE4E2DC), borderStrong = Color(0xFFC9C6BE),
    purple50 = Color(0xFFEEEDFE), purple100 = Color(0xFFCECBF6), purple200 = Color(0xFFAFA9EC),
    purple400 = Color(0xFF7F77DD), purple600 = Color(0xFF534AB7), purple900 = Color(0xFF26215C),
    teal50 = Color(0xFFE1F5EE), teal100 = Color(0xFF9FE1CB), teal600 = Color(0xFF0F6E56), teal900 = Color(0xFF04342C),
    green50 = Color(0xFFEAF3DE), green400 = Color(0xFF97C459), green600 = Color(0xFF3B6D11),
    amber50 = Color(0xFFFAEEDA), amber800 = Color(0xFF633806),
    blue50 = Color(0xFFE6F1FB), blue100 = Color(0xFFB5D4F4), blue400 = Color(0xFF378ADD), blue800 = Color(0xFF0C447C),
    pink100 = Color(0xFFF4C0D1), danger = Color(0xFFA32D2D), dangerSoft = Color(0xFFFBE9E9),
    ink = Color(0xFF1F1E1D), onInk = Color(0xFFFFFFFF),
    isDark = false,
)

val DarkColors = KeptColors(
    surface2 = Color(0xFF232225), surface1 = Color(0xFF1A191B), surface0 = Color(0xFF111012),
    textPrimary = Color(0xFFF2F1EE), textSecondary = Color(0xFFB4B2AC), textMuted = Color(0xFF7E7C77),
    border = Color(0xFF34333A), borderStrong = Color(0xFF4A4952),
    purple50 = Color(0xFF2A2750), purple100 = Color(0xFF3E3980), purple200 = Color(0xFF6A62C9),
    purple400 = Color(0xFF9B93F0), purple600 = Color(0xFFBDB7F7), purple900 = Color(0xFFEEEDFE),
    teal50 = Color(0xFF12332B), teal100 = Color(0xFF1C6A54), teal600 = Color(0xFF7FDDBF), teal900 = Color(0xFFD5F5EA),
    green50 = Color(0xFF223318), green400 = Color(0xFF97C459), green600 = Color(0xFFC3E39A),
    amber50 = Color(0xFF3B2A12), amber800 = Color(0xFFF3D39B),
    blue50 = Color(0xFF132C44), blue100 = Color(0xFF204A73), blue400 = Color(0xFF6FB1F2), blue800 = Color(0xFFCFE4FA),
    pink100 = Color(0xFF7A4A5C), danger = Color(0xFFF08585), dangerSoft = Color(0xFF3A1F1F),
    ink = Color(0xFFF2F1EE), onInk = Color(0xFF1A191B),
    isDark = true,
)

val LocalKeptColors = staticCompositionLocalOf { LightColors }

object KeptTheme {
    val colors: KeptColors
        @Composable get() = LocalKeptColors.current
}

val Mono = FontFamily.Monospace

val KeptTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

val KeptShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun KeptTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.purple400, onPrimary = c.onInk, primaryContainer = c.purple50, onPrimaryContainer = c.purple900,
            secondary = c.teal600, onSecondary = c.onInk, background = c.surface1, onBackground = c.textPrimary,
            surface = c.surface2, onSurface = c.textPrimary, surfaceVariant = c.surface0, onSurfaceVariant = c.textSecondary,
            outline = c.border, outlineVariant = c.borderStrong, error = c.danger, onError = c.onInk,
        )
    } else {
        lightColorScheme(
            primary = c.purple600, onPrimary = c.onInk, primaryContainer = c.purple50, onPrimaryContainer = c.purple900,
            secondary = c.teal600, onSecondary = c.onInk, background = c.surface1, onBackground = c.textPrimary,
            surface = c.surface2, onSurface = c.textPrimary, surfaceVariant = c.surface0, onSurfaceVariant = c.textSecondary,
            outline = c.border, outlineVariant = c.borderStrong, error = c.danger, onError = c.onInk,
        )
    }
    CompositionLocalProvider(LocalKeptColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = KeptTypography, shapes = KeptShapes, content = content)
    }
}
