package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The weekly term of the daily lock (issue #165, story #175):
 * `(habitsIncomplete || weeklyMustDoIncomplete) && isWindowActive`, still OR-ed with night
 * lockdown. The flag comes from `WeeklyGoalRules.Week.isMustDay`, so the worked examples are
 * run through the rules and then through the policy.
 */
class WeeklyLockPolicyTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")
    private val hard = setOf("com.android.dialer")
    private val sunday = LocalDate.of(2026, 11, 22)
    private val wednesday = sunday.plusDays(3)
    private val longAgo = LocalDate.of(2026, 11, 1)

    /** 7:00 am to 9:00 pm, every habit done, night lockdown off. */
    private fun snap(
        weekly: Boolean,
        habitsIncomplete: Boolean = false,
        breakUntil: Instant? = null,
        night: Boolean = false,
        onboardingDone: Boolean = true,
        exceptions: Set<String> = emptySet(),
    ) = LockPolicy.Snapshot(
        lockFromMinute = 7 * 60, dueMinute = 21 * 60, habitsIncomplete = habitsIncomplete, breakActiveUntil = breakUntil,
        hardAllowlist = hard, userExceptions = exceptions, launchable = null, onboardingDone = onboardingDone,
        nightEnabled = night, weeklyMustDoIncomplete = weekly,
    )

    private fun week(target: Int, today: LocalDate, vararg done: LocalDate, from: LocalDate = longAgo) =
        WeeklyGoalRules.week(WeeklyGoalRules.Goal(target, from), done.toSet(), today)

    private fun locks(w: WeeklyGoalRules.Week, at: LocalTime = LocalTime.of(9, 0)) =
        LockPolicy.shouldLock("com.instagram.android", now, at, snap(weekly = w.isMustDay))

    @Test fun `the new snapshot field is off unless a goal says otherwise`() {
        val s = LockPolicy.Snapshot(420, 1260, habitsIncomplete = false, breakActiveUntil = null, hardAllowlist = hard, userExceptions = emptySet(), launchable = null)
        assertFalse(s.weeklyMustDoIncomplete)
        assertFalse(LockPolicy.isLockActive(now, LocalTime.of(9, 0), s))
    }

    @Test fun `all habits done and a must-do session not logged locks inside the daily window`() {
        val s = snap(weekly = true)
        assertTrue(LockPolicy.isDailyLockActive(now, LocalTime.of(7, 0), s))
        assertTrue(LockPolicy.isLockActive(now, LocalTime.of(9, 0), s))
        assertTrue(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(20, 59), s))
        assertEquals(LockReason.DAILY, LockPolicy.lockReason(now, LocalTime.of(9, 0), s))
    }

    @Test fun `it is unlocked before the window and after the give-up time`() {
        val s = snap(weekly = true)
        assertFalse(LockPolicy.isLockActive(now, LocalTime.of(6, 59), s))
        assertFalse("the give-up time ends it", LockPolicy.isLockActive(now, LocalTime.of(21, 0), s))
        assertFalse(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(23, 30), s))
        assertNull(LockPolicy.lockReason(now, LocalTime.of(21, 0), s))
    }

    @Test fun `logging the session unlocks`() {
        val before = week(4, wednesday)
        assertTrue(before.isMustDay)
        assertTrue(locks(before))
        val after = week(4, wednesday, wednesday)
        assertFalse(after.isMustDay)
        assertFalse(locks(after))
    }

    @Test fun `undo brings the lock back`() {
        assertFalse(locks(week(4, wednesday, wednesday)))
        assertTrue(locks(week(4, wednesday)))
    }

    @Test fun `a goal that is not must-do never locks`() {
        // Example B: 4 a week, one on Sunday, Monday has 6 days for 3.
        assertFalse(locks(week(4, sunday.plusDays(1), sunday)))
        // Example D: 4 done by Thursday, Friday and Saturday are free.
        val four = arrayOf(sunday, sunday.plusDays(1), sunday.plusDays(2), sunday.plusDays(4))
        assertFalse(locks(week(4, sunday.plusDays(5), *four)))
        assertFalse(locks(week(4, sunday.plusDays(6), *four)))
        // The next Sunday of example C: a new week, 7 days for 4.
        assertFalse(locks(week(4, sunday.plusDays(7), wednesday)))
    }

    @Test fun `example C locks Thursday and Friday`() {
        assertTrue(locks(week(4, sunday.plusDays(4), wednesday)))
        val friday = week(4, sunday.plusDays(5), wednesday)
        assertTrue(locks(friday))
        assertFalse(friday.canStillHitTarget)
    }

    @Test fun `target 7 locks every day until that day's session is logged`() {
        var logged = emptyArray<LocalDate>()
        for (d in 0..6) {
            val day = sunday.plusDays(d.toLong())
            assertTrue("day $d before its session", locks(week(7, day, *logged)))
            logged += day
            assertFalse("day $d after its session", locks(week(7, day, *logged)))
        }
    }

    @Test fun `a goal created today does not lock today, even at 7 a week`() {
        assertFalse(locks(week(7, wednesday, from = wednesday)))
        assertFalse(locks(week(4, wednesday, from = wednesday)))
        // Thursday, with nothing logged: 3 days for the reduced target of 3.
        assertTrue(locks(week(4, wednesday.plusDays(1), from = wednesday)))
    }

    @Test fun `raising a target can make today a must-do day`() {
        assertFalse(locks(week(2, wednesday)))
        assertTrue(locks(week(4, wednesday)))
    }

    @Test fun `lowering it lifts the lock only when fewer are needed than days are left`() {
        assertTrue(locks(week(4, wednesday)))
        assertFalse("4 to 2: needs 2, 4 days left", locks(week(2, wednesday)))
        assertTrue("7 to 4: still needs 4 in 4 days", locks(week(4, wednesday)))
    }

    @Test fun `a break pauses it and it returns when the break ends`() {
        val s = snap(weekly = true, breakUntil = now.plusSeconds(600))
        assertFalse(LockPolicy.isLockActive(now, LocalTime.of(9, 0), s))
        assertTrue(LockPolicy.isLockActive(now.plusSeconds(601), LocalTime.of(9, 11), s))
    }

    @Test fun `essentials and exceptions still open`() {
        val s = snap(weekly = true, exceptions = setOf("com.spotify.music"))
        assertFalse(LockPolicy.shouldLock("com.android.dialer", now, LocalTime.of(9, 0), s))
        assertFalse(LockPolicy.shouldLock("com.spotify.music", now, LocalTime.of(9, 0), s))
        assertTrue(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(9, 0), s))
        assertFalse("an exception added while the screen is up takes it down", LockPolicy.lockScreenShouldStay("com.spotify.music", now, LocalTime.of(9, 0), s))
        assertTrue(LockPolicy.lockScreenShouldStay("com.instagram.android", now, LocalTime.of(9, 0), s))
    }

    @Test fun `nothing locks before onboarding is done`() {
        assertFalse(LockPolicy.isLockActive(now, LocalTime.of(9, 0), snap(weekly = true, onboardingDone = false)))
    }

    @Test fun `a habit and a must-do session - either one keeps the lock, both done lifts it`() {
        val at = LocalTime.of(9, 0)
        assertTrue(LockPolicy.isLockActive(now, at, snap(weekly = true, habitsIncomplete = true)))
        assertTrue("only the habit ticked", LockPolicy.isLockActive(now, at, snap(weekly = true, habitsIncomplete = false)))
        assertTrue("only the session logged", LockPolicy.isLockActive(now, at, snap(weekly = false, habitsIncomplete = true)))
        assertFalse(LockPolicy.isLockActive(now, at, snap(weekly = false, habitsIncomplete = false)))
    }

    @Test fun `night lockdown wins while its window is open, then the weekly lock shows`() {
        // Night 11:00 pm to 7:00 am, daily 7:00 am to 9:00 pm, moved so they overlap: daily from 6:00.
        val s = snap(weekly = true, night = true).copy(lockFromMinute = 6 * 60)
        assertEquals(LockReason.NIGHT, LockPolicy.lockReason(now, LocalTime.of(6, 30), s))
        assertTrue(LockPolicy.isDailyLockActive(now, LocalTime.of(6, 30), s))
        assertEquals(LockReason.DAILY, LockPolicy.lockReason(now, LocalTime.of(7, 0), s))
        // After the give-up time only night lockdown is left, and it is not the daily lock.
        assertEquals(LockReason.NIGHT, LockPolicy.lockReason(now, LocalTime.of(23, 30), s))
        assertFalse(LockPolicy.isDailyLockActive(now, LocalTime.of(23, 30), s))
    }

    @Test fun `a weekly-only daily lock opens a permission gap, unlike night lockdown alone`() {
        val weekly = LockPolicy.isDailyLockActive(now, LocalTime.of(9, 0), snap(weekly = true))
        val open = PermissionGap.step(openSince = null, dailyLockActive = weekly, permissionsGranted = false, now = 1_000)
        assertEquals(1_000L, open.openSince)
        // Logging the session ends the daily lock, which closes the gap for recording.
        val logged = LockPolicy.isDailyLockActive(now, LocalTime.of(9, 5), snap(weekly = false))
        assertEquals(PermissionGap.Closed(1_000, 5_000), PermissionGap.step(open.openSince, logged, permissionsGranted = false, now = 5_000).closed)
        // Night lockdown alone opens none.
        val nightOnly = LockPolicy.isDailyLockActive(now, LocalTime.of(23, 30), snap(weekly = true, night = true))
        assertNull(PermissionGap.step(null, nightOnly, permissionsGranted = false, now = 9_000).openSince)
    }

    @Test fun `a dead service during a weekly-only lock is a gap when the phone was used`() {
        val daily = LockPolicy.isDailyLockActive(now, LocalTime.of(9, 0), snap(weekly = true))
        val input = GapRules.Input(
            lockShouldBeActive = true, lockPermissionsGranted = true, heartbeatMillis = 0, nowMillis = 10 * 60_000,
            everHeartbeat = true, screenInUseAtMillis = listOf(5 * 60_000L), lockedAppResumedDuringWindow = false,
            dailyLockActive = daily,
        )
        assertEquals(GapRules.Verdict.GAP_SCREEN_ON, GapRules.evaluate(input))
    }

    @Test fun `the lock screen stays locked for a finished day while a must-do session is left`() {
        fun mode(lockActive: Boolean, weekly: Boolean, onTime: Boolean = true) =
            LockMode.of(loaded = true, lockActive = lockActive, blockedStillLocked = lockActive, allDoneOnTime = onTime, weeklyMustDoIncomplete = weekly)
        assertEquals(LockMode.LOCKED, mode(lockActive = true, weekly = true))
        assertEquals("logged: the day is finished, so it opens", LockMode.OPEN, mode(lockActive = false, weekly = false))
        assertEquals("the give-up time passed with the session not logged", LockMode.DISMISS, mode(lockActive = false, weekly = true))
        assertEquals("habits ticked late, session logged", LockMode.DISMISS, mode(lockActive = false, weekly = false, onTime = false))
    }

    @Test fun `which face the lock screen shows`() {
        assertEquals(LockScreenKind.WEEKLY, LockScreenKind.of(night = false, habitsIncomplete = false, weeklyMustDoIncomplete = true))
        assertEquals(LockScreenKind.HABITS, LockScreenKind.of(night = false, habitsIncomplete = true, weeklyMustDoIncomplete = true))
        assertEquals(LockScreenKind.HABITS, LockScreenKind.of(night = false, habitsIncomplete = true, weeklyMustDoIncomplete = false))
        assertEquals(LockScreenKind.HABITS, LockScreenKind.of(night = false, habitsIncomplete = false, weeklyMustDoIncomplete = false))
        assertEquals(LockScreenKind.NIGHT, LockScreenKind.of(night = true, habitsIncomplete = false, weeklyMustDoIncomplete = true))
        assertEquals(LockScreenKind.NIGHT, LockScreenKind.of(night = true, habitsIncomplete = true, weeklyMustDoIncomplete = true))
    }

    @Test fun `today counts on a must-do day before and after its session, and on no other day`() {
        assertTrue(week(4, wednesday).todayCounts)
        val logged = week(4, wednesday, wednesday)
        assertFalse(logged.isMustDay)
        assertTrue("the row stays on the lock screen so it can be undone", logged.todayCounts)
        assertFalse(week(4, sunday.plusDays(1), sunday).todayCounts)
        assertFalse("a free day's session does not make it a must-do day", week(4, sunday.plusDays(1), sunday, sunday.plusDays(1)).todayCounts)
        assertFalse("the creation day", week(7, wednesday, wednesday, from = wednesday).todayCounts)
        assertFalse("a complete week", week(2, wednesday, sunday, sunday.plusDays(1)).todayCounts)
    }
}
