package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The habit cap was four, hard-coded in three screens (issue #38). It is ten now and lives here,
 * so onboarding and Settings → Habits cannot drift apart again.
 */
class HabitLimitsTest {
    @Test fun `the cap is ten and the floor is one`() {
        assertEquals(10, HabitLimits.MAX_HABITS)
        assertEquals(1, HabitLimits.MIN_HABITS)
    }

    @Test fun `can add until the tenth habit, not past it`() {
        assertTrue(HabitLimits.canAdd(0))
        assertTrue(HabitLimits.canAdd(4))
        assertTrue(HabitLimits.canAdd(9))
        assertFalse(HabitLimits.canAdd(10))
        assertFalse(HabitLimits.canAdd(11))
    }

    @Test fun `can remove only while more than one is left`() {
        assertTrue(HabitLimits.canRemove(2))
        assertFalse(HabitLimits.canRemove(1))
        assertFalse(HabitLimits.canRemove(0))
    }
}
