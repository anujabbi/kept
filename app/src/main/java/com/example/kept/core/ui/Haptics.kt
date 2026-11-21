package com.example.kept.core.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Daybreak's light haptics (spec, Haptics). Ticking a habit confirms; a Sprig hop ticks;
 * unticking is silent, so there is no call for it.
 */
class Haptics(private val view: View) {
    fun habitTicked() {
        view.performHapticFeedback(tickConstant(Build.VERSION.SDK_INT))
    }

    fun sprigHop() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    companion object {
        /** `CONFIRM` exists from API 30; below that the nearest light tap is `KEYBOARD_TAP`. */
        fun tickConstant(sdkInt: Int): Int =
            if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
