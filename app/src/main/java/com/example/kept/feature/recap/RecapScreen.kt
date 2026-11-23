package com.example.kept.feature.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.RecapCopy
import com.example.kept.core.domain.RecapMotion
import com.example.kept.core.domain.RecapOutcome
import com.example.kept.core.domain.RecapProgress
import com.example.kept.core.domain.RecapSprig
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.LocalReduceMotion
import com.example.kept.core.ui.Petals
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.SecondaryButton
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.ShareCard
import com.example.kept.core.ui.sprig.SprigMotion
import com.example.kept.core.ui.sprig.SprigSpec
import com.example.kept.core.ui.sprig.SprigView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class RecapUi(
    val record: DayRecordEntity? = null,
    val date: LocalDate = LocalDate.now(),
    /** Derived from this day and the one before it, see [RecapRules.streakReset] (issue #16). */
    val streakReset: Boolean = false,
) {
    val outcome: RecapOutcome? get() = record?.let(RecapRules::outcome)

    /** Weekly goals whose missed must-do session explains the day (story #176); usually none. */
    val weeklyMissed: List<String> get() = record?.let(RecapRules::weeklyMissed).orEmpty()
}

@HiltViewModel
class RecapViewModel @Inject constructor(
    dayRepo: DayRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val date: LocalDate = LocalDate.parse(saved.get<String>("date")!!)
    val state = combine(dayRepo.observe(date), dayRepo.observe(date.minusDays(1))) { r, previous ->
        RecapUi(r, date, RecapRules.streakReset(r, previous))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUi(date = date))

    /** The kept-day petals and bar grow play once per showing, not again after a rotation. */
    val introPending: Boolean get() = saved.get<Boolean>(KEY_INTRO_PLAYED) != true

    fun introStarted() {
        saved[KEY_INTRO_PLAYED] = true
    }

    companion object {
        const val KEY_INTRO_PLAYED = "recap_intro_played"
    }
}

@Composable
fun RecapScreen(date: String, onClose: () -> Unit, onOpenSprigs: () -> Unit, vm: RecapViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    RecapContent(s, onClose, onOpenSprigs, playIntro = vm.introPending, onIntroStarted = vm::introStarted)
}

private val D = DaybreakColors

/** `.grow`: `cubic-bezier(.3, 1.3, .5, 1)`. */
private val GrowEasing = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f)

/** `.knob`: `cubic-bezier(.3, 1.8, .5, 1)`. */
private val PopEasing = CubicBezierEasing(0.3f, 1.8f, 0.5f, 1f)

/** The next form's silhouette (`filter: brightness(0) opacity(0.22)`). */
private const val SILHOUETTE_ALPHA = 0.22f

internal fun recapSky(o: RecapOutcome?): Color = when (o) {
    RecapOutcome.KEPT -> Sky.gold
    RecapOutcome.SHIELD -> Sky.tide
    RecapOutcome.LOCK_OFF -> Sky.recapLockOff
    RecapOutcome.TOO_LATE -> Sky.recapTooLate
    RecapOutcome.SLIPPED, RecapOutcome.WRITTEN_OFF, null -> Sky.overcast
}

/**
 * The recap in Daybreak (`A5-Recap*`, spec Recap), without the ViewModel so screenshot tests can
 * render it. A 400dp sky in the outcome's colour with the headline, the line and Sprig in the
 * outcome's pose; two stats; the progress card toward the next form; and the two buttons.
 *
 * On a kept day, when [playIntro] is set and reduce motion is off, 14 petals fall once, the bar
 * grows from yesterday to today and the knob pops; [onIntroStarted] tells the ViewModel so a
 * recreated screen does not replay it. The page scrolls only when it cannot fit (a small screen
 * or a large font).
 */
