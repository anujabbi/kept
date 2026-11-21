package com.example.kept.feature.home

import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.ui.DayDot
import org.junit.Assert.assertEquals
import org.junit.Test

/** The week row's dot for a closed day (spec, Home). Pure: no Compose. */
class DayDotTest {
    private fun record(
        counted: Boolean = false,
        shield: Boolean = false,
        writtenOff: Boolean = false,
        unprotected: Boolean = false,
    ) = DayRecordEntity(
        date = "2026-09-05", habitsDone = 1, habitsTotal = 2, breaksUsed = if (writtenOff) 4 else 0,
        unprotected = unprotected, broken = writtenOff, writtenOff = writtenOff, countedForStreak = counted,
        shieldConsumed = shield, finalized = true,
    )

    @Test fun `a shielded day is shielded`() =
        assertEquals(DayDot.SHIELDED, dayDot(false, record(shield = true), false))

    @Test fun `a written-off day is missed`() =
        assertEquals(DayDot.MISSED, dayDot(false, record(writtenOff = true), false))

    @Test fun `a written-off day is never shown as saved by the shield (issue 143)`() {
        // A record from a build before #143 could carry both flags; the shield could not save it.
        assertEquals(DayDot.MISSED, dayDot(false, record(writtenOff = true, shield = true), false))
    }

    @Test fun `an unprotected day that was also written off is missed, not hollow`() =
        assertEquals(DayDot.MISSED, dayDot(false, record(writtenOff = true, unprotected = true), false))
}
