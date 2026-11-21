package com.example.kept.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons the Daybreak design source draws inline (24-unit view box, round caps and
 * joins). Paths are copied from the `.dc.html` files; tint them with `Icon(tint = …)`.
 */
object DaybreakIcons {
    /** Tab bar, Today: a sun on the horizon (`Main`). */
    val Today: ImageVector = stroke("Today", 2.2f, circle(12f, 13f, 4f), "M12 3v2M4.5 6.5l1.5 1.5M19.5 6.5L18 8M3 20h18")

    /** Tab bar, Sprig: a sprout (`Main`). */
    val Sprig: ImageVector = stroke("Sprig", 2.2f, "M12 21v-9M12 12c-4 0-6-2-6-6 4 0 6 2 6 6zM12 15c3.5 0 5.5-2 5.5-5.5-3.5 0-5.5 2-5.5 5.5z")

    /** Tab bar, Settings: two sliders (`Main`). */
    val Settings: ImageVector = stroke("Settings", 2.2f, "M4 7h10M18 7h2M4 17h2M10 17h10", circle(16f, 7f, 2f), circle(8f, 17f, 2f))

    /** "Lock is off" banner (`A18-Parts`). */
    val Warning: ImageVector = stroke("Warning", 2.2f, "M12 3l9.5 17H2.5z", "M12 10v4M12 17.5v.01")

    /** "Too late" banner (`A18-Parts`). */
    val Clock: ImageVector = stroke("Clock", 2.2f, circle(12f, 12f, 8.5f), "M12 7.5V12l3 2")

    /** "Add your first habit" tile (`A18-Parts`). */
    val Plus: ImageVector = stroke("Plus", 2.6f, "M12 5v14M5 12h14")

    /** The tick inside a done habit dot (`A18-Parts`). */
    val Check: ImageVector = stroke("Check", 3.4f, "M5 12.5l4.5 4.5L19 7.5")

    /** "Snap photo" on a photo habit (`A18-Parts`). */
    val Camera: ImageVector = stroke("Camera", 2.2f, "M4 8h3l2-3h6l2 3h3v11H4z", circle(12f, 13f, 3.5f))

    /** Streak pill (`Main`). */
    val Flame: ImageVector = stroke("Flame", 2.4f, "M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3 0-6 1-9z")

    /** "Shield ready" (`Main`). */
    val Shield: ImageVector = stroke("Shield", 2.4f, "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z")

    /** Lock screen, "Phone" (`A3-Lock`). */
    val Phone: ImageVector = stroke("Phone", 2.4f, "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z")

    /** An SVG `<circle>` as path data: two half-circle arcs. */
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = addPathNodes(d),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()
}
