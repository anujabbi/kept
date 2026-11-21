package com.example.kept.core.ui.sprig

import com.example.kept.core.domain.SprigOutfit
import com.example.kept.core.domain.SprigPose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sprig's animation and touch state: a port of `this.s` and the frame loop in
 * `componentDidMount` / `handlers()` of `docs/design/daybreak/SprigA.dc.html`. The constants are
 * the design's. Pure Kotlin so the rules are testable without a frame clock.
 */
class SprigMotion(private val random: () -> Float = { Random.nextFloat() }) {
    /** Seconds of animation time. Frozen while [still]. */
    var t = 0f
    /** Follow: eased (`lx`, `ly`) toward the finger target (`tx`, `ty`), each in -1..1. */
    var lx = 0f; var ly = 0f; var tx = 0f; var ty = 0f
    /** Hop, 1 on a tap, decays to 0 in ~0.6s. */
    var hop = 0f
    /** Tickle wobble, 1 on the third quick tap, fades over ~1.4s. */
    var tick = 0f
    /** Petting, eased 0..1 toward [petting]. */
    var pet = 0f
    var petting = false
    var isDown = false
    var moved = 0f
    var taps = 0
    var tapAt = Long.MIN_VALUE / 2
    /** Seconds since the last touch. Over [SLEEP_AFTER_S] an idle Sprig sleeps. */
    var idle = 0f
    /** Seconds left of the current blink. */
    var blink = 0f
    /** Seconds to the next blink. */
    var nextBlink = 2.5f
    /** Reduce motion: no sway, hop or wobble; blinks, petting and sleep still happen. */
    var still = false

    val asleepFromIdle: Boolean get() = idle > SLEEP_AFTER_S

    /** One frame. [dtSeconds] is capped at 50ms like the design's loop. */
    fun step(dtSeconds: Float) {
        val dt = min(MAX_DT, max(0f, dtSeconds))
        if (!still) t += dt
        idle += dt
        val k = min(1f, dt * FOLLOW_EASE)
        lx += (tx - lx) * k
        ly += (ty - ly) * k
        hop = if (still) 0f else max(0f, hop - dt * HOP_DECAY)
        tick = if (still) 0f else max(0f, tick - dt * TICK_DECAY)
        pet += ((if (petting) 1f else 0f) - pet) * min(1f, dt * PET_EASE)
        nextBlink -= dt
        if (nextBlink < 0f) {
            blink = BLINK_S
            nextBlink = BLINK_MIN_S + random() * BLINK_SPREAD_S
        }
        blink = max(0f, blink - dt)
    }

    /** Finger down at [nowMs]: a hop (the caller plays the haptic), a tickle on the third quick tap. */
    fun press(nowMs: Long) {
        isDown = true; moved = 0f; idle = 0f
        taps = if (nowMs - tapAt < TRIPLE_TAP_MS) taps + 1 else 1
        tapAt = nowMs
        if (!still) {
            hop = 1f
            if (taps >= 3) tick = 1f
        }
    }

    /**
     * Pointer at ([fx], [fy]), each 0..1 across Sprig's bounds; [dragPx] is how far it moved
     * since the last event, in pixels (the design's `movementX + movementY`).
     */
    fun move(fx: Float, fy: Float, dragPx: Float) {
        tx = (fx * 2f - 1f).coerceIn(-1f, 1f)
        ty = (fy * 2f - 1.1f).coerceIn(-1f, 1f)
        idle = 0f
        if (isDown) {
            moved += dragPx
            if (moved > PET_DRAG_PX) petting = true
        }
    }

    /** Finger up or gone: stop petting and let the eyes drift back to centre. */
    fun release() {
        isDown = false; petting = false; tx = 0f; ty = 0f
    }

    /** An accessibility click: hop without a pointer. */
    fun poke() {
        idle = 0f
        if (!still) hop = 1f
    }

    companion object {
        const val MAX_DT = 0.05f
        const val FOLLOW_EASE = 9f
        const val HOP_DECAY = 1.7f
        const val TICK_DECAY = 0.7f
        const val PET_EASE = 5f
        const val BLINK_S = 0.13f
        const val BLINK_MIN_S = 2f
        const val BLINK_SPREAD_S = 3.5f
        const val TRIPLE_TAP_MS = 450L
        const val PET_DRAG_PX = 20f
        const val SLEEP_AFTER_S = 12f
    }
}

