package com.example.kept.core.ui.sprig

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.PathParser
import com.example.kept.core.domain.Accessory
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.Variants
import com.example.kept.core.domain.WeekVariant
import kotlin.math.cos
import kotlin.math.sin

/** Everything needed to draw one Sprig. */
data class SprigSpec(
    val form: SprigForm = SprigForm.SPRIG,
    val pose: SprigPose = SprigPose.IDLE,
    val variant: WeekVariant = Variants.table[0],
    val wilted: Boolean = false,
    /** 0..1 breathing phase for idle animation. */
    val breath: Float = 0f,
    val showAccessory: Boolean = true,
)

private data class Palette(
    val body: Color,
    val bodyDark: Color,
    val belly: Color,
    val ears: Color,
    val eye: Color,
    val blush: Color,
    val shadow: Color,
    val leaf: Color,
    val leafLight: Color,
    val stem: Color,
)

private object Paths {
    private val cache = HashMap<String, Path>()
    fun of(d: String): Path = cache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

    const val BODY = "M60 24 C83 24 97 43 97 65 C97 87 81 103 60 103 C39 103 23 87 23 65 C23 43 37 24 60 24 Z"
    const val BODY_DROOP = "M60 26 C83 26 97 45 97 67 C97 89 81 103 60 103 C39 103 23 89 23 67 C23 45 37 26 60 26 Z"
    const val BELLY = "M60 62 C74 62 84 72 84 84 C84 95 73 103 60 103 C47 103 36 95 36 84 C36 72 46 62 60 62 Z"
    const val EAR_L = "M40 36 C35 26 38 17 44 15 C48 21 47 30 44 37 Z"
    const val EAR_R = "M80 36 C85 26 82 17 76 15 C72 21 73 30 76 37 Z"
    const val LEAF_L = "M60 18 C52 18 47 14 46 8 C55 7 60 12 60 18 Z"
    const val LEAF_R = "M60 15 C68 15 73 11 74 5 C65 4 60 9 60 15 Z"
    const val LEAF_3 = "M60 24 C68 22 74 17 74 11 C66 11 61 17 60 24 Z"
    const val LEAF_DROOP = "M50 15 C44 17 41 22 43 27 C49 26 51 20 50 15 Z"
    const val STEM_DROOP = "M60 28 C60 20 56 16 50 15"
    const val ARM_L_DOWN = "M24 78 C18 82 17 88 21 91"
    const val ARM_R_DOWN = "M96 78 C102 82 103 88 99 91"
    const val ARM_R_UP = "M96 74 C104 68 106 60 103 54"
    const val ARM_L_UP = "M24 74 C16 68 14 60 17 54"
    const val ARM_R_BLOCK = "M96 76 C104 70 106 62 103 56"
    const val ARM_L_DROOP = "M24 80 C18 84 17 90 21 93"
    const val ARM_R_DROOP = "M96 80 C102 84 103 90 99 93"
    const val MOUTH_SMILE = "M55 76 C57 80 63 80 65 76"
    const val MOUTH_FROWN = "M54 78 C57 74 63 74 66 78"
    const val MOUTH_SAD = "M55 80 C57 77 63 77 65 80"
    const val MOUTH_OPEN = "M54 74 C56 81 64 81 66 74 Z"
    const val EYE_HAPPY_L = "M41 62 C44 58 50 58 53 62"
    const val EYE_HAPPY_R = "M67 62 C70 58 76 58 79 62"
    const val EYE_SAD_L = "M41 64 C44 61 50 61 53 64"
    const val EYE_SAD_R = "M67 64 C70 61 76 61 79 64"
}

