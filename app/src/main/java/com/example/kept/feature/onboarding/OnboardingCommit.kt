package com.example.kept.feature.onboarding

import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.OnboardingCopy
import java.time.LocalDate

/**
 * Steps the person walking onboarding sees after the welcome screen (#132): habits, lock window,
 * exceptions, permissions, how Sprig works.
 */
const val ONBOARDING_STEPS = OnboardingCopy.STEPS

/**
 * What leaving the habits step records. Only the first-use date: the break length is not asked
 * for during onboarding (issue #37), and the lock window is written by [onboardingLockWindow]
 * only if the person moved it on step 2.
 */
fun Settings.onboardingDraft(today: LocalDate): Settings = copy(firstUseDate = firstUseDate ?: today)

/**
 * The lock window step's answer (#132): [window] is null when the person never moved it, and
 * then the stored window (the defaults on a fresh install) stays exactly as it was.
 */
fun Settings.onboardingLockWindow(window: LockWindowRules.Window?): Settings =
    if (window == null) this else copy(lockFromMinute = window.fromMinute, dueMinute = window.dueMinute)

/**
 * What "Start today" records: the draft, onboarding marked done at its last step, and the
 * first-landing card armed so Home shows it once (issue #41).
 */
fun Settings.onboardingFinished(today: LocalDate): Settings =
    onboardingDraft(today).copy(onboardingDone = true, onboardingStep = ONBOARDING_STEPS, firstLandingPending = true)
