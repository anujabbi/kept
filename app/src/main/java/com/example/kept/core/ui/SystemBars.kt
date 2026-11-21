package com.example.kept.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

/**
 * Edge to edge, light only. Status bar icons start dark (cream pages) whatever the system dark
 * mode says; a [SkyScaffold] on a dark sky turns them light while it is on screen.
 */
fun ComponentActivity.enableDaybreakEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(NAV_SCRIM, NAV_SCRIM),
    )
}

/** Cream at 90%, behind three-button navigation only (gesture navigation draws no scrim). */
private const val NAV_SCRIM = 0xE6FFF9F1.toInt()

/**
 * Asks for light (true) or dark (false) status bar icons while this is in composition. The
 * newest request on a window wins, and leaving restores the one before it, so during a
 * navigation transition the screen coming in decides even though the old one leaves last.
 */
@Composable
fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    val request = remember { StatusBarRequest(light) }
    SideEffect {
        if (request.light != light) {
            request.light = light
            StatusBarRequests.apply(window)
        }
    }
    DisposableEffect(window) {
        StatusBarRequests.add(window, request)
        onDispose { StatusBarRequests.remove(window, request) }
    }
}

private class StatusBarRequest(var light: Boolean)

private object StatusBarRequests {
    private val byWindow = WeakHashMap<Window, MutableList<StatusBarRequest>>()

    fun add(window: Window, r: StatusBarRequest) {
        byWindow.getOrPut(window) { mutableListOf() }.add(r)
        apply(window)
    }

    fun remove(window: Window, r: StatusBarRequest) {
        byWindow[window]?.remove(r)
        apply(window)
    }

    fun apply(window: Window) {
        val light = byWindow[window]?.lastOrNull()?.light ?: false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !light
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
