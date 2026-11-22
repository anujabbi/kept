package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/**
 * The watchdog during night lockdown (issue #164, story #168). `WatchdogWorker` hands [GapRules] the
 * combined lock (does the service need to be running?) and the daily habit lock (can a dead
 * service be a protection gap?), both from [LockPolicy]. Its two actions follow the verdict:
 * `serviceLooksDead` requests the restart, `isGap` records the gap against today.
 */
class WatchdogNightTest {

    private val now = 1_700_000_000_000L
    private fun ago(millis: Long) = now - millis

    /** 23:00 to 07:00 night lockdown beside the default 7:00 am to 9:00 pm daily window. */
    private fun snapshot(habitsIncomplete: Boolean) = LockPolicy.Snapshot(
        lockFromMinute = 7 * 60, dueMinute = 21 * 60, habitsIncomplete = habitsIncomplete, breakActiveUntil = null,
        hardAllowlist = emptySet(), userExceptions = emptySet(), launchable = null,
        nightEnabled = true, nightFromMinute = 23 * 60, nightUntilMinute = 7 * 60,
    )

    /** What the watchdog builds: a heartbeat 30 minutes stale, with the usage evidence given. */
    private fun input(at: LocalTime, s: LockPolicy.Snapshot, inUseAt: List<Long>, lockedAppResumed: Boolean) = GapRules.Input(
        lockShouldBeActive = LockPolicy.isLockActive(Instant.ofEpochMilli(now), at, s),
        lockPermissionsGranted = true,
        heartbeatMillis = ago(30 * 60_000),
        nowMillis = now,
        everHeartbeat = true,
        screenInUseAtMillis = inUseAt,
        lockedAppResumedDuringWindow = lockedAppResumed,
        dailyLockActive = LockPolicy.isDailyLockActive(Instant.ofEpochMilli(now), at, s),
    )

    @Test fun `a stale heartbeat with phone use inside the night window only records no gap and still restarts the service`() {
        // 2 am, habits unfinished but the daily window closed at 9 pm: only night lockdown holds.
        val i = input(LocalTime.of(2, 0), snapshot(habitsIncomplete = true), inUseAt = listOf(ago(20 * 60_000)), lockedAppResumed = true)
        assertTrue("the service is needed", i.lockShouldBeActive)
        assertFalse(i.dailyLockActive)
        val v = GapRules.evaluate(i)
        assertEquals(GapRules.Verdict.NIGHT_ONLY_NOT_A_GAP, v)
        assertFalse("no protection gap is recorded", v.isGap)
        assertTrue("the restart is still requested", v.serviceLooksDead)
    }

    @Test fun `a stale heartbeat while the daily lock is active still records the gap`() {
        val i = input(LocalTime.of(12, 0), snapshot(habitsIncomplete = true), inUseAt = listOf(ago(20 * 60_000)), lockedAppResumed = false)
        assertTrue(i.dailyLockActive)
        val v = GapRules.evaluate(i)
        assertEquals(GapRules.Verdict.GAP_SCREEN_ON, v)
        assertTrue(v.isGap)
        assertTrue(v.serviceLooksDead)
        assertEquals(GapRules.Verdict.GAP_LOCKED_APP_USED, GapRules.evaluate(i.copy(lockedAppResumedDuringWindow = true)))
    }

    @Test fun `while both locks hold the daily lock still decides the gap`() {
        // A daily window that wraps midnight and overlaps the night window, habits undone.
        val s = snapshot(habitsIncomplete = true).copy(lockFromMinute = 20 * 60, dueMinute = 8 * 60)
        val v = GapRules.evaluate(input(LocalTime.of(2, 0), s, inUseAt = listOf(ago(20 * 60_000)), lockedAppResumed = false))
        assertTrue(v.isGap)
    }
}
