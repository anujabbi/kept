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

    /**
     * Break the lock, "What it costs" (`A4-Break`): duotone icons, a coloured line over a pale fill,
     * drawn in their own colours (use them with `tint = Color.Unspecified`). Each sits in a 40dp
     * round tile of [BreakTile]'s colour.
     */
    val Wilt: ImageVector = duotone(
        "Wilt", Color(0xFFB23A3A),
        Part("M12 21v-8"),
        Part("M12 13c-1-3-3.5-4.5-7-4 .3 3.6 3 5.6 7 4z", fill = Color(0xFFF3B7B7)),
        Part("M12 16c1.5-2.6 4-3.3 6.5-2.4-1 2.8-3.6 3.8-6.5 2.4z", fill = Color(0xFFF3B7B7)),
        Part("M8 21h8"),
    )

    /** Streak stays: a flame with a hot core. */
    val StreakFlame: ImageVector = duotone(
        "StreakFlame", Color(0xFFC26A00),
        Part("M12 3c.8 3.8 5 5.6 5 10.5a5 5 0 0 1-10 0c0-2.2 1-3.6 2.4-4.6.1 2 .9 3.1 2 3.1 0-3.4-.4-5.8.6-9z", fill = Color(0xFFFFD38A)),
        Part("M12 13.5c.6 1.6 2.2 2.2 2.2 3.8a2.2 2.2 0 0 1-4.4 0c0-1 .6-1.6 1.2-2 .1.8.5 1.2 1 1.2 0-1.1-.3-2 0-3z", fill = Color(0xFFFF9F2E), stroked = false),
    )

    /**
     * Breaks left: a ticket with a perforation. The design's `stroke-dasharray="1.6 2.2"` on
     * `M14.5 7v10` is drawn as its three dashes, since a vector path has no dash pattern.
     */
    val Ticket: ImageVector = duotone(
        "Ticket", Color(0xFF5B4BD6),
        Part("M4 7.5A1.5 1.5 0 0 1 5.5 6h13A1.5 1.5 0 0 1 20 7.5V10a2 2 0 0 0 0 4v2.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 16.5V14a2 2 0 0 0 0-4z", fill = Color(0xFFCFC8FF)),
        Part("M14.5 7v1.6M14.5 10.8v1.6M14.5 14.6v1.6"),
    )

    /** The round tile behind each cost icon. */
    object BreakTile {
        val wilt = Color(0xFFFBE6E6)
        val streak = Color(0xFFFFF0D6)
        val breaks = Color(0xFFECE9FF)
    }

    /** One `<path>` of a duotone icon: filled with [fill] if set, outlined in the icon's colour if [stroked]. */
    private class Part(val d: String, val fill: Color? = null, val stroked: Boolean = true)

    /** A duotone icon at the design's 1.8 stroke width, round caps and joins. */
    private fun duotone(name: String, line: Color, vararg parts: Part): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                parts.forEach { p ->
                    addPath(
                        pathData = addPathNodes(p.d),
                        fill = p.fill?.let { SolidColor(it) },
                        stroke = if (p.stroked) SolidColor(line) else null,
                        strokeLineWidth = 1.8f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

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
