package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** [RolloverEngine.recapDayFor] picks which day of a multi-day catch-up the recap shows (issue #16). */
class RecapDaySelectionTest {
    private fun output(date: LocalDate, reset: Boolean = false, shield: Boolean = false): RolloverEngine.Output {
        val summary = RolloverEngine.DaySummary(
            date = date, habitsDone = 0, habitsTotal = 2, lockedMillis = 0, breaksUsed = 0,
            unprotected = false, hollow = false, broken = false, writtenOff = false,
            countedForStreak = false, shieldConsumed = shield, streakReset = reset,
            levelEnd = 1, formEnd = SprigForm.SPRIG, streakEnd = 0,
        )
        return RolloverEngine.Output(SprigState(), summary, emptyList(), SprigForm.SPRIG, SprigForm.SPRIG)
    }

    private val d1 = LocalDate.of(2026, 9, 13)
    private val d2 = d1.plusDays(1)
    private val d3 = d1.plusDays(2)

    @Test fun `a single day is shown as is`() {
        assertEquals(d1, RolloverEngine.recapDayFor(listOf(output(d1, reset = true))).summary.date)
    }

    @Test fun `a gap shows the day the streak reset, not the last day`() {
        val outputs = listOf(output(d1, reset = true), output(d2), output(d3))
        assertEquals(d1, RolloverEngine.recapDayFor(outputs).summary.date)
    }

    @Test fun `a gap shows the shielded day before the reset that followed it`() {
        val outputs = listOf(output(d1, shield = true), output(d2, reset = true), output(d3))
        assertEquals(d1, RolloverEngine.recapDayFor(outputs).summary.date)
    }

    @Test fun `a gap with no reset or shield shows the last day`() {
        val outputs = listOf(output(d1), output(d2), output(d3))
        assertEquals(d3, RolloverEngine.recapDayFor(outputs).summary.date)
    }
}