private fun paletteFor(spec: SprigSpec): Palette {
    if (spec.wilted) {
        return Palette(
            body = Color(0xFFD3D1C7), bodyDark = Color(0xFFB4B2A9), belly = Color(0xFFF1EFE8), ears = Color(0xFFB4B2A9),
            eye = Color(0xFF444441), blush = Color(0x00000000), shadow = Color(0xFFEFEDE8),
            leaf = Color(0xFFB4B2A9), leafLight = Color(0xFFC9C6BE), stem = Color(0xFF888780),
        )
    }
    val base = Palette(
        body = Color(0xFFAFA9EC), bodyDark = Color(0xFF7F77DD), belly = Color(0xFFEEEDFE), ears = Color(0xFF7F77DD),
        eye = Color(0xFF26215C), blush = Color(0xFFF4C0D1), shadow = Color(0xFFCECBF6),
        leaf = Color(0xFF97C459), leafLight = Color(0xFFC0DD97), stem = Color(0xFF639922),
    )
    return when (spec.form) {
        SprigForm.SPRIG, SprigForm.BUD, SprigForm.BLOOM, SprigForm.DUO -> base
        SprigForm.THICKET -> base.copy(body = Color(0xFF9C94E6), leaf = Color(0xFF7FB03F))
        SprigForm.GROVE -> base.copy(body = Color(0xFF8E86E0), belly = Color(0xFFE6E4FB), leaf = Color(0xFF6E9E2E))
        SprigForm.ANCIENT -> base.copy(
            body = Color(0xFF7A7096), bodyDark = Color(0xFF574E73), ears = Color(0xFF574E73), belly = Color(0xFFD9D3E6),
            eye = Color(0xFF0F6E56), leaf = Color(0xFF5E8A2A), leafLight = Color(0xFF8FB560), shadow = Color(0xFFBFB9D6),
        )
        SprigForm.CELESTIAL -> base.copy(
            body = Color(0xFF534AB7), bodyDark = Color(0xFF26215C), ears = Color(0xFF3B3390), belly = Color(0xFF8F88E8),
            eye = Color(0xFFFFFFFF), blush = Color(0xFFB9B3F5), shadow = Color(0xFFCECBF6),
            leaf = Color(0xFFF2C14E), leafLight = Color(0xFFFFE8A8), stem = Color(0xFFD9A441),
        )
    }
}

/**
 * Draws Sprig scaled to fit [size], centred. The art is authored in a 120x116 box.
 * Usable from Compose Canvas and from a bitmap-backed DrawScope for share cards.
 */
fun DrawScope.drawSprig(spec: SprigSpec, size: Size = this.size) {
    val s = minOf(size.width / 120f, size.height / 116f)
    val dx = (size.width - 120f * s) / 2f
    val dy = (size.height - 116f * s) / 2f
    translate(dx, dy) {
        scale(s, s, pivot = Offset.Zero) {
            drawSprigUnits(spec)
        }
    }
}

private fun DrawScope.fill(d: String, color: Color) = drawPath(Paths.of(d), color, style = Fill)
private fun DrawScope.stroke(d: String, color: Color, width: Float) =
    drawPath(Paths.of(d), color, style = Stroke(width = width, cap = StrokeCap.Round))

