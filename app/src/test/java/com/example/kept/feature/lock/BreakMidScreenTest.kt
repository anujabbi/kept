package com.example.kept.feature.lock

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kept.core.ui.shots.Shot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Break the lock in Daybreak (`A4-Break.dc.html`, `A4-Break-last.dc.html`) at 390 × 844dp, the
 * design's own size. It takes the same layout branch as 412 × 915dp, so the goldens live there
 * (`break_*`); this class checks that Sprig clears the text and sits behind the hill, that the
 * clock never clips and that both buttons are whole, in every state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class BreakMidScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test fun everyStateFits() {
        val current = mutableStateOf(BreakFixtures.NORMAL)
        val scale = mutableFloatStateOf(1f)
        val left = mutableIntStateOf(60)
        rule.setContent { Shot(scale.floatValue) { BreakLockScreen(current.value, secondsLeft = left.intValue, onCancel = {}, onConfirm = {}) } }
        assertBreakFits(rule, current, scale, left)
    }

    /** At font 1.0 the whole screen fits: nothing scrolls, the clock sits above the buttons. */
    @Test fun nothingScrollsAtFont1() = assertFitsWithoutScroll(rule)
}
