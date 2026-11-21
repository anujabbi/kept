package com.example.kept.core.domain

import kotlin.math.floor

/** How long the break screen counts down before "Unlock" is allowed. Unchanged by Daybreak. */
const val BREAK_COUNTDOWN_SECONDS = 60

/**
 * The break countdown, as a function of time rather than of a loop in the composable, so it keeps
 * going across a configuration change or process death (`LockViewModel` keeps when it started).
 */
object BreakCountdown {
    /**
     * Seconds left [total] seconds after [startedAtMs], read at [nowMs] (both on the same monotonic
     * clock). One whole second must pass for each step, as the old one-second loop did.
     */
    fun secondsLeft(startedAtMs: Long, nowMs: Long, total: Int = BREAK_COUNTDOWN_SECONDS): Int {
        val waited = floor((nowMs - startedAtMs).coerceAtLeast(0L) / 1000.0).toInt()
        return (total - waited).coerceIn(0, total)
    }

    /** Milliseconds until the next whole second after [startedAtMs], so the clock ticks on time. */
    fun untilNextTickMs(startedAtMs: Long, nowMs: Long): Long {
        val elapsed = (nowMs - startedAtMs).coerceAtLeast(0L)
        return 1000L - elapsed % 1000L
    }

    /** Whole seconds the screen has been open, for analytics. */
    fun secondsWaited(startedAtMs: Long, nowMs: Long): Int = ((nowMs - startedAtMs).coerceAtLeast(0L) / 1000L).toInt()

    /** "1:00", "0:09", "0:00". */
    fun clock(secondsLeft: Int): String {
        val s = secondsLeft.coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
}

/**
 * Sprig loses its colour as the countdown runs (`A4-Break`, `renderVals`): saturation from 1 to
 * 0.25 and the bubble's text alpha from 1 to 0.35, linear in the seconds left.
 */
object BreakFade {
    const val SATURATION_END = 0.25f
    const val VOICE_END = 0.35f

    private fun fraction(secondsLeft: Int, total: Int): Float =
        if (total <= 0) 0f else secondsLeft.coerceIn(0, total).toFloat() / total

    fun saturation(secondsLeft: Int, total: Int = BREAK_COUNTDOWN_SECONDS): Float =
        SATURATION_END + (1f - SATURATION_END) * fraction(secondsLeft, total)

    fun voiceAlpha(secondsLeft: Int, total: Int = BREAK_COUNTDOWN_SECONDS): Float =
        VOICE_END + (1f - VOICE_END) * fraction(secondsLeft, total)
}

/** What breaking costs: the three rows, or the last-resort warning on the fourth break of the week. */
sealed interface BreakCosts {
    enum class Kind { WILT, STREAK, BREAKS }

    data class Row(val kind: Kind, val text: String)

    data class Normal(val rows: List<Row>) : BreakCosts

    data class LastResort(val callout: String, val line: String) : BreakCosts

    companion object {
        /**
         * [level] and [streakDays] are Sprig's now, [breaksLeft] the breaks left this week before
         * this one (the row shows one fewer: what is left after it), [overCap] whether this break is past the weekly cap (`LockState.nextBreakOverCap`).
         */
        fun of(level: Int, streakDays: Int, breaksLeft: Int, overCap: Boolean): BreakCosts =
            if (overCap) {
                LastResort(BreakCopy.LAST_RESORT, BreakCopy.writtenOff(streakDays))
            } else {
                Normal(
                    listOfNotNull(
                        Row(Kind.WILT, BreakCopy.wilt(level)),
                        if (streakDays > 0) Row(Kind.STREAK, BreakCopy.streakStays(streakDays)) else null,
                        // A cost: how many are left once this break is taken.
                        Row(Kind.BREAKS, BreakCopy.breaksLeft((breaksLeft - 1).coerceAtLeast(0))),
                    ),
                )
            }
    }
}

/** The break screen's copy (spec, Break the lock). Final; do not reword here. */
object BreakCopy {
    const val HEADLINE = "Break the lock?"
    const val PLEA = "Don't let me wilt."
    const val COSTS_TITLE = "What it costs"
    const val GO_BACK = "Never mind, go back"
    const val LAST_RESORT = "This is your fourth break this week, the last resort."

    /** Sprig at the lowest level cannot drop one, so the row says only what does happen (DECISIONS.md, #128). */
    fun wilt(level: Int): String =
        if (level > LevelRules.FLOOR) "Sprig drops a level and wilts until tomorrow." else "Sprig wilts until tomorrow."

    fun streakStays(days: Int): String = "Your $days day streak stays."

    fun breaksLeft(left: Int): String = "$left of ${BreakCap.MAX_PER_ROLLING_WEEK} breaks left this week."

    fun writtenOff(streakDays: Int): String =
        if (streakDays > 0) "Today is written off. It won't count toward your $streakDays day streak, and your shield can't save it."
        else "Today is written off. It won't count toward your streak, and your shield can't save it."