@Composable
internal fun RecapContent(
    s: RecapUi,
    onClose: () -> Unit,
    onOpenSprigs: () -> Unit = {},
    modifier: Modifier = Modifier,
    playIntro: Boolean = false,
    onIntroStarted: () -> Unit = {},
    sprigMotion: SprigMotion = remember { SprigMotion() },
) {
    val ctx = LocalContext.current
    val r = s.record
    val outcome = s.outcome
    val kept = outcome == RecapOutcome.KEPT
    val streak = r?.streakEnd ?: 0
    val progress = RecapProgress.of(streak, grows = kept)
    val form = SprigForm.forStreak(streak)
    val pose = outcome?.let(RecapCopy::pose) ?: SprigPose.IDLE

    // Decided once the record has loaded: a later recomposition (the ViewModel now saying the
    // intro has played) must not cancel the moment that is running.
    val reduce = LocalReduceMotion.current
    val play = remember(kept) { playIntro && kept && !reduce }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        onIntroStarted()
        clock.animateTo(RecapMotion.END_MS.toFloat(), tween(RecapMotion.END_MS, easing = LinearEasing))
    }
    val elapsed: () -> Long = { if (play) clock.value.toLong() else RecapMotion.END_MS.toLong() }
    val petalsFalling by remember(play) { derivedStateOf { play && clock.value < RecapMotion.PETALS_END_MS } }

    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    var textPx by remember { mutableIntStateOf(0) }
    var bodyPx by remember { mutableIntStateOf(0) }
    var footerPx by remember { mutableIntStateOf(0) }
    // Rounded up, so the page asks for at least the room it needs and never scrolls by a pixel.
    fun dp(px: Int): Int = kotlin.math.ceil(with(density) { px.toDp() }.value).toInt()

    BoxWithConstraints(modifier.fillMaxSize().testTag("recap_screen")) {
        val width = maxWidth.value.toInt()
        val compact = RecapSprig.compact((maxHeight - inset).value.toInt())
        val below = Hill.RISE.value.toInt() + dp(bodyPx) + RecapSprig.MIN_GAP_DP + dp(footerPx)
        val spot = RecapSprig.spot(width, (maxHeight - inset).value.toInt(), RecapSprig.TEXT_TOP_DP + dp(textPx), below, kept)
        val skyHeight = inset + spot.crestDp.dp + Hill.RISE
        val total = maxOf(maxHeight, skyHeight + dp(bodyPx + footerPx).dp + RecapSprig.MIN_GAP_DP.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("recap_page")) {
            SkyScaffold(
                sky = recapSky(outcome),
                skyHeight = skyHeight,
                modifier = Modifier.fillMaxWidth().height(total),
                skyContent = {
                    // Where the hill's crest is, for tests; takes no space and draws nothing.
                    Box(Modifier.offset(y = inset + spot.crestDp.dp).size(0.dp).testTag("recap_crest"))
                    Headline(
                        outcome, streak, s.streakReset, s.weeklyMissed, (width - 2 * RecapSprig.TEXT_SIDE_DP).dp,
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                            .padding(top = inset + RecapSprig.TEXT_TOP_DP.dp, start = RecapSprig.TEXT_SIDE_DP.dp)
                            .onSizeChanged { textPx = it.height },
                    )
                    SprigView(
                        form,
                        pose,
                        Modifier.offset(x = spot.sprigLeftDp.dp, y = inset + spot.sprigTopDp.dp).size(spot.sprigDp.dp).testTag("recap_sprig"),
                        motion = sprigMotion,
                    )
                    if (petalsFalling) Petals(RecapMotion.SHOWER, elapsed, Modifier.matchParentSize().testTag("recap_petals"))
                },
            ) {
                Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).onSizeChanged { bodyPx = it.height }) {
                    if (r != null) {
                        Stats(r, ((width - 48) / 2).dp)
                        ProgressCard(progress, kept, elapsed, compact)
                    }
                }
                Spacer(Modifier.height(RecapSprig.MIN_GAP_DP.dp))
                Spacer(Modifier.weight(1f))
                Footer(
                    showSecond = r != null,
                    secondLabel = if (kept) RecapCopy.SHARE else RecapCopy.SEE_SPRIGS,
                    onSecond = {
                        if (kept && r != null) {
                            ShareCard.share(
                                ctx,
                                ShareCard.Content(
                                    SprigSpec(form, SprigPose.CHEER),
                                    headline = "Day ${r.streakEnd} kept",
                                    subline = "${r.habitsDone} of ${r.habitsTotal} habits",
                                ),
                            )
                        } else {
                            onOpenSprigs()
                        }
                    },
                    onDone = onClose,
                    compact = compact,
                    modifier = Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true).onSizeChanged { footerPx = it.height },
                )
            }
        }
    }
}

/** The headline (52sp kept, 40sp otherwise, never split inside a word) and the line under it. */
@Composable
private fun Headline(outcome: RecapOutcome?, streak: Int, reset: Boolean, weeklyMissed: List<String>, width: Dp, modifier: Modifier = Modifier) {
    val kept = outcome == RecapOutcome.KEPT
    val text = outcome?.let(RecapCopy::headline) ?: RecapCopy.NOTHING
    val size = RecapSprig.headlineSp(kept)
    val base = DaybreakType.display.copy(fontSize = size.sp, lineHeight = (size * 0.98).sp, letterSpacing = (-0.03).em)
    Column(modifier.width(width)) {
        Text(text, Modifier.testTag("recap_headline"), style = fitLongestWord(text, base, width), color = D.ink)
        if (outcome != null) {
            Text(
                RecapCopy.line(outcome, streak, reset, weeklyMissed),
                Modifier.padding(top = 10.dp).testTag("recap_line"),
                style = DaybreakType.body.copy(fontSize = 18.sp, lineHeight = (18 * 1.35).sp, fontWeight = FontWeight.SemiBold),
                color = D.inkSoft,
            )
        }
    }
}

