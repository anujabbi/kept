package com.example.kept.feature.lock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** On screen, Sprig really loses its colour as the countdown runs out (`saturate(1 → 0.25)`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class BreakFadePixelTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Mean HSV saturation over Sprig's box (the overcast sky and cream hill behind it are near grey and stay so). */
    private fun sprigSaturation(): Float {
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val b = rule.onNodeWithTag("break_sprig").fetchSemanticsNode().boundsInRoot
        var sum = 0f
        var n = 0
        for (x in b.left.toInt().coerceAtLeast(0) until b.right.toInt().coerceAtMost(bitmap.width) step 3) {
            for (y in b.top.toInt().coerceAtLeast(0) until b.bottom.toInt().coerceAtMost(bitmap.height) step 3) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16 and 0xFF) / 255f
                val g = (c shr 8 and 0xFF) / 255f
                val bl = (c and 0xFF) / 255f
                val max = maxOf(r, g, bl)
                val min = minOf(r, g, bl)
                if (max > 0f) sum += (max - min) / max
                n++
            }
        }
        return sum / n
    }

    @Test fun sprigLosesItsColourOverTheCountdown() {
        val left = mutableIntStateOf(60)
        rule.setContent { Shot { BreakLockScreen(BreakFixtures.NORMAL, secondsLeft = left.intValue, onCancel = {}, onConfirm = {}) } }
        rule.waitForIdle()
        val full = sprigSaturation()
        left.intValue = 30
        rule.waitForIdle()
        val half = sprigSaturation()
        left.intValue = 0
        rule.waitForIdle()
        val drained = sprigSaturation()
        assertTrue("saturation $full → $half → $drained", half < full * 0.9f && drained < half * 0.8f)
    }
}
