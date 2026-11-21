package com.example.kept.core.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.domain.CelebrationCopy
import com.example.kept.core.domain.MomentFit
import com.example.kept.core.domain.MomentMotion
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.sprig.SprigView
import kotlin.math.ceil

/** `.pop`: `cubic-bezier(.3, 1.6, .5, 1)`. */
private val PopEasing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)

/** `.ring`: CSS `ease-out`. */
private val RingEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** `#5C4A1A`, "Tap to carry on" on the gold moment (`A16-Celebrate`). */
private val TapInk = Color(0xFF5C4A1A)

/**
 * The clock of one moment (`MomentMotion`). It runs once, from the first composition, when
 * [playIntro] is set and reduce motion is off, and tells [onIntroStarted] so a recreated screen
 * shows the end state instead of playing it again. Otherwise it reads the end at once.
 * [frozenAtMs] pins it to one frame, for screenshot tests.
 */
class MomentClock internal constructor(val playing: Boolean, val elapsed: () -> Long)

@Composable
fun rememberMomentClock(playIntro: Boolean, onIntroStarted: () -> Unit, frozenAtMs: Long? = null): MomentClock {
    val reduce = LocalReduceMotion.current
    // Decided once: the ViewModel saying the intro has played must not stop the one that is running.
    val play = remember { playIntro && !reduce && frozenAtMs == null }
    val clock = remember { Animatable(0f) }
    val started by rememberUpdatedState(onIntroStarted)
    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        started()
        clock.animateTo(MomentMotion.END_MS.toFloat(), tween(MomentMotion.END_MS, easing = LinearEasing))
    }
    return remember(play, reduce, frozenAtMs) {
        when {
            reduce -> MomentClock(false) { MomentMotion.END_MS.toLong() }
            frozenAtMs != null -> MomentClock(true) { frozenAtMs }
            play -> MomentClock(true) { clock.value.toLong() }
            else -> MomentClock(false) { MomentMotion.END_MS.toLong() }
        }
    }
}

/** The 18 petals of a moment, while they are falling. */
@Composable
fun MomentPetals(clock: MomentClock, modifier: Modifier = Modifier) {
    val falling by remember(clock) { derivedStateOf { clock.playing && clock.elapsed() < MomentMotion.SHOWER.endMs } }
    if (falling) Petals(MomentMotion.SHOWER, clock.elapsed, modifier.testTag("moment_petals"))
}

/**
 * Sprig cheering in a moment (`.pop` box): it pops up from below, and a ring behind it grows and
 * fades. [sizeDp] is the box; the ring and the optional [glow] are placed in the design's
 * proportions of the design box [designDp]. With reduce motion Sprig is simply there, without the
 * ring (which ends invisible).
 */
@Composable
fun MomentSprig(
    form: SprigForm,
    sizeDp: Int,
    designDp: Int,
    ringDp: Int,
    ringColor: Color,
    clock: MomentClock,
    modifier: Modifier = Modifier,
    glow: Color? = null,
) {
    val k = sizeDp.toFloat() / designDp
    Box(
        modifier.size(sizeDp.dp).testTag("moment_pop").graphicsLayer {
            val p = MomentMotion.pop(PopEasing.transform(MomentMotion.popProgress(clock.elapsed())))
            translationY = (p.yDp * k).dp.toPx()
            scaleX = p.scale
            scaleY = p.scale
            alpha = p.alpha
        },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width / designDp
            if (glow != null) {
                // `left: 40px; right: 40px; bottom: 6px; height: 26px; border-radius: 50%`.
                drawOval(glow, topLeft = Offset(40 * unit, size.height - (6 + 26) * unit), size = Size(size.width - 80 * unit, 26 * unit))
            }
            val e = RingEasing.transform(MomentMotion.ringProgress(clock.elapsed()))
            val alpha = MomentMotion.ringAlpha(e)
            if (clock.playing && alpha > 0f) {
                val stroke = 6 * unit
                // A CSS border sits inside the box: the stroke's centre is half a border in.
                val r = (ringDp * unit / 2f - stroke / 2f) * MomentMotion.ringScale(e)
                drawCircle(ringColor, radius = r, center = center, alpha = alpha, style = Stroke(stroke * MomentMotion.ringScale(e)))
            }
        }
        SprigView(form, SprigPose.CHEER, Modifier.matchParentSize().testTag("moment_sprig"))
    }
}

/**
 * Calls [onTap] for a tap anywhere inside, Sprig included: the moment listens before its
 * children, so Sprig still hops under the finger but the tap carries on all the same.
 */
