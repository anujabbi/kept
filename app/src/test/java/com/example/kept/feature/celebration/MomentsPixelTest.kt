package com.example.kept.feature.celebration

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.example.kept.core.domain.MomentMotion
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.PromiseKeptMoment
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On screen, the "Promise kept" pop and ring really draw (and with reduce motion really do not):
 * pixels are counted in the pop box, Sprig's purple against the gold and the ring's near-white.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class MomentsPixelTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val reduce = mutableStateOf(false)
    private val atMs = mutableStateOf(0L)

    private fun show() {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) {
                KeptTheme(reduceMotion = reduce.value) {
                    // A new composition per frame asked for, so the clock is rebuilt at it.
                    androidx.compose.runtime.key(atMs.value, reduce.value) {
                        PromiseKeptMoment(SprigForm.BLOOM, 12, onCarryOn = {}, frozenAtMs = atMs.value)
                    }
                }
            }
        }
    }

    /** Share of pixels in the pop box for which [match] holds. */
    private fun share(match: (r: Int, g: Int, b: Int) -> Boolean): Float {
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val box = rule.onNodeWithTag("moment_pop", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        var hit = 0
        var n = 0
        for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(bitmap.width) step 3) {
            for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(bitmap.height) step 3) {
                val c = bitmap.getPixel(x, y)
                if (match(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)) hit++
                n++
            }
        }
        return hit.toFloat() / n
    }

    private val sprigPurple = { r: Int, _: Int, b: Int -> b > r }
    /** Gold lightened by the 70% white ring (gold itself is `#FFC23D`, blue 61). */
    private val ringWhite = { r: Int, g: Int, b: Int -> r > 240 && g > 200 && b > 85 }

    @Test fun sprigPopsInAndTheRingFades() {
        show()
        atMs.value = 0
        val before = share(sprigPurple)
        atMs.value = 1_000
        val ring = share(ringWhite)
        atMs.value = MomentMotion.END_MS.toLong()
        val after = share(sprigPurple)
        val ringAfter = share(ringWhite)
        assertTrue("Sprig is not drawn before the pop ($before) and is after ($after)", before < 0.01f && after > 0.15f)
        assertTrue("the ring shows mid-moment ($ring) and is gone at the end ($ringAfter)", ring > ringAfter + 0.02f)
    }

    @Test fun reduceMotionShowsSprigAtOnceWithNoRing() {
        reduce.value = true
        show()
        atMs.value = 1_000
        val sprig = share(sprigPurple)
        val ring = share(ringWhite)
        reduce.value = false
        val ringWithMotion = share(ringWhite)
        assertTrue("Sprig is there with no pop ($sprig)", sprig > 0.15f)
        assertTrue("no ring with reduce motion ($ring vs $ringWithMotion with motion)", ring + 0.02f < ringWithMotion)
    }
}