/** Every value the drawing needs for one frame: the port of `renderVals()`. */
data class SprigFrame(
    val bodyDx: Float, val bodyDy: Float, val lean: Float, val scaleX: Float, val scaleY: Float,
    val armL: Float, val armR: Float, val sprout: Float,
    val eyesDx: Float, val eyesDy: Float,
    val eyeOpen: Boolean, val eyeJoy: Boolean, val eyeShut: Boolean,
    val mouthSmile: Boolean, val mouthOpen: Boolean, val mouthFrown: Boolean, val mouthFlat: Boolean,
    val blush: Float, val brows: Boolean,
    val tearAlpha: Float, val tearDy: Float,
    val zzz: Boolean, val zDy: Float,
    val stage: Int, val outfit: SprigOutfit,
) {
    val asleep: Boolean get() = zzz

    companion object {
        const val PX = 60f
        const val PY = 150f
        const val TOP = 36f

        private val PIf = PI.toFloat()

        fun of(pose: SprigPose, stage: Int, outfit: SprigOutfit, tear: Boolean, s: SprigMotion): SprigFrame {
            val t = s.t
            val asleep = pose == SprigPose.SLEEP || (pose == SprigPose.IDLE && s.asleepFromIdle)
            val sad = pose == SprigPose.DROOP && s.pet < 0.4f
            val joy = !asleep && (pose == SprigPose.CHEER || s.pet > 0.35f || s.hop > 0.25f || s.tick > 0f)
            val shut = asleep || s.blink > 0f
            val br = sin(t * if (asleep) 1.1f else 2f) * if (asleep) 0.02f else 0.012f
            val arc = if (s.hop > 0f) sin((1f - s.hop) * PIf) else 0f
            val sq = if (s.hop > 0f) sin((1f - s.hop) * PIf * 2f) * 0.05f * s.hop else 0f
            val wob = sin(t * 22f) * 5f * s.tick
            val bounce = if (pose == SprigPose.CHEER) -abs(sin(t * 5f)) * 4f else 0f
            val jy = -arc * 12f + bounce + if (sad) 4f else 0f
            val sway = sin(t * 2f) * 4f
            val aL: Float
            val aR: Float
            when (pose) {
                SprigPose.CHEER -> { aL = 150f + sin(t * 10f) * 12f; aR = 150f - sin(t * 10f) * 12f }
                SprigPose.GUARD -> { aL = 8f; aR = 138f + sin(t * 2f) * 4f }
                SprigPose.WAVE -> { aL = sway; aR = 140f + sin(t * 8f) * 18f }
                SprigPose.DROOP -> { aL = -6f; aR = -6f }
                else -> { aL = sway + arc * 130f + s.pet * 30f; aR = -sway + arc * 130f + s.pet * 30f }
            }
            // Reduce motion keeps the eyes following the finger but not the body leaning.
            val lean = if (s.still) 0f else s.lx * 3f + wob
            val showTear = tear && sad
            // A still Sprig shows the tear part-way down instead of falling.
            val ph = if (s.still) 0.4f else (t * 0.55f) % 1f
            return SprigFrame(
                bodyDx = if (s.still) 0f else s.lx * 2.5f, bodyDy = jy, lean = lean,
                scaleX = 1f - br - sq, scaleY = 1f + br + sq,
                armL = aL, armR = -aR,
                sprout = sin(t * 1.4f) * 4f - (if (s.still) 0f else s.lx * 7f) + wob * 1.5f + if (sad) 32f else if (asleep) 18f else 0f,
                eyesDx = s.lx * 3.2f, eyesDy = s.ly * 2.6f + if (sad) -1.5f else 0f,
                eyeOpen = !shut && !joy, eyeJoy = joy, eyeShut = shut && !joy,
                mouthSmile = !joy && !sad && pose != SprigPose.GUARD, mouthOpen = joy, mouthFrown = sad,
                mouthFlat = !joy && !sad && pose == SprigPose.GUARD,
                blush = 0.55f + s.pet * 0.45f, brows = sad,
                tearAlpha = if (!showTear) 0f else if (ph < 0.8f) min(1f, ph * 6f) else (1f - ph) / 0.2f,
                tearDy = ph * 16f,
                zzz = asleep, zDy = -((t * 5f) % 8f),
                stage = stage, outfit = outfit,
            )
        }
    }
}
