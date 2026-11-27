package com.example.kept.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Onboarding in Daybreak (#132; `A1-Welcome`, `A10`–`A14`) at 412 × 915 dp, font scale 1.0 and 2.0:
 * the welcome screen, every step (permissions with one required permission missing and with all
 * granted), Add a habit, and the shared bodies in settings mode. Goldens:
 * `app/src/test/screenshots/onboarding_*`. Also checks every screen fits ([OnboardingShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class OnboardingScreenshotTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { OnboardingShots(rule, "") }

    @Test fun welcome() = shots.shot("welcome", 1f)
    @Test fun welcome_font2() = shots.shot("welcome", 2f)
    @Test fun habits() = shots.shot("habits", 1f)
    @Test fun habits_font2() = shots.shot("habits", 2f)
    @Test fun promise() = shots.shot("promise", 1f)
    @Test fun promise_font2() = shots.shot("promise", 2f)
    @Test fun habitsLimit() = shots.shot("habits_limit", 1f)
    @Test fun habitsLimit_font2() = shots.shot("habits_limit", 2f)
    @Test fun shutdown() = shots.shot("shutdown", 1f)
    @Test fun shutdown_font2() = shots.shot("shutdown", 2f)
    @Test fun lockWindow() = shots.shot("lock_window", 1f)
    @Test fun lockWindow_font2() = shots.shot("lock_window", 2f)
    @Test fun exceptions() = shots.shot("exceptions", 1f)
    @Test fun exceptions_font2() = shots.shot("exceptions", 2f)
    @Test fun permissionsMissing() = shots.shot("permissions_missing", 1f)
    @Test fun permissionsMissing_font2() = shots.shot("permissions_missing", 2f)
    @Test fun permissionsAll() = shots.shot("permissions_all", 1f)
    @Test fun permissionsAll_font2() = shots.shot("permissions_all", 2f)
    @Test fun account() = shots.shot("account", 1f)
    @Test fun account_font2() = shots.shot("account", 2f)
    @Test fun accountError() = shots.shot("account_error", 1f)
    @Test fun ready() = shots.shot("ready", 1f)
    @Test fun ready_font2() = shots.shot("ready", 2f)
    @Test fun readyWeekly() = shots.shot("ready_weekly", 1f)
    @Test fun readyWeekly_font2() = shots.shot("ready_weekly", 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
