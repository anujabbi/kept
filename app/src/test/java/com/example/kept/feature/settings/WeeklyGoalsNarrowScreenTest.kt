package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Weekly goals and the setup screen at 360 × 640 dp, font scale 1.0 and 2.0, in the same states
 * as [WeeklyGoalsScreenshotTest]. Goldens: `app/src/test/screenshots/weekly_goals_360_*`. Also
 * checks every state fits (see [WeeklyGoalsShots.everyStateFits]): seven day chips of 48dp each
 * are the tightest fit here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class WeeklyGoalsNarrowScreenTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { WeeklyGoalsShots(rule, "_360") }

    @Test fun list() = shots.shot("list", 1f)
    @Test fun listEmpty() = shots.shot("list_empty", 1f)
    @Test fun listEmpty_font2() = shots.shot("list_empty", 2f)
    @Test fun listLimit() = shots.shot("list_limit", 1f)
    @Test fun listLimit_font2() = shots.shot("list_limit", 2f)
    @Test fun setup() = shots.shot("setup", 1f)
    @Test fun setupFilled() = shots.shot("setup_filled", 1f)
    @Test fun setupFilled_font2() = shots.shot("setup_filled", 2f)
    @Test fun setupSeven() = shots.shot("setup_seven", 1f)
    @Test fun setupSeven_font2() = shots.shot("setup_seven", 2f)
    @Test fun setupEdit() = shots.shot("setup_edit", 1f)
    @Test fun setupEdit_font2() = shots.shot("setup_edit", 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
