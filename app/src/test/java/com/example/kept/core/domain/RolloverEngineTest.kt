package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RolloverEngineTest {
    private fun day(
        date: LocalDate,
        done: Int = 2,
        beforeDue: Int = done,
        total: Int = 2,
        breaks: Int = 0,
        writtenOff: Boolean = false,
        unprotected: Boolean = false,
    ) = RolloverEngine.DayInput(date, done, beforeDue, total, 3 * 3_600_000L, breaks, writtenOff, unprotected)

    @Test fun `completing every habit extends the streak and records the date`() {
        val d = LocalDate.of(2026, 9, 5)
        val out = RolloverEngine.rollover(SprigState(streakDays = 6), day(d))
        assertEquals(7, out.state.streakDays)
        assertEquals(d, out.state.lastRolloverDate)
        assertTrue(out.summary.countedForStreak)
    }

    @Test fun `crossing a threshold unlocks that form on that date`() {
        val d = LocalDate.of(2026, 9, 5)
        val out = RolloverEngine.rollover(SprigState(streakDays = 6), day(d))
        assertEquals(SprigForm.BUD, out.formBefore)
        assertEquals(SprigForm.BLOOM, out.formAfter)
        assertEquals(1, out.unlocks.size)
        assertEquals(SprigForm.BLOOM, out.unlocks[0].form)
        assertEquals(d, out.unlocks[0].date)
        assertEquals(7, out.unlocks[0].streakAtUnlock)
    }

    @Test fun `a missed day uses the shield and unlocks nothing`() {
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 6, shieldAvailable = true, shieldWeekKey = StreakRules.weekKey(d))
        val out = RolloverEngine.rollover(state, day(d, done = 1))
        assertEquals(6, out.state.streakDays)
        assertTrue(out.summary.shieldConsumed)
        assertFalse(out.state.shieldAvailable)
        assertTrue(out.unlocks.isEmpty())
    }

    @Test fun `a missed day without shield drops the displayed form`() {
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 10, shieldAvailable = false, shieldWeekKey = StreakRules.weekKey(d))
        val out = RolloverEngine.rollover(state, day(d, done = 0))
        assertEquals(0, out.state.streakDays)
        assertEquals(SprigForm.BLOOM, out.formBefore)
        assertEquals(SprigForm.SPRIG, out.formAfter)
        assertTrue(out.summary.streakReset)
        assertEquals(10, out.state.bestStreak)
    }

    @Test fun `rollover on a Monday refills the shield before applying the day`() {
        val monday = LocalDate.of(2026, 9, 7)
        val state = SprigState(streakDays = 3, shieldAvailable = false, shieldWeekKey = StreakRules.weekKey(monday.minusDays(1)))
        val out = RolloverEngine.rollover(state, day(monday, done = 0))
        // Shield refilled for the new week, then consumed by the miss.
        assertEquals(3, out.state.streakDays)
        assertTrue(out.summary.shieldConsumed)
    }

    @Test fun `a broken day still counts but is marked`() {
        val d = LocalDate.of(2026, 9, 5)
        val out = RolloverEngine.rollover(SprigState(streakDays = 1, wilted = true), day(d, breaks = 1))
        assertEquals(2, out.state.streakDays)
        assertTrue(out.summary.broken)
        assertFalse(out.state.wilted)
    }

    @Test fun `an unprotected day is hollow - recorded, not counted, nothing lost`() {
        // Issue #2: the lock stopping is the app's failure, not the teen's. The day is recorded so
        // the week strip can show it hollow, but the streak holds and the shield is untouched.
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 9, shieldAvailable = true, shieldWeekKey = StreakRules.weekKey(d))
        val out = RolloverEngine.rollover(state, day(d, unprotected = true))
        assertEquals(9, out.state.streakDays)
        assertTrue(out.state.shieldAvailable)
        assertFalse(out.summary.shieldConsumed)
        assertFalse(out.summary.streakReset)
        assertFalse(out.summary.countedForStreak)
        assertTrue(out.summary.hollow)
        assertTrue(out.summary.unprotected)
        assertEquals(2, out.summary.habitsDone)
        assertTrue(out.unlocks.isEmpty())
    }

    @Test fun `an unprotected day with nothing done still holds the streak`() {
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 9, shieldAvailable = false, shieldWeekKey = StreakRules.weekKey(d))
        val out = RolloverEngine.rollover(state, day(d, done = 0, unprotected = true))
        assertEquals(9, out.state.streakDays)
        assertFalse(out.summary.streakReset)
        assertTrue(out.summary.hollow)
    }

    @Test fun `a hollow day does not heal a wilt`() {
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 4, wilted = true)
        val out = RolloverEngine.rollover(state, day(d, unprotected = true))
        assertTrue(out.state.wilted)
    }

    @Test fun `an unprotected day that broke the cap is still written off`() {
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 5, shieldAvailable = false, shieldWeekKey = StreakRules.weekKey(d))
        val out = RolloverEngine.rollover(state, day(d, breaks = 4, writtenOff = true, unprotected = true))
        assertEquals(0, out.state.streakDays)
        assertFalse(out.summary.hollow)
        assertTrue(out.summary.writtenOff)
    }

    @Test fun `a written-off day does not spend the shield (issue 143)`() {
        // The break screen said "your shield can't save it", so the engine must not spend it.
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 8, shieldAvailable = true, shieldWeekKey = StreakRules.weekKey(d))
        val out = RolloverEngine.rollover(state, day(d, done = 1, breaks = 4, writtenOff = true))
        assertFalse(out.summary.shieldConsumed)
        assertTrue(out.state.shieldAvailable)
        assertTrue(out.summary.writtenOff)
        assertTrue(out.summary.streakReset)
        assertEquals(0, out.state.streakDays)
        assertEquals(8, out.state.bestStreak)
        assertEquals(
            RecapOutcome.WRITTEN_OFF,
            RecapOutcome.of(
                out.summary.countedForStreak, out.summary.unprotected, out.summary.writtenOff,
                out.summary.shieldConsumed, out.summary.habitsDone, out.summary.habitsTotal,
            ),
        )
    }

    @Test fun `the unspent shield still saves a later missed day that week (issue 143)`() {
        // Thursday written off, Friday kept, Saturday missed: the shield is still there for Saturday.
        val thu = LocalDate.of(2026, 9, 3)
        var state = SprigState(streakDays = 8, shieldAvailable = true, shieldWeekKey = StreakRules.weekKey(thu))
        state = RolloverEngine.rollover(state, day(thu, breaks = 4, writtenOff = true)).state
        state = RolloverEngine.rollover(state, day(thu.plusDays(1))).state
        assertEquals(1, state.streakDays)
        val sat = RolloverEngine.rollover(state, day(thu.plusDays(2), done = 0))
        assertTrue(sat.summary.shieldConsumed)
        assertFalse(sat.summary.streakReset)
        assertEquals(1, sat.state.streakDays)
        assertFalse(sat.state.shieldAvailable)
    }

    @Test fun `pending dates across a multi-day gap are processed in order`() {
        val last = LocalDate.of(2026, 9, 1)
        val yesterday = LocalDate.of(2026, 9, 4)
        val pending = RolloverEngine.pendingDates(last, yesterday, LocalDate.of(2026, 8, 1))
        assertEquals(listOf(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4)), pending)
    }

    @Test fun `no pending dates when rollover already ran for yesterday`() {
        val y = LocalDate.of(2026, 9, 4)
        assertTrue(RolloverEngine.pendingDates(y, y, y.minusDays(10)).isEmpty())
    }

    @Test fun `first use date bounds the catch-up when nothing has rolled over yet`() {
        val pending = RolloverEngine.pendingDates(null, LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 3))
        assertEquals(2, pending.size)
    }

    @Test fun `a habit ticked after the give-up time does not count and the day is missed`() {
        val d = LocalDate.of(2026, 9, 5)
        val state = SprigState(streakDays = 4, shieldAvailable = false, shieldWeekKey = StreakRules.weekKey(d))
        // Both habits were ticked, but one of them after the give-up time.
        val out = RolloverEngine.rollover(state, day(d, done = 2, beforeDue = 1))
        assertFalse(out.summary.countedForStreak)
        assertEquals(0, out.state.streakDays)
        assertTrue(out.summary.streakReset)
        // The record still shows what was actually ticked.
        assertEquals(2, out.summary.habitsDone)
    }

    @Test fun `a wrapping lock window keeps a day ticked before the next morning`() {
        // 22:00 -> 06:00. The same counting the runner does: a completion counts when it lands
        // before that day's give-up moment, which for a wrapping window is the next morning.
        val zone = ZoneId.of("Europe/London")
        val d = LocalDate.of(2026, 9, 5)
        val from = 22 * 60
        val due = 6 * 60
        val dueMillis = GiveUpTime.instantFor(d, from, due, zone).toEpochMilli()
        fun at(date: LocalDate, minute: Int) =
            date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()

        val completions = listOf(at(d, 23 * 60), at(d.plusDays(1), 2 * 60))
        val beforeDue = completions.count { it < dueMillis }
        assertEquals(2, beforeDue)

        val out = RolloverEngine.rollover(
            SprigState(streakDays = 4),
            day(d, done = completions.size, beforeDue = beforeDue),
        )
        assertTrue(out.summary.countedForStreak)
        assertEquals(5, out.state.streakDays)

        // The same ticks against a normal 07:00 -> 21:00 window are both too late.
        val normalDue = GiveUpTime.instantFor(d, 7 * 60, 21 * 60, zone).toEpochMilli()
        assertEquals(0, completions.count { it < normalDue })
    }

    @Test fun `consecutive days across a year boundary each extend the streak`() {
        // The engine works on calendar dates; the caller resolves the local date. Here we assert the
        // state after two consecutive days is two increments, independent of the wall clock.
        var state = SprigState()
        state = RolloverEngine.rollover(state, day(LocalDate.of(2026, 12, 31))).state
        state = RolloverEngine.rollover(state, day(LocalDate.of(2027, 1, 1))).state
        assertEquals(2, state.streakDays)
        assertEquals(LocalDate.of(2027, 1, 1), state.lastRolloverDate)
    }

    // --- weekly goals: a must-do day's promise is the habits and the session (issue #165, story #176) ---

    /** Wednesday 25 November 2026, both habits ticked in time; [due] must-do sessions, [done] of them logged in time. */
    private fun mustDoDay(due: Int, done: Int, missed: List<String> = emptyList(), habitsDone: Int = 2) =
        day(wednesday, done = habitsDone).copy(weeklyDue = due, weeklyDoneBeforeDue = done, weeklyMissed = missed)

    private val wednesday = LocalDate.of(2026, 11, 25)
    private fun streakOf(n: Int, shield: Boolean) =
        SprigState(streakDays = n, shieldAvailable = shield, shieldWeekKey = StreakRules.weekKey(wednesday))

    @Test fun `habits done and the must-do session done counts the day`() {
        val out = RolloverEngine.rollover(streakOf(6, shield = false), mustDoDay(due = 1, done = 1))
        assertTrue(out.summary.countedForStreak)
        assertEquals(7, out.state.streakDays)
        assertEquals(1, out.summary.weeklyDue)
        assertEquals(1, out.summary.weeklyDone)
        assertEquals(emptyList<String>(), out.summary.weeklyMissed)
    }

    @Test fun `habits done and the must-do session missed does not count, and resets the streak with no shield`() {
        val out = RolloverEngine.rollover(streakOf(6, shield = false), mustDoDay(due = 1, done = 0, missed = listOf("Exercise")))
        assertFalse(out.summary.countedForStreak)
        assertFalse(out.summary.shieldConsumed)
        assertTrue(out.summary.streakReset)
        assertEquals(0, out.state.streakDays)
        assertEquals(2, out.summary.habitsDone)
        assertEquals(1, out.summary.weeklyDue)
        assertEquals(0, out.summary.weeklyDone)
        assertEquals(listOf("Exercise"), out.summary.weeklyMissed)
    }

    @Test fun `the shield covers a missed must-do session when one is available`() {
        val out = RolloverEngine.rollover(streakOf(6, shield = true), mustDoDay(due = 1, done = 0, missed = listOf("Exercise")))
        assertFalse(out.summary.countedForStreak)
        assertTrue(out.summary.shieldConsumed)
        assertFalse(out.summary.streakReset)
        assertEquals(6, out.state.streakDays)
        assertFalse(out.state.shieldAvailable)
    }

    @Test fun `one of two must-do sessions is not enough`() {
        val out = RolloverEngine.rollover(streakOf(6, shield = false), mustDoDay(due = 2, done = 1, missed = listOf("Read")))
        assertFalse(out.summary.countedForStreak)
        assertEquals(0, out.state.streakDays)
    }

    @Test fun `a day that is not a must-do day ignores weekly goals, and a day with no goal is unchanged`() {
        // No session was due: the default input, which is what a day with no weekly goal is too.
        val plain = RolloverEngine.rollover(streakOf(6, shield = false), day(wednesday))
        val notDue = RolloverEngine.rollover(streakOf(6, shield = false), mustDoDay(due = 0, done = 0))
        assertEquals(plain, notDue)
        assertTrue(plain.summary.countedForStreak)
        assertEquals(7, plain.state.streakDays)
        assertEquals(0, plain.summary.weeklyDue)
        // And the habits still decide such a day on their own.
        val slipped = RolloverEngine.rollover(streakOf(6, shield = false), mustDoDay(due = 0, done = 0, habitsDone = 1))
        assertFalse(slipped.summary.countedForStreak)
    }

    @Test fun `a must-do session does not make up for a habit, a day with no habits, or a day the lock was off`() {
        assertFalse(RolloverEngine.rollover(streakOf(6, shield = false), mustDoDay(due = 1, done = 1, habitsDone = 1)).summary.countedForStreak)
        // No habits: the day never counted before weekly goals, and a session alone does not change that.
        val noHabits = day(wednesday, done = 0, total = 0).copy(weeklyDue = 1, weeklyDoneBeforeDue = 1)
        assertFalse(RolloverEngine.rollover(streakOf(6, shield = false), noHabits).summary.countedForStreak)
        // A day the lock was off stays hollow and free, whatever the session did.
        val off = RolloverEngine.rollover(streakOf(6, shield = true), day(wednesday, unprotected = true).copy(weeklyDue = 1, weeklyDoneBeforeDue = 0))
        assertTrue(off.summary.hollow)
        assertEquals(6, off.state.streakDays)
        assertTrue(off.state.shieldAvailable)
    }
}
