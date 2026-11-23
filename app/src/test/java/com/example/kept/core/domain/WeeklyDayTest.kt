package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Which weekly goals made a past day a must-do day, and whether their sessions were logged in
 * time (issue #165, story #176). The week is 22 to 28 November 2026, Sunday to Saturday.
 */
class WeeklyDayTest {
    private val sunday = LocalDate.of(2026, 11, 22)
    private val wednesday = sunday.plusDays(3)
    private val longAgo = LocalDate.of(2026, 11, 1)

    /** 9:00 pm that day, as a plain number: the tests only compare it with session times. */
    private fun due(date: LocalDate): Long = date.toEpochDay() * 24 * 60 + 21 * 60
    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long = date.toEpochDay() * 24 * 60 + hour * 60 + minute

    private fun goal(title: String, days: Int, from: LocalDate = longAgo, vararg sessions: Pair<LocalDate, Long>) =
        WeeklyDay.Goal(title, days, from, sessions.toMap())

    private fun outcome(date: LocalDate, vararg goals: WeeklyDay.Goal) = WeeklyDay.outcome(goals.toList(), date, due(date))

    @Test fun `example A, Wednesday with nothing logged is due and missed`() {
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(wednesday, goal("Exercise", 4)))
        // Tuesday could still be skipped: 5 days left, 4 needed.
        assertEquals(WeeklyDayOutcome.NONE, outcome(wednesday.minusDays(1), goal("Exercise", 4)))
    }

    @Test fun `a session counts for the day only when logged before the give-up time`() {
        fun logged(at: Long) = outcome(wednesday, goal("Exercise", 4, longAgo, wednesday to at))
        assertEquals(WeeklyDayOutcome(1, 1, emptyList()), logged(at(wednesday, 20, 59)))
        assertEquals("at the give-up minute it is late, as a habit is", WeeklyDayOutcome(1, 0, listOf("Exercise")), logged(at(wednesday, 21)))
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), logged(at(wednesday, 23, 30)))
    }

    @Test fun `a day the goal did not need is not due, logged or not`() {
        // Example B: one session on Sunday, Monday has 6 days left and 3 needed.
        val monday = sunday.plusDays(1)
        assertEquals(WeeklyDayOutcome.NONE, outcome(monday, goal("Exercise", 4, longAgo, sunday to at(sunday, 9))))
        // Example D: 4 done by Thursday, so Friday and Saturday are free.
        val four = (0..3).map { sunday.plusDays(it.toLong()).let { d -> d to at(d, 9) } }.toTypedArray()
        assertEquals(WeeklyDayOutcome.NONE, outcome(sunday.plusDays(5), goal("Exercise", 4, longAgo, *four)))
        assertEquals(WeeklyDayOutcome.NONE, outcome(sunday.plusDays(6), goal("Exercise", 4, longAgo, *four)))
    }

    @Test fun `the day is judged by the sessions logged up to it, not by later ones`() {
        // Wednesday was skipped; the sessions on Thursday and Friday do not make it any less due.
        val thursday = wednesday.plusDays(1)
        val g = goal("Exercise", 4, longAgo, thursday to at(thursday, 9), thursday.plusDays(1) to at(thursday.plusDays(1), 9))
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(wednesday, g))
    }

    @Test fun `a late session still counts toward the week on the days after`() {
        // Wednesday's session came after the give-up time: Wednesday is missed, but Thursday owes 3 in 3 days, not 4.
        val late = goal("Exercise", 4, longAgo, wednesday to at(wednesday, 22))
        val thursday = wednesday.plusDays(1)
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(wednesday, late))
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(thursday, late))
        // With two more in time on Thursday and Friday, Saturday still owes the fourth.
        val friday = thursday.plusDays(1)
        val three = goal("Exercise", 4, longAgo, wednesday to at(wednesday, 22), thursday to at(thursday, 9), friday to at(friday, 9))
        assertEquals(WeeklyDayOutcome(1, 1, emptyList()), outcome(friday, three))
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(friday.plusDays(1), three))
    }

    @Test fun `the day a goal is set and the days before it never count`() {
        // Set on Wednesday at 7 a week: Wednesday itself is free, and so is every day before it.
        val set = goal("Exercise", 7, wednesday)
        assertEquals(WeeklyDayOutcome.NONE, outcome(wednesday, set))
        assertEquals(WeeklyDayOutcome.NONE, outcome(wednesday.minusDays(1), set))
        assertEquals(WeeklyDayOutcome.NONE, outcome(sunday, set))
        assertEquals("the week before it existed", WeeklyDayOutcome.NONE, outcome(sunday.minusDays(1), set))
        assertEquals("Thursday, its first counted day", WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(wednesday.plusDays(1), set))
    }

    @Test fun `the first partial week uses the reduced target`() {
        // Set on Wednesday at 4 a week: the first week asks for 3, Thursday to Saturday.
        val thursday = wednesday.plusDays(1)
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(thursday, goal("Exercise", 4, wednesday)))
        // A session on the day it was set counts, so Thursday needs 2 of 3 days and is free.
        assertEquals(WeeklyDayOutcome.NONE, outcome(thursday, goal("Exercise", 4, wednesday, wednesday to at(wednesday, 18))))
        // Set on Saturday: nothing is asked that week; the full target starts on Sunday.
        val saturday = sunday.plusDays(6)
        assertEquals(WeeklyDayOutcome.NONE, outcome(saturday, goal("Exercise", 7, saturday)))
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Exercise")), outcome(saturday.plusDays(1), goal("Exercise", 7, saturday)))
    }

    @Test fun `each day is judged in its own Sunday to Saturday week`() {
        val saturday = sunday.plusDays(6)
        val nextSunday = sunday.plusDays(7)
        // One a week, nothing logged: Saturday is the last chance; the next Sunday starts again with 7 days.
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Read")), outcome(saturday, goal("Read", 1)))
        assertEquals(WeeklyDayOutcome.NONE, outcome(nextSunday, goal("Read", 1)))
        // Last week's sessions do not count for the new week: 7 a week is due again on Sunday.
        val lastWeek = (0..6).map { sunday.plusDays(it.toLong()).let { d -> d to at(d, 9) } }.toTypedArray()
        assertEquals(WeeklyDayOutcome(1, 0, listOf("Stretch")), outcome(nextSunday, goal("Stretch", 7, longAgo, *lastWeek)))
    }

    @Test fun `two goals due on one day are counted apart and named in their order`() {
        val exercise = goal("Exercise", 4, longAgo, wednesday to at(wednesday, 9))
        val read = goal("Read", 4)
        val onTrack = goal("Study", 2)
        assertEquals(WeeklyDayOutcome(2, 1, listOf("Read")), outcome(wednesday, exercise, read, onTrack))
        assertEquals(WeeklyDayOutcome(2, 0, listOf("Read", "Exercise")), outcome(wednesday, read, goal("Exercise", 4)))
        assertEquals(WeeklyDayOutcome.NONE, outcome(wednesday))
    }

    @Test fun `the words for a missed session`() {
        assertEquals("Exercise wasn't logged in time.", WeeklyMissedCopy.sentence(listOf("Exercise")))
        assertEquals("Exercise and Read weren't logged in time.", WeeklyMissedCopy.sentence(listOf("Exercise", "Read")))
        assertEquals("Exercise, Read and Study weren't logged in time.", WeeklyMissedCopy.sentence(listOf("Exercise", "Read", "Study")))
        assertEquals("Weekly goal missed: Exercise", WeeklyMissedCopy.historyLine(listOf("Exercise")))
        assertEquals("Weekly goals missed: Exercise, Read", WeeklyMissedCopy.historyLine(listOf("Exercise", "Read")))
        // Stored one per line and read back; an old row's empty column names nothing.
        assertEquals(listOf("Exercise", "Read Dune"), WeeklyMissedCopy.read(WeeklyMissedCopy.store(listOf("Exercise", "Read Dune"))))
        assertEquals(emptyList<String>(), WeeklyMissedCopy.read(""))
    }
}
