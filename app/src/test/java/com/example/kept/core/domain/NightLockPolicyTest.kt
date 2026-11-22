package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/**
 * Night lockdown in [LockPolicy] (issue #164, story #168): a second window, ORed with the daily
 * habit lock. Kept beside `RulesTest`, whose own cases run unchanged with night lockdown off.
 */
class NightLockPolicyTest {
    private val now = Instant.parse("2026-11-21T12:00:00Z")
    private val chrome = "com.android.chrome"

    /** Every habit done, so only night lockdown can hold the phone. */
    private fun night(from: Int, until: Int, enabled: Boolean = true) = LockPolicy.Snapshot(
        lockFromMinute = 7 * 60,
        dueMinute = 21 * 60,
        habitsIncomplete = false,
        breakActiveUntil = null,
        hardAllowlist = Allowlist.build(listOf("com.google.android.dialer")),
        userExceptions = setOf("com.spotify.music"),
        launchable = setOf(chrome, "com.spotify.music", "com.google.android.dialer"),
        nightEnabled = enabled,
        nightFromMinute = from,
        nightUntilMinute = until,
    )

    private fun locked(h: Int, m: Int, s: LockPolicy.Snapshot) = LockPolicy.isLockActive(now, LocalTime.of(h, m), s)

    @Test fun `night lockdown is off unless a snapshot says otherwise`() {
        val s = LockPolicy.Snapshot(
            lockFromMinute = 7 * 60, dueMinute = 21 * 60, habitsIncomplete = false, breakActiveUntil = null,
            hardAllowlist = emptySet(), userExceptions = emptySet(), launchable = null,
        )
        assertFalse(s.nightEnabled)
        assertFalse(LockPolicy.isLockActive(now, LocalTime.of(23, 30), s))
        assertEquals(null, LockPolicy.lockReason(now, LocalTime.of(23, 30), s))
    }

    @Test fun `22 to 7 locks from 22 00 through midnight and the rollover until 7 00`() {
        val s = night(22 * 60, 7 * 60)
        assertFalse(locked(21, 59, s))
        assertTrue(locked(22, 0, s))
        assertTrue(locked(23, 59, s))
        assertTrue(locked(0, 0, s))
        assertTrue(locked(0, 5, s))
        assertTrue(locked(6, 59, s))
        assertFalse(locked(7, 0, s))
    }

    @Test fun `midnight to 7 is unlocked at 23 59 and locked from 0 00 to 6 59`() {
        val s = night(0, 7 * 60)
        assertFalse(locked(23, 59, s))
        assertTrue(locked(0, 0, s))
        assertTrue(locked(6, 59, s))
        assertFalse(locked(7, 0, s))
    }

    @Test fun `essentials and user exceptions stay open at night`() {
        val s = night(23 * 60, 7 * 60)
        assertFalse(LockPolicy.shouldLock("com.google.android.dialer", now, LocalTime.of(23, 30), s))
        assertFalse(LockPolicy.shouldLock("com.android.settings", now, LocalTime.of(23, 30), s))
        assertFalse(LockPolicy.shouldLock(Allowlist.OWN_PACKAGE, now, LocalTime.of(23, 30), s))
        assertFalse(LockPolicy.shouldLock("com.spotify.music", now, LocalTime.of(23, 30), s))
    }

    @Test fun `before onboarding is done the night window locks nothing`() {
        assertFalse(locked(23, 30, night(23 * 60, 7 * 60).copy(onboardingDone = false)))
    }

    @Test fun `night ending does not lift an unfinished daily lock`() {
        // A daily window that wraps midnight (20:00 to 08:00) around a 23:00 to 07:00 night window.
        val s = night(23 * 60, 7 * 60).copy(lockFromMinute = 20 * 60, dueMinute = 8 * 60, habitsIncomplete = true)
        assertEquals(LockReason.NIGHT, LockPolicy.lockReason(now, LocalTime.of(6, 59), s))
        assertEquals(LockReason.DAILY, LockPolicy.lockReason(now, LocalTime.of(7, 0), s))
        assertTrue(locked(7, 0, s))
    }

    @Test fun `night wins when both locks hold`() {
        val s = night(23 * 60, 7 * 60).copy(lockFromMinute = 20 * 60, dueMinute = 8 * 60, habitsIncomplete = true)
        assertTrue(LockPolicy.isDailyLockActive(now, LocalTime.of(23, 30), s))
        assertEquals(LockReason.NIGHT, LockPolicy.lockReason(now, LocalTime.of(23, 30), s))
    }

    @Test fun `the daily lock alone is what protection gaps are judged by`() {
        val nightOnly = night(23 * 60, 7 * 60)
        assertFalse(LockPolicy.isDailyLockActive(now, LocalTime.of(23, 30), nightOnly))
        assertTrue(LockPolicy.isNightLockActive(now, LocalTime.of(23, 30), nightOnly))
        val daily = nightOnly.copy(habitsIncomplete = true)
        assertTrue(LockPolicy.isDailyLockActive(now, LocalTime.of(12, 0), daily))
        assertFalse(LockPolicy.isNightLockActive(now, LocalTime.of(12, 0), daily))
        assertFalse(LockPolicy.isDailyLockActive(now, LocalTime.of(12, 0), daily.copy(breakActiveUntil = now.plusSeconds(60))))
    }
}
