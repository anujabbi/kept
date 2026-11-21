package com.example.kept.feature.lock

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
 * Break the lock in Daybreak (`A4-Break.dc.html`, `A4-Break-last.dc.html`) at 360 × 640dp,
 * font scale 1.0 and 2.0: at 60s, at 0s, and the last resort. Goldens:
 * `app/src/test/screenshots/break_360_*`. Also checks that Sprig clears the text and sits
 * behind the hill, that the clock never clips and that both buttons are whole, in every state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class BreakNarrowScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, fontScale: Float, s: LockUi, left: Int) {
        rule.setContent { Shot(fontScale) { BreakLockScreen(s, secondsLeft = left, onCancel = {}, onConfirm = {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/break_360_${name}_font${fontScale}.png")
    }

    @Test fun at60s() = shot("60s", 1f, BreakFixtures.NORMAL, 60)
    @Test fun at60s_font2() = shot("60s", 2f, BreakFixtures.NORMAL, 60)
    @Test fun at0s() = shot("0s", 1f, BreakFixtures.NORMAL, 0)
    @Test fun at0s_font2() = shot("0s", 2f, BreakFixtures.NORMAL, 0)
    @Test fun lastResort() = shot("last", 1f, BreakFixtures.LAST_RESORT, 60)
    @Test fun lastResort_font2() = shot("last", 2f, BreakFixtures.LAST_RESORT, 60)

    @Test fun everyStateFits() {
        val current = mutableStateOf(BreakFixtures.NORMAL)
        val scale = mutableFloatStateOf(1f)
        val left = mutableIntStateOf(60)
        rule.setContent { Shot(scale.floatValue) { BreakLockScreen(current.value, secondsLeft = left.intValue, onCancel = {}, onConfirm = {}) } }
        assertBreakFits(rule, current, scale, left)
    }

    /**
     * At 360 × 640 the sky comes up and Sprig shrinks; what is left scrolls a little above the
     * fixed buttons, and the clock can be scrolled to.
     */
    @Test fun smallScreenScrollsMinimally() = assertScrollsAtMost(rule, maxScrollDp = 16f)
}
