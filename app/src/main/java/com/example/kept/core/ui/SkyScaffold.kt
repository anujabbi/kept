package com.example.kept.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The hill's geometry (spec, The hill): an ellipse 120dp wider than the screen (60dp overhang
 * each side) with a 48dp vertical radius, whose shape reaches [SEAM_OVERLAP] below the sky's
 * bottom edge so no sky colour can show at the seam at any density.
 */
object Hill {
    val OVERHANG: Dp = 60.dp
    val RADIUS: Dp = 48.dp
    val SEAM_OVERLAP: Dp = 4.dp

    /** The hill's crest sits this far above the sky's bottom edge. */
    val RISE: Dp = RADIUS + SEAM_OVERLAP

    /**
     * The hill for a sky [skyHeightPx] tall in a box [width] × [height] px: an elliptic arc on
     * `(-overhang, crest, width + overhang, crest + 2 × radius)`, filled to the bottom.
     */
    fun path(width: Float, height: Float, skyHeightPx: Float, overhangPx: Float, radiusPx: Float, overlapPx: Float): Path {
        val crest = skyHeightPx - overlapPx - radiusPx
        val oval = Rect(-overhangPx, crest, width + overhangPx, crest + 2 * radiusPx)
        return Path().apply {
            moveTo(-overhangPx, oval.center.y)
            arcTo(oval, startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false)
            lineTo(width + overhangPx, height)
            lineTo(-overhangPx, height)
            close()
        }
    }
}

/**
 * A Daybreak screen: a [sky] band [skyHeight] tall running under the status bar, a [ground]
 * hill over its bottom edge, and the page below.
 *
 * - [skyContent] is drawn on the sky and under the hill, so the hill hides whatever reaches
 *   below the crest (Sprig's lower body). It is not inset; use `statusBarsPadding()` inside.
 * - [content] starts at the sky's bottom edge, on the ground colour.
 * - Sky and ground colour changes animate over 700ms ([keptTween]), or snap with reduce motion.
 * - Status bar icons follow [lightContent]: light on dark skies, dark elsewhere.
 */
@Composable
fun SkyScaffold(
    sky: Color,
    skyHeight: Dp,
    modifier: Modifier = Modifier,
    ground: Color = DaybreakColors.cream,
    lightContent: Boolean = SkyRamp.prefersLightContent(sky),
    skyContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val skyColor by animateColorAsState(sky, keptTween(Motion.SKY_MS), label = "sky")
    val groundColor by animateColorAsState(ground, keptTween(Motion.SKY_MS), label = "ground")
    StatusBarIcons(light = lightContent)
    Box(modifier.fillMaxSize().drawBehind { drawRect(groundColor) }) {
        Box(
            Modifier.fillMaxWidth().height(skyHeight).drawBehind {
                drawRect(skyColor, size = Size(size.width, skyHeight.toPx()))
            },
            content = skyContent,
        )
        Canvas(Modifier.fillMaxSize()) {
            drawPath(
                Hill.path(
                    width = size.width,
                    height = size.height,
                    skyHeightPx = skyHeight.toPx(),
                    overhangPx = Hill.OVERHANG.toPx(),
                    radiusPx = Hill.RADIUS.toPx(),
                    overlapPx = Hill.SEAM_OVERLAP.toPx(),
                ),
                groundColor,
            )
        }
        Column(Modifier.fillMaxSize().padding(top = skyHeight), content = content)
    }
}
