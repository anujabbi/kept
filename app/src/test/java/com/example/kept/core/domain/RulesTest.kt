package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class PointsAndLevelTest {
    @Test fun `two points per whole minute off apps`() {
        assertEquals(0L, PointsRules.forLockedMillis(59_000))
        assertEquals(2L, PointsRules.forLockedMillis(60_000))
        assertEquals(20L, PointsRules.forLockedMillis(10 * 60_000 + 999))
    }

    @Test fun `level up and down with floor of one`() {
        assertEquals(5, LevelRules.up(4))
        assertEquals(3, LevelRules.down(4))
        assertEquals(1, LevelRules.down(1))
        assertEquals(1, LevelRules.down(0))
    }
}

class BreakCapTest {
    private val now = Instant.parse("2026-09-05T12:00:00Z")

    @Test fun `three breaks in seven days exhausts the cap`() {
        val breaks = listOf(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(3)), now.minus(Duration.ofHours(2)))
        assertEquals(0, BreakCap.remaining(breaks, now))
        assertTrue(BreakCap.nextBreakIsOverCap(breaks, now))
    }

    @Test fun `breaks older than seven days roll out of the window`() {
        val breaks = listOf(
            now.minus(Duration.ofDays(8)),
            now.minus(Duration.ofDays(7)).minusSeconds(1),
            now.minus(Duration.ofDays(2)),
        )
        assertEquals(2, BreakCap.remaining(breaks, now))
        assertFalse(BreakCap.nextBreakIsOverCap(breaks, now))
    }

    @Test fun `no breaks means three remaining`() {
        assertEquals(3, BreakCap.remaining(emptyList(), now))
    }
}

class StreakRulesTest {
    @Test fun `completed protected day increments streak`() {
        val r = StreakRules.apply(4, true, StreakRules.DayOutcome(true, unprotected = false, writtenOff = false))
        assertEquals(5, r.streak); assertTrue(r.counted); assertFalse(r.shieldConsumed)
    }

    @Test fun `missed day consumes shield and keeps streak`() {
        val r = StreakRules.apply(12, true, StreakRules.DayOutcome(false, unprotected = false, writtenOff = false))
        assertEquals(12, r.streak); assertTrue(r.shieldConsumed); assertFalse(r.shieldAvailable); assertFalse(r.reset)
    }

    @Test fun `missed day without shield resets streak`() {
        val r = StreakRules.apply(12, false, StreakRules.DayOutcome(false, unprotected = false, writtenOff = false))
        assertEquals(0, r.streak); assertTrue(r.reset)
    }

    @Test fun `unprotected day does not count even if habits done`() {
        val r = StreakRules.apply(3, false, StreakRules.DayOutcome(true, unprotected = true, writtenOff = false))
        assertEquals(0, r.streak); assertFalse(r.counted)
    }

    @Test fun `written off day does not count`() {
        val r = StreakRules.apply(3, true, StreakRules.DayOutcome(true, unprotected = false, writtenOff = true))
        assertEquals(3, r.streak); assertTrue(r.shieldConsumed)
    }

    @Test fun `shield is not wasted on a zero streak`() {
        val r = StreakRules.apply(0, true, StreakRules.DayOutcome(false, unprotected = false, writtenOff = false))
        assertEquals(0, r.streak); assertTrue(r.shieldAvailable); assertFalse(r.shieldConsumed)
    }

    @Test fun `shield refills when ISO week changes`() {
        val sunday = LocalDate.of(2026, 9, 6)
        val monday = LocalDate.of(2026, 9, 7)
        val used = SprigState(shieldAvailable = false, shieldWeekKey = StreakRules.weekKey(sunday))
        assertFalse(StreakRules.refillShield(used, sunday).shieldAvailable)
        assertTrue(StreakRules.refillShield(used, monday).shieldAvailable)
    }
}

class LockPolicyTest {
    private val now = Instant.parse("2026-09-05T12:00:00Z")
    private val base = LockPolicy.Snapshot(
        lockFromMinute = 7 * 60,
        dueMinute = 21 * 60,
        habitsIncomplete = true,
        breakActiveUntil = null,
        hardAllowlist = Allowlist.build(listOf("com.google.android.dialer", "com.android.launcher3")),
        userExceptions = setOf("com.spotify.music"),
        launchable = setOf("com.instagram.android", "com.spotify.music", "com.google.android.dialer", "com.android.chrome"),
    )

