package com.example.kept.feature.onboarding

import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.NightWindow
import com.example.kept.core.domain.OnboardingCopy
import java.time.LocalDate

/**
 * Steps the person walking onboarding sees after the welcome screen (#132, #164): habits,
 * shutdown time, lock window, exceptions, permissions, how Sprig works.
 */
const val ONBOARDING_STEPS = OnboardingCopy.STEPS

/**
 * What leaving the habits step records. Only the first-use date: the break length is not asked
 * for during onboarding (issue #37), and the lock window is written by [onboardingLockWindow]
 * only if the person moved it on step 3, and night lockdown by [onboardingShutdown].
 */
fun Settings.onboardingDraft(today: LocalDate): Settings = copy(firstUseDate = firstUseDate ?: today)

/**
 * The lock window step's answer (#132): [window] is null when the person never moved it, and
 * then the stored window (the defaults on a fresh install) stays exactly as it was.
 */
fun Settings.onboardingLockWindow(window: LockWindowRules.Window?): Settings =
    if (window == null) this else copy(lockFromMinute = window.fromMinute, dueMinute = window.dueMinute)

/**
 * The shutdown step's answer (#164): [startMinute] is the time picked (10 PM, 11 PM or Midnight
 * as a minute of the day) and turns night lockdown on from then until 7:00 am; null is Skip, which
 * leaves it off. Written on "Start today" with the rest, like the lock window.
 */
fun Settings.onboardingShutdown(startMinute: Int?): Settings =
    if (startMinute == null) copy(nightLockEnabled = false)
    else copy(nightLockEnabled = true, nightFromMinute = startMinute, nightUntilMinute = NightWindow.DEFAULT_UNTIL)

/**
 * What "Start today" records: the draft, onboarding marked done at its last step, and the
 * first-landing card armed so Home shows it once (issue #41).
 */
fun Settings.onboardingFinished(today: LocalDate): Settings =
    onboardingDraft(today).copy(onboardingDone = true, onboardingStep = ONBOARDING_STEPS, firstLandingPending = true)
