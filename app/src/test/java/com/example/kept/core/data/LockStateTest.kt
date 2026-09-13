package com.example.kept.core.data

import com.example.kept.core.data.prefs.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/**
 * [LockState.isLockActiveNow] is the guard SettingsViewModel uses to decide whether a
 * lock-affecting settings change (issue #1) needs confirmation. It should agree exactly with
 * [com.example.kept.core.domain.LockPolicy.isLockActive], which drives real enforcement.
 */
class LockStateTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")

    private fun state(
        lockFromMinute: Int = 7 * 60,
        dueMinute: Int = 21 * 60,
        habitsIncomplete: Boolean = true,
        breakActiveUntil: Instant? = null,
        onboardingDone: Boolean = true,
    ) = LockState(
        settings = Settings(lockFromMinute = lockFromMinute, dueMinute = dueMinute, onboardingDone = onboardingDone),
        today = TodaySummary(if (habitsIncomplete) listOf(HabitToday(habit(), null)) else listOf(HabitToday(habit(), doneEntry()))),
        breakActiveUntil = breakActiveUntil,
        breaksUsedThisWeek = 0,
        userExceptions = emptySet(),
        unprotectedToday = false,
    )

    private fun habit() = com.example.kept.core.data.db.HabitEntity(
        id = 1, title = "Read", iconKey = "star",
        proofType = com.example.kept.core.domain.ProofType.MANUAL, targetValue = 1, unit = "", createdAt = 0,
    )

    private fun doneEntry() = com.example.kept.core.data.db.HabitEntryEntity(
        habitId = 1, date = "2026-09-13", progressValue = 1, completedAt = now.toEpochMilli(),
    )

    @Test fun `inside the window with an undone habit the lock is active`() {
        assertTrue(state().isLockActiveNow(now, LocalTime.of(9, 0)))
    }

    @Test fun `outside the window the lock is not active`() {
        assertFalse(state().isLockActiveNow(now, LocalTime.of(23, 0)))
    }

    @Test fun `once every habit is done the lock is not active`() {
        assertFalse(state(habitsIncomplete = false).isLockActiveNow(now, LocalTime.of(9, 0)))
    }

    @Test fun `an active break suspends the lock`() {
        val until = now.plusSeconds(600)
        assertFalse(state(breakActiveUntil = until).isLockActiveNow(now, LocalTime.of(9, 0)))
    }

    @Test fun `a lapsed break no longer suspends the lock`() {
        val until = now.minusSeconds(600)
        assertTrue(state(breakActiveUntil = until).isLockActiveNow(now, LocalTime.of(9, 0)))
    }

    @Test fun `before onboarding is done the lock is never active`() {
        assertFalse(state(onboardingDone = false).isLockActiveNow(now, LocalTime.of(9, 0)))
    }

    @Test fun `a window that wraps midnight is active late at night`() {
        assertTrue(state(lockFromMinute = 22 * 60, dueMinute = 6 * 60).isLockActiveNow(now, LocalTime.of(23, 0)))
    }
}