private fun DrawScope.drawSprigUnits(spec: SprigSpec) {
    val p = paletteFor(spec)
    val pose = if (spec.wilted) SprigPose.DROOP else spec.pose
    val breathe = 1f + 0.012f * sin(spec.breath * 2f * Math.PI.toFloat())
    val accent = Color(spec.variant.accentArgb)

    // Shadow
    drawOval(p.shadow, topLeft = Offset(60f - 29f, 105f), size = Size(58f, 10f))

    // Halo (Celestial) sits behind everything.
    if (spec.form == SprigForm.CELESTIAL && !spec.wilted) {
        drawOval(Color(0xFFF2C14E), topLeft = Offset(38f, 2f), size = Size(44f, 12f), style = Stroke(3f))
    }

    // Sprout / crown
    drawSprout(spec, p, pose, accent)

    // Ears
    fill(Paths.EAR_L, p.ears); fill(Paths.EAR_R, p.ears)

    // Body with breathing
    scale(breathe, breathe, pivot = Offset(60f, 103f)) {
        val body = if (pose == SprigPose.DROOP) Paths.BODY_DROOP else Paths.BODY
        if (spec.form == SprigForm.CELESTIAL && !spec.wilted) {
            drawPath(Paths.of(body), Brush.verticalGradient(listOf(Color(0xFF6E64D4), Color(0xFF26215C)), startY = 24f, endY = 103f))
            // stars
            val stars = listOf(Offset(35f, 45f), Offset(84f, 40f), Offset(30f, 62f), Offset(90f, 66f), Offset(50f, 36f), Offset(70f, 32f))
            stars.forEachIndexed { i, o -> drawCircle(Color.White.copy(alpha = if (i % 2 == 0) 0.9f else 0.6f), if (i % 3 == 0) 1.4f else 0.9f, o) }
        } else {
            fill(body, p.body)
        }
        if (spec.form == SprigForm.ANCIENT && !spec.wilted) {
            // bark cracks + moss
            stroke("M33 50 C36 56 34 62 37 68", p.bodyDark, 1.5f)
            stroke("M87 48 C84 54 86 60 83 66", p.bodyDark, 1.5f)
            drawOval(p.leaf, topLeft = Offset(28f, 40f), size = Size(12f, 7f))
            drawOval(p.leafLight, topLeft = Offset(80f, 36f), size = Size(10f, 6f))
        }
        // Belly
        fill(Paths.BELLY, p.belly)
    }

    // Arms
    when (pose) {
        SprigPose.IDLE -> { stroke(Paths.ARM_L_DOWN, p.bodyDark, 6f); stroke(Paths.ARM_R_DOWN, p.bodyDark, 6f) }
        SprigPose.BLOCK -> { stroke(Paths.ARM_L_DOWN, p.bodyDark, 6f); stroke(Paths.ARM_R_BLOCK, p.bodyDark, 6f) }
        SprigPose.DROOP -> { stroke(Paths.ARM_L_DROOP, p.bodyDark, 6f); stroke(Paths.ARM_R_DROOP, p.bodyDark, 6f) }
        SprigPose.CHEER -> { stroke(Paths.ARM_L_UP, p.bodyDark, 6f); stroke(Paths.ARM_R_UP, p.bodyDark, 6f) }
        SprigPose.WAVE -> { stroke(Paths.ARM_L_DOWN, p.bodyDark, 6f); stroke(Paths.ARM_R_UP, p.bodyDark, 6f) }
    }

    // Face
    when (pose) {
        SprigPose.CHEER -> {
            stroke(Paths.EYE_HAPPY_L, p.eye, 2.5f); stroke(Paths.EYE_HAPPY_R, p.eye, 2.5f)
            fill(Paths.MOUTH_OPEN, p.eye)
        }
        SprigPose.DROOP -> {
            stroke(Paths.EYE_SAD_L, p.eye, 2.5f); stroke(Paths.EYE_SAD_R, p.eye, 2.5f)
            stroke(Paths.MOUTH_SAD, p.eye, 2f)
        }
        else -> {
            drawOval(p.eye, topLeft = Offset(47f - 6.5f, 62f - 8f), size = Size(13f, 16f))
            drawOval(p.eye, topLeft = Offset(73f - 6.5f, 62f - 8f), size = Size(13f, 16f))
            val hl = if (spec.form == SprigForm.CELESTIAL) Color(0xFF26215C) else Color.White
            drawCircle(hl, 2.3f, Offset(49f, 59f)); drawCircle(hl, 2.3f, Offset(75f, 59f))
            if (spec.form == SprigForm.ANCIENT) {
                drawCircle(Color(0xFF9FE1CB).copy(alpha = 0.35f), 9f, Offset(47f, 62f))
                drawCircle(Color(0xFF9FE1CB).copy(alpha = 0.35f), 9f, Offset(73f, 62f))
            }
            stroke(if (pose == SprigPose.BLOCK) Paths.MOUTH_FROWN else Paths.MOUTH_SMILE, p.eye, 2f)
        }
    }
    if (p.blush.alpha > 0f) {
        drawOval(p.blush, topLeft = Offset(28f, 70f), size = Size(12f, 8f))
        drawOval(p.blush, topLeft = Offset(80f, 70f), size = Size(12f, 8f))
    }

    if (spec.showAccessory && !spec.wilted) drawAccessory(spec.variant.accessory, accent, p)
}

