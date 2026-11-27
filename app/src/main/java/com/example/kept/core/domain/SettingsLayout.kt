package com.example.kept.core.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** Settings copy (spec, Settings; `A7-Settings`, `A15-History`). Final; do not reword here. */
object SettingsCopy {
    const val NBSP = '\u00A0'

    const val TITLE = "Settings"

    const val GROUP_PROMISE = "Your promise"
    const val LOCK_FROM = "Lock apps from"
    const val GIVE_UP = "Give-up time"
    const val GIVE_UP_HINT = "After this, apps open and the day is missed"

    /** The confirm when Set in "Lock apps from" would open apps early during a lock (issue #154). */
    const val LOCK_FROM_WHILE_LOCKED = "Your apps are locked right now. Changing when the lock starts opens them early. Still change it?"
    /** The confirm when Set in "Give-up time" would open apps early during a lock (issue #1). */
    const val GIVE_UP_WHILE_LOCKED = "Your apps are locked right now. Changing the give-up time opens them early. Still change it?"

    const val GROUP_LOCK = "Lock"
    const val EXCEPTIONS = "Exceptions"
    const val EXCEPTIONS_HINT = "Apps that never lock"
    const val BREAK_LENGTH = "Unlock length after a break"
    const val PERMISSIONS = "Permissions"
    const val ALL_SET = "All set"

    // Night lockdown (issue #164)
    const val GROUP_NIGHT = "Night lockdown"
    const val NIGHT = "Night lockdown"
    const val NIGHT_HINT = "Apps lock at night so you stop scrolling"
    const val NIGHT_START = "Starts at"
    const val NIGHT_END = "Ends at"
    const val NIGHT_CUSTOM = "Custom…"
    const val MIDNIGHT = "Midnight"
    const val NIGHT_WINDOW_ERROR = "Pick an end time 1 to 12 hours after the start."

    /** The confirms when a change would open apps that night lockdown is holding right now. */
    const val NIGHT_OFF_WHILE_LOCKED = "Your apps are locked right now. Turning off night lockdown opens them. Still turn it off?"
    const val NIGHT_START_WHILE_LOCKED = "Your apps are locked right now. Starting night lockdown later opens them early. Still change it?"
    const val NIGHT_END_WHILE_LOCKED = "Your apps are locked right now. Ending night lockdown earlier opens them sooner. Still change it?"

    /** A night lockdown time: "Midnight" for minute 0, else like [time]. */
    fun nightTime(minute: Int): String = if (minute == 0) MIDNIGHT else time(minute)

    const val GROUP_REMINDERS = "Reminders and privacy"
    const val REMINDERS = "Reminders"
    const val REMINDERS_HINT = "Three nudges a day"
    const val ANALYTICS = "Send anonymous usage data"
    const val ANALYTICS_HINT = "No name, no email, never which apps you use."

    const val HISTORY = "History"
    const val PRIVACY = "KEPT never shares your app usage, screen time, or location with anyone."

    /** What a switch says to TalkBack. */
    const val ON = "On"
    const val OFF = "Off"

    /** The break lengths the editor offers, in minutes. */
    val BREAK_LENGTHS = listOf(15, 30, 60)

    /** "7:00 am", with a no-break space so the time never wraps. */
    fun time(minute: Int): String = minute.minuteOfDayLabel().replace(' ', NBSP)

    /** Exceptions row value: "2 apps", "1 app", "0 apps". */
    fun exceptions(n: Int): String = if (n == 1) "1${NBSP}app" else "$n${NBSP}apps"

    /** Break length value and editor choice: "30 min". */
    fun minutes(m: Int): String = "$m${NBSP}min"

    /** Permissions row value: "All set" when nothing on the page is off, else "{n} off". */
    fun permissions(off: Int): String = if (off <= 0) ALL_SET else "$off${NBSP}off"

    /** History row value: "Best streak 12". */
    fun bestStreak(n: Int): String = "Best${NBSP}streak${NBSP}$n"

    // History (A15)
    const val HISTORY_EMPTY = "Your first recap lands after tonight."
    const val HISTORY_DAYS = 14

    /** "Your last 14 days. Best streak: 12 days." */
    fun historyLine(best: Int): String = "Your last $HISTORY_DAYS days. Best streak: $best ${if (best == 1) "day" else "days"}."

    /** "2 of 2", joined by no-break spaces. */
    fun habitsDone(done: Int, total: Int): String = "$done${NBSP}of${NBSP}$total"

