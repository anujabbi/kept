package com.example.kept.core.domain

import com.example.kept.core.domain.WeeklyGoalRules.DayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Home's weekly card (issue #165, Home; story #174): what it says for a goal's week. */
class WeeklyGoalCardTest {

    // The week of Sunday 22 to Saturday 28 November 2026.
    private val sun = LocalDate.of(2026, 11, 22)
    private fun day(n: Int) = sun.plusDays(n.toLong())
    private val longAgo = LocalDate.of(2026, 11, 1)

    private fun card(target: Int, today: LocalDate, vararg sessions: LocalDate, from: LocalDate = longAgo, activity: String = "Exercise", goalTarget: String? = null) =
        WeeklyCard.of(activity, WeeklyGoalRules.week(WeeklyGoalRules.Goal(target, from), sessions.toSet(), today), goalTarget = goalTarget)

    private fun WeeklyCard.plainTitle() = title.replace(WeeklyCardCopy.NBSP, ' ')
    private fun WeeklyCard.states() = days.map { it.state }

    @Test fun `example B, one session on Sunday and the clock on Monday`() {
        val c = card(4, day(1), day(0))
        assertEquals("Exercise · 1 of 4 this week", c.plainTitle())
        assertEquals(
            listOf(DayState.DONE, DayState.TODAY, DayState.COMING_UP, DayState.COMING_UP, DayState.COMING_UP, DayState.COMING_UP, DayState.COMING_UP),
            c.states(),
        )
        assertEquals(WeeklyCardStatus.ON_TRACK, c.status)
        assertEquals("On track, no lock", c.statusLine)
        assertEquals(4, c.segments)
        assertEquals(1, c.filled)
        assertFalse(c.doneToday)
    }

    @Test fun `example A, nothing logged and the clock on Wednesday`() {
        val c = card(4, day(3))
        assertEquals("Exercise · 0 of 4 this week", c.plainTitle())
        assertEquals(
            listOf(DayState.SKIPPED, DayState.SKIPPED, DayState.SKIPPED, DayState.TODAY, DayState.COMING_UP, DayState.COMING_UP, DayState.COMING_UP),
            c.states(),
        )
        assertEquals(WeeklyCardStatus.TODAY_COUNTS, c.status)
        assertEquals("Today counts: apps lock until it's done", c.statusLine)
        assertEquals(0, c.filled)
    }

    @Test fun `marking today done moves the count and today's cell, and undo moves them back`() {
        val after = card(4, day(1), day(0), day(1))
        assertEquals("Exercise · 2 of 4 this week", after.plainTitle())
        assertEquals(DayState.DONE, after.days[1].state)
        assertTrue(after.days[1].isToday)
        assertTrue(after.doneToday)
        assertEquals(2, after.filled)
        // Undo takes today's session away again: the count, today's cell and the bar go back.
        val undone = card(4, day(1), day(0))
        assertEquals("Exercise · 1 of 4 this week", undone.plainTitle())
        assertEquals(DayState.TODAY, undone.days[1].state)
        assertFalse(undone.doneToday)
        assertEquals(1, undone.filled)
    }

    @Test fun `a must-do day stops counting once today's session is logged`() {
        val c = card(4, day(3), day(3))
        assertEquals("Exercise · 1 of 4 this week", c.plainTitle())
        assertEquals(WeeklyCardStatus.ON_TRACK, c.status)
    }

    @Test fun `the next Sunday reads 0 of 4 with an empty strip`() {
        val c = card(4, day(7), day(0), day(2), day(3), day(5))
        assertEquals("Exercise · 0 of 4 this week", c.plainTitle())
        assertEquals(listOf(DayState.TODAY) + List(6) { DayState.COMING_UP }, c.states())
        assertEquals(0, c.filled)
        assertEquals(WeeklyCardStatus.ON_TRACK, c.status)
    }

    @Test fun `a complete week is on track and every segment is filled`() {
        val c = card(4, day(4), day(0), day(1), day(3), day(4))
        assertEquals("Exercise · 4 of 4 this week", c.plainTitle())
        assertEquals(4, c.segments)
        assertEquals(4, c.filled)
        assertEquals(WeeklyCardStatus.ON_TRACK, c.status)
        assertTrue(c.doneToday)
    }

