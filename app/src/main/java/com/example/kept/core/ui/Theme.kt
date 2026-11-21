package com.example.kept.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kept.R

/**
 * The colour names screens used before Daybreak, each pointing at the closest Daybreak token
 * (see DECISIONS.md). Later Daybreak stories move each screen onto [DaybreakColors] directly
 * as they re-lay it out; new code uses [DaybreakColors], not these.
 */
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
    val teal600: Color,
    val teal900: Color,
    val green50: Color,
    val green400: Color,
    val green600: Color,
    val amber50: Color,
    val amber800: Color,
    val blue50: Color,
    val blue400: Color,
    val blue800: Color,
    val pink100: Color,
    val danger: Color,
    val dangerSoft: Color,
    val ink: Color,
    val onInk: Color,
)

private val D = DaybreakColors

val LightColors = KeptColors(
    surface2 = D.white, surface1 = D.cream, surface0 = D.line,
    textPrimary = D.ink, textSecondary = D.muted, textMuted = D.faint,
    border = D.lineSoft, borderStrong = D.field,
    purple50 = D.purpleTint, purple100 = D.purpleTrack, purple200 = D.purpleDone,
    purple400 = D.purple, purple600 = D.purple, purple900 = D.purpleInk,
    // Daybreak has no teal: the old teal "success" colours become its green.
    teal50 = D.greenTint, teal600 = D.green, teal900 = D.greenInk,
    green50 = D.greenTint, green400 = D.greenBright, green600 = D.green,
    amber50 = D.amberTint, amber800 = D.amberInk,
    blue50 = D.shieldTint, blue400 = D.shieldBlue, blue800 = D.shieldBlue,
    // Only the confetti uses it: Sprig's flower pink, until the Sprig story replaces both.
    pink100 = Color(0xFFFF8FB0),
    danger = D.danger, dangerSoft = D.dangerTint,
    ink = D.ink, onInk = D.cream,
)

val LocalKeptColors = staticCompositionLocalOf { LightColors }

object KeptTheme {
    val colors: KeptColors
        @Composable get() = LocalKeptColors.current
}

val Mono = FontFamily.Monospace

/** Bricolage Grotesque, cut at the 14pt optical size, for text up to about 20sp. */
val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

/** Bricolage Grotesque ExtraBold at the 40pt optical size, for display, headline and timer. */
val BricolageDisplay = FontFamily(
    Font(R.font.bricolage_grotesque_display_extrabold, FontWeight.ExtraBold),
)

/** The spec's type roles (Design tokens, Type). Sentence case everywhere; never all caps. */
object DaybreakType {
    val display = TextStyle(fontFamily = BricolageDisplay, fontSize = 52.sp, lineHeight = (52 * 0.96).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em)
    val headline = TextStyle(fontFamily = BricolageDisplay, fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
    val pageTitle = TextStyle(fontFamily = BricolageDisplay, fontSize = 34.sp, lineHeight = (34 * 1.02).sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
    val section = TextStyle(fontFamily = Bricolage, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val rowTitle = TextStyle(fontFamily = Bricolage, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontFamily = Bricolage, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val bodySmall = TextStyle(fontFamily = Bricolage, fontSize = 14.sp, lineHeight = (14 * 1.4).sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontFamily = Bricolage, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val smallLabel = TextStyle(fontFamily = Bricolage, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    val button = TextStyle(fontFamily = Bricolage, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
    val tab = TextStyle(fontFamily = Bricolage, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    /** 84sp with tabular figures, so the countdown does not jitter. */
    val timer = TextStyle(fontFamily = BricolageDisplay, fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em, fontFeatureSettings = "tnum")
}

/**
 * The Material styles existing screens use, unchanged in size so no screen re-flows, now in
 * Bricolage Grotesque. Regular (400) is not bundled, so those styles use Medium (500), the
 * spec's body weight.
 */
val KeptTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Bricolage, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Bricolage, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Bricolage, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Bricolage, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = Bricolage, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Bricolage, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontFamily = Bricolage, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontFamily = Bricolage, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontFamily = Bricolage, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = Bricolage, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = Bricolage, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
)

val KeptShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * The only theme. KEPT is light only (spec, owner decision 9): it never reads the system dark
 * mode setting, so a phone in dark mode still renders the light theme.
 *
 * [reduceMotion] defaults to the system animator scale; tests pass it explicitly.
 */
@Composable
fun KeptTheme(
    reduceMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val scheme = lightColorScheme(
        primary = D.purple, onPrimary = D.white, primaryContainer = D.purpleTint, onPrimaryContainer = D.purpleInk,
        secondary = D.green, onSecondary = D.white, background = D.cream, onBackground = D.ink,
        surface = D.white, onSurface = D.ink, surfaceVariant = D.line, onSurfaceVariant = D.muted,
        outline = D.lineSoft, outlineVariant = D.field, error = D.danger, onError = D.white,
    )
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val systemReduce = remember(context, inPreview) { !inPreview && Motion.systemReduceMotion(context) }
    CompositionLocalProvider(LocalKeptColors provides LightColors, LocalReduceMotion provides (reduceMotion ?: systemReduce)) {
        MaterialTheme(colorScheme = scheme, typography = KeptTypography, shapes = KeptShapes, content = content)
    }
}
