package com.example.kept.core.domain

import kotlin.math.roundToInt

/**
 * What a closed day came to, as the recap tells it (spec, Recap, outcome table). [streakKept] is
 * the table's "Streak" column: the first three keep the streak, the last three reset it.
 */
enum class RecapOutcome(val streakKept: Boolean) {
    KEPT(true),
    SHIELD(true),
    LOCK_OFF(true),
    SLIPPED(false),
    TOO_LATE(false),
    WRITTEN_OFF(false);

    companion object {
        /**
         * The outcome for a day record. The order is the one the recap always used: a counted day
         * is kept; a day the lock was off (and not also written off) cost nothing (issue #2); then
         * written off, which the shield cannot save (issue #143: a record from an older build may
         * still carry the shield flag); then the shield; then too late, which is everything ticked
         * with nothing else gone wrong, so only the give-up time can have cost it (issue #7);
         * anything else slipped.
         */
        fun of(
            counted: Boolean,
            unprotected: Boolean,
            writtenOff: Boolean,
            shieldConsumed: Boolean,
            habitsDone: Int,
            habitsTotal: Int,
        ): RecapOutcome = when {
            counted -> KEPT
            unprotected && !writtenOff -> LOCK_OFF
            writtenOff -> WRITTEN_OFF
            shieldConsumed -> SHIELD
            habitsTotal > 0 && habitsDone >= habitsTotal -> TOO_LATE
            else -> SLIPPED
        }
    }
}

/** The recap's copy (spec, Recap). Final; do not reword here. */
object RecapCopy {
    const val NOTHING = "Nothing recorded."
    const val SHARE = "Share this look"
    const val SEE_SPRIGS = "See my Sprigs"
    const val DONE = "Done"
    const val HABITS_DONE = "habits done"

    fun headline(o: RecapOutcome): String = when (o) {
        RecapOutcome.KEPT -> "Promise kept."
        RecapOutcome.SHIELD -> "Shield saved it."
        RecapOutcome.LOCK_OFF -> "The lock was off."
        RecapOutcome.SLIPPED -> "The promise slipped."
        RecapOutcome.TOO_LATE -> "Too late to count."
        RecapOutcome.WRITTEN_OFF -> "Day written off."
    }

    /**
     * The line under the headline, with the real streak [streak] (the day's end). A reset outcome
     * only says "Streak reset." when this day took a live streak to 0 ([reset], issue #16); a day
     * that slipped with the streak already at 0 says what happened without claiming a reset. A
     * lock-off day with no streak to keep does not promise a "0-day streak" (DECISIONS.md, #129).
     */
    fun line(o: RecapOutcome, streak: Int, reset: Boolean): String = when (o) {
        RecapOutcome.KEPT -> "$streak ${days(streak)} running."
        RecapOutcome.SHIELD -> "Your weekly shield kept the $streak-day streak alive."
        RecapOutcome.LOCK_OFF ->
            if (streak > 0) "That one is on us. Today does not count, but your $streak-day streak is safe."
            else "That one is on us. Today does not count."
        RecapOutcome.SLIPPED -> if (reset) "Streak reset. Tomorrow is fresh." else "Tomorrow is fresh."
        RecapOutcome.TOO_LATE ->
            if (reset) "Everything got ticked after the give-up time. Streak reset."
            else "Everything got ticked after the give-up time."
        RecapOutcome.WRITTEN_OFF ->
            if (reset) "A fourth break this week. Streak reset, and the shield could not save it."
            else "A fourth break this week, so today does not count."
    }

    fun pose(o: RecapOutcome): SprigPose = when (o) {
        RecapOutcome.KEPT -> SprigPose.CHEER
        RecapOutcome.SHIELD, RecapOutcome.LOCK_OFF -> SprigPose.IDLE
        else -> SprigPose.DROOP
    }

    /** "2 of 2": the stat's value, with no-break spaces so it never wraps. */
    fun done(done: Int, total: Int): String = "$done of $total"

    fun brokenLabel(n: Int): String = if (n == 1) "lock broken" else "locks broken"

