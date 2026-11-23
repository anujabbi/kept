package com.example.kept.core.domain

import java.time.LocalDate

/**
 * What one closed day's must-do sessions came to (issue #165, story #176): how many weekly goals
 * made that day a must-do day, how many of those sessions were logged before the give-up time,
 * and the names of the goals that were not, in the goals' order.
 */
data class WeeklyDayOutcome(val due: Int, val done: Int, val missed: List<String>) {
    companion object {
        /** No weekly goal, or a day none of them needed. */
        val NONE = WeeklyDayOutcome(0, 0, emptyList())
    }
}

/**
 * The must-do sessions of a day that has passed, for the rollover (story #176).
 *
 * Everything is asked about [LocalDate]s, as in [WeeklyGoalRules]: whether a goal made the day a
 * must-do day is that goal's week as it stood on that date, not on the day the rollover runs. So
 * a phone that was off for a week closes each day against its own week, across a Sunday too. The
 * one clock value is the give-up time, which the caller turns into an instant for that date.
 */
object WeeklyDay {
    /**
     * A goal as stored. [sessions] maps every date with a logged session to the moment it was
     * logged (epoch millis); dates after the day in question are ignored.
     */
    data class Goal(val title: String, val daysPerWeek: Int, val effectiveFrom: LocalDate, val sessions: Map<LocalDate, Long>)

    /**
     * [date]'s outcome for [goals]. A goal counts when [date] was one of its must-do days: on the
     * morning of [date] it owed at least as many sessions as the week had days left. The day it
     * was set and any day before it never count. Its session is done when one is logged on
     * [date] before [dueMillis], that date's give-up time; a session logged later still counts
     * toward the week's total, but the day is missed.
     */
    fun outcome(goals: List<Goal>, date: LocalDate, dueMillis: Long): WeeklyDayOutcome {
        val due = goals.filter { g ->
            date.isAfter(g.effectiveFrom) &&
                WeeklyGoalRules.week(WeeklyGoalRules.Goal(g.daysPerWeek, g.effectiveFrom), g.sessions.keys, date).todayCounts
        }
        if (due.isEmpty()) return WeeklyDayOutcome.NONE
        val missed = due.filterNot { g -> g.sessions[date]?.let { it < dueMillis } == true }
        return WeeklyDayOutcome(due.size, due.size - missed.size, missed.map { it.title })
    }
}

/**
 * Words for a day that was missed because of a weekly session (story #176). Not in #165, which
 * only says the recap and history explain the result (DECISIONS.md, #176).
 */
object WeeklyMissedCopy {
    /** "Exercise", "Exercise and Read", "Exercise, Read and Study". */
    fun names(goals: List<String>): String = when (goals.size) {
        0 -> ""
        1 -> goals[0]
        else -> goals.dropLast(1).joinToString(", ") + " and " + goals.last()
    }

    /**
     * The recap's sentence: "Exercise wasn't logged in time." Short, so that with one goal the
     * recap's line stays at three lines on a 360dp screen and nothing scrolls at font 1.0.
     */
    fun sentence(goals: List<String>): String =
        if (goals.size == 1) "${goals[0]} wasn't logged in time." else "${names(goals)} weren't logged in time."

    /** Under a history row: "Weekly goal missed: Exercise". */
    fun historyLine(goals: List<String>): String =
        (if (goals.size == 1) "Weekly goal missed: " else "Weekly goals missed: ") + goals.joinToString(", ")

    /** How the names are kept in `day_records.weeklyMissed`: one per line. */
    fun store(goals: List<String>): String = goals.joinToString("\n")

    fun read(stored: String): List<String> = stored.split('\n').filter { it.isNotBlank() }
}
