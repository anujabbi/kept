package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Weekly goals and the setup screen (issue #165, mockup 1; story #173) at 412 × 915 dp, font
 * scale 1.0 and 2.0: the list with two goals, empty and at the limit of three; setup empty,
 * filled (Exercise, 4 days a week), with 7 selected and its hint, and an existing goal with
 * "Delete goal". Goldens: `app/src/test/screenshots/weekly_goals_*`. Also checks every state fits
 * (see [WeeklyGoalsShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class WeeklyGoalsScreenshotTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { WeeklyGoalsShots(rule, "") }

    @Test fun list() = shots.shot("list", 1f)
    @Test fun list_font2() = shots.shot("list", 2f)
    @Test fun listEmpty() = shots.shot("list_empty", 1f)
    @Test fun listEmpty_font2() = shots.shot("list_empty", 2f)
    @Test fun listLimit() = shots.shot("list_limit", 1f)
    @Test fun listLimit_font2() = shots.shot("list_limit", 2f)
    @Test fun setup() = shots.shot("setup", 1f)
    @Test fun setup_font2() = shots.shot("setup", 2f)
    @Test fun setupFilled() = shots.shot("setup_filled", 1f)
    @Test fun setupFilled_font2() = shots.shot("setup_filled", 2f)
    @Test fun setupSeven() = shots.shot("setup_seven", 1f)
    @Test fun setupSeven_font2() = shots.shot("setup_seven", 2f)
    @Test fun setupEdit() = shots.shot("setup_edit", 1f)
    @Test fun setupEdit_font2() = shots.shot("setup_edit", 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
