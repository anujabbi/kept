package com.example.kept.core.ui.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyScaffold
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class SkyScaffoldTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // A sky height that lands on a fractional pixel at 2.625x and 3.5x.
    private val skyHeight = 301.dp

    /**
     * No sky colour between the sky and the hill: every pixel from 4dp above the sky's bottom
     * edge to just below it is ground colour, across the full width.
     */
    private fun assertNoSeam(name: String) {
        rule.setContent { KeptTheme(reduceMotion = true) { SkyScaffold(sky = Sky.night, skyHeight = skyHeight) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/sky_seam_$name.png")
        val density = rule.activity.resources.displayMetrics.density
        val pixels = contentPixels()
        val from = floor((skyHeight.value - 4f) * density).toInt()
        val to = ceil(skyHeight.value * density).toInt() + 1
        for (y in from..to) for (x in 0 until pixels.width) {
            assertEquals("pixel ($x, $y) at ${density}x", DaybreakColors.cream, pixels[x, y])
        }
        // And the sky is really there just above the overlap.
        assertEquals(Sky.night, pixels[pixels.width / 2, from - (60 * density).toInt()])
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun noSeamAt1x() = assertNoSeam("1x")

    @Test @Config(qualifiers = "w412dp-h915dp-420dpi")
    fun noSeamAt2_625x() = assertNoSeam("2.625x")

    @Test @Config(qualifiers = "w412dp-h915dp-560dpi")
    fun noSeamAt3_5x() = assertNoSeam("3.5x")

    /** The activity's content drawn to a software bitmap (Compose's `captureToImage` needs PixelCopy). */
    private fun contentPixels(): Pixels {
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        return Pixels(bitmap)
    }

    class Pixels(private val bitmap: Bitmap) {
        val width get() = bitmap.width
        operator fun get(x: Int, y: Int) = Color(bitmap.getPixel(x, y))
    }

    private fun skyPixel(): Color {
        val px = contentPixels()
        return px[px.width / 2, 20]
    }

    /** Shows a night sky, then pauses the clock and asks for gold. */
    private fun changeNightToGold(reduceMotion: Boolean) {
        var sky by mutableStateOf(Sky.night)
        rule.setContent { KeptTheme(reduceMotion = reduceMotion) { SkyScaffold(sky = sky, skyHeight = 200.dp) } }
        rule.waitForIdle()
        assertEquals(Sky.night, skyPixel())
        rule.mainClock.autoAdvance = false
        sky = Sky.gold
    }

    /**
     * Steps the paused clock one frame at a time. Between frames it sends snapshot apply
     * notifications, which on a device the global snapshot manager does on its own, so a value
     * written inside a frame (a snap) reaches the draw as it would on a phone.
     */
    private fun advanceFrames(ms: Long) {
        repeat((ms / FRAME_MS).toInt()) {
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread { Snapshot.sendApplyNotifications() }
        }
    }

    // The paused test clock needs about 100ms (a few frames) before a state change reaches the
    // drawn pixels, so both tests look at 150ms: a snap is done by then, a 700ms fade is not.

    @Test fun skyColourAnimatesOver700ms() {
        changeNightToGold(reduceMotion = false)
        advanceFrames(150)
        val early = skyPixel()
        assertNotEquals("not gold yet at 150ms", Sky.gold, early)
        advanceFrames(250)
        val mid = skyPixel()
        assertNotEquals("still moving at 400ms", Sky.gold, mid)
        assertNotEquals("moved by 400ms", Sky.night, mid)
        assertNotEquals("moving between 150 and 400ms", early, mid)
        advanceFrames(500)
        assertEquals("gold once the 700ms fade is over", Sky.gold, skyPixel())
    }

    @Test fun reduceMotionSnapsTheSky() {
        changeNightToGold(reduceMotion = true)
        advanceFrames(150)
        assertEquals("gold at once, no 700ms fade", Sky.gold, skyPixel())
    }

    private fun lightStatusBarIcons(): Boolean {
        val window = rule.activity.window
        return !WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
    }

    @Test fun statusBarIconsFollowTheSky() {
        var sky by mutableStateOf(Sky.night)
        var shown by mutableStateOf(true)
        rule.setContent { KeptTheme(reduceMotion = true) { if (shown) SkyScaffold(sky = sky, skyHeight = 200.dp) } }
        rule.waitForIdle()
        assertTrue("light icons on night", lightStatusBarIcons())

        sky = Sky.gold
        rule.waitForIdle()
        assertFalse("dark icons on gold", lightStatusBarIcons())

        sky = Sky.lockRamp[4]
        rule.waitForIdle()
        assertTrue("light icons on the lock sky", lightStatusBarIcons())

        shown = false
        rule.waitForIdle()
        assertFalse("dark icons once the sky is gone", lightStatusBarIcons())
    }

    @Test @Config(qualifiers = "w412dp-h915dp-night-420dpi")
    fun darkModeStillRendersLight() {
        var systemDark = false
        var background = Color.Unspecified
        rule.setContent {
            KeptTheme(reduceMotion = true) {
                systemDark = isSystemInDarkTheme()
                background = MaterialTheme.colorScheme.background
            }
        }
        rule.waitForIdle()
        assertTrue("the phone is in dark mode", systemDark)
        assertEquals(DaybreakColors.cream, background)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
