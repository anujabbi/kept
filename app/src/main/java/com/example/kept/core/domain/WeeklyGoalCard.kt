package com.example.kept.core.domain

import com.example.kept.core.domain.WeeklyGoalRules.DayState
import java.time.DayOfWeek

/** Home's weekly card and the "Add a weekly goal" card (issue #165, Home; story #174). */
object WeeklyCardCopy {
    const val NBSP = WeeklyGoalCopy.NBSP

    /** The section's header, as in mockup 4. */
    const val SECTION = "This week"
    const val SPAN = "Sun to Sat"

    const val ON_TRACK = "On track, no lock"
    const val TODAY_COUNTS = "Today counts: apps lock until it's done"

    /**
     * Today's session is logged in a week whose target is out of reach. Not in the issue, which
     * has only the two lines above; "On track" would be untrue here (DECISIONS.md, #174).
     */
    const val DONE_TODAY = "Done for today, no lock"

    /**
     * A must-do day whose session is not logged, after the give-up time: the lock has ended for
     * the day, so "apps lock until it's done" would be untrue (planner's note on #175). Not in
     * the issue (DECISIONS.md, #175).
     */
    const val LOCK_ENDED = "Past the give-up time, no lock"

    /** The detail on the card's today row, as on the weekly lock screen's (mockup 3). */
    const val TODAY_ROW = "Today"

    /** The today row's detail with the goal's target beside it: "Today · 30 min"; "Today" with none (story #205). */
    fun todayRow(target: String?): String = if (target == null) TODAY_ROW else "$TODAY_ROW · $target"

    const val ADD = WeeklyGoalCopy.ADD
    const val ADD_LINE = WeeklyGoalCopy.SUBTITLE

    /**
     * Under the title in a goal's first week, when that week asks for fewer than its days (#218):
     * "First week: 3 days left, so 3 this week", "First week: 1 day left, so 1 this week"; a goal
     * set on a Saturday has no day left: "First week: no days left, so it starts Sunday".
     */
    fun firstWeek(daysLeft: Int): String = when (daysLeft) {
        0 -> "First week: no days left, so it starts Sunday"
        1 -> "First week: 1${NBSP}day left, so 1 this week"
        else -> "First week: $daysLeft${NBSP}days left, so $daysLeft this week"
    }

    /** "Exercise · 1 of 4 this week". The count stays on one line. */
    fun title(activity: String, done: Int, target: Int): String = "$activity · $done${NBSP}of${NBSP}$target this week"

    private val LABELS = mapOf(
        DayOfWeek.SUNDAY to "Su", DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Tu", DayOfWeek.WEDNESDAY to "We",
        DayOfWeek.THURSDAY to "Th", DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa",
    )

    /** Under a strip cell: "Su" to "Sa". */
    fun dayLabel(day: DayOfWeek): String = LABELS.getValue(day)

    private fun dayName(day: DayOfWeek): String = day.name.lowercase().replaceFirstChar { it.uppercase() }

    /** What TalkBack says for a strip cell: "Monday, done", "Wednesday, today, not done yet". */
    fun spoken(day: WeeklyGoalRules.Day): String {
        val name = dayName(day.date.dayOfWeek)
        return when (day.state) {
            DayState.DONE -> if (day.isToday) "$name, today, done" else "$name, done"
            DayState.SKIPPED -> "$name, skipped"
            DayState.TODAY -> "$name, today, not done yet"
            DayState.COMING_UP -> "$name, coming up"
            DayState.NOT_COUNTED -> "$name, not counted"
        }
    }
}

enum class WeeklyCardStatus(val line: String) {
    /** Today is not a must-do day. */
    ON_TRACK(WeeklyCardCopy.ON_TRACK),

    /** Today is a must-do day and its session is not logged. */
    TODAY_COUNTS(WeeklyCardCopy.TODAY_COUNTS),

    /** Today's session is logged but the week's target can no longer be reached. */
    DONE_TODAY(WeeklyCardCopy.DONE_TODAY),

    /** A must-do day, not logged, after the give-up time: nothing locks any more today. */
    LOCK_ENDED(WeeklyCardCopy.LOCK_ENDED),
}

/** One cell of the Su–Sa strip. */
data class WeeklyCardDay(val label: String, val state: DayState, val isToday: Boolean, val spoken: String) {
    companion object {
        fun of(day: WeeklyGoalRules.Day): WeeklyCardDay =
            WeeklyCardDay(WeeklyCardCopy.dayLabel(day.date.dayOfWeek), day.state, day.isToday, WeeklyCardCopy.spoken(day))
    }
}

/**
 * Everything the card shows for one goal's week, worked out from [WeeklyGoalRules.Week] so the
 * screen only draws it.
 */
data class WeeklyCard(
    val title: String,
    /** The progress bar: one segment per session of this week's target (none when the target is 0). */
    val segments: Int,
    /** Segments filled: the sessions done, never more than there are segments. */
    val filled: Int,
    /** Sunday to Saturday. */
    val days: List<WeeklyCardDay>,
    val status: WeeklyCardStatus,
    val doneToday: Boolean,
    /** The today row's detail: "Today · 30 min", or "Today" for a goal with no target. */
    val todayDetail: String = WeeklyCardCopy.TODAY_ROW,
    /** [WeeklyCardCopy.firstWeek] in a shortened first week, else null. */
    val firstWeekLine: String? = null,
) {
    val statusLine: String get() = status.line

    companion object {
        /**
         * [lockEndedToday] is true once today's give-up time has passed: a must-do day's status
         * then stops promising a lock, so it agrees with Home's headline (story #175).
         * [goalTarget] is the goal's target as [WeeklyGoalCopy.target] reads it, or null.
         * [daysPerWeek] is the goal's days a week: a week whose target is lower is the goal's
         * shortened first week ([WeeklyGoalRules.targetFor]), whose target is the days left after
         * the day it was set.
         */
        fun of(
            activity: String,
            week: WeeklyGoalRules.Week,
            lockEndedToday: Boolean = false,
            goalTarget: String? = null,
            daysPerWeek: Int? = null,
        ): WeeklyCard = WeeklyCard(
            title = WeeklyCardCopy.title(activity, week.done, week.target),
            segments = week.target,
            filled = week.done.coerceAtMost(week.target),
            days = week.days.map(WeeklyCardDay::of),
            status = when {
                week.isMustDay && lockEndedToday -> WeeklyCardStatus.LOCK_ENDED
                week.isMustDay -> WeeklyCardStatus.TODAY_COUNTS
                !week.canStillHitTarget -> WeeklyCardStatus.DONE_TODAY
                else -> WeeklyCardStatus.ON_TRACK
            },
            doneToday = week.doneToday,
            todayDetail = WeeklyCardCopy.todayRow(goalTarget),
            firstWeekLine = if (daysPerWeek != null && week.target < daysPerWeek) WeeklyCardCopy.firstWeek(week.target) else null,
        )
    }
}
