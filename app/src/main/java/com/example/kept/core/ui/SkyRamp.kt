package com.example.kept.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Which sky a screen shows for its progress, and whether text and status bar icons on it are
 * light. Pure, so the rules are unit tested (`SkyRampTest`).
 */
object SkyRamp {

    /** `round(done / total × steps)`, rounding half up like the design's `Math.round`. */
    fun index(done: Int, total: Int, steps: Int): Int {
        if (total <= 0) return 0
        val d = done.coerceIn(0, total)
        // floor(steps × d / total + 1/2), in integers so 1/4 × 6 = 1.5 rounds to 2 exactly.
        return (2 * steps * d + total) / (2 * total)
    }

    /** Home: one of the seven [Sky.progressRamp] colours. */
    fun homeIndex(done: Int, total: Int): Int = index(done, total, Sky.progressRamp.lastIndex)

    fun homeSky(done: Int, total: Int): Color = Sky.progressRamp[homeIndex(done, total)]

    /**
     * Text on the Home sky is light while the index is below the halfway point of the ramp
     * (index 0 to 2 of 0 to 6), else ink.
     */
    fun homeTextIsLight(done: Int, total: Int): Boolean = homeIndex(done, total) < HOME_HALFWAY

    /** Lock while locked: one of the five [Sky.lockRamp] colours. */
    fun lockIndex(done: Int, total: Int): Int = index(done, total, Sky.lockRamp.lastIndex)

    /** Lock: the ramp while anything is left, gold when everything is done. */
    fun lockSky(done: Int, total: Int): Color =
        if (total > 0 && done >= total) Sky.gold else Sky.lockRamp[lockIndex(done, total)]

    /** Every lock-ramp colour is a night colour, so text stays light until the gold sky. */
    fun lockTextIsLight(done: Int, total: Int): Boolean = !(total > 0 && done >= total)

    /** The text colour for a sky whose content is light or dark. */
    fun textOn(lightContent: Boolean): Color = if (lightContent) DaybreakColors.skyTextLight else DaybreakColors.ink

    /**
     * For a fixed sky (night, gold, tide…): light content when the colour is dark. The cut sits
     * between the ramp's index 2 (`#6E5590`, light text) and index 3 (`#B57F98`, ink), so it
     * agrees with [homeTextIsLight] at every step.
     */
    fun prefersLightContent(sky: Color): Boolean = sky.luminance() < LUMINANCE_CUT

    private const val HOME_HALFWAY = 3
    private const val LUMINANCE_CUT = 0.2f
}
