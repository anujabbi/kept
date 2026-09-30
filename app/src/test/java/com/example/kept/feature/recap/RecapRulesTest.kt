package com.example.kept.feature.recap

import com.example.kept.core.data.db.DayRecordEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RecapRules.streakReset] decides whether the slipped-day recap may claim "Streak reset" (issue #16). */
class RecapRulesTest {
    private fun day(
        date: String,
        streakEnd: Int,
        counted: Boolean = false,
        shieldConsumed: Boolean = false,
    ) = DayRecordEntity(date = date, countedForStreak = counted, shieldConsumed = shieldConsumed, streakEnd = streakEnd, finalized = true)

    @Test fun `a day that took a live streak to zero is a reset`() {
        assertTrue(RecapRules.streakReset(day("2026-09-15", 0), day("2026-09-14", 5)))
    }

    @Test fun `a slipped day when the streak was already zero is not a reset`() {
        assertFalse(RecapRules.streakReset(day("2026-09-15", 0), day("2026-09-14", 0)))
    }

    @Test fun `no previous day means no reset claim`() {
        assertFalse(RecapRules.streakReset(day("2026-09-15", 0), null))
    }

    @Test fun `a shielded day is not a reset`() {
        assertFalse(RecapRules.streakReset(day("2026-09-15", 5, shieldConsumed = true), day("2026-09-14", 5)))
    }

    @Test fun `a counted day is not a reset`() {
        assertFalse(RecapRules.streakReset(day("2026-09-15", 6, counted = true), day("2026-09-14", 5)))
    }

    @Test fun `no record means no reset claim`() {
        assertFalse(RecapRules.streakReset(null, day("2026-09-14", 5)))
    }
}
