package com.example.kept.feature.onboarding

import com.example.kept.core.data.prefs.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Onboarding no longer has a lock-rule step (issue #37). What it writes to [Settings] on the way
 * through and on finish is pinned here so nothing in the flow can overwrite the lock window or
 * break length again: those come from the [Settings] defaults and are changed in Settings only.
 */
class OnboardingCommitTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 1)

    /** The debug seed path, or a user who changed Settings before re-running onboarding. */
    private val tuned = Settings(lockFromMinute = 6 * 60, dueMinute = 22 * 60 + 15, breakDurationMin = 60)

    @Test fun `finishing leaves lock window and break length untouched`() {
        val after = tuned.onboardingFinished(today)
        assertEquals(6 * 60, after.lockFromMinute)
        assertEquals(22 * 60 + 15, after.dueMinute)
        assertEquals(60, after.breakDurationMin)
    }

    @Test fun `a fresh install keeps the Settings defaults`() {
        val after = Settings().onboardingFinished(today)
        assertEquals(7 * 60, after.lockFromMinute)
        assertEquals(21 * 60, after.dueMinute)
        assertEquals(30, after.breakDurationMin)
    }

    @Test fun `finishing marks onboarding done at the last of four steps`() {
        val after = Settings().onboardingFinished(today)
        assertTrue(after.onboardingDone)
        assertEquals(4, ONBOARDING_STEPS)
        assertEquals(ONBOARDING_STEPS, after.onboardingStep)
    }

    @Test fun `the draft sets first use date once and never moves it`() {
        val first = Settings().onboardingDraft(today)
        assertEquals(today, first.firstUseDate)
        assertFalse(first.onboardingDone)
        val later = first.onboardingDraft(today.plusDays(3))
        assertEquals(today, later.firstUseDate)
        assertEquals(today, later.onboardingFinished(today.plusDays(3)).firstUseDate)
    }
}
