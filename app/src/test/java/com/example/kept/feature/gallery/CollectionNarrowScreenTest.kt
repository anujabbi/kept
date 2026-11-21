package com.example.kept.feature.gallery

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Sprig tab in Daybreak (`A6-Collection.dc.html`) at 360 × 640 dp, font scale 1.0 and 2.0, at
 * day 0, day 12 and day 100, plus the page scrolled to its end. Goldens:
 * `app/src/test/screenshots/collection_360_*`. Also checks every state fits (see [CollectionShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class CollectionNarrowScreenTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { CollectionShots(rule, "_360") }

    @Test fun day0() = shots.shot("day0", CollectionFixtures.DAY_0, 1f)
    @Test fun day0_font2() = shots.shot("day0", CollectionFixtures.DAY_0, 2f)
    @Test fun day12() = shots.shot("day12", CollectionFixtures.DAY_12, 1f)
    @Test fun day12_font2() = shots.shot("day12", CollectionFixtures.DAY_12, 2f)
    @Test fun day100() = shots.shot("day100", CollectionFixtures.DAY_100, 1f)
    @Test fun day100_font2() = shots.shot("day100", CollectionFixtures.DAY_100, 2f)
    @Test fun day12_end() = shots.scrolledShot("day12", CollectionFixtures.DAY_12, 1f)
    @Test fun day12_end_font2() = shots.scrolledShot("day12", CollectionFixtures.DAY_12, 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
