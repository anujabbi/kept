package com.example.kept.core.domain

/**
 * When the "Promise kept" moment plays (spec, Moments; issue #130). It plays once per day,
 * whether the day was finished in KEPT or on the lock screen, and only for a day that counts:
 * every habit ticked before the give-up time.
 *
 * Two dates are stored: `celebrationPendingDate`, set when a day is finished in KEPT (or from a
 * notification) and waiting for KEPT to be open, and `celebrationShownDate`, the day the moment
 * last played on either surface.
 */
object MomentRules {

    /** The tick finished the day and it counts: everything ticked, the last one before the give-up time. */
    fun finishedInTime(allDoneNow: Boolean, beforeDue: Boolean): Boolean = allDoneNow && beforeDue

    /** KEPT shows the moment: today is pending, it has not played today, and the day counts. */
    fun dueInKept(pendingDate: String?, shownDate: String?, today: String, allDoneOnTime: Boolean): Boolean =
        pendingDate == today && shownDate != today && allDoneOnTime

    /** The moment may play now (on either surface) only if it has not played today. */
    fun mayPlay(shownDate: String?, today: String): Boolean = shownDate != today

    /**
     * The lock screen stands the moment in for a tick that may finish the day ([remaining] at most
     * 1), so the gold open state does not flash first. Only while today's moment can still play
     * ([shownDate], as stored) and at most once per day ([standInDate], remembered by the screen
     * itself, because the stored date is only written after the tick: an undo and re-tick right
     * after carrying on must not stand it in again, QA on PR #144).
     */
    fun lockStandsIn(remaining: Int, shownDate: String?, standInDate: String?, today: String): Boolean =
        remaining <= 1 && mayPlay(shownDate, today) && standInDate != today

    /**
     * After the lock screen's claim comes back: the moment goes up if this tick [claimed] it and the
     * user has not already carried on today ([carriedOnDate]), perhaps while the tick was in flight.
     */
    fun lockRaisesAfterClaim(claimed: Boolean, carriedOnDate: String?, today: String): Boolean =
        claimed && carriedOnDate != today

    /**
     * What a finished day leaves pending for KEPT. Nothing when the lock screen plays the moment
     * itself ([shownHere]) or it already played today; otherwise today, for KEPT to show.
     */
    fun pendingAfterFinish(shownHere: Boolean, shownDate: String?, today: String): String? =
        if (shownHere || shownDate == today) null else today
}

/** The "Promise kept" moment's copy (`A16-Celebrate`). Final; do not reword here. */
object CelebrationCopy {
    const val HEADLINE = "Promise kept."
    const val CARRY_ON = "Tap to carry on"

    /** Today is day `streakDays + 1` of the streak: the streak counts it at rollover. */
    fun streakDay(streakDays: Int): Int = streakDays + 1

    fun line(day: Int): String = "Day $day of your streak. Apps are open."

    /** What TalkBack reads for the whole moment (the design's button label, with the line). */
    fun spoken(day: Int): String = "$HEADLINE ${line(day)} $CARRY_ON."
}

/** The "Sprig evolves" moment's copy (`A17-Evolve`, issue #130). Final; do not reword here. */
object EvolveCopy {
    const val SHARE = "Share"
    const val CARRY_ON = "Carry on"

    fun kept(days: Int): String = if (days == 1) "1 day kept" else "$days days kept"

    fun headline(form: SprigForm): String = "Sprig is now ${form.displayName}"

    /** The outfit as the moment names it (issue #130's list; shorter than [SprigOutfit.description]). */
    fun outfit(form: SprigForm): String = when (form) {
        SprigForm.SPRIG -> "none"
        SprigForm.BUD -> "a red scarf"
        SprigForm.BLOOM -> "a yellow cardigan"
        SprigForm.THICKET -> "a scarf and a cardigan"
        SprigForm.GROVE -> "a leaf crown"
        SprigForm.ANCIENT -> "round glasses and a beard"
        SprigForm.CELESTIAL -> "a starry cape and a halo"
    }

