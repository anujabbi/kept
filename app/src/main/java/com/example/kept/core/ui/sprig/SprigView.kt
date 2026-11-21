package com.example.kept.core.ui.sprig

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigLook
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.LocalReduceMotion
import com.example.kept.core.ui.rememberHaptics
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** Sprigs smaller than this (lists, rows) are drawn still and do not take touches. */
val SPRIG_ANIMATE_MIN = 100.dp

/** How often a scrolled-away Sprig checks whether it is back in view. */
private const val OFFSCREEN_POLL_MS = 250L

/**
 * The Daybreak Sprig (spec, Sprig (Daybreak)). Size it with [modifier]. At 100dp and over it
 * animates on one frame clock, paused when it leaves composition, is scrolled out of view or
 * the app goes to the background, and reacts to touch: follow, tap to hop, triple tap to tickle,
 * drag to pet, sleep after 12 seconds untouched. With reduce motion on it does not sway, hop or wobble, but poses,
 * blinks, petting and sleep still change.
 *
 * [saturation] (0 to 1) drains the colour, as the break screen does over its countdown.
 *
 * [motion] is hoisted for tests; screens leave it alone.
 */
@Composable
fun SprigView(
    form: SprigForm,
    pose: SprigPose,
    modifier: Modifier = Modifier,
    wilted: Boolean = false,
    tear: Boolean = false,
    silhouetteAlpha: Float? = null,
    animate: Boolean = true,
    motion: SprigMotion = remember { SprigMotion() },
    saturation: Float = 1f,
) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    var px by remember { mutableStateOf(IntSize.Zero) }
    val big = with(density) { minOf(px.width, px.height).toDp() } >= SPRIG_ANIMATE_MIN
    val live = animate && big && silhouetteAlpha == null
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = live && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    // Where Sprig is laid out, read live by the loop to tell when it is scrolled out of view.
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Read only in the draw phase, so a frame redraws the Canvas without recomposing.
    var frames by remember { mutableIntStateOf(0) }
    var asleep by remember { mutableStateOf(false) }
    val currentPose by rememberUpdatedState(pose)

    SideEffect { motion.still = reduce || !live }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            // Off screen (scrolled out of a scroll container's viewport, which keeps it composed):
            // no frames, just a cheap check a few times a second until it comes back.
            if (place[0]?.isVisibleInWindow() == false) {
                last = 0L
                offscreenPause()
                continue
            }
            withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                motion.step(dt)
                frames++
                asleep = currentPose == SprigPose.SLEEP || (currentPose == SprigPose.IDLE && motion.asleepFromIdle)
            }
        }
    }

    val spec = SprigSpec(form, pose, wilted, tear, silhouetteAlpha, saturation)
    val description = SprigLook.contentDescription(form, if (asleep) SprigPose.SLEEP else pose, wilted)
    val touch = if (!live) Modifier else Modifier
        // Its own focus stop, so inside a clickable card TalkBack's double tap hops Sprig instead
        // of running the card's click.
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = description
            onClick { motion.poke(); haptics.sprigHop(); true }
        }
        .pointerInput(motion) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    when (event.type) {
                        PointerEventType.Press -> {
                            motion.press(change.uptimeMillis)
                            haptics.sprigHop()
                            motion.move(change.position.x / w, change.position.y / h, 0f)
                            change.consume()
                        }
                        PointerEventType.Move -> {
                            val d = change.position - change.previousPosition
                            motion.move(change.position.x / w, change.position.y / h, abs(d.x) + abs(d.y))
                            if (motion.isDown) change.consume()
                        }
                        PointerEventType.Release, PointerEventType.Exit -> {
                            motion.release()
                            if (event.type == PointerEventType.Release) change.consume()
                        }
                        else -> Unit
                    }
                }
            }
        }
    val a11y = if (live) Modifier else Modifier.semantics { contentDescription = description }

    val track = Modifier.onGloballyPositioned { place[0] = it }
    Canvas(modifier.onSizeChanged { px = it }.then(track).then(a11y).then(touch)) {
        frames // redraw on every frame tick
        drawSprig(spec, motion)
    }
}

/**
 * Waits [OFFSCREEN_POLL_MS]. Goes through [InfiniteAnimationPolicy] like the frame loop does, so
 * UI tests that cancel infinite animations are not kept busy by the poll either.
 */
private suspend fun offscreenPause() {
    val policy = coroutineContext[InfiniteAnimationPolicy]
    if (policy == null) delay(OFFSCREEN_POLL_MS) else policy.onInfiniteOperation { delay(OFFSCREEN_POLL_MS) }
}

/**
 * True while some of this node is on screen. `boundsInWindow` is clipped by every ancestor, so a
 * Sprig scrolled out of a scroll container's viewport has empty bounds.
 */
internal fun LayoutCoordinates.isVisibleInWindow(): Boolean {
    if (!isAttached) return false
    val b = boundsInWindow()
    return b.width > 0f && b.height > 0f
}