    fun nextLine(next: SprigForm, daysLeft: Int): String = "${next.displayName} in $daysLeft ${days(daysLeft)}"

    fun dayCount(day: Int, target: Int): String = "Day $day of $target"

    fun finalForm(form: SprigForm): String = "Sprig is ${form.displayName}, the final form."

    private fun days(n: Int) = if (n == 1) "day" else "days"
}

/**
 * The recap's progress card: where the streak stands between the current form and the next.
 * The bar spans the days between the two forms' thresholds, with one dot for each day inside the
 * span, a fill to today and, on a kept day, a fill that grows from yesterday (`A5-Recap`).
 */
sealed interface RecapProgress {
    data class Toward(
        val from: SprigForm,
        val to: SprigForm,
        /** The streak: "Day {day} of {target}". */
        val day: Int,
        val target: Int,
        /** Days between the two forms, and how many of them are behind. */
        val span: Int,
        val into: Int,
        /** Where the fill starts (yesterday on a kept day) and ends (today), 0 to 1. */
        val fromFraction: Float,
        val toFraction: Float,
        /** Each day inside the span: where it sits on the bar, and whether it is behind. */
        val dots: List<Dot>,
    ) : RecapProgress {
        val daysLeft: Int get() = target - day
    }

    data class Final(val form: SprigForm) : RecapProgress

    data class Dot(val fraction: Float, val reached: Boolean)

    companion object {
        /**
         * The card for a streak of [streak] days. [grows] is set on a kept day: the fill starts a
         * day back. A day that just reached a form starts its new span at 0 with nothing to grow
         * from, as in the design (`max(0, into − 1)`).
         */
        fun of(streak: Int, grows: Boolean): RecapProgress {
            val s = streak.coerceAtLeast(0)
            val from = SprigForm.forStreak(s)
            val to = SprigForm.next(from) ?: return Final(from)
            val span = to.streakThreshold - from.streakThreshold
            val into = s - from.streakThreshold
            val toFraction = into.toFloat() / span
            val fromFraction = if (grows) (into - 1).coerceAtLeast(0).toFloat() / span else toFraction
            val dots = (1 until span).map { k -> Dot(k.toFloat() / span, k <= into) }
            return Toward(from, to, s, to.streakThreshold, span, into, fromFraction, toFraction, dots)
        }
    }
}

/**
 * Where Sprig stands on the recap, in dp from the top of the sky below the status bar
 * (`A5-Recap`): a 400dp sky whose hill crest is at 352, the headline at 60, and Sprig 250dp
 * centred, its top at 150 on a kept day (202 above the crest) and 172 otherwise (180 above).
 *
 * The crest is the reference, as on Home and Break. A short screen brings it up so the page
 * below fits, and Sprig shrinks with it (down to [SIZE_MIN_DP]) before the crest moves; a tall
 * headline (large font) moves it down so Sprig's box always clears the text by [CLEARANCE_DP].
 */
object RecapSprig {
    const val DESIGN_CREST_DP = 352
    const val TEXT_TOP_DP = 60
    const val TEXT_SIDE_DP = 24
    const val SIZE_DP = 250
    /** Still over 100dp, so Sprig keeps its touch interactions. */
    const val SIZE_MIN_DP = 110
    const val CLEARANCE_DP = 8

    /** Least space between the progress card and the buttons (the design's `flex: 1`). */
    const val MIN_GAP_DP = 16

    /**
     * Screens shorter than this between the bars (360 × 640) use the compact page: 12dp above
     * the card instead of 24 and a 16dp footer margin instead of 28, so the buttons show at font
     * 1.0 without scrolling (DECISIONS.md, #129).
     */
    const val COMPACT_BELOW_DP = 760
    const val COMPACT_CARD_GAP_DP = 12
    const val COMPACT_FOOTER_DP = 16

    fun compact(heightDp: Int): Boolean = heightDp < COMPACT_BELOW_DP

    fun designTopDp(kept: Boolean): Int = if (kept) 150 else 172

    fun aboveCrestDp(kept: Boolean): Int = DESIGN_CREST_DP - designTopDp(kept)

