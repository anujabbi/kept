package com.example.kept.core.ui.sprig

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigLook
import com.example.kept.core.domain.SprigOutfit
import com.example.kept.core.domain.SprigPose

/** Everything needed to draw one Sprig, still or animated. */
data class SprigSpec(
    val form: SprigForm = SprigForm.SPRIG,
    val pose: SprigPose = SprigPose.IDLE,
    /** A wilted Sprig keeps its outfit, desaturated. */
    val wilted: Boolean = false,
    /** With [SprigPose.DROOP], a tear falls from the left eye (Break only). */
    val tear: Boolean = false,
    /** Locked forms: the whole drawing, outfit included, as solid ink at this alpha. */
    val silhouetteAlpha: Float? = null,
)

/** Colours from `SprigA.dc.html`. */
internal object SprigColors {
    val body = Color(0xFF8B7CF6)
    val arm = Color(0xFF6B5BE0)
    val ink = Color(0xFF1E1B3C)
    val stem = Color(0xFF218A52)
    val leaf = Color(0xFF2FA866)
    val leafLight = Color(0xFF5CCB86)
    val petal = Color(0xFFFF8FB0)
    val gold = Color(0xFFFFD25E)
    val blush = Color(0xFFFF9EB5)
    val tear = Color(0xFF7CC4FF)
    val cape = Color(0xFF2B2760)
    val capeStar = Color(0xFFFFE9A8)
    val cardigan = Color(0xFFFFB84D)
    val cardiganDark = Color(0xFFE0952A)
    val scarf = Color(0xFFFF6F61)
    val beard = Color(0xFFF1EEFF)
}

/** How much colour a wilted Sprig keeps. */
internal const val WILTED_SATURATION = 0.2f

private object Paths {
    private val cache = HashMap<String, Path>()
    fun of(d: String): Path = cache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

    const val BODY = "M16 160 V84 C16 52 34 34 60 34 C86 34 104 52 104 84 V160 Z"
    const val ARM_L = "M24 90 V110"
    const val ARM_R = "M96 90 V110"
    const val STEM = "M60 38 C60 30 61 24 60 17"
    const val LEAF_1 = "M60 24 C50 24 44 18 44 10 C54 10 60 16 60 24 Z"
    const val LEAF_2 = "M60 20 C70 20 77 14 77 6 C66 6 60 12 60 20 Z"
    const val CAPE = "M20 66 Q2 112 2 160 H118 Q118 112 100 66 Z"
    const val CARDIGAN = "M16 100 Q60 110 104 100 V160 H16 Z"
    const val CARDIGAN_HEM = "M16 100 Q60 110 104 100"
    const val SCARF = "M17 94 Q60 106 103 94"
    const val SCARF_TAIL = "M78 100 L84 118"
    const val SCARF_STRIPES = "M30 97 L35 98.5 M46 100 L51 101 M68 101 L73 100"
    const val JOY_L = "M39 72 Q45 63 51 72"
    const val JOY_R = "M69 72 Q75 63 81 72"
    const val SHUT_L = "M39 70 Q45 75 51 70"
    const val SHUT_R = "M69 70 Q75 75 81 70"
    const val TEAR = "M45 75 C42.5 79 42 81 42 82.5 A3 3 0 0 0 48 82.5 C48 81 47.5 79 45 75 Z"
    const val BEARD = "M44 86 Q48 99 60 104 Q72 99 76 86 Q68 91 60 89.5 Q52 91 44 86 Z"
    const val GLASSES_BRIDGE = "M54 69 Q60 66 66 69"
    const val SMILE = "M54 80 Q60 87 66 80"
    const val OPEN = "M53 79 Q60 93 67 79 Z"
    const val FROWN = "M54 86 Q60 80 66 86"
    const val FLAT = "M55 83 H65"
    const val BROW_L = "M38 62.5 Q43 61 50 57.5"
    const val BROW_R = "M82 62.5 Q77 61 70 57.5"
}

/** The design's clip: generous above and to the sides, the bottom edge of the box only. */
private val CLIP = Rect(-60f, -80f, 180f, 120f)

private val zPaint by lazy {
    android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.argb(255, 0x1E, 0x1B, 0x3C)
    }
}

/**
 * Draws a still Sprig scaled into [size], centred. Works in a Compose `Canvas` and in a
 * bitmap-backed `CanvasDrawScope` (the share card).
 */
