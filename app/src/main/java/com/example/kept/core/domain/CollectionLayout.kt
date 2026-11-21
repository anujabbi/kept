package com.example.kept.core.domain

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The Sprig tab (spec, Collection; `A6-Collection.dc.html`): the current form on a Tide sky and a
 * grid of all seven forms.
 */
enum class FormStatus { REACHED, CURRENT, LOCKED }

/** One card in the forms grid. Only a [FormStatus.LOCKED] form cannot be tapped. */
data class FormTile(val form: SprigForm, val status: FormStatus) {
    val tappable: Boolean get() = status != FormStatus.LOCKED
}

object CollectionForms {
    /**
     * Every form reached, each counted once: the current form and those below it, plus any form a
     * gallery entry records (a form reached before a streak reset stays reached). The gallery can
     * hold a form more than once (it was reached twice), which must not count twice (QA on #125).
     */
    fun reached(gallery: Iterable<SprigForm>, current: SprigForm): Set<SprigForm> =
        (SprigForm.entries.filter { it.streakThreshold <= current.streakThreshold } + gallery).toSet()

    /** "{k} of 7 reached": the number of distinct forms reached. */
    fun reachedCount(gallery: Iterable<SprigForm>, current: SprigForm): Int = reached(gallery, current).size

    /** All seven forms in order, each reached, current or locked. */
    fun tiles(gallery: Iterable<SprigForm>, current: SprigForm): List<FormTile> {
        val got = reached(gallery, current)
        return SprigForm.entries.map { f ->
            FormTile(
                f,
                when {
                    f == current -> FormStatus.CURRENT
                    f in got -> FormStatus.REACHED
                    else -> FormStatus.LOCKED
                },
            )
        }
    }
}

/** The Sprig tab's copy (spec, Collection; `A6-Collection`). Final; do not reword here. */
object CollectionCopy {
    const val FORMS = "Forms"
    const val FINAL_FORM = "Final form"
    const val YOU_ARE_HERE = "You are here"
    const val REACHED = "Reached"

    fun day(streak: Int): String = "Day $streak of your streak"

    fun reached(count: Int): String = "$count of ${SprigForm.entries.size} reached"

    /**
     * "{Next} in {n} days", or "Final form" at Celestial. One day left reads "in 1 day"
     * (DECISIONS.md, #131). Words are joined by no-break spaces: a pill never wraps.
     */
    fun nextPill(streak: Int): String {
        val s = streak.coerceAtLeast(0)
        val next = SprigForm.next(SprigForm.forStreak(s)) ?: return FINAL_FORM
        val n = next.streakThreshold - s
        return "${next.displayName} in $n ${if (n == 1) "day" else "days"}".replace(' ', NBSP)
    }

    /** The second line of a card: "You are here", "Reached" or "Day {n}". */
    fun note(tile: FormTile): String = when (tile.status) {
        FormStatus.CURRENT -> YOU_ARE_HERE
        FormStatus.REACHED -> REACHED
        FormStatus.LOCKED -> "Day ${tile.form.streakThreshold}"
    }

    /** What TalkBack reads for a card. */
    fun description(tile: FormTile): String = when (tile.status) {
        FormStatus.CURRENT -> "${tile.form.displayName}, you are here"
        FormStatus.REACHED -> "${tile.form.displayName}, reached"
        FormStatus.LOCKED -> "${tile.form.displayName}, locked, unlocks at day ${tile.form.streakThreshold}"
    }

    private const val NBSP = ' '
}

/**
 * Where the headline and Sprig sit on the Tide sky, in dp from the top of the sky below the
 * status bar (`A6-Collection`): a 300dp sky whose hill crest is at 252, the headline column 180dp
 * wide at (24, 56), and Sprig 200dp on the right, its top at 56 (its bottom 4dp under the crest).
 *
 * Widths come from the real screen, as on compact Home ([HomeSprig.compactWidths]): the column
 * and Sprig's box never touch, because Sprig's drawing can reach the edge of its box. Sprig gives
 * way first, down to [SPRIG_MIN_DP], then the column. The crest moves down when the headline
 * block grows (large font), and Sprig moves with it so the hill still hides its lower body.
 */
object CollectionSprig {
    const val TEXT_SIDE_DP = 24
    const val TEXT_TOP_DP = 56
    const val TEXT_DP = 180
    const val SPRIG_DP = 200
    const val SPRIG_MIN_DP = 150
    /** Sprig's box ends 4dp from the right edge (`left: 186px` + 200 in a 390 frame). */
    const val SPRIG_END_DP = 4
    /** Space kept between the headline column and Sprig's box. */
    const val GAP_DP = 4
    const val DESIGN_CREST_DP = 252
    /** Sprig's bottom sits this far below the crest. */
    const val SPRIG_BELOW_CREST_DP = 4
    /** Space kept between the bottom of the headline block and the crest. */
    const val TEXT_TO_CREST_DP = 12

    data class Widths(val textDp: Int, val sprigDp: Int)

    fun widths(screenDp: Int): Widths {
        val room = screenDp - TEXT_SIDE_DP - SPRIG_END_DP - GAP_DP
        val sprig = (room - TEXT_DP).coerceIn(SPRIG_MIN_DP, SPRIG_DP)
        val text = (room - sprig).coerceAtMost(TEXT_DP)
        return Widths(text, sprig)
    }

    /** The crest for a headline block ending at [textBottomDp]; never above the design's. */
    fun crestDp(textBottomDp: Int): Int = maxOf(DESIGN_CREST_DP, textBottomDp + TEXT_TO_CREST_DP)

    /**
     * Sprig's top for a crest at [crestDp] on a screen [screenDp] wide. Sprig's bottom keeps the
     * design's 4dp under the crest, and a smaller Sprig keeps its bottom there too. Sprig's
     * drawing is clipped at its box's bottom, and Sprig stands off-centre where the hill has
     * already curved down, so the bottom also goes at least [UNDER_HILL_DP] under the hill at
     * both lower corners: the hill always hides its lower body (DECISIONS.md, #131).
     */
    fun sprigTopDp(crestDp: Int, sprigDp: Int, screenDp: Int): Int {
        val right = (screenDp - SPRIG_END_DP).toFloat()
        val left = right - sprigDp
        val hill = maxOf(hillDp(crestDp, screenDp, left), hillDp(crestDp, screenDp, right))
        val bottom = maxOf(crestDp + SPRIG_BELOW_CREST_DP, ceil(hill).toInt() + UNDER_HILL_DP)
        return bottom - sprigDp
    }

    /** How far below the hill's edge Sprig's bottom corners sit, at least. */
    const val UNDER_HILL_DP = 2

    /**
     * The hill's top edge at [x]: the foundation's ellipse `(-60, crest, width + 60, crest + 96)`
     * (spec, The hill).
     */
    fun hillDp(crestDp: Int, screenDp: Int, x: Float): Float {
        val a = screenDp / 2f + HILL_OVERHANG_DP
        val k = ((x - screenDp / 2f) / a).coerceIn(-1f, 1f)
        return crestDp + HILL_RADIUS_DP - HILL_RADIUS_DP * sqrt(1f - k * k)
    }

    private const val HILL_OVERHANG_DP = 60f
    private const val HILL_RADIUS_DP = 48f
}
