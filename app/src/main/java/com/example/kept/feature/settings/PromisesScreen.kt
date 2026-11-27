package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.domain.PromiseItem
import com.example.kept.core.domain.PromiseList
import com.example.kept.core.domain.PromiseListCopy
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.CadenceChip
import com.example.kept.core.ui.DashedAddRow
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.PageHeader
import com.example.kept.core.ui.PromiseCard
import com.example.kept.core.ui.StepPage

private val D = DaybreakColors

/** A daily habit as a card of Your promises (#236). */
fun HabitEntity.promise(): PromiseItem =
    PromiseItem(id, null, title, iconKey, PromiseListCopy.habitAmount(proofType == ProofType.PHOTO, targetValue, unit), sortOrder)

/** A weekly goal as a card of Your promises (#236). */
fun WeeklyGoalEntity.promise(): PromiseItem =
    PromiseItem(id, daysPerWeek, title, iconKey, PromiseListCopy.goalAmount(title, targetValue, unit), sortOrder)

/**
 * Settings → Your promises (#236): every daily habit and weekly goal in one list, in the person's
 * own order. A card opens its promise to change it ([onEditHabit] or [onEditGoal]); "Add a habit"
 * opens a new one ([onAdd]).
 */
@Composable
fun PromisesScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onEditGoal: (Long) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    goals: WeeklyGoalsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val g by goals.state.collectAsStateWithLifecycle()
    // Both stores load after the first frame; wait rather than show half a list.
    if (!s.loaded || !g.loaded) {
        Box(Modifier.fillMaxSize().background(D.cream))
        return
    }
    PromisesContent(
        PromiseList.ordered(s.habits.map { it.promise() } + g.goals.map { it.promise() }),
        onBack, onAdd,
        onOpen = { if (it.weekly) onEditGoal(it.id) else onEditHabit(it.id) },
    )
}

/**
 * The page, built like onboarding's habit step (`A10`): back arrow, "Your promises" and its line,
 * then a card per promise with its icon, name, amount, how-often chip and a chevron; the dashed
 * "Add a habit" row (or, with both sides full, why not); and the count, "4 daily habits · 2 weekly
 * goals". All of it scrolls; from font 1.5 the title scrolls with it.
 */
@Composable
fun PromisesContent(items: List<PromiseItem>, onBack: () -> Unit, onAdd: () -> Unit, onOpen: (PromiseItem) -> Unit) {
    val daily = items.count { !it.weekly }
    val weekly = items.size - daily
    StepPage(
        header = PageHeader.Back(onBack),
        title = PromiseListCopy.TITLE,
        line = PromiseListCopy.LINE,
        modifier = Modifier.testTag("promises_page"),
    ) {
        items(items, key = { it.key }) { PromiseRow(it) { onOpen(it) } }
        item(key = "add") {
            if (PromiseList.canAdd(daily, weekly)) {
                DashedAddRow(PromiseListCopy.ADD, "promises_add", onAdd)
            } else {
                Text(
                    PromiseListCopy.FULL,
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp).testTag("promises_full"),
                    style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted,
                )
            }
        }
        item(key = "count") {
            Text(
                PromiseListCopy.count(daily, weekly),
                Modifier.fillMaxWidth().padding(top = 4.dp).testTag("promises_count"),
                style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One promise ([PromiseCard]): the whole card, its chip included, is one button that TalkBack
 * reads as the name, the amount and how often.
 */
@Composable
private fun PromiseRow(p: PromiseItem, onClick: () -> Unit) {
    PromiseCard(
        icon = HabitIcons.daybreak(p.iconKey),
        title = p.title,
        detail = p.amount,
        action = Modifier.clickable(role = Role.Button, onClickLabel = PromiseListCopy.CHANGE, onClick = onClick),
        tag = "promise_${p.key}",
        chip = { CadenceChip(p.chip, "promise_${p.key}_chip") },
    ) {
        Icon(DaybreakIcons.Chevron, null, Modifier.size(18.dp), tint = D.faint)
    }
}