fun DrawScope.drawSprig(spec: SprigSpec, size: Size = this.size) =
    drawSprig(spec, SprigMotion().apply { still = true }, size)

/** Draws Sprig at one frame of [motion]. */
fun DrawScope.drawSprig(spec: SprigSpec, motion: SprigMotion, size: Size = this.size) {
    val frame = SprigFrame.of(spec.pose, SprigLook.stage(spec.form), SprigLook.outfit(spec.form), spec.tear, motion)
    drawSprigFrame(frame, size, spec.wilted, spec.silhouetteAlpha)
}

/** The art is authored in a 120 × 120 box; the body runs below it and is clipped there. */
fun DrawScope.drawSprigFrame(frame: SprigFrame, size: Size = this.size, wilted: Boolean = false, silhouetteAlpha: Float? = null) {
    val s = minOf(size.width, size.height) / 120f
    val dx = (size.width - 120f * s) / 2f
    val dy = (size.height - 120f * s) / 2f
    translate(dx, dy) {
        scale(s, s, pivot = Offset.Zero) {
            clipRect(CLIP.left, CLIP.top, CLIP.right, CLIP.bottom) {
                val filter = when {
                    silhouetteAlpha != null -> Paint().apply {
                        colorFilter = ColorFilter.tint(SprigColors.ink, BlendMode.SrcIn)
                        alpha = silhouetteAlpha.coerceIn(0f, 1f)
                    }
                    wilted -> Paint().apply {
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(WILTED_SATURATION) })
                    }
                    else -> null
                }
                if (filter == null) {
                    drawUnits(frame)
                } else {
                    drawIntoCanvas { it.saveLayer(CLIP, filter) }
                    drawUnits(frame)
                    drawIntoCanvas { it.restore() }
                }
            }
        }
    }
}

private fun DrawScope.fill(d: String, color: Color, alpha: Float = 1f) =
    drawPath(Paths.of(d), color, alpha = alpha)

private fun DrawScope.stroke(d: String, color: Color, width: Float, alpha: Float = 1f, round: Boolean = true) =
    drawPath(Paths.of(d), color, alpha = alpha, style = Stroke(width = width, cap = if (round) StrokeCap.Round else StrokeCap.Butt))

private fun DrawScope.ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, alpha: Float = 1f) =
    drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f), alpha = alpha)

private fun DrawScope.drawUnits(f: SprigFrame) {
    val outfit = f.outfit
    val pivot = Offset(SprigFrame.PX, SprigFrame.PY)
    translate(f.bodyDx, f.bodyDy) {
        rotate(f.lean, pivot) {
            scale(f.scaleX, f.scaleY, pivot) {
                // Starry cape and halo sit behind everything.
                if (outfit == SprigOutfit.STAR) {
                    fill(Paths.CAPE, SprigColors.cape)
                    drawCircle(SprigColors.capeStar, 1.3f, Offset(10f, 120f))
                    drawCircle(SprigColors.capeStar, 1.1f, Offset(110f, 104f))
                    drawCircle(SprigColors.capeStar, 1.4f, Offset(108f, 134f))
                    drawOval(SprigColors.gold, Offset(60f - 17f, 27f - 4.5f), Size(34f, 9f), style = Stroke(2.8f))
                }
                rotate(f.armL, Offset(24f, 90f)) { stroke(Paths.ARM_L, SprigColors.arm, 12f) }
                rotate(f.armR, Offset(96f, 90f)) { stroke(Paths.ARM_R, SprigColors.arm, 12f) }
                rotate(f.sprout, Offset(SprigFrame.PX, SprigFrame.TOP)) { drawSprout(f.stage) }
                fill(Paths.BODY, SprigColors.body)
                if (outfit == SprigOutfit.SWEATER || outfit == SprigOutfit.BOTH) {
                    fill(Paths.CARDIGAN, SprigColors.cardigan)
                    stroke(Paths.CARDIGAN_HEM, SprigColors.cardiganDark, 3f, round = false)
                    drawCircle(SprigColors.cardiganDark, 1.6f, Offset(60f, 114f))
                    drawCircle(SprigColors.cardiganDark, 1.6f, Offset(60f, 120f))
                }
                if (outfit == SprigOutfit.SCARF || outfit == SprigOutfit.BOTH) {
                    stroke(Paths.SCARF, SprigColors.scarf, 9f)
                    stroke(Paths.SCARF_TAIL, SprigColors.scarf, 7f)
                    stroke(Paths.SCARF_STRIPES, Color.White, 2f, alpha = 0.7f)
                }
                if (outfit == SprigOutfit.CROWN) drawCrown()
                ellipse(33f, 81f, 6.5f, 4.2f, SprigColors.blush, f.blush)
                ellipse(87f, 81f, 6.5f, 4.2f, SprigColors.blush, f.blush)
                translate(f.eyesDx, f.eyesDy) { drawEyes(f) }
                if (outfit == SprigOutfit.SAGE) {
                    fill(Paths.BEARD, SprigColors.beard)
                    for (cx in floatArrayOf(45f, 75f)) {
                        drawCircle(Color.White, 9f, Offset(cx, 70f), alpha = 0.18f)
                        drawCircle(SprigColors.ink, 9f, Offset(cx, 70f), style = Stroke(2.2f))
                    }
                    stroke(Paths.GLASSES_BRIDGE, SprigColors.ink, 2.2f, round = false)
                }
                if (f.mouthSmile) stroke(Paths.SMILE, SprigColors.ink, 2.6f)
                if (f.mouthOpen) fill(Paths.OPEN, SprigColors.ink)
                if (f.mouthFrown) stroke(Paths.FROWN, SprigColors.ink, 2.6f)
                if (f.mouthFlat) stroke(Paths.FLAT, SprigColors.ink, 2.6f)
                if (f.brows) {
                    stroke(Paths.BROW_L, SprigColors.ink, 2.4f)
                    stroke(Paths.BROW_R, SprigColors.ink, 2.4f)
                }
            }
        }
    }
    if (f.zzz) {
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            zPaint.textSize = 11f
            nc.drawText("z", 90f, 40f + f.zDy, zPaint)
            zPaint.textSize = 8f
            nc.drawText("z", 99f, 30f + f.zDy, zPaint)
        }
    }
}