    private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    /** "Tue 29 Sep", joined by no-break spaces. */
    fun date(d: LocalDate): String = d.format(DATE).replace(' ', NBSP)

    /** A history row's outcome label (spec, Settings). Slipped reads "Missed" here. */
    fun outcome(o: RecapOutcome): String = when (o) {
        RecapOutcome.KEPT -> "Kept"
        RecapOutcome.SHIELD -> "Shield${NBSP}used"
        RecapOutcome.SLIPPED -> "Missed"
        RecapOutcome.TOO_LATE -> "Too${NBSP}late"
        RecapOutcome.LOCK_OFF -> "Lock${NBSP}was${NBSP}off"
        RecapOutcome.WRITTEN_OFF -> "Written${NBSP}off"
    }
}

/** Which lock window editor Set was tapped in. */
enum class LockWindowField { LOCK_FROM, GIVE_UP }

/**
 * What Set in a lock window editor changes (issue #154): the new window and the confirm that
 * editor shows during a lock. [of] is null when the picked time is the one already saved, so
 * an unchanged Set saves nothing and asks nothing.
 */
data class LockWindowEdit(val from: Int, val due: Int, val confirm: String) {
    companion object {
        fun of(field: LockWindowField, picked: Int, from: Int, due: Int): LockWindowEdit? = when (field) {
            LockWindowField.LOCK_FROM ->
                if (picked == from) null else LockWindowEdit(picked, due, SettingsCopy.LOCK_FROM_WHILE_LOCKED)
            LockWindowField.GIVE_UP ->
                if (picked == due) null else LockWindowEdit(from, picked, SettingsCopy.GIVE_UP_WHILE_LOCKED)
        }
    }
}

/**
 * The Settings sky (`A7-Settings`): a lilac band 190dp tall with "Settings" (40/800) at
 * `left 24, top 64` and a 130dp Sprig at `left 246, top 34`, that is 14dp in from the right
 * edge of the 390dp frame. The hill's crest is 48dp above the band's bottom edge (142).
 *
 * Widths come from the real screen: the title gets what is left beside Sprig, and a taller
 * title (large font) moves the crest, and Sprig with it, down.
 */
object SettingsSky {
    const val TEXT_SIDE_DP = 24
    const val TEXT_TOP_DP = 64
    const val SPRIG_DP = 130
    const val SPRIG_END_DP = 14
    /** Space kept between the title and Sprig's box. */
    const val GAP_DP = 8
    const val DESIGN_CREST_DP = 142
    /** Sprig's bottom sits this far below the crest in the design (164 − 142). */
    const val SPRIG_BELOW_CREST_DP = 22
    /** Space kept between the bottom of the title and the crest. */
    const val TEXT_TO_CREST_DP = 12

    /** The title's width on a screen [screenDp] wide. */
    fun titleWidthDp(screenDp: Int): Int = screenDp - TEXT_SIDE_DP - SPRIG_END_DP - SPRIG_DP - GAP_DP

    /** The crest for a title ending at [textBottomDp]; never above the design's. */
    fun crestDp(textBottomDp: Int): Int = maxOf(DESIGN_CREST_DP, textBottomDp + TEXT_TO_CREST_DP)

    /**
     * Sprig's top for a crest at [crestDp] on a screen [screenDp] wide: its bottom keeps the
     * design's 22dp under the crest, and goes at least [HillEdge.UNDER_DP] under the hill at both
     * lower corners, so the hill always hides its lower body.
     */
    fun sprigTopDp(crestDp: Int, screenDp: Int): Int {
        val right = (screenDp - SPRIG_END_DP).toFloat()
        val left = right - SPRIG_DP
        val under = HillEdge.bottomUnderDp(crestDp.toFloat(), screenDp.toFloat(), left, right)
        val bottom = maxOf(crestDp + SPRIG_BELOW_CREST_DP, ceil(under).toInt())
        return bottom - SPRIG_DP
    }
}

/**
 * Where a Settings row puts its value. Side by side with the title only when the whole title
 * fits on one line beside the value and chevron, and no word of the hint is wider than that
 * room (the onboarding stepper rule, QA on PR #147); otherwise the value goes under the text, so
 * no title is split inside a word at a large font on a narrow screen.
 */
object SettingsRowFit {
    fun sideBySide(titleDp: Float, hintWordDp: Float, trailingDp: Float, innerDp: Float, gapDp: Float = 12f): Boolean =
        maxOf(titleDp, hintWordDp) + gapDp + trailingDp <= innerDp
}
