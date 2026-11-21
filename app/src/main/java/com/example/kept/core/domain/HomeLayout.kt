package com.example.kept.core.domain

import kotlin.math.roundToInt

/**
 * Home's two layouts (spec, Home): roomy for 1 to 3 habits (`Main.dc.html`), compact from 4
 * (`A2-Home-6.dc.html`), where only the habit list scrolls. No habits counts as roomy.
 */
enum class HomeLayout {
    ROOMY, COMPACT;

    companion object {
        const val COMPACT_FROM = 4

        fun forHabitCount(count: Int): HomeLayout = if (count >= COMPACT_FROM) COMPACT else ROOMY
    }
}

/**
 * Where Sprig stands on Home, in dp from the top of the sky, ported from the design scripts.
 * The hill's crest is the reference: Sprig keeps the design's distance to it, so a sky that has
 * to grow for large text moves Sprig with the hill instead of letting it sink behind it.
 */
object HomeSprig {
    /** Sprig's size per layout: 240 centred (`Main`), 180 on the right (`A2-Home-6`). */
    fun sizeDp(layout: HomeLayout): Int = if (layout == HomeLayout.ROOMY) 240 else 180

    /** The hill's crest in the design: `Main` puts the hill at 396; `A2-Home-6` at 260 − 48. */
    fun designCrestDp(layout: HomeLayout): Int = if (layout == HomeLayout.ROOMY) 396 else 212

    /**
     * Sprig's top in the design for [done] of [total]. `Main` (2 habits): 224, 200, 176, i.e.
     * 224 − 48 × done/total. `A2-Home-6`: `round(100 − 44 × done/total)`.
     */
    fun designTopDp(layout: HomeLayout, done: Int, total: Int): Int {
        val frac = if (total <= 0) 0f else done.coerceIn(0, total).toFloat() / total
        return when (layout) {
            HomeLayout.ROOMY -> (224 - 48 * frac).roundToInt()
            HomeLayout.COMPACT -> (100 - 44 * frac).roundToInt()
        }
    }

    /** How far Sprig's top sits above the crest at [done] of [total]. */
    fun aboveCrestDp(layout: HomeLayout, done: Int, total: Int): Int =
        designCrestDp(layout) - designTopDp(layout, done, total)

    /** The most Sprig ever rises above the crest (everything done). */
    fun maxAboveCrestDp(layout: HomeLayout): Int = aboveCrestDp(layout, 1, 1)

    /** Space kept between the bottom of the headline block and Sprig's box in the roomy layout. */
    const val CLEARANCE_DP = 8

    /** Space kept between the compact headline block and the crest. */
    const val COMPACT_TEXT_TO_CREST_DP = 12

    /**
     * The crest for a headline block ending at [textBottomDp] (from the sky's top). Roomy: Sprig
     * is centred under the text, so the crest moves down until even the highest Sprig clears
     * the text by [CLEARANCE_DP]. Compact: the text sits beside Sprig, so it only has to end
     * above the crest. Never above the design's crest.
     */
    fun crestDp(layout: HomeLayout, textBottomDp: Int): Int = when (layout) {
        HomeLayout.ROOMY -> maxOf(designCrestDp(layout), textBottomDp + CLEARANCE_DP + maxAboveCrestDp(layout))
        HomeLayout.COMPACT -> maxOf(designCrestDp(layout), textBottomDp + COMPACT_TEXT_TO_CREST_DP)
    }

    /** Sprig's top for a crest at [crestDp]. */
    fun topDp(layout: HomeLayout, crestDp: Int, done: Int, total: Int): Int = crestDp - aboveCrestDp(layout, done, total)
}

/** Home's copy (spec, Home). Final; do not reword here. */
object HomeCopy {
    private val WORDS = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

    const val PROMISE_KEPT = "Promise kept."

    /**
     * "One to go." … "Ten to go." while habits are left, "Promise kept." when the day was
     * finished in time. Everything ticked but some of it after the give-up time borrows the
     * recap's "Too late to count." (DECISIONS.md, #126).
     */
    fun headline(total: Int, done: Int, keptOnTime: Boolean): String {
        val left = (total - done).coerceAtLeast(0)
        return when {
            total <= 0 -> "Add a habit."
            left == 0 -> if (keptOnTime) PROMISE_KEPT else "Too late to count."
            else -> "${WORDS.getOrElse(left - 1) { left.toString() }} to go."
        }
    }

    /**
     * The line under the headline. The spec's two lines, plus the states Home already told
     * apart before Daybreak (no habits, past the give-up time, a break running).
     */
    fun line(total: Int, done: Int, giveUp: String, pastDue: Boolean, breakActive: Boolean): String = when {
        total <= 0 -> "Nothing locks until you add one."
        done >= total -> "Apps are open. See you tomorrow."
        pastDue -> "Apps are open. Today won't count."
        breakActive -> "Lock paused for a break. Apps lock again soon."
        else -> "Apps unlock when you finish, or at $giveUp."
    }

    fun streak(days: Int): String = "$days day streak"

    fun count(done: Int, total: Int): String = "$done of $total"

    fun bestStreak(best: Int): String = "Best streak $best"

    const val SHIELD_READY = "Shield ready"
    const val SHIELD_USED = "Shield used this week"
    const val SHIELD_CARD_TITLE = "Your weekly free pass"
    const val SHIELD_CARD_READY = "Miss a day and the shield saves your streak. You get a new one every week."
    const val SHIELD_CARD_USED = "You used this week's shield. A new one comes on Monday."

    /**
     * A habit's detail on its row: "30 min", "10 pages"; a once-a-day habit with no unit (or
     * "times") reads "Once a day", like Add a habit.
     */
    fun habitDetail(target: Int, unit: String): String {
        val u = unit.trim()
        return when {
            target > 1 -> "$target $u".trim()
            u.isEmpty() || u == "times" -> "Once a day"
            else -> "1 $u"
        }
    }
}
