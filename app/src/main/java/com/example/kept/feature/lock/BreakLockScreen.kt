package com.example.kept.feature.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.domain.BREAK_COUNTDOWN_SECONDS
import com.example.kept.core.domain.BreakCopy
import com.example.kept.core.domain.BreakCosts
import com.example.kept.core.domain.BreakCountdown
import com.example.kept.core.domain.BreakFade
import com.example.kept.core.domain.BreakSprig
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.LocalReduceMotion
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.SprigMotion
import com.example.kept.core.ui.sprig.SprigView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val D = DaybreakColors

/** `spRise`: `cubic-bezier(.3, 1.4, .5, 1)`, 1s after 0.3s. */
private val RiseEasing = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)
internal const val RISE_MS = 1000
internal const val RISE_DELAY_MS = 300

/** `bubPop`: `cubic-bezier(.3, 1.6, .5, 1)`, 0.4s at 1.2s, from scale 0.3 and opacity 0. */
private val PopEasing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)
internal const val POP_MS = 400
internal const val POP_DELAY_MS = 1200
private const val POP_FROM_SCALE = 0.3f

/** The clock on a short screen ([BreakSprig.compact]); 84sp elsewhere. */
private const val COMPACT_TIMER_SP = 60

/**
 * On a short screen the page starts this far up into the hill. The hill is cream from 16dp below
 * its crest at the screen's edges (and higher towards the middle), and the sky ends 52dp below
 * the crest, so text lifted 24dp still sits on cream, never on the sky.
 */
private val COMPACT_LIFT = 24.dp

/** Measures and places the content [by] taller and that much higher. */
private fun Modifier.liftIntoHill(by: Dp): Modifier = if (by == 0.dp) this else layout { m, c ->
    val up = by.roundToPx()
    val p = m.measure(c.copy(minHeight = c.minHeight + up, maxHeight = if (c.hasBoundedHeight) c.maxHeight + up else c.maxHeight))
    layout(p.width, p.height - up) { p.place(0, -up) }
}

/** `transition: filter 1s linear` and `opacity 1s linear`: each second's fade runs over that second. */
private const val FADE_MS = 1000

/**
 * Break the lock in Daybreak (`A4-Break`, `A4-Break-last`, spec Break the lock). An overcast sky
 * with "Break the lock?", Sprig drooping in tears and the bubble "Don't let me wilt."; what the
 * break costs (or the last-resort warning on the fourth break this week); the countdown; and
 * "Never mind, go back" over "Unlock anyway ({s}s)", which becomes "Unlock for {minutes} min".
 *
 * The countdown is [secondsLeft], kept by `LockViewModel` so it survives a rotation. When
 * [playIntro] is set Sprig rises from behind the hill and the bubble pops, once; [onIntroStarted]
 * tells the ViewModel so a recreated screen does not replay it. With reduce motion Sprig and the
 * bubble simply appear. Sprig's colour and the bubble's text fade with the countdown either way.
 *
 * [sprigMotion] is hoisted for tests.
 */
