package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.PageHeader
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.StepPage

private val D = DaybreakColors

/** Your habits (`A8-Habits`), from the Settings "Habits" row. A row opens Edit habit. */
@Composable
fun HabitsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    HabitsContent(s.habits.map(HabitItem::of), onBack, onAdd, onEdit)
}

/** One row of Your habits: what it shows and which habit it opens. */
data class HabitItem(val id: Long, val title: String, val detail: String, val iconKey: String) {
    companion object {
        fun of(h: HabitEntity) = HabitItem(h.id, h.title, HabitsCopy.detail(h.proofType, h.targetValue, h.unit), h.iconKey)
    }
}

/**
 * Your habits: back arrow, "Your habits" (40/800), the explanation, then the rows; only the
 * list scrolls. The fixed footer holds "Add a habit" and "{n} of 10 habits"; at 10 the button
 * is disabled and the footer says [HabitLimits.LIMIT_REACHED].
 */
@Composable
fun HabitsContent(habits: List<HabitItem>, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val canAdd = HabitLimits.canAdd(habits.size)
    StepPage(
        header = PageHeader.Back(onBack),
        title = HabitsCopy.TITLE,
        line = HabitsCopy.LINE,
        titleStyle = DaybreakType.headline,
        modifier = Modifier.testTag("habits_page"),
        footer = {
            PrimaryButton(HabitsCopy.ADD, onClick = onAdd, enabled = canAdd, icon = DaybreakIcons.Plus, modifier = Modifier.testTag("habits_add"))
            Text(
                HabitsCopy.count(habits.size),
                Modifier.fillMaxWidth().testTag("habits_count"),
                style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted, textAlign = TextAlign.Center,
            )
        },
    ) {
        items(habits, key = { it.id }) { HabitListRow(it, onClick = { onEdit(it.id) }) }
    }
}

/**
 * A row (`A8-Habits`): 64dp, white, radius 20, a 1.5dp `lineSoft` border; a 40dp purple-tint
 * icon tile, the title (16/700) over its detail (13/500 muted), and a chevron.
 */
@Composable
private fun HabitListRow(h: HabitItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(D.white).border(1.5.dp, D.lineSoft, shape)
            .clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("habit_row_${h.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.purpleTint), contentAlignment = Alignment.Center) {
            Icon(HabitIcons.daybreak(h.iconKey), null, Modifier.size(20.dp), tint = D.purple)
        }
        Column(Modifier.weight(1f)) {
            Text(h.title, Modifier.testTag("habit_title_${h.id}"), style = DaybreakType.rowTitle, color = D.ink)
            Text(h.detail, Modifier.testTag("habit_detail_${h.id}"), style = DaybreakType.caption, color = D.muted)
        }
        Icon(DaybreakIcons.Chevron, null, Modifier.size(18.dp), tint = D.faint)
    }
}
