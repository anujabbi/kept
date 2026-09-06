package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RolloverEngineTest {
    private fun day(date: LocalDate, done: Int = 2, total: Int = 2, breaks: Int = 0, writtenOff: Boolean = false, unprotected: Boolean = false, buddy: Boolean? = null) =
        RolloverEngine.DayInput(date, done, total, 3 * 3_600_000L, 254, breaks, writtenOff, unprotected, buddy)

    @Test fun `completing every habit extends the streak and records the date`() {
        val d = LocalDate.of(2026, 9, 5)
        val out = RolloverEngine.rollover(SprigState(streakDays = 6), day(d))
        assertEquals(7, out.state.streakDays)
        assertEquals(d, out.state.lastRolloverDate)
        assertTrue(out.summary.countedForStreak)
    }

    @Test fun `crossing a threshold unlocks that form with the week variant`() {
        val d = LocalDate.of(2026, 9, 5)
        val out = RolloverEngine.rollover(SprigState(streakDays = 6), day(d))
        assertEquals(SprigForm.BUD, out.formBefore)
        assertEquals(SprigForm.BLOOM, out.formAfter)
        assertEquals(1, out.unlocks.size)
        assertEquals(SprigForm.BLOOM, out.unlocks[0].form)
        assertEquals(Variants.forDate(d), out.unlocks[0].variant)
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

    @Test fun `an over-cap day is written off`() {
        val d = LocalDate.of(2026, 9, 5)
        val out = RolloverEngine.rollover(SprigState(streakDays = 1, shieldAvailable = false, shieldWeekKey = StreakRules.weekKey(d)), day(d, breaks = 4, writtenOff = true))
        assertEquals(0, out.state.streakDays)
        assertTrue(out.summary.writtenOff)
    }

    @Test fun `pair streak grows only when both did it and unlocks Duo at seven`() {
        var state = SprigState(streakDays = 20)
        var d = LocalDate.of(2026, 9, 1)
        var duo = false
        repeat(7) {
            val out = RolloverEngine.rollover(state, day(d, buddy = true))
            state = out.state
            if (out.unlocks.any { it.form == SprigForm.DUO }) duo = true
            d = d.plusDays(1)
        }
        assertEquals(7, state.pairStreak)
        assertTrue(duo)
        val broken = RolloverEngine.rollover(state, day(d, buddy = false))
        assertEquals(0, broken.state.pairStreak)
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

    @Test fun `midnight boundary - a day completed at 23-59 counts for that day`() {
        // The engine works on calendar dates; the caller resolves the local date. Here we assert the
        // state after two consecutive days is two increments, independent of the wall clock.
        var state = SprigState()
        state = RolloverEngine.rollover(state, day(LocalDate.of(2026, 12, 31))).state
        state = RolloverEngine.rollover(state, day(LocalDate.of(2027, 1, 1))).state
        assertEquals(2, state.streakDays)
        assertEquals(LocalDate.of(2027, 1, 1), state.lastRolloverDate)
    }
}