fun Modifier.tapAnywhere(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (waitForUpOrCancellation(PointerEventPass.Initial) != null) onTap()
    }
}

/**
 * The full-screen "Promise kept" moment (`A16-Celebrate`, spec Moments): gold, Sprig cheering in
 * [form]'s outfit at 260dp with its pop and fading ring, 18 petals, "Promise kept.", "Day {n} of
 * your streak. Apps are open." and "Tap to carry on". A tap anywhere, or Back where the host
 * handles it, calls [onCarryOn]. KEPT shows it over the app and the lock screen shows it before
 * its gold open state (issue #130).
 *
 * TalkBack hears it as a pane titled "Promise kept." with a polite live region on the headline,
 * and "Tap to carry on" is a button.
 */
@Composable
fun PromiseKeptMoment(
    form: SprigForm,
    streakDays: Int,
    onCarryOn: () -> Unit,
    modifier: Modifier = Modifier,
    playIntro: Boolean = true,
    onIntroStarted: () -> Unit = {},
    frozenAtMs: Long? = null,
) {
    val clock = rememberMomentClock(playIntro, onIntroStarted, frozenAtMs)
    val ctx = LocalContext.current
    LaunchedEffect(clock.playing) { if (clock.playing && frozenAtMs == null) celebrateBuzz(ctx) }
    val day = CelebrationCopy.streakDay(streakDays)
    val line = CelebrationCopy.line(day)
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var textPx by remember { mutableIntStateOf(0) }
    var tapPx by remember { mutableIntStateOf(0) }
    fun dp(px: Int): Int = ceil(with(density) { px.toDp() }.value).toInt()

    StatusBarIcons(light = false)
    BoxWithConstraints(
        modifier.fillMaxSize().background(Sky.gold).testTag("celebration")
            .semantics {
                paneTitle = CelebrationCopy.HEADLINE
                onClick(label = "Carry on") { onCarryOn(); true }
            }
            .tapAnywhere(onCarryOn),
    ) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        // "Tap to carry on" sits 40dp above the bottom; the centred block never runs into it.
        val tapReserve = 40 + dp(tapPx) + 12
        val text = dp(textPx)
        val sprig = MomentFit.sprigDp(MomentFit.CELEBRATE_DP, height, top.value.toInt() + bottom.value.toInt() + tapReserve + 6 + text, width - 56)
        val block = sprig + 6 + text
        val ideal = (height - block) / 2
        val lowest = height - bottom.value.toInt() - tapReserve - block
        val y = minOf(ideal, lowest).coerceAtLeast(top.value.toInt())
        val total = maxOf(height, y + block + tapReserve + bottom.value.toInt())

        MomentPetals(clock, Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(total.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp).offset(y = y.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MomentSprig(form, sprig, MomentFit.CELEBRATE_DP, ringDp = 200, ringColor = Color.White.copy(alpha = 0.7f), clock = clock)
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).onSizeChanged { textPx = it.height },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val width28 = (width - 56).dp
                        Text(
                            CelebrationCopy.HEADLINE,
                            Modifier.testTag("celebration_headline").semantics { liveRegion = LiveRegionMode.Polite },
                            style = fitLongestWord(CelebrationCopy.HEADLINE, HEADLINE_STYLE, width28),
                            color = DaybreakColors.ink, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            line, Modifier.testTag("celebration_line"),
                            style = LINE_STYLE, color = DaybreakColors.inkSoft, textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(
                    CelebrationCopy.CARRY_ON,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 40.dp, start = 28.dp, end = 28.dp)
                        .onSizeChanged { tapPx = it.height }
                        .testTag("celebration_carry_on")
                        .semantics { role = Role.Button; onClick { onCarryOn(); true } },
                    style = TAP_STYLE, color = TapInk, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** `font-size: 52px; line-height: 1; font-weight: 800; letter-spacing: -0.03em`. */
private val HEADLINE_STYLE = DaybreakType.display.copy(lineHeight = 52.sp)

/** `font-size: 18px; font-weight: 600` (line height 1.4, as body copy). */
private val LINE_STYLE = DaybreakType.body.copy(fontSize = 18.sp, lineHeight = (18 * 1.4).sp, fontWeight = FontWeight.SemiBold)

/** `font-size: 14px; font-weight: 700`. */
private val TAP_STYLE = DaybreakType.body.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)

/** The moment's short double buzz (kept from issue #9). VIBRATE is declared for the lock flow. */
private fun celebrateBuzz(ctx: Context) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        } ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 60, 34), -1))
    }
}
