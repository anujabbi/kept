package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The watcher service's permission-off gap, tick by tick (issue #164, PR #170 review).
 *
 * A gap is timed only while the daily habit lock should be holding with a lock permission off.
 * Night lockdown keeps the lock "active" past the daily lock's end, and must neither stretch a
 * gap that began under the daily lock nor start one of its own.
 */
class PermissionGapTest {

    private val min = 60_000L

    /** Runs ticks in order and returns the gaps recorded plus the gap still open at the end. */
    private fun run(vararg ticks: Triple<Long, Boolean, Boolean>): Pair<List<PermissionGap.Closed>, Long?> {
        var open: Long? = null
        val closed = mutableListOf<PermissionGap.Closed>()
        for ((now, daily, granted) in ticks) {
            val step = PermissionGap.step(open, dailyLockActive = daily, permissionsGranted = granted, now = now)
            step.closed?.let { closed += it }
            open = step.openSince
        }
        return closed to open
    }

    private fun tick(minute: Long, daily: Boolean, granted: Boolean) = Triple(minute * min, daily, granted)

    @Test fun `a gap opens when a permission goes off under the daily lock and closes when it returns`() {
        val (closed, open) = run(
            tick(0, daily = true, granted = true),
            tick(1, daily = true, granted = false),
            tick(2, daily = true, granted = false),
            tick(5, daily = true, granted = true),
        )
        assertEquals(listOf(PermissionGap.Closed(1 * min, 5 * min)), closed)
        assertNull(open)
    }

    @Test fun `a gap that began under the daily lock closes when the daily lock ends, though night holds and the permission is still off`() {
        // Daily window ends at minute 10 (23:00); night lockdown holds from there; usage access
        // went off at minute 0 (22:50) and comes back at minute 460 (06:30).
        val (closed, open) = run(
            tick(0, daily = true, granted = false),
            tick(9, daily = true, granted = false),
            tick(10, daily = false, granted = false),
            tick(11, daily = false, granted = false),
            tick(300, daily = false, granted = false),
            tick(460, daily = false, granted = true),
            tick(461, daily = false, granted = true),
        )
        assertEquals("one gap, closed at the daily lock's end", listOf(PermissionGap.Closed(0, 10 * min)), closed)
        assertNull(open)
    }

    @Test fun `nothing is recorded for a permission that is off only while night lockdown holds`() {
        val (closed, open) = run(
            tick(0, daily = false, granted = true),
            tick(1, daily = false, granted = false),
            tick(200, daily = false, granted = false),
            tick(201, daily = false, granted = true),
        )
        assertEquals(emptyList<PermissionGap.Closed>(), closed)
        assertNull(open)
    }

    @Test fun `a fresh gap starts when the daily lock comes back with the permission still off`() {
        val (closed, open) = run(
            tick(0, daily = true, granted = false),
            tick(10, daily = false, granted = false),
            tick(100, daily = false, granted = false),
            tick(600, daily = true, granted = false),
            tick(601, daily = true, granted = false),
            tick(630, daily = true, granted = true),
        )
        assertEquals(
            listOf(PermissionGap.Closed(0, 10 * min), PermissionGap.Closed(600 * min, 630 * min)),
            closed,
        )
        assertNull(open)
    }
}
