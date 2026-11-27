package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Your promises (#236) at 412 × 915 dp, font scale 1.0 and 2.0: the mockup's six, top and end,
 * and both sides full. Goldens: `app/src/test/screenshots/promises_*`. Also checks every state
 * fits ([PromisesShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class PromisesScreenshotTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { PromisesShots(rule, "") }

    @Test fun list() = shots.shot("list", 1f)
    @Test fun list_font2() = shots.shot("list", 2f)
    @Test fun listEnd() = shots.shotEnd("list", 1f)
    @Test fun listEnd_font2() = shots.shotEnd("list", 2f)
    @Test fun fullEnd() = shots.shotEnd("full", 1f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
