package com.example.kept.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Headlines may wrap between words but never inside one: at large font scales (or with a long
 * app name on the lock screen) the size steps down until the longest word fits [width].
 *
 * The width of a word does not shrink in proportion to its size (Bricolage's optical sizing
 * makes small sizes relatively wider), so a single proportional step is not enough (QA on #127):
 * the size is found by measuring every word, with a binary search between [MIN_FIT] of the size
 * and the full size. Hyphenation is off, so a line can only end between words.
 */
@Composable
fun fitLongestWord(text: String, style: TextStyle, width: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val max = with(density) { width.roundToPx() }
    return remember(text, style, max, density) { fitStyle(measurer, density, text, style, max) }
}

/** The smallest fraction of the size a headline is taken down to. */
const val MIN_FIT = 0.35f

private fun fitStyle(measurer: TextMeasurer, density: Density, text: String, style: TextStyle, maxPx: Int): TextStyle {
    val base = style.copy(hyphens = Hyphens.None)
    val words = text.split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return base
    fun scaled(k: Float) = if (k == 1f) base else base.copy(fontSize = base.fontSize * k, lineHeight = base.lineHeight * k)
    fun fits(k: Float): Boolean {
        val s = scaled(k)
        return words.all { measurer.measure(it, s, softWrap = false, maxLines = 1, density = density).size.width <= maxPx }
    }
    if (fits(1f)) return base
    // The largest scale that fits, searched in [MIN_FIT, 1). A word so long that even MIN_FIT does
    // not fit keeps shrinking below it: a smaller headline is better than one split mid-word.
    var lo = if (fits(MIN_FIT)) MIN_FIT else 0.05f
    var hi = if (lo == MIN_FIT) 1f else MIN_FIT
    repeat(12) {
        val mid = (lo + hi) / 2
        if (fits(mid)) lo = mid else hi = mid
    }
    return scaled(lo)
}
