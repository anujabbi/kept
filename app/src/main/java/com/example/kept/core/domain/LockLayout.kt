package com.example.kept.core.domain

/**
 * What the lock screen shows (spec, Lock and Behaviour changes). Habits are ticked on the lock
 * screen itself; once the day is finished in time it turns into the open state with one
 * "Open {App}" button instead of going away on its own.
 */
enum class LockMode {
    /** Habits left: night sky, the habits, "Phone" and "Break the lock". */
    LOCKED,

    /** Every habit done in time: gold sky, Sprig cheers, "Open {App}". */
    OPEN,

    /** Nothing left to cover (break, exception, window over, or finished late): get out of the way. */
    DISMISS,
    ;

    companion object {
        /**
         * The mode for one emission of the lock state. A day finished in time opens the lock screen
         * rather than dismissing it, even though the lock itself is no longer active. A day
         * finished after the give-up time has nothing to celebrate, so it dismisses like any other
         * lifted lock.
         */
        /**
         * [night] is true while night lockdown is the lock holding the phone (issue #164). It
         * holds whether or not the day's habits are done, so a finished day does not open the
         * screen then; it stays locked for as long as its app is. [weeklyMustDoIncomplete] is
         * true while a weekly goal's must-do session is not logged today (issue #165).
         */
        fun of(
            loaded: Boolean,
            lockActive: Boolean,
            blockedStillLocked: Boolean,
            allDoneOnTime: Boolean,
            night: Boolean = false,
            weeklyMustDoIncomplete: Boolean = false,
        ): LockMode = when {
            !loaded -> LOCKED
            night -> if (lockActive && blockedStillLocked) LOCKED else DISMISS
            // A must-do session not logged (issue #165) keeps a finished day from opening the
            // screen: it stays locked while the lock holds, and gets out of the way when it lifts.
            allDoneOnTime && !weeklyMustDoIncomplete -> OPEN
            !lockActive || !blockedStillLocked -> DISMISS
            else -> LOCKED
        }
    }
}

/** The lock screen's copy (spec, Lock). Final; do not reword here. */
object LockCopy {
    /** The name used when the locked app's label is unknown (DECISIONS.md, #127). */
    const val UNKNOWN_APP = "This app"

    fun appName(label: String?): String = label?.trim().orEmpty().ifEmpty { UNKNOWN_APP }

    fun headline(app: String, open: Boolean): String = if (open) "$app is open." else "$app can wait."

    const val LINE_LOCKED = "Finish your promised habits to unlock."
    const val LINE_OPEN = "Promise kept. Enjoy it."

    fun line(open: Boolean): String = if (open) LINE_OPEN else LINE_LOCKED

    fun openButton(app: String): String = "Open $app"

    const val PHONE = "Phone"
    const val BREAK = "Break the lock"

    fun count(done: Int, total: Int): String = HomeCopy.count(done, total)

    fun pose(open: Boolean): SprigPose = if (open) SprigPose.CHEER else SprigPose.GUARD

    // Night lockdown (issue #164): no app name, no habits, Sprig asleep.
    const val NIGHT_HEADLINE = "It's shutdown time."

    /** "Apps open at 7:00 am." */
    fun nightLine(untilMinute: Int): String = "Apps open at ${untilMinute.minuteOfDayLabel()}."

    val NIGHT_POSE = SprigPose.SLEEP
}

/** The foreground notification while night lockdown holds the phone (issue #164). */
object NightNotice {
    const val TITLE = "Night lockdown"

    /** "Apps open at 7:00 am" */
    fun text(untilMinute: Int): String = "Apps open at ${untilMinute.minuteOfDayLabel()}"
}

/**
 * Where Sprig stands on the lock screen, in dp from the top of the sky (`A3-Lock`): a 280dp sky
 * whose hill's crest is at 232, the headline column at 60, Sprig 180dp on the right at 116
 * (guarding) or 80 (cheering). Like compact Home, the crest is the reference: a headline block
 * that grows with the font moves the crest down, and Sprig keeps its distance to the crest.
 */
object LockSprig {
    const val SKY_DP = 280
    const val DESIGN_CREST_DP = SKY_DP - 48
    const val TEXT_TOP_DP = 60

    /** The weekly variant's block starts higher, at its "Weekly goal" chip (weekly goals mockup 3). */
    const val WEEKLY_TEXT_TOP_DP = 40

    /**
     * The weekly variant says more than the other faces (a chip, a two-clause headline, a longer
     * line). From this font scale up Sprig steps aside and the words take the screen's width, as
     * on the weekly goal setup screen; beside Sprig they would push the hill off a small screen.
     */
    const val WEEKLY_SPRIG_MAX_FONT_SCALE = 1.5f
    fun weeklyShowsSprig(fontScale: Float): Boolean = fontScale < WEEKLY_SPRIG_MAX_FONT_SCALE
    const val SIZE_DP = 180

    /** Sprig's top in the design: 80 when open, 116 while guarding. */
    fun designTopDp(open: Boolean): Int = if (open) 80 else 116

    /** The crest for a headline block ending at [textBottomDp]; never above the design's. */
    fun crestDp(textBottomDp: Int): Int =
        maxOf(DESIGN_CREST_DP, textBottomDp + HomeSprig.COMPACT_TEXT_TO_CREST_DP)

    /**
     * Sprig's top for a crest at [crestDp]. A Sprig drawn smaller than [SIZE_DP] (a narrow screen)
     * keeps the design's bottom edge, so its lower body stays behind the hill.
     */
    fun topDp(crestDp: Int, open: Boolean, sprigDp: Int = SIZE_DP): Int =
        crestDp - (DESIGN_CREST_DP - designTopDp(open)) + (SIZE_DP - sprigDp)
}