    @Test fun `dialer can never be locked`() {
        assertFalse(LockPolicy.shouldLock("com.google.android.dialer", now, LocalTime.of(12, 0), base))
        assertFalse(LockPolicy.shouldLock("com.android.dialer", now, LocalTime.of(12, 0), base))
        assertFalse(LockPolicy.shouldLock("com.android.emergency", now, LocalTime.of(12, 0), base))
        assertFalse(LockPolicy.shouldLock("com.android.settings", now, LocalTime.of(12, 0), base))
        assertFalse(LockPolicy.shouldLock(Allowlist.OWN_PACKAGE, now, LocalTime.of(12, 0), base))
    }

    @Test fun `everything launchable is locked by default inside the window`() {
        assertTrue(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(12, 0), base))
        assertTrue(LockPolicy.shouldLock("com.android.chrome", now, LocalTime.of(7, 0), base))
    }

    @Test fun `user exceptions are not locked`() {
        assertFalse(LockPolicy.shouldLock("com.spotify.music", now, LocalTime.of(12, 0), base))
    }

    @Test fun `non-launchable system packages are ignored`() {
        assertFalse(LockPolicy.shouldLock("com.android.providers.media", now, LocalTime.of(12, 0), base))
    }

    @Test fun `outside the window nothing is locked`() {
        assertFalse(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(6, 59), base))
        assertFalse(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(21, 0), base))
    }

    @Test fun `window that wraps midnight`() {
        val s = base.copy(lockFromMinute = 22 * 60, dueMinute = 6 * 60)
        assertTrue(LockPolicy.isWindowActive(LocalTime.of(23, 0), s))
        assertTrue(LockPolicy.isWindowActive(LocalTime.of(2, 0), s))
        assertFalse(LockPolicy.isWindowActive(LocalTime.of(12, 0), s))
    }

    @Test fun `habits complete releases the lock`() {
        assertFalse(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(12, 0), base.copy(habitsIncomplete = false)))
    }

    @Test fun `active break suspends the lock until it expires`() {
        val s = base.copy(breakActiveUntil = now.plusSeconds(600))
        assertFalse(LockPolicy.shouldLock("com.instagram.android", now, LocalTime.of(12, 0), s))
        assertTrue(LockPolicy.shouldLock("com.instagram.android", now.plusSeconds(601), LocalTime.of(12, 10), s))
    }

    @Test fun `allowlist filters the picker`() {
        val apps = listOf("com.instagram.android", "com.google.android.dialer", Allowlist.OWN_PACKAGE)
        val pickable = Allowlist.pickable(apps, { it }, base.hardAllowlist)
        assertEquals(listOf("com.instagram.android"), pickable)
    }
}

class EvolutionTest {
    @Test fun `form thresholds`() {
        assertEquals(SprigForm.SPRIG, SprigForm.forStreak(0))
        assertEquals(SprigForm.SPRIG, SprigForm.forStreak(2))
        assertEquals(SprigForm.BUD, SprigForm.forStreak(3))
        assertEquals(SprigForm.BLOOM, SprigForm.forStreak(7))
        assertEquals(SprigForm.THICKET, SprigForm.forStreak(20))
        assertEquals(SprigForm.GROVE, SprigForm.forStreak(30))
        assertEquals(SprigForm.ANCIENT, SprigForm.forStreak(99))
        assertEquals(SprigForm.CELESTIAL, SprigForm.forStreak(500))
    }

    @Test fun `next form after current`() {
        assertEquals(SprigForm.BUD, SprigForm.next(SprigForm.SPRIG))
        assertEquals(null, SprigForm.next(SprigForm.CELESTIAL))
    }

    @Test fun `variant is stable within a week and changes across weeks`() {
        val mon = LocalDate.of(2026, 9, 7)
        val sun = LocalDate.of(2026, 9, 13)
        val nextMon = LocalDate.of(2026, 9, 14)
        assertEquals(Variants.forDate(mon), Variants.forDate(sun))
        assertTrue(Variants.forDate(mon) != Variants.forDate(nextMon))
    }
}
