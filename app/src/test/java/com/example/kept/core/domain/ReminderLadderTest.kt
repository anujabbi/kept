package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The three-step reminder ladder (issue #9): lock-window start, due - 2 h, due - 30 min. The times
 * are computed from the resolved give-up instant, so a window that wraps midnight no longer
 * collapses the middle step onto `lockFrom + 30`.
 */
class ReminderLadderTest {
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val date: LocalDate = LocalDate.of(2026, 9, 14)

    private fun at(d: LocalDate, minuteOfDay: Int): Instant =
        d.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant()

    private fun steps(from: Int, due: Int, on: LocalDate = date) = ReminderLadder.stepsFor(on, from, due, zone)

    @Test fun `a normal window fires all three steps`() {
        val s = steps(7 * 60, 21 * 60)
        assertEquals(listOf(ReminderKind.MORNING, ReminderKind.BEFORE_DUE, ReminderKind.LAST_CALL), s.map { it.kind })
        assertEquals(at(date, 7 * 60), s[0].at)
        assertEquals(at(date, 19 * 60), s[1].at)
        assertEquals(at(date, 20 * 60 + 30), s[2].at)
    }

    @Test fun `minutes before due are the real delta`() {
        val s = steps(7 * 60, 21 * 60)
        assertEquals(14 * 60, s[0].minutesBeforeDue)
        assertEquals(120, s[1].minutesBeforeDue)
        assertEquals(30, s[2].minutesBeforeDue)
    }

    @Test fun `a wrapping window keeps due minus two hours instead of collapsing onto the start`() {
        // 22:00 -> 06:00. The old `(due - 120).coerceAtLeast(lockFrom + 30)` gave 22:30 because it
        // did the arithmetic on minute-of-day; the give-up moment is 06:00 the next morning.
        val s = steps(22 * 60, 6 * 60)
        assertEquals(at(date, 22 * 60), s[0].at)
        assertEquals(at(date.plusDays(1), 4 * 60), s[1].at)
        assertEquals(at(date.plusDays(1), 5 * 60 + 30), s[2].at)
        assertEquals(120, s[1].minutesBeforeDue)
        assertEquals(30, s[2].minutesBeforeDue)
    }

    @Test fun `a short window floors the middle step at the start plus thirty minutes`() {
        // 18:00 -> 19:00: due - 2 h is before the window even opens.
        val s = steps(18 * 60, 19 * 60)
        assertEquals(listOf(ReminderKind.MORNING, ReminderKind.BEFORE_DUE), s.map { it.kind })
        assertEquals(at(date, 18 * 60), s[0].at)
        assertEquals(at(date, 18 * 60 + 30), s[1].at)
        // Copy is phrased from the real delta, not from the "2 hours" the step is named after.
        assertEquals(30, s[1].minutesBeforeDue)
    }

    @Test fun `a window shorter than the floor fires only the morning step`() {
        val s = steps(18 * 60, 18 * 60 + 20)
        assertEquals(listOf(ReminderKind.MORNING), s.map { it.kind })
    }

    @Test fun `every step lands strictly before the give-up moment and in order`() {
        val due = GiveUpTime.instantFor(date, 20 * 60, 23 * 60, zone)
        val s = steps(20 * 60, 23 * 60)
        assertTrue(s.all { it.at.isBefore(due) })
        assertEquals(s.map { it.at }.sorted(), s.map { it.at })
        assertEquals(s.map { it.at }.distinct().size, s.size)
    }

    @Test fun `the next firing is today's when it is still ahead`() {
        val now = at(date, 6 * 60)
        val step = ReminderLadder.nextFiring(ReminderKind.MORNING, now, date, 7 * 60, 21 * 60, zone)
        assertEquals(at(date, 7 * 60), step!!.at)
    }

    @Test fun `a step already past today moves to tomorrow`() {
        val now = at(date, 8 * 60)
        val step = ReminderLadder.nextFiring(ReminderKind.MORNING, now, date, 7 * 60, 21 * 60, zone)
        assertEquals(at(date.plusDays(1), 7 * 60), step!!.at)
    }

    @Test fun `a step the window has no room for is never scheduled`() {
        val now = at(date, 6 * 60)
        assertNull(ReminderLadder.nextFiring(ReminderKind.LAST_CALL, now, date, 18 * 60, 18 * 60 + 20, zone))
    }

    @Test fun `the deadline ahead on a normal window is tonight's`() {
        assertEquals(at(date, 21 * 60), ReminderLadder.deadlineAhead(at(date, 19 * 60), date, 7 * 60, 21 * 60, zone))
    }

    @Test fun `the deadline ahead in the small hours of a wrapping window is this morning's`() {
        assertEquals(at(date, 6 * 60), ReminderLadder.deadlineAhead(at(date, 2 * 60), date, 22 * 60, 6 * 60, zone))
    }

    @Test fun `the deadline ahead after a wrapping window closes is the next morning's`() {
        assertEquals(at(date.plusDays(1), 6 * 60), ReminderLadder.deadlineAhead(at(date, 23 * 60), date, 22 * 60, 6 * 60, zone))
    }

    @Test fun `a wrapping window still has yesterday's last call ahead in the small hours`() {
        // 02:00 on the 14th sits inside the window that opened at 22:00 on the 13th.
        val now = at(date, 2 * 60)
        val step = ReminderLadder.nextFiring(ReminderKind.LAST_CALL, now, date, 22 * 60, 6 * 60, zone)
        assertEquals(at(date, 5 * 60 + 30), step!!.at)
    }
}
