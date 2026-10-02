package com.example.kept.feature.onboarding

import com.example.kept.core.data.prefs.Settings
import java.time.LocalDate

/** Steps the person walking onboarding sees: habits, exceptions, permissions, meet Sprig. */
const val ONBOARDING_STEPS = 4

/**
 * What leaving the habits step records. Only the first-use date: the lock window and break length
 * are no longer asked for during onboarding (issue #37), so they stay whatever [Settings] already
 * holds, which on a fresh install is the defaults Settings shows later.
 */
fun Settings.onboardingDraft(today: LocalDate): Settings = copy(firstUseDate = firstUseDate ?: today)

/** What "Start today" records: the draft, plus onboarding marked done at its last step. */
fun Settings.onboardingFinished(today: LocalDate): Settings =
    onboardingDraft(today).copy(onboardingDone = true, onboardingStep = ONBOARDING_STEPS)
