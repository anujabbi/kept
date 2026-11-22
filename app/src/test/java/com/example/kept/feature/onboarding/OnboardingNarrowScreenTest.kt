package com.example.kept.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Onboarding in Daybreak (#132; `A1-Welcome`, `A10`–`A14`) at 360 × 640 dp, font scale 1.0 and 2.0:
 * the welcome screen, every step (permissions with one required permission missing and with all
 * granted), Add a habit, and the shared bodies in settings mode. Goldens:
 * `app/src/test/screenshots/onboarding_360_*`. Also checks every screen fits ([OnboardingShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class OnboardingNarrowScreenTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { OnboardingShots(rule, "_360") }

    @Test fun welcome() = shots.shot("welcome", 1f)
    @Test fun welcome_font2() = shots.shot("welcome", 2f)
    @Test fun habits() = shots.shot("habits", 1f)
    @Test fun habits_font2() = shots.shot("habits", 2f)
    @Test fun habitsLimit() = shots.shot("habits_limit", 1f)
    @Test fun habitsLimit_font2() = shots.shot("habits_limit", 2f)
    @Test fun addHabit() = shots.shot("add_habit", 1f)
    @Test fun addHabit_font2() = shots.shot("add_habit", 2f)
    @Test fun shutdown() = shots.shot("shutdown", 1f)
    @Test fun shutdown_font2() = shots.shot("shutdown", 2f)
    @Test fun shutdownPicked() = shots.shot("shutdown_picked", 1f)
    @Test fun shutdownPicked_font2() = shots.shot("shutdown_picked", 2f)
    @Test fun lockWindow() = shots.shot("lock_window", 1f)
    @Test fun lockWindow_font2() = shots.shot("lock_window", 2f)
    @Test fun exceptions() = shots.shot("exceptions", 1f)
    @Test fun exceptions_font2() = shots.shot("exceptions", 2f)
    @Test fun permissionsMissing() = shots.shot("permissions_missing", 1f)
    @Test fun permissionsMissing_font2() = shots.shot("permissions_missing", 2f)
    @Test fun permissionsAll() = shots.shot("permissions_all", 1f)
    @Test fun permissionsAll_font2() = shots.shot("permissions_all", 2f)
    @Test fun ready() = shots.shot("ready", 1f)
    @Test fun ready_font2() = shots.shot("ready", 2f)
    @Test fun exceptionsSettings() = shots.shot("exceptions_settings", 1f)
    @Test fun exceptionsSettings_font2() = shots.shot("exceptions_settings", 2f)
    @Test fun permissionsSettings() = shots.shot("permissions_settings", 1f)
    @Test fun permissionsSettings_font2() = shots.shot("permissions_settings", 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