    fun headlineSp(kept: Boolean): Int = if (kept) 52 else 40

    data class Spot(val crestDp: Int, val sprigDp: Int, val sprigTopDp: Int, val sprigLeftDp: Int)

    /**
     * The layout for a screen [widthDp] wide with [heightDp] between the system bars, a headline
     * block ending at [textBottomDp] and [belowCrestDp] of page needed under the crest (hill,
     * stats, card, gap and buttons). Sprig keeps the design's share above the crest at every size,
     * so its lower body stays behind the hill.
     */
    fun spot(widthDp: Int, heightDp: Int, textBottomDp: Int, belowCrestDp: Int, kept: Boolean): Spot {
        val above = aboveCrestDp(kept)
        val room = (heightDp - belowCrestDp).coerceAtMost(DESIGN_CREST_DP)
        val forRoom = ((room - textBottomDp - CLEARANCE_DP) * SIZE_DP.toFloat() / above).toInt()
        val size = forRoom.coerceIn(SIZE_MIN_DP, minOf(SIZE_DP, widthDp))
        val sprigAbove = (above * size.toFloat() / SIZE_DP).roundToInt()
        val crest = maxOf(room, textBottomDp + CLEARANCE_DP + sprigAbove)
        return Spot(crest, size, crest - sprigAbove, (widthDp - size) / 2)
    }
}

/**
 * The kept-day moment (`A5-Recap` keyframes), as functions of the milliseconds since the screen
 * opened, so the composable only reads a clock: 14 petals fall once, the bar grows from yesterday
 * to today and the knob pops. With reduce motion none of it plays.
 */
object RecapMotion {
    /** `.grow`: 1.2s after 0.9s. */
    const val GROW_MS = 1200
    const val GROW_DELAY_MS = 900

    /** `.knob`: 0.5s after 2s, scale 1 → 1.35 → 1. */
    const val POP_MS = 500
    const val POP_DELAY_MS = 2000
    const val POP_SCALE = 1.35f

    /**
     * `.petal`: 14 petals, 2.6s each, falling 380dp and turning 320°, fading out from 80%. The
     * script: `x = 12 + (i × 53) % 360`, colour `i % 5`, delay `((i × 37) % 11) / 10` s.
     */
    val SHOWER = PetalShower.of(
        count = 14, x0 = 12, xStep = 53, xMod = 360, delayStep = 37, delayMod = 11,
        durationMs = 2600, fallDp = 380f, turnDeg = 320f, fadeOutFrom = 0.8f,
    )

    val PETALS: List<PetalShower.Petal> get() = SHOWER.petals

    /** When the last petal has fallen. */
    val PETALS_END_MS: Int = SHOWER.endMs

    /** Everything is over by then. */
    val END_MS: Int = maxOf(PETALS_END_MS, POP_DELAY_MS + POP_MS, GROW_DELAY_MS + GROW_MS)

    fun petalProgress(p: PetalShower.Petal, ms: Long): Float? = SHOWER.progress(p, ms)

    fun petalAlpha(t: Float): Float = SHOWER.alpha(t)

    /** The grow's linear progress, 0 to 1 (ease it in the UI). */
    fun growProgress(ms: Long): Float = ((ms - GROW_DELAY_MS).toFloat() / GROW_MS).coerceIn(0f, 1f)

    /** The pop's linear progress, 0 to 1, split at the 50% keyframe. */
    fun popProgress(ms: Long): Float = ((ms - POP_DELAY_MS).toFloat() / POP_MS).coerceIn(0f, 1f)

    /**
     * The knob's scale for a pop at linear progress [t], each half eased by [ease]
     * (`cubic-bezier(.3, 1.8, .5, 1)`) as a CSS keyframe segment is.
     */
    fun knobScale(t: Float, ease: (Float) -> Float): Float = when {
        t <= 0f || t >= 1f -> 1f
        t < 0.5f -> 1f + (POP_SCALE - 1f) * ease(t / 0.5f)
        else -> POP_SCALE - (POP_SCALE - 1f) * ease((t - 0.5f) / 0.5f)
    }
}
