package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Your promises (#236) at 360 × 640 dp, font scale 1.0 and 2.0. Goldens:
 * `app/src/test/screenshots/promises_360_*`. Also checks every state fits
 * ([PromisesShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class PromisesNarrowScreenTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { PromisesShots(rule, "_360") }

    @Test fun list() = shots.shot("list", 1f)
    @Test fun list_font2() = shots.shot("list", 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