/** "{done} of {total}" habits done | "{n}" lock(s) broken. The values never wrap; they step down. */
@Composable
private fun Stats(r: DayRecordEntity, cellWidth: Dp) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 24.dp).testTag("recap_stats")) {
        // Both values take the size that fits the wider one, so the labels start on one line.
        val done = RecapCopy.done(r.habitsDone, r.habitsTotal)
        val broken = "${r.breaksUsed}"
        val base = DaybreakType.display.copy(fontSize = 28.sp, lineHeight = (28 * 1.1).sp, letterSpacing = (-0.02).em)
        val style = fitLongestWord(if (done.length >= broken.length) done else broken, base, cellWidth - 8.dp)
        Stat(done, RecapCopy.HABITS_DONE, "recap_done", style, Modifier.weight(1f))
        Stat(
            broken, RecapCopy.brokenLabel(r.breaksUsed), "recap_broken", style,
            Modifier.weight(1f).drawBehind { drawRect(D.rowLine, size = Size(1.5.dp.toPx(), size.height)) },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, tag: String, valueStyle: TextStyle, modifier: Modifier) {
    // 4dp sides so a long label at a large font wraps before it touches the divider.
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, Modifier.testTag(tag), style = valueStyle, color = D.ink, maxLines = 1, textAlign = TextAlign.Center)
        Text(
            label,
            Modifier.testTag("${tag}_label"),
            style = DaybreakType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = D.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The progress card (`A5-Recap`): "{Next} in {n} days" and "Day {d} of {target}" over the current
 * form, the bar and the next form's silhouette; at the final form, one line and no bar.
 */
@Composable
private fun ProgressCard(progress: RecapProgress, kept: Boolean, elapsed: () -> Long, compact: Boolean) {
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = if (compact) RecapSprig.COMPACT_CARD_GAP_DP.dp else 24.dp).fillMaxWidth()
            .background(D.purplePale, RoundedCornerShape(24.dp))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp)
            .testTag("recap_progress"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val lineStyle = DaybreakType.rowTitle.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
        when (progress) {
            is RecapProgress.Final -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniSprig(progress.form, silhouette = false, tag = "recap_from_sprig")
                Text(RecapCopy.finalForm(progress.form), Modifier.weight(1f).testTag("recap_final"), style = lineStyle, color = D.ink)
            }
            is RecapProgress.Toward -> {
                CardHeader(
                    first = { Text(RecapCopy.nextLine(progress.to, progress.daysLeft), Modifier.testTag("recap_next"), style = lineStyle, color = D.ink) },
                    second = {
                        Text(
                            RecapCopy.dayCount(progress.day, progress.target),
                            Modifier.testTag("recap_count"),
                            style = DaybreakType.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = D.muted,
                        )
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniSprig(progress.from, silhouette = false, tag = "recap_from_sprig")
                    Bar(progress, kept, elapsed, Modifier.weight(1f))
                    MiniSprig(progress.to, silhouette = true, tag = "recap_to_sprig")
                }
            }
        }
    }
}

/** Least space between "{Next} in {n} days" and "Day {d} of {target}" on one line. */
private val HEADER_GAP = 12.dp

/**
 * The card's top line (`justify-content: space-between; align-items: baseline`): [first] at the
 * start and [second] at the end on a shared baseline when both fit with [HEADER_GAP] between them;
 * otherwise [second] goes under [first], at the start. A `FlowRow` with `SpaceBetween` left them
 * touching when they only just fit (QA on #129).
 */
@Composable
private fun CardHeader(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Layout(contents = listOf(first, second), modifier = Modifier.fillMaxWidth()) { (a, b), c ->
        val w = c.maxWidth
        val loose = c.copy(minWidth = 0, minHeight = 0)
        val pa = a.single().measure(loose)
        val pb = b.single().measure(loose)
        val gap = HEADER_GAP.roundToPx()
        if (pa.width + gap + pb.width <= w) {
            val baseA = pa[androidx.compose.ui.layout.FirstBaseline]
            val baseB = pb[androidx.compose.ui.layout.FirstBaseline]
            val base = maxOf(baseA, baseB)
            val h = maxOf(base - baseA + pa.height, base - baseB + pb.height)
            layout(w, h) {
                pa.place(0, base - baseA)
                pb.place(w - pb.width, base - baseB)
            }
        } else {
            val between = 2.dp.roundToPx()
            layout(w, pa.height + between + pb.height) {
                pa.place(0, 0)
                pb.place(0, pa.height + between)
            }
        }
    }
}

