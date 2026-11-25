package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.domain.WeeklyGoalRules
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.PageHeader
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.StepPage

private val D = DaybreakColors

/** Weekly goals (issue #165), from the Settings "Weekly goals" row. A row opens the goal's setup screen. */
@Composable
fun WeeklyGoalsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit, vm: WeeklyGoalsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    // The goals load after the first frame; wait, rather than offer "Add" at "0 of 3" for a moment.
    if (!s.loaded) {
        Box(Modifier.fillMaxSize().background(D.cream))
        return
    }
    WeeklyGoalsContent(s.goals.map(WeeklyGoalItem::of), onBack, onAdd, onEdit)
}

/** One row of the list: what it shows and which goal it opens. */
data class WeeklyGoalItem(val id: Long, val text: String, val iconKey: String) {
    companion object {
        fun of(g: WeeklyGoalEntity) = WeeklyGoalItem(g.id, WeeklyGoalCopy.listRow(g.title, WeeklyGoalCopy.target(g.targetValue, g.unit), g.daysPerWeek), g.iconKey)
    }
}

/**
 * The list, on the Your habits frame: back arrow, "Weekly goals", the line, then a row per goal
 * ("Exercise · 4 days a week"); only the list scrolls. The fixed footer holds "Add a weekly
 * goal" and "{n} of 3 weekly goals"; at 3 the button is disabled and the footer says why
 * ([WeeklyGoalCopy.LIMIT_REACHED]). With no goals the list is empty.
 */
@Composable
fun WeeklyGoalsContent(goals: List<WeeklyGoalItem>, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    StepPage(
        header = PageHeader.Back(onBack),
        title = WeeklyGoalCopy.LIST_TITLE,
        line = WeeklyGoalCopy.LIST_LINE,
        titleStyle = DaybreakType.headline,
        modifier = Modifier.testTag("goals_page"),
        footer = {
            PrimaryButton(
                WeeklyGoalCopy.ADD, onClick = onAdd, enabled = WeeklyGoalRules.canAdd(goals.size),
                icon = DaybreakIcons.Plus, modifier = Modifier.testTag("goals_add"),
            )
            Text(
                WeeklyGoalCopy.count(goals.size),
                Modifier.fillMaxWidth().testTag("goals_count"),
                style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted, textAlign = TextAlign.Center,
            )
        },
    ) {
        items(goals, key = { it.id }) { WeeklyGoalRow(it, onClick = { onEdit(it.id) }) }
    }
}

/** A row, as on Your habits: 64dp, white, radius 20, a 40dp purple-tint icon tile, the text (16/700) and a chevron. */
@Composable
private fun WeeklyGoalRow(g: WeeklyGoalItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(D.white).border(1.5.dp, D.lineSoft, shape)
            .clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("goal_row_${g.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.purpleTint), contentAlignment = Alignment.Center) {
            Icon(HabitIcons.daybreak(g.iconKey), null, Modifier.size(20.dp), tint = D.purple)
        }
        Text(g.text, Modifier.weight(1f).testTag("goal_text_${g.id}"), style = DaybreakType.rowTitle, color = D.ink)
        Icon(DaybreakIcons.Chevron, null, Modifier.size(18.dp), tint = D.faint)
    }
}