private fun DrawScope.drawSprout(spec: SprigSpec, p: Palette, pose: SprigPose, accent: Color) {
    if (spec.wilted || pose == SprigPose.DROOP) {
        stroke(Paths.STEM_DROOP, p.stem, 2.5f)
        fill(Paths.LEAF_DROOP, p.leaf)
        return
    }
    when (spec.form) {
        SprigForm.SPRIG -> {
            // a tiny nub of a stem: promise of growth
            stroke("M60 26 L60 17", p.stem, 2.5f)
            drawCircle(p.leaf, 2.6f, Offset(60f, 16f))
        }
        SprigForm.BUD -> {
            stroke("M60 26 L60 12", p.stem, 2.5f)
            fill(Paths.LEAF_L, p.leaf)
        }
        SprigForm.BLOOM -> {
            stroke("M60 26 L60 12", p.stem, 2.5f)
            fill(Paths.LEAF_L, p.leaf); fill(Paths.LEAF_R, p.leafLight)
            drawFlower(Offset(60f, 9f), 4.2f, accent)
        }
        SprigForm.THICKET -> {
            stroke("M60 26 L60 12", p.stem, 2.5f)
            fill(Paths.LEAF_L, p.leaf); fill(Paths.LEAF_R, p.leafLight); fill(Paths.LEAF_3, p.stem)
        }
        SprigForm.GROVE -> {
            // leaf crown: ring of 7 leaves around the head
            for (i in 0 until 7) {
                val a = Math.PI.toFloat() * (0.15f + 0.7f * i / 6f)
                val cx = 60f - 30f * cos(a); val cy = 30f - 12f * sin(a)
                drawLeaf(Offset(cx, cy), 9f, if (i % 2 == 0) p.leaf else p.leafLight, -a)
            }
            drawFlower(Offset(60f, 14f), 4.5f, accent)
        }
        SprigForm.ANCIENT -> {
            stroke("M60 26 C58 18 52 14 47 12", p.stem, 3f)
            stroke("M60 26 C62 18 68 14 73 12", p.stem, 3f)
            fill(Paths.LEAF_L, p.leaf); fill(Paths.LEAF_R, p.leafLight)
            drawLeaf(Offset(46f, 10f), 8f, p.leaf, 2.4f)
            drawLeaf(Offset(74f, 10f), 8f, p.leafLight, 0.7f)
        }
        SprigForm.CELESTIAL -> {
            stroke("M60 26 L60 12", p.stem, 2.5f)
            fill(Paths.LEAF_L, p.leaf); fill(Paths.LEAF_R, p.leafLight)
            drawStar(Offset(60f, 6f), 5f, Color(0xFFFFE8A8))
        }
        SprigForm.DUO -> {
            stroke("M57 26 C56 20 54 15 52 12", p.stem, 2.5f)
            stroke("M63 26 C64 20 66 15 68 12", p.stem, 2.5f)
            drawLeaf(Offset(50f, 10f), 8f, p.leaf, 2.6f)
            drawLeaf(Offset(70f, 10f), 8f, p.leafLight, 0.5f)
            drawHeart(Offset(60f, 12f), 3.2f, accent)
        }
    }
}

