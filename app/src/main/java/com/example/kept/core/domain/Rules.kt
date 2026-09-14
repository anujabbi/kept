package com.example.kept.core.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.IsoFields

object LevelRules {
    const val FLOOR = 1
    fun up(level: Int): Int = level + 1
    fun down(level: Int): Int = maxOf(FLOOR, level - 1)

    /**
     * Finishing every habit before the give-up time grants one level, once per day. The grant is
     * recorded against [today] because the give-up time is a mutable setting: recomputing
     * eligibility when the user undoes would refund the wrong way whenever the setting moved
     * during the day (issue #7).
     */
    fun grantForDay(state: SprigState, today: String, allDoneOnTime: Boolean): SprigState = when {
        !allDoneOnTime -> state
        state.levelGrantedDate == today -> state
        else -> state.copy(level = up(state.level), levelGrantedDate = today)
    }

    /** Takes the day's level back only if that day actually granted one. */
    fun revokeForDay(state: SprigState, today: String): SprigState =
        if (state.levelGrantedDate == today) state.copy(level = down(state.level), levelGrantedDate = null) else state
}

object BreakCap {
    const val MAX_PER_ROLLING_WEEK = 3
    val WINDOW: Duration = Duration.ofDays(7)

    fun usedInWindow(breakTimes: List<Instant>, now: Instant): Int {
        val start = now.minus(WINDOW)
        return breakTimes.count { !it.isBefore(start) && !it.isAfter(now) }
    }

    fun remaining(breakTimes: List<Instant>, now: Instant): Int =
        maxOf(0, MAX_PER_ROLLING_WEEK - usedInWindow(breakTimes, now))

    /** True when the next break would exceed the cap and write the day off. */
    fun nextBreakIsOverCap(breakTimes: List<Instant>, now: Instant): Boolean =
        usedInWindow(breakTimes, now) >= MAX_PER_ROLLING_WEEK
}

object StreakRules {
    fun weekKey(date: LocalDate): String =
        "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    /** Refill the weekly shield when the ISO week changes. */
    fun refillShield(state: SprigState, today: LocalDate): SprigState {
        val key = weekKey(today)
        return if (state.shieldWeekKey != key) state.copy(shieldAvailable = true, shieldWeekKey = key) else state
    }

    data class DayOutcome(
        val allHabitsDone: Boolean,
        val unprotected: Boolean,
        val writtenOff: Boolean, // break cap exceeded that day
    ) {
        val counts: Boolean get() = allHabitsDone && !unprotected && !writtenOff

        /**
         * A day the lock could not be enforced on (issue #2). KEPT failed, not the teen, so the
         * day is skipped: recorded, never counted, and free. Breaking the lock past the cap is a
         * deliberate act, so a day that was also written off keeps its penalty.
         */
        val hollow: Boolean get() = unprotected && !writtenOff
    }

    data class Result(
        val streak: Int,
        val shieldAvailable: Boolean,
        val shieldConsumed: Boolean,
        val counted: Boolean,
        val reset: Boolean,
        /** The day passed through without counting and without costing anything. */
        val hollow: Boolean = false,
    )

    fun apply(streak: Int, shieldAvailable: Boolean, outcome: DayOutcome): Result {
        if (outcome.counts) return Result(streak + 1, shieldAvailable, shieldConsumed = false, counted = true, reset = false)
        if (outcome.hollow) {
            return Result(streak, shieldAvailable, shieldConsumed = false, counted = false, reset = false, hollow = true)
        }
        if (shieldAvailable && streak > 0) {
            return Result(streak, shieldAvailable = false, shieldConsumed = true, counted = false, reset = false)
        }
        return Result(0, shieldAvailable, shieldConsumed = false, counted = false, reset = streak > 0)
    }
}

/**
 * When a day's promise expires (issue #7). The lock window may wrap midnight (e.g. 22:00 -> 06:00,
 * see [LockPolicy.isWindowActive]), and then the give-up time belongs to the *next* calendar
 * morning: judging it on the same date would make every tick after 06:01 permanently late.
 */
object GiveUpTime {
    /** True when the lock window runs past midnight. */
    fun wraps(lockFromMinute: Int, dueMinute: Int): Boolean = lockFromMinute > dueMinute

    /** The calendar date on which [date]'s give-up minute falls. */
    fun dateOf(date: LocalDate, lockFromMinute: Int, dueMinute: Int): LocalDate =
        if (wraps(lockFromMinute, dueMinute)) date.plusDays(1) else date

    /** The exact moment [date]'s promise expires. Ticks strictly before this count for [date]. */
    fun instantFor(date: LocalDate, lockFromMinute: Int, dueMinute: Int, zone: ZoneId): Instant =
        dateOf(date, lockFromMinute, dueMinute)
            .atTime(dueMinute / 60, dueMinute % 60)
            .atZone(zone)
            .toInstant()

