package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapRulesTest {

    private val now = 1_700_000_000_000L
    private fun ago(millis: Long) = now - millis

    private fun input(
        lockShouldBeActive: Boolean = true,
        lockPermissionsGranted: Boolean = true,
        heartbeatMillis: Long = ago(30 * 60_000),
        everHeartbeat: Boolean = true,
        screenInUseAt: List<Long> = emptyList(),
        lockedAppResumed: Boolean = false,
        evidenceFrom: Long = heartbeatMillis,
    ) = GapRules.Input(
        lockShouldBeActive = lockShouldBeActive,
        lockPermissionsGranted = lockPermissionsGranted,
        heartbeatMillis = heartbeatMillis,
        nowMillis = now,
        everHeartbeat = everHeartbeat,
        screenInUseAtMillis = screenInUseAt,
        lockedAppResumedDuringWindow = lockedAppResumed,
        evidenceFromMillis = evidenceFrom,
    )

    @Test fun `a fresh heartbeat means the service is alive`() {
        val v = GapRules.evaluate(input(heartbeatMillis = ago(45_000)))
        assertEquals(GapRules.Verdict.SERVICE_ALIVE, v)
        assertFalse(v.isGap)
    }

    @Test fun `a stale heartbeat with the screen off all along is Doze, not a gap`() {
        // The whole point of issue #2: the poll loop sleeps in deep Doze, so the heartbeat goes
        // stale while the phone sits on a desk. The user did nothing wrong.
        val v = GapRules.evaluate(input(heartbeatMillis = ago(3 * 60 * 60_000)))
        assertEquals(GapRules.Verdict.DOZE_NOT_A_GAP, v)
        assertFalse(v.isGap)
    }

    @Test fun `the phone being in use earlier in the window is a gap`() {
        val v = GapRules.evaluate(input(screenInUseAt = listOf(ago(20 * 60_000))))
        assertEquals(GapRules.Verdict.GAP_SCREEN_ON, v)
        assertTrue(v.isGap)
    }

    @Test fun `waking the phone in the last minute is not a gap on its own`() {
        // Doze all night, the user picks the phone up, and that very wake is what let the watchdog
        // run. Counting it would mark the night unprotected for doing nothing.
        val v = GapRules.evaluate(
            input(heartbeatMillis = ago(8 * 60 * 60_000), screenInUseAt = listOf(ago(5_000))),
        )
        assertEquals(GapRules.Verdict.WOKE_AT_END_NOT_A_GAP, v)
        assertFalse(v.isGap)
        assertTrue(v.serviceLooksDead)
    }

    @Test fun `a wake at the tail plus an earlier use is still a gap`() {
        // Both stamps are inside the stale window (the heartbeat is 30 minutes old); the earlier
        // one is what makes this a gap rather than a wake.
        val v = GapRules.evaluate(input(screenInUseAt = listOf(ago(20 * 60_000), ago(2_000))))
        assertEquals(GapRules.Verdict.GAP_SCREEN_ON, v)
    }

    @Test fun `a locked app resumed at the tail is a gap even though the wake is not`() {
        // The usage events are the stronger signal: something the lock covers got in front of the
        // user unprotected, whenever it happened.
        val v = GapRules.evaluate(input(screenInUseAt = listOf(ago(3_000)), lockedAppResumed = true))
        assertEquals(GapRules.Verdict.GAP_LOCKED_APP_USED, v)
        assertTrue(v.isGap)
    }

    @Test fun `nothing is a gap outside a lock window`() {
        val v = GapRules.evaluate(input(lockShouldBeActive = false, screenInUseAt = listOf(ago(600_000)), lockedAppResumed = true))
        assertEquals(GapRules.Verdict.LOCK_NOT_DUE, v)
        assertFalse(v.isGap)
        assertFalse(v.serviceLooksDead)
    }

    @Test fun `a missing lock permission is its own verdict, not a gap and not a restart`() {
        // The service is running and records that gap itself from inside tick; the watchdog must
        // not write a second overlapping one, nor restart a service that never stopped.
        val v = GapRules.evaluate(input(lockPermissionsGranted = false, screenInUseAt = listOf(ago(600_000)), lockedAppResumed = true))
        assertEquals(GapRules.Verdict.PERMISSION_MISSING, v)
        assertFalse(v.isGap)
        assertFalse(v.serviceLooksDead)
    }

    @Test fun `a device that has never heartbeat is not a gap`() {
        // Fresh install, service has not run once yet: there is no window to judge.
        val v = GapRules.evaluate(input(everHeartbeat = false, screenInUseAt = listOf(ago(600_000))))
        assertEquals(GapRules.Verdict.NO_HEARTBEAT_YET, v)
        assertFalse(v.isGap)
    }

    @Test fun `the stale threshold is two minutes and the boundary is not stale`() {
        assertEquals(2 * 60_000L, GapRules.STALE_AFTER_MILLIS)
        // Use inside the stale window and old enough not to be the wake that triggered the check.
        val use = listOf(ago(90_000))
        assertFalse(GapRules.evaluate(input(heartbeatMillis = ago(GapRules.STALE_AFTER_MILLIS), screenInUseAt = use)).isGap)
        assertTrue(GapRules.evaluate(input(heartbeatMillis = ago(GapRules.STALE_AFTER_MILLIS + 1), screenInUseAt = use)).isGap)
    }

    @Test fun `the wake tail is a minute and the boundary counts as earlier use`() {
        assertEquals(60_000L, GapRules.WAKE_TAIL_MILLIS)
        assertTrue(GapRules.evaluate(input(screenInUseAt = listOf(ago(GapRules.WAKE_TAIL_MILLIS)))).isGap)
        assertFalse(GapRules.evaluate(input(screenInUseAt = listOf(ago(GapRules.WAKE_TAIL_MILLIS - 1)))).isGap)
    }

    @Test fun `use from before the window being judged is not evidence`() {
        // The night the watchdog cannot see: last night's window closed at 23:00, the service
        // stopped, the phone was used until 01:00, and this morning's window opened at 07:00. A
        // watchdog run at 07:30 still sees a heartbeat from 23:00 and usage events from 01:00 —
        // which belong to a window that is over, not to today (issue #2).
        val windowStart = ago(30 * 60_000)
        val v = GapRules.evaluate(
            input(
                heartbeatMillis = ago(8 * 60 * 60_000),
                evidenceFrom = windowStart,
                screenInUseAt = listOf(ago(6 * 60 * 60_000)),
            ),
        )
        assertEquals(GapRules.Verdict.DOZE_NOT_A_GAP, v)
        assertFalse(v.isGap)
        // Still worth restarting: the service really is not running.
        assertTrue(v.serviceLooksDead)
    }

    @Test fun `use inside the clamped window is still a gap`() {
        val windowStart = ago(30 * 60_000)
        val v = GapRules.evaluate(
            input(
                heartbeatMillis = ago(8 * 60 * 60_000),
                evidenceFrom = windowStart,
                screenInUseAt = listOf(ago(6 * 60 * 60_000), ago(20 * 60_000)),
            ),
        )
        assertEquals(GapRules.Verdict.GAP_SCREEN_ON, v)
    }

    @Test fun `the clamp never moves the floor earlier than the heartbeat`() {
        // A window that opened before the service last checked in: the heartbeat still wins, so
        // the minutes the lock *was* being enforced cannot be blamed on it.
        val v = GapRules.evaluate(
            input(
                heartbeatMillis = ago(10 * 60_000),
                evidenceFrom = ago(5 * 60 * 60_000),
                screenInUseAt = listOf(ago(3 * 60 * 60_000)),
            ),
        )
        assertEquals(GapRules.Verdict.DOZE_NOT_A_GAP, v)
    }

    @Test fun `an event stamped after now is ignored`() {
        // Probe queries are inclusive at the edges and an OEM can stamp an event a moment late.
        val v = GapRules.evaluate(input(screenInUseAt = listOf(now + 5_000)))
        assertEquals(GapRules.Verdict.DOZE_NOT_A_GAP, v)
    }

    @Test fun `the evidence floor defaults to the heartbeat`() {
        assertEquals(ago(30 * 60_000), input().evidenceFloorMillis)
    }

    @Test fun `every gap verdict carries copy for the record and the notification`() {
        GapRules.Verdict.entries.filter { it.isGap }.forEach { assertTrue(it.reason.isNotBlank()) }
    }

    @Test fun `a stale heartbeat is worth a restart whether or not it is a gap`() {
        assertTrue(GapRules.evaluate(input()).serviceLooksDead)
        assertTrue(GapRules.evaluate(input(screenInUseAt = listOf(ago(600_000)))).serviceLooksDead)
        assertFalse(GapRules.evaluate(input(heartbeatMillis = ago(1_000))).serviceLooksDead)
    }
}
