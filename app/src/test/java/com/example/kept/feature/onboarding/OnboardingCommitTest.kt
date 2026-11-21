package com.example.kept.feature.onboarding

import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockWindowRules
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

    @Test fun `finishing marks onboarding done at the last of five steps`() {
        val after = Settings().onboardingFinished(today)
        assertTrue(after.onboardingDone)
        assertEquals(5, ONBOARDING_STEPS)
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

    /**
     * Migration (issue #41): a user whose onboarding finished before the card existed has
     * `onboarding_done` stored and no `first_landing_pending` key. The key reads back as false
     * when absent, so such a Settings must not be pending.
     */
    @Test fun `onboarding already done with no first landing key is not pending`() {
        val migrated = Settings(onboardingDone = true, onboardingStep = ONBOARDING_STEPS)
        assertFalse(migrated.firstLandingPending)
    }
}
