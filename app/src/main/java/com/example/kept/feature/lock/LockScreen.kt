package com.example.kept.feature.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.domain.HomeCopy
import com.example.kept.core.domain.HomeSprig
import com.example.kept.core.domain.LockCopy
import com.example.kept.core.domain.LockSprig
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakHabitRow
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.HabitRowState
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.LockPalette
import com.example.kept.core.ui.Motion
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyRamp
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.keptTween
import com.example.kept.core.ui.rememberPhotoTick
import com.example.kept.core.ui.sprig.SprigView

/** What the lock screen can ask for. `LockActivity` owns the system side (dialer, launching the app). */
class LockActions(
    val onComplete: (Long) -> Unit = {},
    val onUndo: (Long) -> Unit = {},
    val onPhoto: (Long, String) -> Unit = { _, _ -> },
    val onPhone: () -> Unit = {},
    val onBreak: () -> Unit = {},
    val onOpenApp: () -> Unit = {},
)

private val D = DaybreakColors

/**
 * The lock screen in Daybreak (`A3-Lock`, spec Lock): the same structure as compact Home. A sky
 * on the lock ramp with the headline left and Sprig guarding on the right, a `#1B1842` hill and
 * ground, the day's habits (tickable right here), and a fixed footer with "Phone" and "Break the
 * lock". Only the habit list scrolls. When the last habit is ticked in time the screen turns
 * gold, Sprig cheers, and the footer becomes one "Open {App}" button.
 *
 * While night lockdown holds the phone (`LockUi.night`, issue #164) the same frame shows its night
 * variant: "It's shutdown time." and "Apps open at {end}.", Sprig asleep, "Phone" and "Break the
 * lock", and neither the habit list nor "Open {App}". Finishing habits does not lift it, so the
 * habits stay in KEPT, where they can still be ticked.
 *
 * [appLabel] is the locked app's label from the package manager.
 */