    @Test fun `more sessions than the target fill no more segments than there are`() {
        val c = card(2, day(3), day(0), day(1), day(3))
        assertEquals("Exercise · 3 of 2 this week", c.plainTitle())
        assertEquals(2, c.segments)
        assertEquals(2, c.filled)
    }

    @Test fun `the first partial week shows the reduced target and the days before as not counted`() {
        // Set on Wednesday with 4 days a week: 3 this week (Thursday to Saturday), no lock today.
        val wed = card(4, day(3), from = day(3))
        assertEquals("Exercise · 0 of 3 this week", wed.plainTitle())
        assertEquals(3, wed.segments)
        assertEquals(
            listOf(DayState.NOT_COUNTED, DayState.NOT_COUNTED, DayState.NOT_COUNTED, DayState.TODAY, DayState.COMING_UP, DayState.COMING_UP, DayState.COMING_UP),
            wed.states(),
        )
        assertEquals(WeeklyCardStatus.ON_TRACK, wed.status)
        // Thursday, nothing logged: the creation day is not a skip, and today counts.
        val thu = card(4, day(4), from = day(3))
        assertEquals(DayState.NOT_COUNTED, thu.days[3].state)
        assertEquals(WeeklyCardStatus.TODAY_COUNTS, thu.status)
    }

    @Test fun `a goal set on Saturday asks for nothing that week`() {
        val c = card(4, day(6), from = day(6))
        assertEquals("Exercise · 0 of 0 this week", c.plainTitle())
        assertEquals(0, c.segments)
        assertEquals(WeeklyCardStatus.ON_TRACK, c.status)
    }

    @Test fun `a lost week still says today counts, and after today's session it does not claim on track`() {
        // Target 4, nothing until Friday: 2 days left, 4 needed.
        val fri = card(4, day(5))
        assertEquals(WeeklyCardStatus.TODAY_COUNTS, fri.status)
        val done = card(4, day(5), day(5))
        assertEquals(WeeklyCardStatus.DONE_TODAY, done.status)
        assertEquals("Done for today, no lock", done.statusLine)
    }

    @Test fun `target 7 counts every day until it is logged`() {
        assertEquals(WeeklyCardStatus.TODAY_COUNTS, card(7, day(0)).status)
        assertEquals(WeeklyCardStatus.ON_TRACK, card(7, day(0), day(0)).status)
    }

    @Test fun `the strip is labelled Su to Sa`() {
        assertEquals(listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"), card(4, day(1)).days.map { it.label })
    }

    @Test fun `every day's state has a spoken description`() {
        val c = card(4, day(3), day(1), from = day(0))
        assertEquals(
            listOf(
                "Sunday, not counted",
                "Monday, done",
                "Tuesday, skipped",
                "Wednesday, today, not done yet",
                "Thursday, coming up",
                "Friday, coming up",
                "Saturday, coming up",
            ),
            c.days.map { it.spoken },
        )
        assertEquals("Wednesday, today, done", card(4, day(3), day(3)).days[3].spoken)
    }

    @Test fun `the count never wraps apart and a long activity is kept whole`() {
        val c = card(4, day(1), day(0), activity = "Practice the double bass now")
        assertEquals("Practice the double bass now · 1${WeeklyCardCopy.NBSP}of${WeeklyCardCopy.NBSP}4 this week", c.title)
    }

    @Test fun `the today row and the section use the issue's and the mockup's words`() {
        assertEquals("This week", WeeklyCardCopy.SECTION)
        assertEquals("Sun to Sat", WeeklyCardCopy.SPAN)
        assertEquals("Today", WeeklyCardCopy.TODAY_ROW)
        assertEquals("Add a weekly goal", WeeklyCardCopy.ADD)
        assertEquals("For things you don't need to do every day.", WeeklyCardCopy.ADD_LINE)
    }

    @Test fun `the today row carries the goal's target, and reads Today alone without one`() {
        assertEquals("Today · 30 min", card(4, day(3), goalTarget = "30${WeeklyCardCopy.NBSP}min").todayDetail.replace(WeeklyCardCopy.NBSP, ' '))
        assertEquals("a migrated goal reads as before", "Today", card(4, day(3)).todayDetail)
        assertEquals("the title keeps its cadence", "Exercise · 0 of 4 this week", card(4, day(3), goalTarget = "30 min").title.replace(WeeklyCardCopy.NBSP, ' '))
        assertEquals("Today", WeeklyCardCopy.todayRow(null))
    }
}