    /**
     * True when today's give-up time has already passed. A wrapping window never expires during
     * the calendar day it belongs to, so it is never "too late" for today.
     */
    fun isPastDue(minuteOfDay: Int, lockFromMinute: Int, dueMinute: Int): Boolean =
        !wraps(lockFromMinute, dueMinute) && minuteOfDay >= dueMinute
}

/**
 * Where the lock window that is open (or was last open) began (issue #2).
 *
 * The watchdog asks `UsageStatsManager` "was this phone in use while unprotected?" over
 * `[lastHeartbeat, now]`. That interval is only meaningful inside a lock window: a phone used at
 * 23:00 last night, hours after the window closed, is not evidence of anything, and a watchdog run
 * at 08:00 this morning with a heartbeat still stamped from last night would have turned it into
 * today's protection gap. Clamping the probe to this instant keeps the evidence inside the window
 * it is supposed to be judging.
 */
object LockWindowStart {
    /**
     * The start of the window covering [nowLocal], or of the one that most recently started.
     *
     * A wrapping window (22:00 -> 06:00) asked at 02:00 started *yesterday* at 22:00, so a start
     * time still ahead of [nowLocal] today means the answer belongs to the previous day. Works
     * unchanged for a normal window, where today's start is always at or before now while the
     * window is open.
     */
    fun instantFor(nowLocal: LocalDateTime, lockFromMinute: Int, zone: ZoneId): Instant {
        val todayStart = nowLocal.toLocalDate().atTime(lockFromMinute / 60, lockFromMinute % 60)
        val start = if (nowLocal.isBefore(todayStart)) todayStart.minusDays(1) else todayStart
        return start.atZone(zone).toInstant()
    }
}

/**
 * Decides whether a foreground package must be intercepted. Pure function of a snapshot so
 * the service stays thin and this stays unit-testable.
 */
object LockPolicy {
    data class Snapshot(
        val lockFromMinute: Int,
        val dueMinute: Int,
        val habitsIncomplete: Boolean,
        val breakActiveUntil: Instant?,
        val hardAllowlist: Set<String>,
        val userExceptions: Set<String>,
        /** Packages with a launcher activity. Null means "unknown, treat everything as launchable". */
        val launchable: Set<String>?,
        val onboardingDone: Boolean = true,
    )

    fun isWindowActive(localTime: LocalTime, s: Snapshot): Boolean {
        val m = localTime.hour * 60 + localTime.minute
        return if (s.lockFromMinute <= s.dueMinute) {
            m >= s.lockFromMinute && m < s.dueMinute
        } else {
            // window wraps midnight (e.g. 22:00 -> 06:00)
            m >= s.lockFromMinute || m < s.dueMinute
        }
    }

    fun isLockActive(now: Instant, localTime: LocalTime, s: Snapshot): Boolean {
        if (!s.onboardingDone) return false
        if (!s.habitsIncomplete) return false
        if (!isWindowActive(localTime, s)) return false
        val until = s.breakActiveUntil
        if (until != null && now.isBefore(until)) return false
        return true
    }

    /**
     * True when [pkg] is one the lock covers, ignoring the clock. Separated from [shouldLock] so
     * the watchdog can ask, after the fact, whether an app resumed during a protection gap was one
     * that should have been stopped (issue #2).
     */
    fun isProtectedPackage(pkg: String, s: Snapshot): Boolean {
        if (pkg in s.hardAllowlist) return false
        if (pkg in s.userExceptions) return false
        val launchable = s.launchable
        if (launchable != null && pkg !in launchable) return false
        return true
    }

    fun shouldLock(pkg: String, now: Instant, localTime: LocalTime, s: Snapshot): Boolean =
        isProtectedPackage(pkg, s) && isLockActive(now, localTime, s)

    /**
     * True when a lock screen already on top for [pkg] should stay there (issue #4). The lock
     * screen must answer to the package it is covering, not just to the global lock: adding that
     * package as an exception from Settings has to take the lock screen away immediately, rather
     * than leaving it up until the user backs out and relaunches the app.
     *
     * [pkg] is null or blank only when the lock screen was started without a package, in which
     * case the global lock is the whole answer. Launchability plays no part: the package already
     * got in front of the user, so whether the installed-apps source lists it is irrelevant here.
     */
    fun lockScreenShouldStay(pkg: String?, now: Instant, localTime: LocalTime, s: Snapshot): Boolean =
        if (pkg.isNullOrBlank()) isLockActive(now, localTime, s)
        else shouldLock(pkg, now, localTime, s.copy(launchable = null))
}

/** Formats minute-of-day as "7:00 am". */
fun Int.minuteOfDayLabel(): String {
    val h24 = this / 60
    val m = this % 60
    val suffix = if (h24 < 12) "am" else "pm"
    val h = when (val x = h24 % 12) { 0 -> 12; else -> x }
    return if (m == 0) "$h:00 $suffix" else "$h:${m.toString().padStart(2, '0')} $suffix"
}
