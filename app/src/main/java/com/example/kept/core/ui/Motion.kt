package com.example.kept.core.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reduce motion. When the system animator scale is 0 ("Remove animations" in accessibility
 * settings sets it to 0 too) Daybreak plays no orchestrated motion: skies and Sprig snap to
 * their new state instead of animating. [KeptTheme] provides the value; tests override it.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Motion {
    /** Sky colour, Sprig height and hill colour (spec, Motion). */
    const val SKY_MS = 700

    /** Sprig's height change overshoots: `cubic-bezier(0.3, 1.5, 0.5, 1)` over 800ms. */
    const val RISE_MS = 800
    val RiseEasing: Easing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)

    /** True when the animator duration scale turns animations off. */
    fun isReduced(animatorScale: Float): Boolean = animatorScale == 0f

    /** Reads the system animator scale; 1 (animations on) when it cannot be read. */
    fun systemReduceMotion(context: Context): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return isReduced(scale)
    }
}

/** A tween that turns into a snap when reduce motion is on. */
@Composable
@ReadOnlyComposable
fun <T> keptTween(durationMillis: Int = Motion.SKY_MS, easing: Easing = FastOutSlowInEasing): AnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else tween(durationMillis, easing = easing)
