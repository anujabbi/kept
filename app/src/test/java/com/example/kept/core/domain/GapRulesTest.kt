package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapRulesTest {

    private fun input(
        lockShouldBeActive: Boolean = true,
        heartbeatAgeMillis: Long = 30 * 60_000L,
        everHeartbeat: Boolean = true,
        screenInteractive: Boolean = false,
        lockedAppResumed: Boolean = false,
    ) = GapRules.Input(
        lockShouldBeActive = lockShouldBeActive,
        heartbeatAgeMillis = heartbeatAgeMillis,
        everHeartbeat = everHeartbeat,
        screenInteractiveDuringWindow = screenInteractive,
        lockedAppResumedDuringWindow = lockedAppResumed,
    )

    @Test fun `a fresh heartbeat means the service is alive`() {
        val v = GapRules.evaluate(input(heartbeatAgeMillis = 45_000))
        assertEquals(GapRules.Verdict.SERVICE_ALIVE, v)
        assertFalse(v.isGap)
    }

    @Test fun `a stale heartbeat with the screen off all along is Doze, not a gap`() {
        // The whole point of issue #2: the poll loop sleeps in deep Doze, so the heartbeat goes
        // stale while the phone sits on a desk. The user did nothing wrong.
        val v = GapRules.evaluate(input(heartbeatAgeMillis = 3 * 60 * 60_000))
        assertEquals(GapRules.Verdict.DOZE_NOT_A_GAP, v)
        assertFalse(v.isGap)
    }

    @Test fun `a stale heartbeat while the screen was on is a gap`() {
        val v = GapRules.evaluate(input(screenInteractive = true))
        assertEquals(GapRules.Verdict.GAP_SCREEN_ON, v)
        assertTrue(v.isGap)
    }

    @Test fun `a locked app resumed during the stale window is a gap even with the screen flag off`() {
        // Usage events are the stronger signal: something got in front of the user unprotected.
        val v = GapRules.evaluate(input(screenInteractive = false, lockedAppResumed = true))
        assertEquals(GapRules.Verdict.GAP_LOCKED_APP_USED, v)
        assertTrue(v.isGap)
    }

    @Test fun `nothing is a gap outside a lock window`() {
        val v = GapRules.evaluate(input(lockShouldBeActive = false, screenInteractive = true, lockedAppResumed = true))
        assertEquals(GapRules.Verdict.LOCK_NOT_DUE, v)
        assertFalse(v.isGap)
    }

    @Test fun `a device that has never heartbeat is not a gap`() {
        // Fresh install, service has not run once yet: there is no window to judge.
        val v = GapRules.evaluate(input(everHeartbeat = false, screenInteractive = true))
        assertEquals(GapRules.Verdict.NO_HEARTBEAT_YET, v)
        assertFalse(v.isGap)
    }

    @Test fun `the stale threshold is two minutes and the boundary is not stale`() {
        assertEquals(2 * 60_000L, GapRules.STALE_AFTER_MILLIS)
        assertFalse(GapRules.evaluate(input(heartbeatAgeMillis = GapRules.STALE_AFTER_MILLIS, screenInteractive = true)).isGap)
        assertTrue(GapRules.evaluate(input(heartbeatAgeMillis = GapRules.STALE_AFTER_MILLIS + 1, screenInteractive = true)).isGap)
    }

    @Test fun `every gap verdict carries copy for the record and the notification`() {
        GapRules.Verdict.entries.filter { it.isGap }.forEach { assertTrue(it.reason.isNotBlank()) }
    }

    @Test fun `a stale heartbeat is always worth a restart, gap or not`() {
        assertTrue(GapRules.evaluate(input()).serviceLooksDead)
        assertTrue(GapRules.evaluate(input(screenInteractive = true)).serviceLooksDead)
        assertFalse(GapRules.evaluate(input(heartbeatAgeMillis = 1_000)).serviceLooksDead)
        assertFalse(GapRules.evaluate(input(lockShouldBeActive = false)).serviceLooksDead)
    }
}