/** A 48dp Sprig, drawn still, over its form's name; the next form is a silhouette in its outfit. */
@Composable
private fun MiniSprig(form: SprigForm, silhouette: Boolean, tag: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SprigView(
            form,
            SprigPose.IDLE,
            Modifier.size(48.dp).testTag(tag),
            silhouetteAlpha = if (silhouette) SILHOUETTE_ALPHA else null,
            animate = false,
        )
        Text(
            form.displayName,
            style = DaybreakType.smallLabel,
            color = if (silhouette) D.muted else D.ink,
            maxLines = 1,
        )
    }
}

private val BAR_HEIGHT = 16.dp
private val FILL_MIN = 16.dp
private val KNOB = 26.dp
private val KNOB_RING = 4.dp

/** The knob's right edge runs 6dp past the fill's (`right: -6px`), its top 5dp above the bar. */
private val KNOB_PAST = 6.dp
private val KNOB_ABOVE = 5.dp

/**
 * The bar: a 16dp `#DCD6FA` track, the purple fill (at least 16dp), a dot for each day inside the
 * span (white once behind, faint purple ahead) and the white knob on today. The fill's width and
 * the knob's scale are read in layout and draw, so the moment does not recompose the screen.
 */
@Composable
private fun Bar(p: RecapProgress.Toward, kept: Boolean, elapsed: () -> Long, modifier: Modifier = Modifier) {
    val fraction: () -> Float = {
        if (!kept) p.toFraction
        else p.fromFraction + (p.toFraction - p.fromFraction) * GrowEasing.transform(RecapMotion.growProgress(elapsed()))
    }
    Layout(
        content = {
            Box(Modifier.background(D.purple, RoundedCornerShape(8.dp)).testTag("recap_bar_fill"))
            Canvas(Modifier) {
                val r = 3.dp.toPx()
                for (d in p.dots) {
                    drawCircle(
                        if (d.reached) Color.White.copy(alpha = 0.55f) else D.purple.copy(alpha = 0.25f),
                        radius = r,
                        center = Offset(size.width * d.fraction, size.height / 2),
                    )
                }
            }
            Box(
                Modifier
                    .graphicsLayer {
                        val s = RecapMotion.knobScale(RecapMotion.popProgress(elapsed())) { PopEasing.transform(it) }
                        scaleX = s; scaleY = s
                    }
                    .background(D.purple, CircleShape)
                    .padding(KNOB_RING)
                    .background(D.white, CircleShape)
                    .testTag("recap_knob"),
            )
        },
        modifier = modifier.height(BAR_HEIGHT).background(D.purpleTrack, RoundedCornerShape(8.dp)).testTag("recap_bar"),
    ) { measurables, c ->
        val w = c.maxWidth
        val h = BAR_HEIGHT.roundToPx()
        val fillW = maxOf(FILL_MIN.roundToPx(), (w * fraction()).toInt()).coerceAtMost(w)
        val fill = measurables[0].measure(Constraints.fixed(fillW, h))
        val dots = measurables[1].measure(Constraints.fixed(w, h))
        val k = KNOB.roundToPx()
        val knob = measurables[2].measure(Constraints.fixed(k, k))
        layout(w, h) {
            fill.place(0, 0)
            dots.place(0, 0)
            knob.place(fillW + KNOB_PAST.roundToPx() - k, -KNOB_ABOVE.roundToPx())
        }
    }
}

/**
 * Two buttons share a 360dp screen at 154dp each; Material's 24dp sides would wrap "Share this
 * look" onto two lines, so the label gets 10dp sides instead (the button is still 56dp tall).
 */
private val BUTTON_PADDING = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

/** "Share this look" or "See my Sprigs", and "Done", side by side (`A5-Recap`). */
@Composable
private fun Footer(showSecond: Boolean, secondLabel: String, onSecond: () -> Unit, onDone: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = if (compact) RecapSprig.COMPACT_FOOTER_DP.dp else 28.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSecond) {
            SecondaryButton(
                secondLabel, onClick = onSecond, modifier = Modifier.weight(1f).fillMaxHeight().testTag("recap_second"),
                contentPadding = BUTTON_PADDING,
            )
        }
        PrimaryButton(
            RecapCopy.DONE, onClick = onDone, modifier = Modifier.weight(1f).fillMaxHeight().testTag("recap_done_button"),
            contentPadding = BUTTON_PADDING,
        )
    }
}
