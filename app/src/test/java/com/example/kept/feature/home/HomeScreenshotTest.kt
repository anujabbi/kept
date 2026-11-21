package com.example.kept.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.kept.core.ui.shots.Shot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home in Daybreak (`Main.dc.html`, `A2-Home-6.dc.html`, `A18-Parts.dc.html`) at 412 × 915dp,
 * font scale 1.0 and 2.0. Goldens: `app/src/test/screenshots/home_*`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HomeScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, fontScale: Float, s: HomeUiState) {
        rule.setContent { Shot(fontScale) { HomeFrame(s) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/home_${name}_font${fontScale}.png")
    }

    private fun both(name: String, s: HomeUiState, fontScale: Float) = shot(name, fontScale, s)

    @Test fun done0of2() = both("0of2", HomeFixtures.state(2, 0), 1f)
    @Test fun done0of2_font2() = both("0of2", HomeFixtures.state(2, 0), 2f)
    @Test fun done1of2() = both("1of2", HomeFixtures.state(2, 1), 1f)
    @Test fun done1of2_font2() = both("1of2", HomeFixtures.state(2, 1), 2f)
    @Test fun done2of2() = both("2of2", HomeFixtures.state(2, 2), 1f)
    @Test fun done2of2_font2() = both("2of2", HomeFixtures.state(2, 2), 2f)
    @Test fun done2of6() = both("2of6", HomeFixtures.state(6, 2), 1f)
    @Test fun done2of6_font2() = both("2of6", HomeFixtures.state(6, 2), 2f)
    @Test fun done6of6() = both("6of6", HomeFixtures.state(6, 6), 1f)
    @Test fun done6of6_font2() = both("6of6", HomeFixtures.state(6, 6), 2f)
    @Test fun done0of10() = both("0of10", HomeFixtures.state(10, 0), 1f)
    @Test fun done0of10_font2() = both("0of10", HomeFixtures.state(10, 0), 2f)
    @Test fun lockOff() = both("lock_off", HomeFixtures.state(2, 0, lockOff = true), 1f)
    @Test fun lockOff_font2() = both("lock_off", HomeFixtures.state(2, 0, lockOff = true), 2f)
    @Test fun pastGiveUp() = both("past_give_up", HomeFixtures.state(2, 1, late = 1, pastDue = true), 1f)
    @Test fun pastGiveUp_font2() = both("past_give_up", HomeFixtures.state(2, 1, late = 1, pastDue = true), 2f)
    @Test fun noHabits() = both("no_habits", HomeFixtures.state(0, 0), 1f)
    @Test fun noHabits_font2() = both("no_habits", HomeFixtures.state(0, 0), 2f)
    @Test fun shieldUsed() = both("shield_used", HomeFixtures.state(2, 0, shieldReady = false, week = HomeFixtures.everyDayKind()), 1f)
    @Test fun shieldUsed_font2() = both("shield_used", HomeFixtures.state(2, 0, shieldReady = false, week = HomeFixtures.everyDayKind()), 2f)
    @Test fun firstLanding() = both("first_landing", HomeFixtures.state(3, 0, firstLanding = true, lockOff = true), 1f)
    @Test fun firstLanding_font2() = both("first_landing", HomeFixtures.state(3, 0, firstLanding = true, lockOff = true), 2f)

    @Test fun shieldOpen() {
        rule.setContent { Shot(1f) { HomeFrame(HomeFixtures.state(2, 1)) } }
        rule.onNodeWithTag("home_shield").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/home_shield_open_font1.0.png")
    }

    @Test fun shieldOpenCompact() {
        rule.setContent { Shot(1f) { HomeFrame(HomeFixtures.state(6, 2)) } }
        rule.onNodeWithTag("home_shield").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/home_shield_open_compact_font1.0.png")
    }
}
