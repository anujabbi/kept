package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The clamp the watchdog probes from (issue #2). Everything the watchdog concludes about a day
 * rests on this instant, so the wrapping-window case is the one that matters: get it wrong and a
 * phone used last night marks this morning unprotected.
 */
class LockWindowStartTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val sevenAm = 7 * 60
    private val tenPm = 22 * 60

    private fun startFor(nowLocal: String, lockFromMinute: Int) =
        LockWindowStart.instantFor(LocalDateTime.parse(nowLocal), lockFromMinute, zone)
            .atZone(zone)
            .toLocalDateTime()

    @Test fun `a normal window starts this morning`() {
        assertEquals(
            LocalDateTime.parse("2026-09-13T07:00"),
            startFor("2026-09-13T09:30", sevenAm),
        )
    }

    @Test fun `a wrapping window asked after midnight started yesterday`() {
        // 22:00 -> 06:00, asked at 02:00. Today's 22:00 has not happened yet, so the window the
        // phone is inside began last night.
        assertEquals(
            LocalDateTime.parse("2026-09-12T22:00"),
            startFor("2026-09-13T02:00", tenPm),
        )
    }

    @Test fun `a wrapping window asked in the evening started tonight`() {
        assertEquals(
            LocalDateTime.parse("2026-09-13T23:10"),
            startFor("2026-09-13T23:30", 23 * 60 + 10),
        )
    }

    @Test fun `the boundary minute belongs to the window it opens`() {
        assertEquals(
            LocalDateTime.parse("2026-09-13T07:00"),
            startFor("2026-09-13T07:00", sevenAm),
        )
    }

    @Test fun `asked before a normal window opens, the answer is yesterday's window`() {
        // Not a case the watchdog probes — it only asks while the lock should be active — but the
        // answer must still be a real past instant rather than one in the future, or the clamp
        // would exclude every event there is.
        assertEquals(
            LocalDateTime.parse("2026-09-12T07:00"),
            startFor("2026-09-13T06:00", sevenAm),
        )
    }
}
