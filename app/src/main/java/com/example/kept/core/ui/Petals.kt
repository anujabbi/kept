package com.example.kept.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.example.kept.core.domain.PetalShower

/** `petalFall`: `cubic-bezier(.2, .6, .4, 1)`, the same in every design file. */
val PetalEasing = CubicBezierEasing(0.2f, 0.6f, 0.4f, 1f)

/**
 * The falling petals of [shower] (`.petal`), drawn at [elapsed] milliseconds into the moment and
 * clipped to this composable's bounds. The recap and the two moments draw their petals with this;
 * the caller decides whether they play at all (reduce motion shows none).
 */
@Composable
fun Petals(shower: PetalShower, elapsed: () -> Long, modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds()) {
        val ms = elapsed()
        val w = PetalShower.W_DP.dp.toPx()
        val h = PetalShower.H_DP.dp.toPx()
        val big = CornerRadius(8.dp.toPx())
        // `border-radius: 8px 8px 8px 2px`: the bottom-left corner is the sharp one.
        val shape = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, big, big, big, CornerRadius(2.dp.toPx())))
        }
        for (petal in shower.petals) {
            val t = shower.progress(petal, ms) ?: continue
            val e = PetalEasing.transform(t)
            val x = petal.xDp.dp.toPx()
            val y = (PetalShower.TOP_DP + shower.fallDp * e).dp.toPx()
            translate(x, y) {
                rotate(shower.turnDeg * e, pivot = Offset(w / 2, h / 2)) {
                    drawPath(shape, Color(petal.colour), alpha = shower.alpha(t))
                }
            }
        }
    }
}
