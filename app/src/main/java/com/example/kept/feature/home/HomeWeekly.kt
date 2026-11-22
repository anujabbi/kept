package com.example.kept.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.data.WeeklyGoalWeek
import com.example.kept.core.domain.WeeklyCard
import com.example.kept.core.domain.WeeklyCardCopy
import com.example.kept.core.domain.WeeklyCardDay
import com.example.kept.core.domain.WeeklyCardStatus
import com.example.kept.core.domain.WeeklyGoalRules.DayState
import com.example.kept.core.ui.AddRow
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakHabitRow
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.HabitRowState
import com.example.kept.core.ui.dashedBorder

// Home's weekly goals (issue #165, Home; story #174), from weekly goals mockup 4: the "This week"
// header, a card per goal, and the "Add a weekly goal" row when there is none. What a card says
// is worked out by `WeeklyCard` in core/domain; this file only draws it.

private val D = DaybreakColors

/**
 * "This week · Sun to Sat", then a card per goal, or the add row when there are no goals.
 * The header is inset by 4dp like "Today" in the roomy layout.
 */
@Composable
internal fun WeeklySection(goals: List<WeeklyGoalWeek>, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("home_weekly"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(WeeklyCardCopy.SECTION, Modifier.weight(1f, fill = false), style = DaybreakType.section, color = D.ink)
            Text(WeeklyCardCopy.SPAN, style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.muted, maxLines = 1)
        }
        if (goals.isEmpty()) {
            AddRow(WeeklyCardCopy.ADD, WeeklyCardCopy.ADD_LINE, actions.onAddWeeklyGoal, Modifier.testTag("home_weekly_add"))
        }
        goals.forEach { g ->
            WeeklyGoalCard(
                id = g.id,
                activity = g.goal.title,
                card = WeeklyCard.of(g.goal.title, g.week),
                onDone = { actions.onWeeklyDone(g.id) },
                onUndo = { actions.onWeeklyUndo(g.id) },
            )
        }
    }
}

/**
 * One goal's week: "{activity} · {done} of {target} this week", a segment per session of the
 * target, the Su–Sa strip, the status line, and today's row, which is a habit row: a tap logs
 * today's session, holding it once done undoes it.
 */
@Composable
internal fun WeeklyGoalCard(id: Long, activity: String, card: WeeklyCard, onDone: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().testTag("home_weekly_card_$id").clip(shape).background(D.white).border(2.dp, D.rowLine, shape)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            card.title,
            Modifier.testTag("home_weekly_title_$id"),
            style = DaybreakType.rowTitle.copy(fontSize = 17.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.ExtraBold,
            color = D.ink,
        )
        if (card.segments > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(card.segments) { i ->
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (i < card.filled) D.streakGold else D.line))
                }
            }
        }
        Row(Modifier.fillMaxWidth().testTag("home_weekly_strip_$id"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            card.days.forEach { StripDay(it, Modifier.weight(1f)) }
        }
        StatusLine(card.status, Modifier.testTag("home_weekly_status_$id"))
        DaybreakHabitRow(
            title = activity,
            detail = WeeklyCardCopy.TODAY_ROW,
            state = if (card.doneToday) HabitRowState.Done else HabitRowState.ToDo,
            onTick = onDone,
            onUndo = onUndo,
            modifier = Modifier.testTag("home_weekly_today_$id"),
        )
    }
}

/** One day of the strip: a 28dp circle over "Su" to "Sa"; TalkBack hears the day and its state. */
@Composable
private fun StripDay(day: WeeklyCardDay, modifier: Modifier = Modifier) {
    Column(
        modifier.clearAndSetSemantics { contentDescription = day.spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val circle = Modifier.size(28.dp)
        val drawn = when (day.state) {
            DayState.DONE -> circle.clip(CircleShape).background(D.streakGold)
            DayState.SKIPPED -> circle.clip(CircleShape).background(D.missedDay)
            DayState.TODAY -> circle.dashedBorder(2.dp, D.purple, 14.dp, dash = 4.dp, gap = 3.dp)
            DayState.COMING_UP -> circle.border(2.dp, D.weekRing, CircleShape)
            // Neither a ring nor a fill: the goal did not count this day.
            DayState.NOT_COUNTED -> circle
        }
        Box(drawn.testTag("home_weekly_day_${day.state.name.lowercase()}"), contentAlignment = Alignment.Center) {
            when (day.state) {
                DayState.DONE -> Icon(DaybreakIcons.Check, null, Modifier.size(15.dp), tint = D.ink)
                DayState.SKIPPED -> Box(Modifier.width(10.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(D.faint))
                DayState.NOT_COUNTED -> Box(Modifier.size(6.dp).clip(CircleShape).background(D.ring))
                DayState.TODAY, DayState.COMING_UP -> Unit
            }
        }
        Text(
            day.label,
            style = DaybreakType.smallLabel,
            fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (day.isToday) D.purple else D.muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun StatusLine(status: WeeklyCardStatus, modifier: Modifier = Modifier) {
    val (icon, tint, ink) = when (status) {
        WeeklyCardStatus.ON_TRACK -> Triple(DaybreakIcons.Check, D.green, D.green)
        WeeklyCardStatus.TODAY_COUNTS -> Triple(DaybreakIcons.Clock, D.amber, D.amberLabel)
        WeeklyCardStatus.DONE_TODAY -> Triple(DaybreakIcons.Check, D.muted, D.muted)
    }
    // The icon grows with the text, so it still sits on the first line at a large font.
    val (iconSize, iconTop) = with(LocalDensity.current) { 14.sp.toDp() to 2.sp.toDp() }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.padding(top = iconTop).size(iconSize), tint = tint)
        Text(status.line, style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = ink)
    }
}
