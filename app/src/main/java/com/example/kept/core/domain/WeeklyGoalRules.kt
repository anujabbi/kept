package com.example.kept.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Weekly goals (issue #165): "X days a week", at most one session a day, week Sunday to Saturday.
 *
 * Everything here works on local dates and counts. Nothing reads a clock or a zone, so a 23- or
 * 25-hour day cannot change a result: the caller turns "now" into today's local date and passes
 * it in. Progress is never stored; it is counted from the dates that have a logged session.
 */
object WeeklyGoalRules {
    const val MIN_DAYS = 1
    const val MAX_DAYS = 7
    const val DEFAULT_DAYS = 3
    const val DAYS_IN_WEEK = 7

    /** Weekly goals have their own limit; they do not count toward [HabitLimits.MAX_HABITS]. */
    const val MAX_GOALS = 3
    fun canAdd(activeGoals: Int): Boolean = activeGoals < MAX_GOALS

    fun isValidDaysPerWeek(days: Int): Boolean = days in MIN_DAYS..MAX_DAYS

    /** The Sunday on or before [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    /** The Saturday on or after [date]. */
    fun weekEnd(date: LocalDate): LocalDate = weekStart(date).plusDays(DAYS_IN_WEEK - 1L)

    /** Days left in the week including [date]: Sunday 7 down to Saturday 1. */
    fun remaining(date: LocalDate): Int = (ChronoUnit.DAYS.between(date, weekEnd(date)) + 1).toInt()

    /**
     * The target for the week that holds [date].
     *
     * In the week the goal starts counting ([effectiveFrom]'s week) it is reduced to the days left
     * after the creation day, so a goal set on Wednesday asks for at most 3 (Thursday to Saturday)
     * and one set on Saturday asks for 0. From the next Sunday it is [daysPerWeek]. A week that ends
     * before the goal starts has no target. [daysPerWeek] is read as it is now, so an edit applies
     * to the current week at once.
     */
    fun targetFor(daysPerWeek: Int, effectiveFrom: LocalDate, date: LocalDate): Int {
        val week = weekStart(date)
        val firstWeek = weekStart(effectiveFrom)
        return when {
            week.isBefore(firstWeek) -> 0
            week == firstWeek -> minOf(daysPerWeek, remaining(effectiveFrom) - 1)
            else -> daysPerWeek
        }
    }

    /** Sessions still owed this week, never below 0. */
    fun needed(target: Int, done: Int): Int = (target - done).coerceAtLeast(0)

    /** The mechanic's test, before today's session is logged: no more days than sessions owed. */
    fun cannotSkip(needed: Int, remaining: Int): Boolean = needed > 0 && needed >= remaining

    /** What the lock, the notification and the Home card need to know about one goal. */
    data class Goal(val daysPerWeek: Int, val effectiveFrom: LocalDate)

    enum class DayState {
        /** A session is logged for the day (today included). */
        DONE,
        /** A past day the goal counted, with no session. */
        SKIPPED,
        /** Today, not logged yet. */
        TODAY,
        /** A day after today. */
        COMING_UP,
        /** A past day with no session that the goal did not count: before it was set, or the day it was set. Not a skip. */
        NOT_COUNTED,
    }

    data class Day(val date: LocalDate, val state: DayState, val isToday: Boolean)

    /** One goal's week as of [today]. */
    data class Week(
        val weekStart: LocalDate,
        val today: LocalDate,
        /** This week's target: [Goal.daysPerWeek], or the reduced first-week target. */
        val target: Int,
        /** Days this week, up to and including today, with a logged session. */
        val done: Int,
        /** Days left in the week including today. */
        val remaining: Int,
        /** `target − done`, floored at 0. */
        val needed: Int,
        val doneToday: Boolean,
        /**
         * Today's session is owed and not yet logged: this is the flag that locks. False on the
         * creation day, false once today's session is logged, false when nothing is needed.
         */
        val isMustDay: Boolean,
        /**
         * Today is a must-do day for this goal, before and after its session is logged: what
         * [isMustDay] was this morning. The lock screen keeps a logged session's row from it, so
         * holding the row can undo it (story #175).
         */
        val todayCounts: Boolean = isMustDay,
        /**
         * False when the target is out of reach ("You can't hit {target} this week"): more
         * sessions are needed than there are days left to log one. Must-do days go on regardless.
         */
        val canStillHitTarget: Boolean,
        /** Sunday to Saturday, seven entries. */
        val days: List<Day>,
    )

    /**
     * The state of [goal]'s week on [today], given every date that has a logged session
     * ([sessionDates] may hold other weeks; only this week up to today is counted).
     */
    fun week(goal: Goal, sessionDates: Set<LocalDate>, today: LocalDate): Week {
        val start = weekStart(today)
        val dates = (0 until DAYS_IN_WEEK).map { start.plusDays(it.toLong()) }
        val logged = dates.filter { !it.isAfter(today) && it in sessionDates }.toSet()
        val target = targetFor(goal.daysPerWeek, goal.effectiveFrom, today)
        val done = logged.size
        val remaining = remaining(today)
        val needed = needed(target, done)
        val doneToday = today in logged
        // Today can take a session only while it has none, so a logged today is not a day left.
        val daysLeftToLog = if (doneToday) remaining - 1 else remaining
        // What was owed before today's session, if it is logged: that is what made today count.
        val todayCounts = today != goal.effectiveFrom && cannotSkip(needed(target, if (doneToday) done - 1 else done), remaining)
        return Week(
            weekStart = start,
            today = today,
            target = target,
            done = done,
            remaining = remaining,
            needed = needed,
            doneToday = doneToday,
            isMustDay = !doneToday && todayCounts,
            todayCounts = todayCounts,
            canStillHitTarget = needed <= daysLeftToLog,
            days = dates.map { date ->
                val state = when {
                    date in logged -> DayState.DONE
                    date == today -> DayState.TODAY
                    date.isAfter(today) -> DayState.COMING_UP
                    !date.isAfter(goal.effectiveFrom) -> DayState.NOT_COUNTED
                    else -> DayState.SKIPPED
                }
                Day(date, state, isToday = date == today)
            },
        )
    }
}
