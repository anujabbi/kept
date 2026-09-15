package com.example.kept.core.lock

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The order the restart rungs are tried in (issue #2).
 *
 * The rung that matters is the exact alarm. Expedited work was the whole ladder below the
 * notification, and it is *not* an exemption from the Android 12+ background
 * foreground-service-start restriction — so whenever the direct start failed for that reason, the
 * expedited retry failed for the same reason and the ladder was one rung of theatre followed by a
 * notification. Exact-alarm delivery is an exemption, so it is the rung that can actually work.
 */
class RestartLadderTest {

    @Test fun `a refused direct start goes to expedited work`() {
        assertEquals(
            RestartLadder.Next.EXPEDITED_WORK,
            RestartLadder.after(RestartMethod.DIRECT, canScheduleExactAlarms = true),
        )
        // Expedited work needs no permission, so it is tried either way.
        assertEquals(
            RestartLadder.Next.EXPEDITED_WORK,
            RestartLadder.after(RestartMethod.DIRECT, canScheduleExactAlarms = false),
        )
    }

    @Test fun `a refused expedited start goes to the exact alarm when one may be scheduled`() {
        assertEquals(
            RestartLadder.Next.EXACT_ALARM,
            RestartLadder.after(RestartMethod.EXPEDITED_WORK, canScheduleExactAlarms = true),
        )
    }

    @Test fun `without SCHEDULE_EXACT_ALARM the ladder skips straight to the notification`() {
        // Nothing is gained by arming an alarm the platform will not deliver; the honest answer is
        // to tell the user the lock is off.
        assertEquals(
            RestartLadder.Next.LOCK_OFF_NOTIFICATION,
            RestartLadder.after(RestartMethod.EXPEDITED_WORK, canScheduleExactAlarms = false),
        )
    }

    @Test fun `the exact alarm is the last rung`() {
        assertEquals(
            RestartLadder.Next.LOCK_OFF_NOTIFICATION,
            RestartLadder.after(RestartMethod.EXACT_ALARM, canScheduleExactAlarms = true),
        )
    }

    @Test fun `the ladder terminates from every rung`() {
        // No method may loop back to one already tried, or a refused start would spin forever.
        RestartMethod.entries.forEach { method ->
            listOf(true, false).forEach { canExact ->
                var next = RestartLadder.after(method, canExact)
                var hops = 0
                while (next != RestartLadder.Next.LOCK_OFF_NOTIFICATION && hops < 10) {
                    next = RestartLadder.after(
                        when (next) {
                            RestartLadder.Next.EXPEDITED_WORK -> RestartMethod.EXPEDITED_WORK
                            RestartLadder.Next.EXACT_ALARM -> RestartMethod.EXACT_ALARM
                            RestartLadder.Next.LOCK_OFF_NOTIFICATION -> return@forEach
                        },
                        canExact,
                    )
                    hops++
                }
                assertEquals("$method / canExact=$canExact", RestartLadder.Next.LOCK_OFF_NOTIFICATION, next)
            }
        }
    }

    @Test fun `every method has a distinct analytics value`() {
        val values = RestartMethod.entries.map { it.eventValue }
        assertEquals(listOf("direct", "expedited_work", "exact_alarm"), values)
        assertEquals(values.size, values.distinct().size)
    }
}