@Composable
fun BreakLockScreen(
    state: LockUi,
    secondsLeft: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    playIntro: Boolean = false,
    onIntroStarted: () -> Unit = {},
    total: Int = BREAK_COUNTDOWN_SECONDS,
    sprigMotion: SprigMotion = remember { SprigMotion() },
) {
    val reduce = LocalReduceMotion.current
    val animateIntro = playIntro && !reduce
    val rise = remember { Animatable(if (animateIntro) BreakSprig.RISE_FROM_DP.toFloat() else 0f) }
    val pop = remember { Animatable(if (animateIntro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (playIntro) onIntroStarted()
        if (!animateIntro) return@LaunchedEffect
        coroutineScope {
            launch { rise.animateTo(0f, tween(RISE_MS, RISE_DELAY_MS, RiseEasing)) }
            launch { pop.animateTo(1f, tween(POP_MS, POP_DELAY_MS, PopEasing)) }
        }
    }
    val saturation by animateFloatAsState(BreakFade.saturation(secondsLeft, total), tween(FADE_MS, easing = LinearEasing), label = "break-saturation")
    val voice by animateFloatAsState(BreakFade.voiceAlpha(secondsLeft, total), tween(FADE_MS, easing = LinearEasing), label = "break-voice")

    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var textHeightPx by remember { mutableIntStateOf(0) }
    var bubbleHeightPx by remember { mutableIntStateOf(0) }
    val textBottom = BreakSprig.TEXT_TOP_DP + with(density) { textHeightPx.toDp() }.value.toInt()
    val bubbleHeight = with(density) { bubbleHeightPx.toDp() }.value.toInt()

    BoxWithConstraints(modifier.fillMaxSize().testTag("break_screen")) {
        val width = maxWidth.value.toInt()
        val height = (maxHeight - inset - navInset).value.toInt()
        val spot = BreakSprig.spot(width, height, textBottom, bubbleHeight)
        val compact = BreakSprig.compact(height)
        SkyScaffold(
            sky = Sky.overcast,
            skyHeight = inset + spot.crestDp.dp + Hill.RISE,
            skyContent = {
                // Where the hill's crest is, for tests; takes no space and draws nothing.
                Box(Modifier.offset(y = inset + spot.crestDp.dp).size(0.dp).testTag("break_crest"))
                Headline(
                    Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                        .padding(top = inset + BreakSprig.TEXT_TOP_DP.dp, start = BreakSprig.TEXT_SIDE_DP.dp)
                        .onSizeChanged { textHeightPx = it.height },
                )
                SprigView(
                    state.sprig.form,
                    SprigPose.DROOP,
                    Modifier.offset(x = spot.sprigLeftDp.dp, y = inset + spot.sprigTopDp.dp)
                        .graphicsLayer { translationY = rise.value.dp.toPx() }
                        .size(spot.sprigDp.dp)
                        .testTag("break_sprig"),
                    tear = true,
                    motion = sprigMotion,
                    saturation = saturation,
                )
                Bubble(
                    voice,
                    spot.bubbleMaxWidthDp,
                    Modifier.offset(x = BreakSprig.BUBBLE_LEFT_DP.dp, y = inset + spot.bubbleTopDp.dp)
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .onSizeChanged { bubbleHeightPx = it.height }
                        .graphicsLayer {
                            val p = pop.value
                            transformOrigin = TransformOrigin(1f, 1f)
                            scaleX = POP_FROM_SCALE + (1f - POP_FROM_SCALE) * p
                            scaleY = scaleX
                            alpha = p.coerceIn(0f, 1f)
                        },
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).liftIntoHill(if (compact) COMPACT_LIFT else 0.dp).clipToBounds()) {
                    Body(
                        BreakCosts.of(state.sprig.level, state.sprig.streakDays, state.breaksLeft, state.overCap),
                        secondsLeft,
                        compact,
                    )
                }
                Footer(secondsLeft, state.breakMinutes, onCancel, onConfirm, compact)
            }
        }
    }
}

@Composable
private fun Headline(modifier: Modifier = Modifier) {
    val width = BreakSprig.TEXT_WIDTH_DP.dp
    val base = DaybreakType.headline.copy(fontSize = 38.sp, lineHeight = 38.sp)
    Text(
        BreakCopy.HEADLINE,
        modifier.width(width).testTag("break_headline"),
        style = fitLongestWord(BreakCopy.HEADLINE, base, width),
        color = D.ink,
    )
}

/** "Don't let me wilt.": white, `18 18 4 18` corners (the tail points at Sprig), a soft shadow. */
@Composable
private fun Bubble(voice: Float, maxWidthDp: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    val textMax = (maxWidthDp - 2 * BUBBLE_PAD_X).coerceAtLeast(40).dp
    val base = DaybreakType.rowTitle.copy(fontSize = 17.sp, lineHeight = (17 * 1.2).sp)
    Box(
        modifier.widthIn(max = maxWidthDp.dp)
            .testTag("break_bubble")
            .shadow(8.dp, shape, ambientColor = D.bubbleShadow, spotColor = D.bubbleShadow)
            .background(D.white, shape)
            .padding(horizontal = BUBBLE_PAD_X.dp, vertical = 10.dp),
    ) {
        Text(
            BreakCopy.PLEA,
            Modifier.widthIn(max = textMax).graphicsLayer { alpha = voice }.testTag("break_plea"),
            style = fitLongestWord(BreakCopy.PLEA, base, textMax),
            color = D.ink,
        )
    }
}

private const val BUBBLE_PAD_X = 14

/**
 * "What it costs" and the countdown. The countdown sits centred in the space left, as the
 * design's `flex: 1`; when there is none (a small screen, a large font) the two scroll together.
 */
@Composable
private fun Body(costs: BreakCosts, secondsLeft: Int, compact: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = constraints.maxHeight
        val timerWidth = maxWidth - 48.dp
        Layout(
            content = {
                Costs(costs, compact)
                Countdown(secondsLeft, timerWidth, compact)
            },
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("break_body"),
        ) { measurables, c ->
            val loose = c.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val top = measurables[0].measure(loose)
            val clock = measurables[1].measure(loose.copy(minWidth = 0))
            val h = maxOf(viewport, top.height + clock.height)
            layout(c.maxWidth, h) {
                top.place(0, 0)
                val free = h - top.height - clock.height
                clock.place((c.maxWidth - clock.width) / 2, top.height + free / 2)
            }
        }
    }
}

