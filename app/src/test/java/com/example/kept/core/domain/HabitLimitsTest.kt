package com.example.kept.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The habit cap was four, hard-coded in three screens (issue #38). It is ten now and lives here,
 * so onboarding and Settings → Habits cannot drift apart again.
 */
class HabitLimitsTest {

    @Test fun `can remove only while more than one is left`() {
        assertTrue(HabitLimits.canRemove(2))
        assertFalse(HabitLimits.canRemove(1))
        assertFalse(HabitLimits.canRemove(0))
    }
}
