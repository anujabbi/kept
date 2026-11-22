package com.example.kept.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.domain.WeeklyCardDay
import com.example.kept.core.domain.WeeklyGoalRules.DayState
import com.example.kept.core.domain.WeeklyLockCopy
import com.example.kept.core.domain.WeeklyLockScreen
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakHabitRow
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.HabitRowState
import com.example.kept.core.ui.LockPalette
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.dashedBorder

// The weekly variant of the lock screen (issue #165, Lock screen; weekly goals mockup 3), shown
// when a weekly goal's must-do session is the only thing holding the lock. What it says is worked
// out by `WeeklyLockScreen` in core/domain; this file only draws it, inside `LockScreen`'s frame.

private val D = DaybreakColors

/** The "Weekly goal" chip over the headline: a lock and the words on a translucent pill. */
@Composable
internal fun WeeklyChip(modifier: Modifier = Modifier) {
    val icon = with(LocalDensity.current) { 13.sp.toDp() }
    Row(
        modifier.heightIn(min = 30.dp).clip(CircleShape).background(LockPalette.chip).padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("lock_weekly_chip"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DaybreakIcons.Lock, null, Modifier.size(icon), tint = LockPalette.soft)
        Text(WeeklyLockCopy.CHIP, style = DaybreakType.caption, fontWeight = FontWeight.Bold, color = LockPalette.soft, maxLines = 1)
    }
}

/**
 * Below the hill: "This week" with "{activity} · {done} of {target}", the Su–Sa strip and its
 * legend, today's row and the hint, then the footer rule and the gold "Mark today's {activity}
 * done", which sits right over "Phone" and "Break the lock" (those two never scroll).
 *
 * The week scrolls inside the room the sky and the buttons leave, so the gold button, the way
 * out, stays in view. It keeps at least [PAGE_MIN]; only when even that and the button do not
 * fit (a small screen, a large font and a long activity) do the two scroll together. Tapping
 * today's row marks the session done, as the button does.
 */
@Composable
internal fun WeeklyPage(w: WeeklyLockScreen, rule: Color, onDone: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = constraints.maxHeight
        Layout(
            content = {
                Week(w, onDone)
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(rule))
                    WeeklyDoneButton(w.button, onDone, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
                }
            },
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) { measurables, c ->
            val button = measurables[1].measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val weekH = maxOf(viewport - button.height, PAGE_MIN.roundToPx())
            val week = measurables[0].measure(c.copy(minHeight = weekH, maxHeight = weekH))
            layout(c.maxWidth, weekH + button.height) {
                week.place(0, 0)
                button.place(0, weekH)
            }
        }
    }
}

/** The least height the week keeps: its "This week" header. */
private val PAGE_MIN = 56.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Week(w: WeeklyLockScreen, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 14.dp).testTag("lock_weekly_page")) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(WeeklyLockCopy.SECTION, Modifier.semantics { heading() }, style = DaybreakType.section, color = D.skyTextLight)
            Text(
                w.summary,
                Modifier.align(Alignment.Bottom).testTag("lock_weekly_summary"),
                style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = LockPalette.soft, textAlign = TextAlign.End,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("lock_weekly_strip"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            w.days.forEach { StripDay(it, Modifier.weight(1f)) }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp).clearAndSetSemantics { }.testTag("lock_weekly_legend"),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Legend(WeeklyLockCopy.LEGEND_DONE, Modifier.clip(CircleShape).background(D.streakGold))
            Legend(WeeklyLockCopy.LEGEND_SKIPPED, Modifier.clip(CircleShape).background(LockPalette.rule).border(1.5.dp, LockPalette.rowLine, CircleShape))
            Legend(WeeklyLockCopy.LEGEND_TODAY, Modifier.dashedBorder(1.5.dp, Sky.gold, 5.dp, dash = 2.dp, gap = 2.dp))
            Legend(WeeklyLockCopy.LEGEND_COMING_UP, Modifier.border(1.5.dp, LockPalette.rowLine, CircleShape))
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            DaybreakHabitRow(
                title = w.activity,
                detail = WeeklyLockCopy.TODAY,
                state = HabitRowState.ToDo,
                onTick = onDone,
                onUndo = {},
                modifier = Modifier.testTag("lock_weekly_today"),
                night = true,
            )
            if (w.hint != null) {
                Text(
                    w.hint,
                    Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp).testTag("lock_weekly_hint"),
                    style = DaybreakType.caption, color = LockPalette.ring,
                )
            }
        }
    }
}

/** One day of the strip on the night ground: a 34dp circle over "Su" to "Sa"; TalkBack hears the day and its state. */
@Composable
private fun StripDay(day: WeeklyCardDay, modifier: Modifier = Modifier) {
    Column(
        modifier.clearAndSetSemantics { contentDescription = day.spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val circle = Modifier.size(34.dp)
        val drawn = when (day.state) {
            DayState.DONE -> circle.clip(CircleShape).background(D.streakGold)
            DayState.SKIPPED -> circle.clip(CircleShape).background(LockPalette.rule).border(2.dp, LockPalette.rowLine, CircleShape)
            DayState.TODAY -> circle.dashedBorder(2.dp, Sky.gold, 17.dp, dash = 4.dp, gap = 3.dp)
            DayState.COMING_UP -> circle.border(2.dp, LockPalette.rowLine, CircleShape)
            // Neither a ring nor a fill: the goal did not count this day.
            DayState.NOT_COUNTED -> circle
        }
        Box(drawn.testTag("lock_weekly_day_${day.state.name.lowercase()}"), contentAlignment = Alignment.Center) {
            when (day.state) {
                DayState.DONE -> Icon(DaybreakIcons.Check, null, Modifier.size(16.dp), tint = D.ink)
                DayState.SKIPPED -> Box(Modifier.width(10.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(LockPalette.dash))
                DayState.NOT_COUNTED -> Box(Modifier.size(6.dp).clip(CircleShape).background(LockPalette.rowLine))
                DayState.TODAY, DayState.COMING_UP -> Unit
            }
        }
        Text(
            day.label,
            style = DaybreakType.smallLabel,
            fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (day.isToday) Sky.gold else LockPalette.soft,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** One entry of the strip's legend: a 10dp swatch and its word. */
@Composable
private fun Legend(text: String, swatch: Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).then(swatch))
        Text(text, style = DaybreakType.smallLabel, fontWeight = FontWeight.SemiBold, color = LockPalette.ring, maxLines = 1)
    }
}

/** "Mark today's {activity} done": the one gold button on the night ground, with a tick. */
@Composable
internal fun WeeklyDoneButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PrimaryButton(
        text,
        onClick = onClick,
        modifier = modifier.testTag("lock_weekly_done"),
        containerColor = Sky.gold,
        contentColor = D.ink,
        icon = DaybreakIcons.Check,
    )
}

/** The headline's clauses kept whole: "4 days left," and "4 to go." each on its own line when they fit. */
internal fun wholeClauses(headline: String): String =
    headline.split(", ").joinToString(", ") { it.replace(' ', WeeklyLockCopy.NBSP) }
