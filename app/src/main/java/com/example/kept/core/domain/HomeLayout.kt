package com.example.kept.core.domain

import kotlin.math.roundToInt

/**
 * Home's rows (spec, Home): roomy rows for 1 to 3 habits, compact rows from 4. Since #218 the sky
 * is the same compact sky for every count, and everything under the hill scrolls together; only
 * the rows' height follows the count. No habits counts as roomy.
 */
enum class HomeLayout {
    ROOMY, COMPACT;

    companion object {
        const val COMPACT_FROM = 4

        fun forHabitCount(count: Int): HomeLayout = if (count >= COMPACT_FROM) COMPACT else ROOMY
    }
}

/**
 * Where Sprig stands in a compact sky (`A2-Home-6.dc.html`), in dp from the top of the sky. The
 * hill's crest is the reference: Sprig keeps its distance to it, so a sky that has to grow for
 * large text moves Sprig with the hill instead of letting it sink behind it. Home (#218) draws a
 * 150dp Sprig and a lower sky than the design; the lock screen and the Sprig tab share the
 * column widths ([compactWidths]).
 */
object HomeSprig {
    /** Sprig's size in `A2-Home-6`: 180 on the right. */
    const val DESIGN_SPRIG_DP = 180

    /** Side padding of the headline column, and Sprig's gap to the right edge. */
    const val TEXT_SIDE_DP = 24
    const val COMPACT_SPRIG_END_DP = 14

    /** Space kept between the headline column and Sprig's box. */
    const val COMPACT_GAP_DP = 4

    /** The smallest compact Sprig, for narrow screens. */
    const val COMPACT_SPRIG_MIN_DP = 150

    /** The compact headline column's design width. */
    const val COMPACT_TEXT_DP = 180

    /** Widths in the compact layout for a screen [screenDp] wide. */
    data class CompactWidths(val textDp: Int, val sprigDp: Int)

    /**
     * The lock screen's and the Sprig tab's columns: the headline column and Sprig share the row
     * without touching. Sprig's drawing can reach the edge of its box (arms, Celestial's cape), so
     * the column ends [COMPACT_GAP_DP] before the box rather than tucking under it. The column
     * keeps its 180dp while there is room; on narrower screens Sprig gives way first (down to
     * [COMPACT_SPRIG_MIN_DP]), then the column.
     */
    fun compactWidths(screenDp: Int): CompactWidths {
        val room = screenDp - TEXT_SIDE_DP - COMPACT_SPRIG_END_DP - COMPACT_GAP_DP
        val sprig = (room - COMPACT_TEXT_DP).coerceIn(COMPACT_SPRIG_MIN_DP, DESIGN_SPRIG_DP)
        val text = (room - sprig).coerceAtMost(COMPACT_TEXT_DP)
        return CompactWidths(text, sprig)
    }

    /** Space kept between the headline block and the crest. */
    const val COMPACT_TEXT_TO_CREST_DP = 12

    // Home (#218): the compact sky for every habit count, with a 150dp Sprig and the crest at about 156.

    /** Home's Sprig: about 150dp, to the right of the headline. */
    const val HOME_SPRIG_DP = 150

    /** Home's crest when the headline block is short: 156dp under the status bar. */
    const val HOME_CREST_DP = 156

    /** Home's columns: Sprig keeps its 150dp and the headline takes the rest of the row. */
    fun homeWidths(screenDp: Int): CompactWidths =
        CompactWidths(screenDp - TEXT_SIDE_DP - COMPACT_SPRIG_END_DP - COMPACT_GAP_DP - HOME_SPRIG_DP, HOME_SPRIG_DP)

    /** Home's Sprig top sits this far above the crest with nothing done, so its face shows over the hill. */
    const val HOME_ABOVE_CREST_DP = 100

    /** How far Home's Sprig rises as the habits get done; its bottom stays under the hill at both corners. */
    const val HOME_RISE_DP = 34

    /** Home's Sprig top for a crest at [crestDp], [done] of [total]. */
    fun homeTopDp(crestDp: Int, done: Int, total: Int): Int {
        val frac = if (total <= 0) 0f else done.coerceIn(0, total).toFloat() / total
        return crestDp - HOME_ABOVE_CREST_DP - (HOME_RISE_DP * frac).roundToInt()
    }

    /** Home's crest for a headline block ending at [textBottomDp]: the block always ends above it. */
    fun homeCrestDp(textBottomDp: Int): Int = maxOf(HOME_CREST_DP, textBottomDp + COMPACT_TEXT_TO_CREST_DP)
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
    fun line(total: Int, done: Int, giveUp: String, pastDue: Boolean, breakActive: Boolean, weeklyMissed: Boolean = false): String = when {
        total <= 0 -> "Nothing locks until you add one."
        // A must-do session was not logged before the give-up time (story #176).
        weeklyMissed -> "Apps are open. Today won't count."
        done >= total -> "Apps are open. See you tomorrow."
        pastDue -> "Apps are open. Today won't count."
        breakActive -> "Lock paused for a break. Apps lock again soon."
        // A no-break space keeps "9:00 pm" on one line.
        else -> "Apps unlock when you finish, or at ${giveUp.replace(' ', '\u00A0')}."
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