    fun clockLabel(secondsLeft: Int): String = if (secondsLeft > 0) "You can unlock in" else "You can unlock now"

    fun unlockLabel(secondsLeft: Int, minutes: Int): String =
        if (secondsLeft > 0) "Unlock anyway (${secondsLeft}s)" else "Unlock for $minutes min"
}

/**
 * Where things stand on the break screen, in dp from the top of the sky below the status bar
 * (`A4-Break`): a 360dp overcast sky whose hill's crest is at 312, the headline at 60 in a 190dp
 * column, the bubble at (20, 178), and Sprig 230dp at (166, 116), its right edge 6dp past the
 * screen's on the 390dp artboard.
 *
 * The crest is the reference, as on Home and Lock. A short screen (360 × 640) brings the crest up,
 * down to [CREST_MIN_DP], and Sprig shrinks with it; a narrow one shrinks Sprig to keep the
 * headline column; a tall headline or bubble (large font) moves the crest down.
 */
object BreakSprig {
    const val DESIGN_CREST_DP = 312
    const val CREST_MIN_DP = 216
    const val TEXT_TOP_DP = 60
    const val TEXT_WIDTH_DP = 190
    const val TEXT_SIDE_DP = 24
    const val BUBBLE_LEFT_DP = 20
    const val DESIGN_BUBBLE_TOP_DP = 178
    const val SIZE_DP = 230
    const val SIZE_MIN_DP = 170
    const val DESIGN_LEFT_DP = 166
    const val DESIGN_TOP_DP = 116

    /** Sprig's box runs this far past the right edge, as on the 390dp artboard (166 + 230 − 390). */
    const val RIGHT_OVERHANG_DP = 6

    /** Sprig's box bottom sits this far below the crest (116 + 230 − 312), behind the hill. */
    const val BELOW_CREST_DP = 34

    /** Everything from the crest down on the artboard: 844 − 312. */
    const val DESIGN_BELOW_CREST_DP = 844 - DESIGN_CREST_DP

    /** Least gap from the headline to the bubble, and from the bubble to the crest. */
    const val TEXT_GAP_DP = 12
    const val BUBBLE_TO_CREST_DP = 24

    /** The rise starts this far down (`spRise`: `translateY(260px)`). */
    const val RISE_FROM_DP = 260

    /**
     * Screens shorter than this between the bars (360 × 640) use the compact page: a 60sp clock,
     * 8dp between cost rows and a 16dp footer margin, so the clock shows above the buttons with
     * little or no scrolling (DECISIONS.md, #128).
     */
    const val COMPACT_BELOW_DP = 760

    fun compact(heightDp: Int): Boolean = heightDp < COMPACT_BELOW_DP

    data class Spot(val crestDp: Int, val sprigDp: Int, val sprigTopDp: Int, val sprigLeftDp: Int, val bubbleTopDp: Int, val bubbleMaxWidthDp: Int)

    /**
     * The layout for a screen [widthDp] wide with [heightDp] between the system bars, a headline
     * block ending at [textBottomDp] and a bubble [bubbleHeightDp] tall.
     */
    fun spot(widthDp: Int, heightDp: Int, textBottomDp: Int, bubbleHeightDp: Int): Spot {
        val forHeight = (heightDp - DESIGN_BELOW_CREST_DP).coerceIn(CREST_MIN_DP, DESIGN_CREST_DP)
        val crest = maxOf(forHeight, textBottomDp + TEXT_GAP_DP + bubbleHeightDp + BUBBLE_TO_CREST_DP)
        // Sprig keeps the design's top (116) and its bottom 34dp below the crest while it can.
        val size = minOf((crest + BELOW_CREST_DP - DESIGN_TOP_DP).coerceIn(SIZE_MIN_DP, SIZE_DP), widthDp + RIGHT_OVERHANG_DP - DESIGN_LEFT_DP)
            .coerceAtLeast(SIZE_MIN_DP)
        val left = widthDp + RIGHT_OVERHANG_DP - size
        val bubbleTop = maxOf(textBottomDp + TEXT_GAP_DP, crest - (DESIGN_CREST_DP - DESIGN_BUBBLE_TOP_DP))
        // The bubble's tail points at Sprig's side but stops short of its body (x = 16 of 120), for
        // the largest Sprig this width allows, so the width never depends on the bubble's own
        // height. A large font wraps the plea rather than cover Sprig.
        val largest = minOf(SIZE_DP, widthDp + RIGHT_OVERHANG_DP - DESIGN_LEFT_DP).coerceAtLeast(SIZE_MIN_DP)
        val bodyLeft = widthDp + RIGHT_OVERHANG_DP - largest * (120f - BODY_LEFT_UNITS) / 120f
        val bubbleMax = bodyLeft.toInt() - BUBBLE_LEFT_DP - BODY_CLEARANCE_DP
        return Spot(crest, size, crest + BELOW_CREST_DP - size, left, bubbleTop, bubbleMax)
    }

    private const val BODY_LEFT_UNITS = 16f
    private const val BODY_CLEARANCE_DP = 2
}
