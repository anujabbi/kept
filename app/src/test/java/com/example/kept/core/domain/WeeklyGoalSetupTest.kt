package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weekly goal setup screen's rules and copy (issue #165, Setup; story #173). */
class WeeklyGoalSetupTest {

    private fun plain(s: String) = s.replace(WeeklyGoalCopy.NBSP, ' ')

    // --- the draft -----------------------------------------------------------------------------

    @Test fun the_day_chips_are_1_to_7_and_a_pick_outside_is_pulled_in() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), WeeklyGoalDraft.DAY_CHOICES)
        for (n in 1..7) assertEquals(n, WeeklyGoalDraft().withDays(n).days)
        assertEquals(1, WeeklyGoalDraft().withDays(0).days)
        assertEquals(7, WeeklyGoalDraft().withDays(8).days)
        assertEquals("days keep the activity", "Read", WeeklyGoalDraft("Read").withDays(2).title)
    }

    // --- copy ----------------------------------------------------------------------------------

    @Test fun the_summary_follows_the_activity_and_the_number() {
        assertEquals("Exercise, 4 days a week", plain(WeeklyGoalDraft("Exercise", 4).summary))
        assertEquals("Read, 4 days a week", plain(WeeklyGoalDraft("Exercise", 4).pick("Read").summary))
        assertEquals("Read, 5 days a week", plain(WeeklyGoalDraft("Read", 4).withDays(5).summary))
        assertEquals("Read, 7 days a week", plain(WeeklyGoalDraft("Read", 7).summary))
        assertEquals("the saved name, not the spaces around it", "Climb, 2 days a week", plain(WeeklyGoalDraft("  Climb ", 2).summary))
    }

    @Test fun the_days_never_wrap() {
        assertFalse(WeeklyGoalCopy.days(4).contains(' '))
        assertEquals("Exercise · 4 days a week", plain(WeeklyGoalCopy.listRow("Exercise", 4)))
        assertEquals("4 of 7", plain(WeeklyGoalDraft("Exercise", 4).daysCount))
        assertEquals("3 of 7", plain(WeeklyGoalDraft().daysCount))
    }

    // --- layout --------------------------------------------------------------------------------

    @Test fun seven_day_chips_are_each_at_least_48dp_wide_from_336dp_up() {
        for (w in listOf(336, 360, 390, 412, 600)) {
            val side = WeeklyGoalSetupLayout.dayRowSideDp(w)
            assertTrue("side at $w", side in 0..21)
            assertTrue("cell at $w is ${(w - 2 * side) / 7f}", (w - 2 * side) / 7f >= 48f)
        }
        assertEquals("the drawn chips start at the page's 24dp", 21, WeeklyGoalSetupLayout.dayRowSideDp(412))
        assertEquals(21, WeeklyGoalSetupLayout.dayRowSideDp(390))
        assertEquals(11, WeeklyGoalSetupLayout.dayRowSideDp(360))
        assertEquals(0, WeeklyGoalSetupLayout.dayRowSideDp(320))
    }

    @Test fun sprig_leaves_the_title_alone_at_a_large_font() {
        assertTrue(WeeklyGoalSetupLayout.showsSprig(1f))
        assertTrue(WeeklyGoalSetupLayout.showsSprig(1.3f))
        assertFalse(WeeklyGoalSetupLayout.showsSprig(1.5f))
        assertFalse(WeeklyGoalSetupLayout.showsSprig(2f))
    }
}
