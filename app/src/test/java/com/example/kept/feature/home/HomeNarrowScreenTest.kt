package com.example.kept.feature.home

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.example.kept.core.ui.shots.Shot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home on a small phone, 360 × 640dp (spec, Fit and finish): Sprig clears the text in every
 * state, and goldens for both layouts at font 1.0 and 2.0 (`home_360_*`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class HomeNarrowScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test fun sprigNeverOverlapsTheHeadlineBlock() {
        val current = mutableStateOf(HomeFixtures.state(2, 0))
        val scale = mutableFloatStateOf(1f)
        rule.setContent { Shot(scale.floatValue) { HomeFrame(current.value) } }
        assertSprigClearsText(rule, current, scale)
    }

    @Test fun sprigSitsBehindTheHill() {
        val current = mutableStateOf(HomeFixtures.state(2, 0))
        val scale = mutableFloatStateOf(1f)
        rule.setContent { Shot(scale.floatValue) { HomeFrame(current.value) } }
        assertSprigSitsBehindHill(rule, current, scale)
    }

    @Test fun compactListKeepsRoomForRowsAtLargeFonts() {
        rule.setContent { Shot(2f) { HomeFrame(HomeFixtures.state(6, 6)) } }
        rule.waitForIdle()
        val list = rule.onNodeWithTag("home_habit_list").fetchSemanticsNode()
        val h = list.size.height / rule.density.density
        org.junit.Assert.assertTrue("habit list is ${h}dp", h >= 127.9f)
        // The week is still there, below the fold.
        rule.onNodeWithTag("home_shield").performScrollTo().assertIsDisplayed()
    }

    private fun shot(name: String, fontScale: Float, s: HomeUiState) {
        rule.setContent { Shot(fontScale) { HomeFrame(s) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/home_360_${name}_font${fontScale}.png")
    }

    @Test fun done0of2() = shot("0of2", 1f, HomeFixtures.state(2, 0))
    @Test fun done2of2_font2() = shot("2of2", 2f, HomeFixtures.state(2, 2))
    @Test fun done0of6() = shot("0of6", 1f, HomeFixtures.state(6, 0))
    @Test fun done6of6_font2() = shot("6of6", 2f, HomeFixtures.state(6, 6))

    /** The weekly card (story #174) on the small phone, scrolled into view. */
    private fun weeklyShot(name: String, fontScale: Float, s: HomeUiState, tag: String) {
        rule.setContent { Shot(fontScale) { HomeFrame(s) } }
        rule.onNodeWithTag(tag).performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/home_360_weekly_${name}_font${fontScale}.png")
    }

    @Test fun weeklyTodayCounts() = weeklyShot("today_counts", 1f, HomeFixtures.state(2, 1, weekly = listOf(HomeFixtures.todayCounts())), "home_weekly_card_1")
    @Test fun weeklyTodayCounts_font2() = weeklyShot("today_counts", 2f, HomeFixtures.state(2, 1, weekly = listOf(HomeFixtures.todayCounts())), "home_weekly_card_1")
    @Test fun weeklyAdd() = weeklyShot("add", 1f, HomeFixtures.state(2, 0), "home_weekly_add")
    @Test fun weeklyAdd_font2() = weeklyShot("add", 2f, HomeFixtures.state(2, 0), "home_weekly_add")

    @Test fun headlineNeverBreaksInsideAWord() {
        val current = mutableStateOf(HomeFixtures.state(2, 0))
        val scale = mutableFloatStateOf(1f)
        rule.setContent { Shot(scale.floatValue) { HomeFrame(current.value) } }
        assertHomeHeadlineWhole(rule, current, scale)
    }
}
