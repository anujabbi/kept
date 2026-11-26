package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Your promises' rules that no screen test reaches cheaply (#236): the order when two kinds share
 * a place, the count's wording at zero, and switching across a week's edge or onto a must-do day.
 * The screen and the switches themselves are in `PromisesBehaviourTest` and `PromiseSwitchTest`.
 */
class PromiseListTest {

    private val wed = LocalDate.parse("2026-11-25")
    private val sun = LocalDate.parse("2026-11-22")

    @Test fun `a daily habit and a weekly goal at the same place read habit first, then oldest`() {
        fun item(id: Long, days: Int?, order: Int) = PromiseItem(id, days, "x$id", "star", "", order)
        val list = PromiseList.ordered(listOf(item(1, 3, 0), item(2, null, 1), item(1, null, 0), item(3, null, 1)))
        assertEquals(listOf("habit_1", "goal_1", "habit_2", "habit_3"), list.map { it.key })
    }

    @Test fun `the count leaves out a kind there is none of`() {
        assertEquals("1 daily habit", PromiseListCopy.count(1, 0))
        assertEquals("4 daily habits · 1 weekly goal", PromiseListCopy.count(4, 1))
        assertEquals("2 weekly goals", PromiseListCopy.count(0, 2))
    }

    @Test fun `add offers a row while either side has room`() {
        assertTrue(PromiseList.canAdd(10, 2))
        assertTrue(PromiseList.canAdd(9, 3))
        assertFalse(PromiseList.canAdd(10, 3))
    }

    @Test fun `only this week's done days carry over, and a switch on Sunday starts the week empty`() {
        val done = listOf("2026-11-20", "2026-11-21", "2026-11-22").map(LocalDate::parse)
        assertEquals(setOf(sun), PromiseSwitch.carried(done, sun))
        assertEquals("Saturday's tick is last week's", 0, PromiseSwitch.weekAfter(3, LocalDate.parse("2026-11-01"), done.take(2), sun).done)
    }

    @Test fun `the goal counts from the habit's own start, so its first week is not cut short`() {
        assertEquals(LocalDate.parse("2026-11-01"), PromiseSwitch.goalStart(LocalDate.parse("2026-11-01"), wed))
        assertEquals("a habit set today starts today", wed, PromiseSwitch.goalStart(wed, wed))
        val monday = LocalDate.parse("2026-11-23")
        assertEquals("set Monday, switched Wednesday to 4: the whole 4", 4, PromiseSwitch.weekAfter(4, monday, emptyList(), wed).target)
        assertEquals("set today: the rest of the week after today", 3, PromiseSwitch.weekAfter(4, wed, emptyList(), wed).target)
    }

    @Test fun `a switch opens the lock only when the habit held it and the goal will not`() {
        val start = LocalDate.parse("2026-11-01")
        // Wednesday, nothing done: 3 a week fits Thursday to Saturday, so today is free.
        assertTrue(PromiseSwitch.opensLock(false, PromiseSwitch.weekAfter(3, start, emptyList(), wed)))
        // 4 a week with nothing done needs every day left: today is a must-do day, the lock holds.
        assertFalse(PromiseSwitch.opensLock(false, PromiseSwitch.weekAfter(4, start, emptyList(), wed)))
        // Done today: the habit was not holding the lock.
        assertFalse(PromiseSwitch.opensLock(true, PromiseSwitch.weekAfter(4, start, listOf(wed), wed)))
    }
}
