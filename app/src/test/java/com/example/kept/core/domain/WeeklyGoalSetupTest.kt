package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The weekly goal list's copy (issue #165, story #173). Goals are set on the promise since #218. */
class WeeklyGoalSetupTest {

    private fun plain(s: String) = s.replace(WeeklyGoalCopy.NBSP, ' ')

    @Test fun the_list_row_carries_the_target_and_reads_as_before_without_one() {
        assertEquals("Exercise · 30 min · 4 days a week", plain(WeeklyGoalCopy.listRow("Exercise", "30 min", 4)))
        assertEquals("Exercise · 4 days a week", plain(WeeklyGoalCopy.listRow("Exercise", null, 4)))
        assertEquals("10 pages", plain(WeeklyGoalCopy.target(10, "pages")!!))
        assertEquals("1 page", plain(WeeklyGoalCopy.target(1, "pages")!!))
        assertEquals("8 glasses", plain(WeeklyGoalCopy.target(8, "glasses")!!))
        assertEquals("1 time", plain(WeeklyGoalCopy.target(1, "times")!!))
        assertEquals(null, WeeklyGoalCopy.target(null, null))
        assertFalse("the target never wraps", WeeklyGoalCopy.target(30, "min")!!.contains(' '))
    }

    @Test fun the_delete_confirm_names_the_goal_and_its_days() {
        assertEquals("Exercise, 4 days a week will be removed from your weekly goals.", plain(WeeklyGoalCopy.deleteBody("Exercise", 4)))
    }
}
