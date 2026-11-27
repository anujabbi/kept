package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The weekly goal list's copy (issue #165, story #173). Goals are set on the promise since #218. */
class WeeklyGoalSetupTest {

    private fun plain(s: String) = s.replace(WeeklyGoalCopy.NBSP, ' ')

    @Test fun the_target_reads_as_a_habit_amount_except_one_time() {
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
