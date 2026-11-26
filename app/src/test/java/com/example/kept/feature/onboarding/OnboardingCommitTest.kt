package com.example.kept.feature.onboarding

import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.NightSetting
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
            listOf(OnboardingStep.HABITS, OnboardingStep.LOCK_WINDOW, OnboardingStep.SHUTDOWN, EXCEPTIONS_STEP, PERMISSIONS_STEP, OnboardingStep.READY),
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

    // Shutdown time (#164, #218) --------------------------------------------------------------

    private fun night(from: Int, until: Int, on: Boolean = true) = NightSetting(on, from, until)

    @Test fun `continue turns night lockdown on at both slider times`() {
        val after = Settings().onboardingShutdown(night(23 * 60, 7 * 60 + 30)).onboardingFinished(today)
        assertTrue(after.nightLockEnabled)
        assertEquals(1380, after.nightFromMinute)
        assertEquals(450, after.nightUntilMinute)
    }

    @Test fun `Skip stores night lockdown off`() {
        assertFalse(Settings().onboardingShutdown(night(23 * 60, 7 * 60, on = false)).onboardingFinished(today).nightLockEnabled)
        assertFalse(Settings(nightLockEnabled = true).onboardingShutdown(night(23 * 60, 7 * 60, on = false)).nightLockEnabled)
    }

    @Test fun `the shutdown answer changes nothing but night lockdown`() {
        assertEquals(tuned.copy(nightLockEnabled = true, nightFromMinute = 0, nightUntilMinute = 420), tuned.onboardingShutdown(night(0, 420)))
        assertEquals(tuned, tuned.onboardingShutdown(night(0, 420, on = false)))
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
