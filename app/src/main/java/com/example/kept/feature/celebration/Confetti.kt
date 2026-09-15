package com.example.kept.feature.celebration

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.unit.dp
import com.example.kept.core.ui.KeptTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PIECES = 90
private const val FALL_MILLIS = 2_800

private data class Piece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val widthDp: Float,
    val heightDp: Float,
    val colorIndex: Int,
    val drift: Float,
)

/**
 * A one-shot confetti burst (issue #9). Hand-drawn on a Canvas rather than pulled in as a library:
 * ninety rectangles thrown from the middle of the screen is not worth a dependency.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val c = KeptTheme.colors
    val palette = listOf(c.purple400, c.teal600, c.amber800, c.green400, c.blue400, c.pink100)
    val pieces = remember {
        // A fresh scatter every time; a fixed seed made every celebration land identically.
        val rng = Random(System.nanoTime())
        List(PIECES) {
            Piece(
                angle = (rng.nextFloat() * 2f - 1f) * 1.15f,
                speed = 0.55f + rng.nextFloat() * 0.85f,
                spin = (rng.nextFloat() * 2f - 1f) * 14f,
                widthDp = 5f + rng.nextFloat() * 5f,
                heightDp = 8f + rng.nextFloat() * 8f,
                colorIndex = rng.nextInt(6),
                drift = (rng.nextFloat() * 2f - 1f) * 0.35f,
            )
        }
    }

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val t by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(FALL_MILLIS, easing = LinearEasing),
        label = "confetti",
    )

    Canvas(modifier) {
        if (t <= 0f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.30f)
        val reach = size.height * 1.2f
        val fade = (1f - t).coerceIn(0f, 1f)
        pieces.forEach { p ->
            // Thrown outwards, then gravity takes over: x drifts linearly, y is a launch minus a fall.
            val x = origin.x + sin(p.angle) * reach * 0.55f * p.speed * t + p.drift * size.width * t
            val launch = cos(p.angle).coerceAtLeast(0.15f) * reach * 0.30f * p.speed
            val y = origin.y - launch * t + reach * 0.62f * t * t
            if (y > size.height + 40f) return@forEach
            rotateRad(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = palette[p.colorIndex].copy(alpha = 0.35f + 0.65f * fade),
                    topLeft = Offset(x - p.widthDp.dp.toPx() / 2f, y - p.heightDp.dp.toPx() / 2f),
                    size = Size(p.widthDp.dp.toPx(), p.heightDp.dp.toPx()),
                )
            }
        }
    }
}
