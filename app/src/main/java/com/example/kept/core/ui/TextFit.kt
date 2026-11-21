package com.example.kept.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

/**
 * Headlines may wrap between words but never inside one: at large font scales (or with a long
 * app name on the lock screen) the size steps down until the longest word fits [width].
 */
@Composable
fun fitLongestWord(text: String, style: TextStyle, width: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val max = with(density) { width.roundToPx() }
    return remember(text, style, max, density) {
        val widest = text.split(' ').filter { it.isNotEmpty() }
            .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1, density = density).size.width } ?: 0
        if (widest <= max) style
        else {
            val k = max.toFloat() / widest * 0.98f
            style.copy(fontSize = style.fontSize * k, lineHeight = style.lineHeight * k)
        }
    }
}
