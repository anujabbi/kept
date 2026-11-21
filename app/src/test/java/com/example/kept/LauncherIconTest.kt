package com.example.kept

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sign

/**
 * The Daybreak launcher icon (issue #155), drawn from the real `R.mipmap.ic_launcher` layers the
 * way a launcher does: both 108dp layers drawn 1.5x the visible size, clipped by a mask. Goldens:
 * `app/src/test/screenshots/launcher_icon_*`, one sheet per size (48dp and 108dp) with circle,
 * squircle, rounded-square, the themed (monochrome) icon, and the full canvas with the 72dp
 * viewport and the 66dp safe zone drawn on it. Also checks the face, sprout and flower stay in
 * the safe zone, and that the round icon is the same drawing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "xxhdpi")
class LauncherIconTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val density get() = context.resources.displayMetrics.density

    private fun icon(id: Int = R.mipmap.ic_launcher): AdaptiveIconDrawable =
        context.getDrawable(id) as AdaptiveIconDrawable

    /** Draws [layer] the way AdaptiveIconDrawable does: its bounds are the visible [size] plus 25% each side. */
    private fun Canvas.layer(layer: Drawable, left: Float, top: Float, size: Float) {
        val inset = size / 4f
        layer.setBounds((left - inset).toInt(), (top - inset).toInt(), (left + size + inset).toInt(), (top + size + inset).toInt())
        layer.draw(this)
    }

    private fun circle(r: RectF) = Path().apply { addOval(r, Path.Direction.CW) }
    private fun roundedSquare(r: RectF) = Path().apply { addRoundRect(r, r.width() * 0.22f, r.width() * 0.22f, Path.Direction.CW) }
    private fun squircle(r: RectF) = Path().apply {
        val n = 4.0
        val a = r.width() / 2.0
        for (i in 0..360) {
            val t = Math.toRadians(i.toDouble())
            val x = r.centerX() + a * sign(cos(t)) * abs(cos(t)).pow(2 / n)
            val y = r.centerY() + a * sign(sin(t)) * abs(sin(t)).pow(2 / n)
            if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
        }
        close()
    }

    private fun Canvas.masked(mask: Path, body: Canvas.() -> Unit) {
        save(); clipPath(mask); body(); restore()
    }

    private fun sheet(sizeDp: Int): Bitmap {
        val s = sizeDp * density
        val pad = 16 * density
        val full = s * 1.5f
        val w = (pad + (s + pad) * 4 + full + pad).toInt()
        val h = (pad + full + pad).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#F4F1EC"))
        val d = icon()
        val top = pad + (full - s) / 2
        val masks = listOf(::circle, ::squircle, ::roundedSquare)
        masks.forEachIndexed { i, mask ->
            val left = pad + i * (s + pad)
            val r = RectF(left, top, left + s, top + s)
            c.masked(mask(r)) { layer(d.background, left, top, s); layer(d.foreground, left, top, s) }
        }
        // Themed icon (Android 13+): the launcher fills the mask with a theme colour and tints
        // the monochrome layer. Light Material You colours.
        val left = pad + 3 * (s + pad)
        val r = RectF(left, top, left + s, top + s)
        c.masked(circle(r)) {
            drawColor(Color.parseColor("#E8DEF8"))
            val mono = d.monochrome!!.mutate()
            mono.colorFilter = PorterDuffColorFilter(Color.parseColor("#4A4458"), PorterDuff.Mode.SRC_IN)
            layer(mono, left, top, s)
        }
        // The whole 108-unit canvas, unmasked, with the visible 72dp viewport and 66dp safe zone.
        val fl = pad + 4 * (s + pad)
        val ft = pad
        c.save(); c.clipRect(fl, ft, fl + full, ft + full)
        c.layer(d.background, fl + full / 6, ft + full / 6, s)
        c.layer(d.foreground, fl + full / 6, ft + full / 6, s)
        c.restore()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density; color = Color.parseColor("#1E1B3C") }
        c.drawRect(fl + full / 6, ft + full / 6, fl + full * 5 / 6, ft + full * 5 / 6, line)
        line.color = Color.parseColor("#D4380D")
        c.drawCircle(fl + full / 2, ft + full / 2, full * 33f / 108f, line)
        return bmp
    }

    @Test fun sheet48() = sheet(48).captureRoboImage("src/test/screenshots/launcher_icon_48dp.png")

    @Test fun sheet108() = sheet(108).captureRoboImage("src/test/screenshots/launcher_icon_108dp.png")

    /** One layer at 1px = 1/[scale] dp over the full 108dp canvas. */
    private fun render(layer: Drawable, scale: Int = 4): Bitmap {
        val px = 108 * scale
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        layer.setBounds(0, 0, px, px)
        layer.draw(Canvas(bmp))
        return bmp
    }

    @Test fun faceSproutAndFlowerSitInsideTheSafeZone() {
        val scale = 4
        val bmp = render(icon().foreground, scale)
        val important = mapOf(
            "eyes and mouth" to 0x1E1B3C, "blush" to 0xFF9EB5, "petals" to 0xFF8FB0, "flower centre" to 0xFFD25E,
            "left leaf" to 0x2FA866, "right leaf" to 0x5CCB86, "stem" to 0x218A52,
        )
        val found = mutableMapOf<String, Int>()
        var farthest = 0.0
        val centre = 54.0 * scale
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) != 255) continue
            val name = important.entries.firstOrNull { (_, rgb) ->
                abs(Color.red(p) - (rgb shr 16 and 0xFF)) <= 4 &&
                    abs(Color.green(p) - (rgb shr 8 and 0xFF)) <= 4 &&
                    abs(Color.blue(p) - (rgb and 0xFF)) <= 4
            }?.key ?: continue
            found[name] = (found[name] ?: 0) + 1
            farthest = maxOf(farthest, hypot(x + 0.5 - centre, y + 0.5 - centre) / scale)
        }
        assertEquals("every part was drawn", important.keys, found.keys)
        assertTrue("farthest face/flower pixel is ${"%.1f".format(farthest)}dp from centre; safe zone radius is 33dp", farthest <= 33.0)
    }

    @Test fun roundIconIsTheSameDrawing() {
        val a = icon(R.mipmap.ic_launcher)
        val b = icon(R.mipmap.ic_launcher_round)
        assertTrue(render(a.background).sameAs(render(b.background)))
        assertTrue(render(a.foreground).sameAs(render(b.foreground)))
        assertTrue(render(a.monochrome!!).sameAs(render(b.monochrome!!)))
    }

    @Test fun monochromeIsOneColourWithTheFaceCutOut() {
        val mono = icon().monochrome
        assertNotNull(mono)
        val bmp = render(mono!!, 4)
        // Map an SVG point inside Sprig's group to the 108dp canvas at 4px/dp.
        fun at(x: Double, y: Double): Int {
            val cx = 54 + (13.2 + 0.68 * x - 54) * 2 / 3
            val cy = 54 + (27 + 0.68 * y - 54) * 2 / 3
            return bmp.getPixel((cx * 4).toInt(), (cy * 4).toInt())
        }
        assertEquals("body is filled", 255, Color.alpha(at(60.0, 100.0)))
        assertEquals("eye is cut out", 0, Color.alpha(at(45.0, 67.5)))
        assertEquals("mouth is cut out", 0, Color.alpha(at(60.0, 83.0)))
        assertEquals("flower is filled", 255, Color.alpha(at(60.0, 9.0)))
        val colours = buildSet { for (y in 0 until bmp.height) for (x in 0 until bmp.width) bmp.getPixel(x, y).let { if (Color.alpha(it) == 255) add(it) } }
        assertEquals("one colour", 1, colours.size)
    }
}
