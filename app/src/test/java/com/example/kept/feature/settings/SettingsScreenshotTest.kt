package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings in Daybreak (`A7-Settings`, `A15-History`, `A12-Exceptions-settings`,
 * `A13-Permissions-settings`) at 412 × 915 dp, font scale 1.0 and 2.0: the tab all set and with
 * permissions off, the tab scrolled to its end, History with every outcome and empty, What stays
 * open, and Permissions all granted and with one missing. Goldens:
 * `app/src/test/screenshots/settings_*`. Also checks every page fits (see [SettingsShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class SettingsScreenshotTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { SettingsShots(rule, "") }

    @Test fun settings() = shots.shot("settings", 1f)
    @Test fun settings_font2() = shots.shot("settings", 2f)
    @Test fun settings_missing() = shots.shot("settings_missing", 1f)
    @Test fun settings_missing_font2() = shots.shot("settings_missing", 2f)
    @Test fun settings_end() = shots.scrolledShot(1f)
    @Test fun settings_end_font2() = shots.scrolledShot(2f)
    @Test fun history() = shots.shot("history", 1f)
    @Test fun history_font2() = shots.shot("history", 2f)
    @Test fun history_end() = shots.historyEndShot(1f)
    @Test fun history_end_font2() = shots.historyEndShot(2f)
    @Test fun history_empty() = shots.shot("history_empty", 1f)
    @Test fun history_empty_font2() = shots.shot("history_empty", 2f)
    @Test fun exceptions() = shots.shot("exceptions", 1f)
    @Test fun exceptions_font2() = shots.shot("exceptions", 2f)
    @Test fun permissions_all() = shots.shot("permissions_all", 1f)
    @Test fun permissions_all_font2() = shots.shot("permissions_all", 2f)
    @Test fun permissions_missing() = shots.shot("permissions_missing", 1f)
    @Test fun permissions_missing_font2() = shots.shot("permissions_missing", 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
