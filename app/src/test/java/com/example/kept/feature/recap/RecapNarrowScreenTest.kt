package com.example.kept.feature.recap

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.kept.core.ui.shots.Shot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The recap in Daybreak (`A5-Recap.dc.html` and its `-slipped`, `-shield`, `-lockoff`, `-late`,
 * `-writtenoff` wrappers) at 360 × 640dp, font scale 1.0 and 2.0: all six outcomes and the
 * final form. Goldens: `app/src/test/screenshots/recap_360_*`. Also checks every state fits
 * (see [assertRecapFits]) and how far the page scrolls at font 1.0.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class RecapNarrowScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, fontScale: Float, s: RecapUi) {
        rule.setContent { Shot(fontScale) { RecapContent(s, onClose = {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/recap_360_${name}_font${fontScale}.png")
    }

    @Test fun kept() = shot("kept", 1f, RecapFixtures.KEPT)
    @Test fun kept_font2() = shot("kept", 2f, RecapFixtures.KEPT)
    @Test fun shield() = shot("shield", 1f, RecapFixtures.SHIELD)
    @Test fun shield_font2() = shot("shield", 2f, RecapFixtures.SHIELD)
    @Test fun lockOff() = shot("lockoff", 1f, RecapFixtures.LOCK_OFF)
    @Test fun lockOff_font2() = shot("lockoff", 2f, RecapFixtures.LOCK_OFF)
    @Test fun slipped() = shot("slipped", 1f, RecapFixtures.SLIPPED)
    @Test fun slipped_font2() = shot("slipped", 2f, RecapFixtures.SLIPPED)
    @Test fun tooLate() = shot("late", 1f, RecapFixtures.TOO_LATE)
    @Test fun tooLate_font2() = shot("late", 2f, RecapFixtures.TOO_LATE)
    @Test fun writtenOff() = shot("writtenoff", 1f, RecapFixtures.WRITTEN_OFF)
    @Test fun writtenOff_font2() = shot("writtenoff", 2f, RecapFixtures.WRITTEN_OFF)
    @Test fun finalForm() = shot("final", 1f, RecapFixtures.FINAL)
    @Test fun finalForm_font2() = shot("final", 2f, RecapFixtures.FINAL)

    @Test fun everyStateFits() {
        val current = mutableStateOf(RecapFixtures.KEPT)
        val scale = mutableFloatStateOf(1f)
        rule.setContent { Shot(scale.floatValue) { RecapContent(current.value, onClose = {}) } }
        assertRecapFits(rule, current, scale, 360f)
    }

    /** At 360 × 640 the crest comes up, Sprig shrinks and the page is compact, so nothing scrolls at font 1.0. */
    @Test fun scrollAtFont1() {
        val current = mutableStateOf(RecapFixtures.KEPT)
        rule.setContent { Shot(1f) { RecapContent(current.value, onClose = {}) } }
        for ((name, ui) in RecapFixtures.ALL) {
            current.value = ui
            rule.waitForIdle()
            val range = rule.recapScrollDp()
            assertTrue("$name scrolls ${range}dp", range <= 0.5f)
        }
    }
}
