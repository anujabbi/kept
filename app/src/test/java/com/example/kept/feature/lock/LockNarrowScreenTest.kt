package com.example.kept.feature.lock

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.kept.core.ui.shots.Shot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The lock screen in Daybreak (`A3-Lock.dc.html`) at 360 × 640dp, a small phone, font scale 1.0 and 2.0:
 * the issue's states 0/2, 1/2, 2/2 (open) and 2/10, plus 10/10. Goldens:
 * `app/src/test/screenshots/lock_360_*`. Also checks that Sprig clears the text, sits behind the
 * hill, and that the footer is never cut off, in every state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class LockNarrowScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, fontScale: Float, s: LockUi) {
        rule.setContent { Shot(fontScale) { LockScreen(s, "Chrome", LockActions()) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/lock_360_${name}_font${fontScale}.png")
    }

    @Test fun done0of2() = shot("0of2", 1f, LockFixtures.state(2, 0))
    @Test fun done0of2_font2() = shot("0of2", 2f, LockFixtures.state(2, 0))
    @Test fun done1of2() = shot("1of2", 1f, LockFixtures.state(2, 1))
    @Test fun done1of2_font2() = shot("1of2", 2f, LockFixtures.state(2, 1))
    @Test fun open2of2() = shot("2of2_open", 1f, LockFixtures.state(2, 2))
    @Test fun open2of2_font2() = shot("2of2_open", 2f, LockFixtures.state(2, 2))
    @Test fun done2of10() = shot("2of10", 1f, LockFixtures.state(10, 2))
    @Test fun done2of10_font2() = shot("2of10", 2f, LockFixtures.state(10, 2))
    @Test fun open10of10() = shot("10of10_open", 1f, LockFixtures.state(10, 10))
    @Test fun open10of10_font2() = shot("10of10_open", 2f, LockFixtures.state(10, 10))

    @Test fun everyStateFits() {
        val current = mutableStateOf(LockFixtures.state(2, 0))
        val scale = mutableFloatStateOf(1f)
        val label = mutableStateOf("Chrome")
        rule.setContent { Shot(scale.floatValue) { LockScreen(current.value, label.value, LockActions()) } }
        assertLockFits(rule, current, scale, label)
    }

    @Test fun appNamesNeverBreakInsideAWord() {
        val current = mutableStateOf(LockFixtures.state(2, 0))
        val scale = mutableFloatStateOf(1f)
        val label = mutableStateOf("Instagram")
        rule.setContent { Shot(scale.floatValue) { LockScreen(current.value, label.value, LockActions()) } }
        assertAppNamesWhole(rule, current, scale, label)
    }
}
