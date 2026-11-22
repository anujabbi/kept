package com.example.kept.feature.onboarding

import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.feature.settings.EXCEPTIONS_STEP
import com.example.kept.feature.settings.PERMISSIONS_STEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What onboarding writes to [Settings] on the way through and on finish. The break length is
 * never asked for (issue #37). The lock window step (#132) writes the window only when the person
 * moved it; otherwise the stored window, the defaults on a fresh install, stays as it was.
 */
class OnboardingCommitTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 1)

    /** The debug seed path, or a user who changed Settings before re-running onboarding. */
    private val tuned = Settings(lockFromMinute = 6 * 60, dueMinute = 22 * 60 + 15, breakDurationMin = 60)

    @Test fun `finishing marks onboarding done at the last of six steps`() {
        val after = Settings().onboardingFinished(today)
        assertTrue(after.onboardingDone)
        assertEquals(6, ONBOARDING_STEPS)
        assertEquals(6, OnboardingStep.READY)
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            listOf(OnboardingStep.HABITS, OnboardingStep.SHUTDOWN, OnboardingStep.LOCK_WINDOW, EXCEPTIONS_STEP, PERMISSIONS_STEP, OnboardingStep.READY),
        )
        assertEquals(OnboardingStep.EXCEPTIONS, EXCEPTIONS_STEP)
        assertEquals(OnboardingStep.PERMISSIONS, PERMISSIONS_STEP)
        assertEquals(ONBOARDING_STEPS, after.onboardingStep)
    }

    @Test fun `an untouched lock window step writes nothing`() {
        val after = tuned.onboardingLockWindow(null).onboardingFinished(today)
        assertEquals(6 * 60, after.lockFromMinute)
        assertEquals(22 * 60 + 15, after.dueMinute)
    }

    @Test fun `a moved lock window is written on finish, and nothing else changes`() {
        val after = tuned.onboardingLockWindow(LockWindowRules.Window(8 * 60, 20 * 60 + 30)).onboardingFinished(today)
        assertEquals(8 * 60, after.lockFromMinute)
        assertEquals(20 * 60 + 30, after.dueMinute)
        assertEquals(60, after.breakDurationMin)
        assertTrue(after.onboardingDone)
    }

    // Shutdown time (#164) ---------------------------------------------------------------------

    @Test fun `11 PM turns night lockdown on from 1380 until 420`() {
        val after = Settings().onboardingShutdown(23 * 60).onboardingFinished(today)
        assertTrue(after.nightLockEnabled)
        assertEquals(1380, after.nightFromMinute)
        assertEquals(420, after.nightUntilMinute)
    }

    @Test fun `the end is 7 am whatever was stored before`() {
        val after = Settings(nightUntilMinute = 9 * 60).onboardingShutdown(22 * 60)
        assertEquals(7 * 60, after.nightUntilMinute)
    }

    @Test fun `Skip stores night lockdown off`() {
        assertFalse(Settings().onboardingShutdown(null).onboardingFinished(today).nightLockEnabled)
        assertFalse(Settings(nightLockEnabled = true).onboardingShutdown(null).nightLockEnabled)
    }

    @Test fun `the shutdown answer changes nothing but night lockdown`() {
        assertEquals(tuned.copy(nightLockEnabled = true, nightFromMinute = 0, nightUntilMinute = 420), tuned.onboardingShutdown(0))
        assertEquals(tuned, tuned.onboardingShutdown(null))
    }

    /** Leaving the habits step writes the draft; night lockdown is not part of it. */
    @Test fun `the draft never touches night lockdown`() {
        val draft = Settings().onboardingDraft(today)
        assertFalse(draft.nightLockEnabled)
        assertEquals(Settings().nightFromMinute, draft.nightFromMinute)
        assertEquals(Settings().nightUntilMinute, draft.nightUntilMinute)
    }

    @Test fun `the draft sets first use date once and never moves it`() {
        val first = Settings().onboardingDraft(today)
        assertEquals(today, first.firstUseDate)
        assertFalse(first.onboardingDone)
        val later = first.onboardingDraft(today.plusDays(3))
        assertEquals(today, later.firstUseDate)
        assertEquals(today, later.onboardingFinished(today.plusDays(3)).firstUseDate)
    }

    /** "Start today" is the only thing that arms the first-landing card on Home (issue #41). */
    @Test fun `finishing arms the first landing card`() {
        assertFalse(Settings().firstLandingPending)
        assertTrue(Settings().onboardingFinished(today).firstLandingPending)
        assertFalse(Settings().onboardingDraft(today).firstLandingPending)
    }
}