private fun DrawScope.drawLeaf(center: Offset, len: Float, color: Color, angle: Float) {
    val path = Path().apply {
        moveTo(0f, 0f)
        cubicTo(len * 0.5f, -len * 0.5f, len, -len * 0.2f, len, 0f)
        cubicTo(len, len * 0.2f, len * 0.5f, len * 0.5f, 0f, 0f)
        close()
    }
    translate(center.x, center.y) {
        rotate(degrees = angle * 180f / Math.PI.toFloat(), pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

private fun DrawScope.drawFlower(center: Offset, r: Float, color: Color) {
    for (i in 0 until 5) {
        val a = 2f * Math.PI.toFloat() * i / 5f
        drawCircle(color, r * 0.55f, Offset(center.x + r * cos(a), center.y + r * sin(a)))
    }
    drawCircle(Color(0xFFFFE8A8), r * 0.45f, center)
}

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = -Math.PI.toFloat() / 2f + i * Math.PI.toFloat() / 5f
        val x = center.x + rad * cos(a); val y = center.y + rad * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawHeart(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + r)
        cubicTo(center.x - 2.2f * r, center.y - 0.6f * r, center.x - 0.6f * r, center.y - 1.8f * r, center.x, center.y - 0.6f * r)
        cubicTo(center.x + 0.6f * r, center.y - 1.8f * r, center.x + 2.2f * r, center.y - 0.6f * r, center.x, center.y + r)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawAccessory(a: Accessory, accent: Color, p: Palette) {
    when (a) {
        Accessory.NONE -> Unit
        Accessory.SCARF -> {
            stroke("M36 92 C48 100 72 100 84 92", accent, 6f)
            stroke("M78 94 L82 104", accent, 5f)
        }
        Accessory.BOW -> {
            fill("M76 20 L84 14 L84 26 Z", accent); fill("M76 20 L68 14 L68 26 Z", accent)
            drawCircle(Color(0xFFFFE8A8), 2f, Offset(76f, 20f))
        }
        Accessory.GLASSES -> {
            drawCircle(accent, 10f, Offset(47f, 62f), style = Stroke(2.2f))
            drawCircle(accent, 10f, Offset(73f, 62f), style = Stroke(2.2f))
            stroke("M57 62 L63 62", accent, 2.2f)
        }
        Accessory.HAT -> {
            fill("M38 30 L82 30 L78 22 L42 22 Z", accent)
            fill("M46 22 L74 22 L70 6 L50 6 Z", accent)
        }
        Accessory.STAR -> drawStar(Offset(88f, 30f), 5f, accent)
        Accessory.SHELL -> {
            drawArc(accent, 180f, 180f, useCenter = true, topLeft = Offset(80f, 26f), size = Size(14f, 14f))
        }
        Accessory.FLAME -> {
            fill("M30 34 C26 28 30 22 32 18 C33 23 36 24 36 30 C36 34 33 36 30 34 Z", accent)
            fill("M31 32 C30 29 31 27 32 25 C33 27 34 29 33 32 Z", Color(0xFFFFE8A8))
        }
        Accessory.SNOW -> {
            listOf(Offset(30f, 30f), Offset(90f, 34f), Offset(26f, 50f)).forEach { drawCircle(Color.White, 2.2f, it); drawCircle(accent, 2.2f, it, style = Stroke(0.8f)) }
        }
        Accessory.CROWN_TINY -> {
            fill("M50 8 L54 2 L58 8 L62 2 L66 8 L70 2 L72 12 L48 12 Z", accent)
        }
        Accessory.HEADPHONES -> {
            stroke("M30 60 C30 32 90 32 90 60", accent, 3f)
            drawRoundRect(accent, topLeft = Offset(24f, 56f), size = Size(9f, 14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
            drawRoundRect(accent, topLeft = Offset(87f, 56f), size = Size(9f, 14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
        }
        Accessory.BANDANA -> {
            fill("M36 30 C48 24 72 24 84 30 L82 36 C70 32 50 32 38 36 Z", accent)
            fill("M82 34 L92 40 L86 30 Z", accent)
        }
    }
}