private fun DrawScope.drawSprout(stage: Int) {
    stroke(Paths.STEM, SprigColors.stem, 3f)
    if (stage < 1) drawCircle(SprigColors.leaf, 3f, Offset(60f, 16f))
    if (stage >= 1) fill(Paths.LEAF_1, SprigColors.leaf)
    if (stage >= 2) fill(Paths.LEAF_2, SprigColors.leafLight)
    if (stage >= 3) {
        drawCircle(SprigColors.petal, 4f, Offset(60f, 9f))
        drawCircle(SprigColors.petal, 4f, Offset(55f, 13f))
        drawCircle(SprigColors.petal, 4f, Offset(65f, 13f))
        drawCircle(SprigColors.gold, 2.6f, Offset(60f, 13f))
    }
}

private fun DrawScope.drawCrown() {
    fun leaf(cx: Float, cy: Float, deg: Float, color: Color) =
        rotate(deg, Offset(cx, cy)) { ellipse(cx, cy, 7f, 3.6f, color) }
    leaf(31f, 52f, -58f, SprigColors.leaf)
    leaf(42f, 42f, -35f, SprigColors.leafLight)
    leaf(60f, 37.5f, 0f, SprigColors.leaf)
    leaf(78f, 42f, 35f, SprigColors.leafLight)
    leaf(89f, 52f, 58f, SprigColors.leaf)
    drawCircle(SprigColors.petal, 2.6f, Offset(60f, 38f))
}

private fun DrawScope.drawEyes(f: SprigFrame) {
    if (f.eyeOpen) {
        drawCircle(SprigColors.ink, 5.2f, Offset(45f, 70f))
        drawCircle(SprigColors.ink, 5.2f, Offset(75f, 70f))
        drawCircle(Color.White, 1.7f, Offset(46.8f, 68f))
        drawCircle(Color.White, 1.7f, Offset(76.8f, 68f))
    }
    if (f.eyeJoy) {
        stroke(Paths.JOY_L, SprigColors.ink, 3f)
        stroke(Paths.JOY_R, SprigColors.ink, 3f)
    }
    if (f.eyeShut) {
        stroke(Paths.SHUT_L, SprigColors.ink, 3f)
        stroke(Paths.SHUT_R, SprigColors.ink, 3f)
    }
    if (f.tearAlpha > 0f) {
        translate(0f, f.tearDy) {
            fill(Paths.TEAR, SprigColors.tear, f.tearAlpha)
            drawCircle(Color.White, 0.9f, Offset(44f, 81.5f), alpha = f.tearAlpha)
        }
    }
}
