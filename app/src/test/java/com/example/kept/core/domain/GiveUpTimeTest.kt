package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a day's promise expires (issue #7). A lock window that wraps midnight (22:00 -> 06:00) is
 * supported by [LockPolicy.isWindowActive], so the give-up time has to follow it over midnight
 * instead of landing at 06:00 on the morning the day started.
 */
class GiveUpTimeTest {
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val date: LocalDate = LocalDate.of(2026, 9, 5)

    private fun millisAt(d: LocalDate, minuteOfDay: Int): Long =
        d.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone).toInstant().toEpochMilli()

    // A normal window: 07:00 -> 21:00.
    private val from = 7 * 60
    private val due = 21 * 60

    // A wrapping window: 22:00 -> 06:00.
    private val wrapFrom = 22 * 60
    private val wrapDue = 6 * 60

    @Test fun `a normal window expires the same evening`() {
        assertFalse(GiveUpTime.wraps(from, due))
        assertEquals(date, GiveUpTime.dateOf(date, from, due))
        assertEquals(millisAt(date, due), GiveUpTime.instantFor(date, from, due, zone).toEpochMilli())
    }

    @Test fun `a wrapping window expires the next morning`() {
        assertTrue(GiveUpTime.wraps(wrapFrom, wrapDue))
        assertEquals(date.plusDays(1), GiveUpTime.dateOf(date, wrapFrom, wrapDue))
        assertEquals(millisAt(date.plusDays(1), wrapDue), GiveUpTime.instantFor(date, wrapFrom, wrapDue, zone).toEpochMilli())
    }

    @Test fun `a tick at 23-30 counts on a wrapping window but not on a normal one`() {
        val tick = millisAt(date, 23 * 60 + 30)
        assertTrue(tick < GiveUpTime.instantFor(date, wrapFrom, wrapDue, zone).toEpochMilli())
        assertFalse(tick < GiveUpTime.instantFor(date, from, due, zone).toEpochMilli())
    }

    @Test fun `a tick after the wrapping window closes does not count`() {
        val tick = millisAt(date.plusDays(1), 6 * 60 + 1)
        assertFalse(tick < GiveUpTime.instantFor(date, wrapFrom, wrapDue, zone).toEpochMilli())
    }

    @Test fun `a wrapping window is never past due during its own calendar day`() {
        // The old rule (minuteOfDay >= dueMinute) made 06:01 onwards permanently "too late".
        assertFalse(GiveUpTime.isPastDue(6 * 60 + 1, wrapFrom, wrapDue))
        assertFalse(GiveUpTime.isPastDue(23 * 60 + 30, wrapFrom, wrapDue))
    }

    @Test fun `a normal window is past due from the give-up minute onwards`() {
        assertFalse(GiveUpTime.isPastDue(due - 1, from, due))
        assertTrue(GiveUpTime.isPastDue(due, from, due))
        assertTrue(GiveUpTime.isPastDue(due + 1, from, due))
    }
}