@Composable
private fun Costs(costs: BreakCosts, compact: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp).testTag("break_costs"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        Text(BreakCopy.COSTS_TITLE, style = DaybreakType.section, color = D.ink)
        when (costs) {
            is BreakCosts.Normal -> costs.rows.forEach { CostRow(it) }
            is BreakCosts.LastResort -> {
                Text(
                    costs.callout,
                    Modifier.fillMaxWidth().background(D.dangerTint, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("break_last_resort"),
                    style = DaybreakType.bodySmall.copy(lineHeight = (14 * 1.4).sp),
                    fontWeight = FontWeight.SemiBold,
                    color = D.dangerInk,
                )
                Text(
                    costs.line,
                    Modifier.testTag("break_written_off"),
                    style = DaybreakType.body.copy(lineHeight = (15 * 1.4).sp),
                    fontWeight = FontWeight.SemiBold,
                    color = D.ink,
                )
            }
        }
    }
}

@Composable
private fun CostRow(row: BreakCosts.Row) {
    val (icon, tile) = costIcon(row.kind)
    Row(
        Modifier.fillMaxWidth().testTag("break_cost_${row.kind.name.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(tile, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = Color.Unspecified)
        }
        Text(
            row.text,
            Modifier.weight(1f),
            style = DaybreakType.body.copy(lineHeight = (15 * 1.35).sp),
            fontWeight = FontWeight.SemiBold,
            color = D.ink,
        )
    }
}

private fun costIcon(kind: BreakCosts.Kind): Pair<ImageVector, Color> = when (kind) {
    BreakCosts.Kind.WILT -> DaybreakIcons.Wilt to DaybreakIcons.BreakTile.wilt
    BreakCosts.Kind.STREAK -> DaybreakIcons.StreakFlame to DaybreakIcons.BreakTile.streak
    BreakCosts.Kind.BREAKS -> DaybreakIcons.Ticket to DaybreakIcons.BreakTile.breaks
}

/** "You can unlock in" over the 84sp tabular clock, which steps down rather than clip at large fonts. */
@Composable
private fun Countdown(secondsLeft: Int, width: Dp, compact: Boolean) {
    val timer = if (compact) DaybreakType.timer.copy(fontSize = COMPACT_TIMER_SP.sp, lineHeight = COMPACT_TIMER_SP.sp) else DaybreakType.timer
    Column(
        Modifier.padding(vertical = if (compact) 0.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            BreakCopy.clockLabel(secondsLeft),
            Modifier.testTag("break_clock_label"),
            style = DaybreakType.body,
            fontWeight = FontWeight.SemiBold,
            color = D.muted,
            textAlign = TextAlign.Center,
        )
        // Fitted to "0:00", as wide as any clock with tabular figures, so the size never jumps.
        Text(
            BreakCountdown.clock(secondsLeft),
            Modifier.testTag("break_countdown"),
            style = fitLongestWord("0:00", timer, width),
            color = D.ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun Footer(secondsLeft: Int, minutes: Int, onCancel: () -> Unit, onConfirm: () -> Unit, compact: Boolean) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = if (compact) 16.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PrimaryButton(
            BreakCopy.GO_BACK,
            onClick = onCancel,
            modifier = Modifier.heightIn(min = 60.dp).testTag("break_go_back"),
            textStyle = DaybreakType.button.copy(fontSize = 18.sp, lineHeight = 23.sp),
        )
        QuietButton(
            BreakCopy.unlockLabel(secondsLeft, minutes),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("unlock_anyway"),
            color = D.danger,
            enabled = secondsLeft == 0,
            disabledColor = D.waitingInk,
        )
    }
}
