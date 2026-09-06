package com.example.kept.core.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.IsoFields

object LevelRules {
    const val FLOOR = 1
    fun up(level: Int): Int = level + 1
    fun down(level: Int): Int = maxOf(FLOOR, level - 1)
}

object PointsRules {
    const val PER_MINUTE_OFF_APPS = 2L
    const val HABIT_BONUS = 50L

    /** Points for a span of protected time. Whole minutes only. */
    fun forLockedMillis(millis: Long): Long = (millis / 60_000L) * PER_MINUTE_OFF_APPS
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
    }

    data class Result(
        val streak: Int,
        val shieldAvailable: Boolean,
        val shieldConsumed: Boolean,
        val counted: Boolean,
        val reset: Boolean,
    )

    fun apply(streak: Int, shieldAvailable: Boolean, outcome: DayOutcome): Result {
        if (outcome.counts) return Result(streak + 1, shieldAvailable, shieldConsumed = false, counted = true, reset = false)
        if (shieldAvailable && streak > 0) {
            return Result(streak, shieldAvailable = false, shieldConsumed = true, counted = false, reset = false)
        }
        return Result(0, shieldAvailable, shieldConsumed = false, counted = false, reset = streak > 0)
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

    fun shouldLock(pkg: String, now: Instant, localTime: LocalTime, s: Snapshot): Boolean {
        if (pkg in s.hardAllowlist) return false
        if (pkg in s.userExceptions) return false
        val launchable = s.launchable
        if (launchable != null && pkg !in launchable) return false
        return isLockActive(now, localTime, s)
    }
}

/** Formats minute-of-day as "7:00 am". */
fun Int.minuteOfDayLabel(): String {
    val h24 = this / 60
    val m = this % 60
    val suffix = if (h24 < 12) "am" else "pm"
    val h = when (val x = h24 % 12) { 0 -> 12; else -> x }
    return if (m == 0) "$h:00 $suffix" else "$h:${m.toString().padStart(2, '0')} $suffix"
}