@Composable
fun LockScreen(state: LockUi, appLabel: String, actions: LockActions, modifier: Modifier = Modifier) {
    val app = LockCopy.appName(appLabel)
    val open = state.open
    val night = state.night
    val today = state.lock?.today ?: TodaySummary(emptyList())
    // Night lockdown is not about the day's progress: it keeps the darkest sky of the lock ramp.
    val done = if (night) 0 else today.done
    val total = if (night) 0 else today.total
    val headline = if (night) LockCopy.NIGHT_HEADLINE else LockCopy.headline(app, open)
    val line = if (night) LockCopy.nightLine(state.nightUntilMinute) else LockCopy.line(open)

    val ink by animateColorAsState(if (open) D.ink else D.skyTextLight, keptTween(Motion.SKY_MS), label = "lock-ink")
    val soft by animateColorAsState(if (open) D.inkSoft else LockPalette.soft, keptTween(Motion.SKY_MS), label = "lock-soft")
    val rule by animateColorAsState(if (open) D.line else LockPalette.rule, keptTween(Motion.SKY_MS), label = "lock-rule")

    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    // The headline block's height, measured; a taller block (large font, long app name) moves the hill down.
    var textHeightPx by remember { mutableIntStateOf(0) }
    val textBottom = LockSprig.TEXT_TOP_DP + with(density) { textHeightPx.toDp() }.value.toInt()
    val crest = LockSprig.crestDp(textBottom)
    val skyHeight = inset + crest.dp + Hill.RISE

    BoxWithConstraints(modifier.fillMaxSize().testTag("lock_screen")) {
        // Like compact Home, the column and Sprig widths come from the real screen width.
        val widths = HomeSprig.compactWidths(maxWidth.value.toInt())
        val sprigTop by animateDpAsState(
            inset + LockSprig.topDp(crest, open, widths.sprigDp).dp,
            keptTween(Motion.RISE_MS, Motion.RiseEasing),
            label = "lock-sprig-top",
        )
        SkyScaffold(
            sky = SkyRamp.lockSky(done, total),
            skyHeight = skyHeight,
            ground = if (open) D.cream else Sky.lockGround,
            lightContent = SkyRamp.lockTextIsLight(done, total),
            skyContent = {
                SprigView(
                    state.sprig.form,
                    if (night) LockCopy.NIGHT_POSE else LockCopy.pose(open),
                    Modifier.align(Alignment.TopEnd).padding(end = HomeSprig.COMPACT_SPRIG_END_DP.dp)
                        .offset(y = sprigTop).size(widths.sprigDp.dp).testTag("lock_sprig"),
                    wilted = state.sprig.wilted,
                )
                // Where the hill's crest is, for tests; takes no space and draws nothing.
                Box(Modifier.offset(y = inset + crest.dp).size(0.dp).testTag("lock_crest"))
                Headline(
                    headline, line, ink, soft, widths.textDp.dp,
                    Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                        .padding(top = inset + LockSprig.TEXT_TOP_DP.dp, start = HomeSprig.TEXT_SIDE_DP.dp)
                        .onSizeChanged { textHeightPx = it.height },
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { if (!night) HabitsPage(today, open, ink, soft, actions) }
                Footer(app, open, rule, actions)
            }
        }
    }
}

@Composable
private fun Headline(headline: String, line: String, ink: Color, soft: Color, width: Dp, modifier: Modifier = Modifier) {
    val base = DaybreakType.headline.copy(fontSize = 38.sp, lineHeight = 38.sp)
    Column(modifier.width(width)) {
        Text(headline, Modifier.testTag("lock_headline"), style = fitLongestWord(headline, base, width), color = ink)
        Spacer(Modifier.height(8.dp))
        Text(
            line,
            Modifier.testTag("lock_line"),
            style = DaybreakType.body.copy(lineHeight = (15 * 1.35).sp),
            color = soft,
        )
    }
}

/**
 * "Today" and the habits. The list keeps at least [LIST_MIN] of height: on a small screen at a
 * large font, where the sky and the footer would squeeze it to nothing, "Today" and the list
 * scroll together instead, and the list keeps scrolling inside. The footer never scrolls.
 */
@Composable
private fun HabitsPage(today: TodaySummary, open: Boolean, ink: Color, soft: Color, actions: LockActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = constraints.maxHeight
        Layout(
            content = {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Today", Modifier.alignByBaseline(), style = DaybreakType.section, color = ink)
                    if (today.total > 0) {
                        Text(
                            LockCopy.count(today.done, today.total),
                            Modifier.alignByBaseline().testTag("lock_count"),
                            style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = soft, maxLines = 1,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp).testTag("lock_habit_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    today.habits.forEach { LockHabitRow(it, open, actions) }
                }
            },
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) { measurables, c ->
            val loose = c.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val top = measurables[0].measure(loose)
            val listH = maxOf(viewport - top.height, LIST_MIN.roundToPx())
            val list = measurables[1].measure(c.copy(minHeight = listH, maxHeight = listH))
            layout(c.maxWidth, top.height + listH) {
                top.place(0, 0)
                list.place(0, top.height)
            }
        }
    }
}

private val FOOTER_GAP = 12.dp
private val BUTTON_PAD = 12.dp

/** The least height the habit list keeps: about two rows. */
private val LIST_MIN = 128.dp

/**
 * A habit on the lock screen: the dark row while locked, the cream page's row once open. A tick
 * goes through the same actions as Home's; a photo habit opens the camera from the lock screen
 * and saves the proof the same way ([rememberPhotoTick]).
 */
@Composable
private fun LockHabitRow(h: HabitToday, open: Boolean, actions: LockActions) {
    val snap = rememberPhotoTick { path -> actions.onPhoto(h.id, path) }
    val photo = h.habit.proofType == ProofType.PHOTO
    DaybreakHabitRow(
        title = h.habit.title,
        detail = HomeCopy.habitDetail(h.habit.targetValue, h.habit.unit),
        state = when {
            !h.isDone -> HabitRowState.ToDo
            h.late -> HabitRowState.DoneLate
            else -> HabitRowState.Done
        },
        onTick = { if (photo) snap() else actions.onComplete(h.id) },
        onUndo = { actions.onUndo(h.id) },
        modifier = Modifier.testTag("lock_habit_${h.id}"),
        photo = photo,
        night = !open,
    )
}

/**
 * Locked: "Phone" (outlined, opens the dialer in one tap, always there) and "Break the lock".
 * Open: one "Open {App}" button.
 */
@Composable
private fun Footer(app: String, open: Boolean, rule: Color, actions: LockActions) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(rule))
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)) {
            if (open) {
                PrimaryButton(
                    LockCopy.openButton(app),
                    onClick = actions.onOpenApp,
                    modifier = Modifier.testTag("lock_open_app"),
                    textStyle = DaybreakType.button.copy(fontSize = 18.sp, lineHeight = 23.sp),
                )
            } else {
                BoxWithConstraints {
                    // Each button gets half the row. At large font scales "Phone" steps its size down
                    // until it fits beside its icon rather than breaking inside the word.
                    val half = (maxWidth - FOOTER_GAP) / 2
                    val phoneStyle = fitLongestWord(LockCopy.PHONE, DaybreakType.rowTitle, half - BUTTON_PAD * 2 - 4.dp - 26.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(FOOTER_GAP), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = actions.onPhone,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("emergency_call"),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, D.skyTextLight),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = D.skyTextLight),
                            contentPadding = PaddingValues(horizontal = BUTTON_PAD, vertical = 8.dp),
                        ) {
                            Icon(DaybreakIcons.Phone, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(LockCopy.PHONE, style = phoneStyle, maxLines = 1)
                        }
                        TextButton(
                            onClick = actions.onBreak,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("break_lock"),
                            colors = ButtonDefaults.textButtonColors(contentColor = LockPalette.soft),
                            contentPadding = PaddingValues(horizontal = BUTTON_PAD, vertical = 8.dp),
                        ) {
                            Text(LockCopy.BREAK, style = DaybreakType.rowTitle, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