    /** "New outfit: {description}. Next up is {next}, at day {day}."; the final form has no "Next up". */
    fun line(form: SprigForm): String {
        val next = SprigForm.next(form)
        val outfit = "New outfit: ${outfit(form)}."
        return if (next == null) outfit else "$outfit Next up is ${next.displayName}, at day ${next.streakThreshold}."
    }
}

/**
 * The two moments' motion (`A16-Celebrate`, `A17-Evolve` keyframes) as functions of the
 * milliseconds since the moment opened. Sprig pops up, a ring grows and fades behind it, and 18
 * petals fall once. With reduce motion none of it plays and the moment shows its end state: Sprig
 * in place, no ring, no petals.
 */
object MomentMotion {
    /** `.pop`: 0.7s after 0.15s, from 120dp lower at 0.6 scale and transparent. */
    const val POP_MS = 700
    const val POP_DELAY_MS = 150
    const val POP_FROM_Y_DP = 120f
    const val POP_FROM_SCALE = 0.6f

    /** `.ring`: 1.4s after 0.5s, ease-out, scale 0.4 → 1.6 and opacity 0.9 → 0. */
    const val RING_MS = 1400
    const val RING_DELAY_MS = 500
    const val RING_FROM_SCALE = 0.4f
    const val RING_TO_SCALE = 1.6f
    const val RING_FROM_ALPHA = 0.9f

    /** `.petal` in both moments: 18 petals, `x = 8 + (i × 47) % 370`, delay `((i × 29) % 13) / 10` s, 2.8s, 860dp, 360°. */
    val SHOWER = PetalShower.of(
        count = 18, x0 = 8, xStep = 47, xMod = 370, delayStep = 29, delayMod = 13,
        durationMs = 2800, fallDp = 860f, turnDeg = 360f, fadeOutFrom = 0.85f,
    )

    /** Everything is over by then. */
    val END_MS: Int = maxOf(SHOWER.endMs, POP_DELAY_MS + POP_MS, RING_DELAY_MS + RING_MS)

    /** The pop's linear progress, 0 to 1 (ease it in the UI). `both` holds the start during the delay. */
    fun popProgress(ms: Long): Float = ((ms - POP_DELAY_MS).toFloat() / POP_MS).coerceIn(0f, 1f)

    /** The ring's linear progress, 0 to 1. */
    fun ringProgress(ms: Long): Float = ((ms - RING_DELAY_MS).toFloat() / RING_MS).coerceIn(0f, 1f)

    data class Pop(val yDp: Float, val scale: Float, val alpha: Float)

    /** Sprig's offset, scale and opacity at eased pop progress [e] (which may overshoot 1). */
    fun pop(e: Float): Pop = Pop(
        yDp = POP_FROM_Y_DP * (1f - e),
        scale = POP_FROM_SCALE + (1f - POP_FROM_SCALE) * e,
        alpha = e.coerceIn(0f, 1f),
    )

    fun ringScale(e: Float): Float = RING_FROM_SCALE + (RING_TO_SCALE - RING_FROM_SCALE) * e
    fun ringAlpha(e: Float): Float = (RING_FROM_ALPHA * (1f - e)).coerceIn(0f, 1f)
}

/**
 * How big the moment's Sprig is drawn. The design's size (260 for "Promise kept", 280 for the
 * evolution) when it fits; on a short screen or at a large font it shrinks to the room the text
 * leaves, down to [MIN_DP] (still interactive), and past that the page scrolls.
 */
object MomentFit {
    const val CELEBRATE_DP = 260
    const val EVOLVE_DP = 280
    const val MIN_DP = 110

    /** Sprig's size for [heightDp] of screen when everything but Sprig takes [restDp]. */
    fun sprigDp(designDp: Int, heightDp: Int, restDp: Int, widthDp: Int): Int =
        (heightDp - restDp).coerceAtMost(minOf(designDp, widthDp)).coerceAtLeast(MIN_DP)
}
