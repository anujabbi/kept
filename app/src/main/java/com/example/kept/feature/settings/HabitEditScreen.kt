package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.domain.PromiseLimits
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.ui.DaybreakColors

/**
 * Add a habit, or edit habit [habitId], on the promise (#218). A new promise starts every day;
 * picking a day count saves it as a weekly goal instead. An existing habit opens filled in and
 * stays every day, and saving keeps its history. "Remove habit" goes through the lock-window
 * confirm (issue #1); the last habit cannot be removed and says why.
 */
@Composable
fun HabitEditScreen(
    habitId: Long?,
    onClose: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    goals: WeeklyGoalsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val g by goals.state.collectAsStateWithLifecycle()
    // Close once: a second tap on Remove while the page is leaving must not remove or pop again.
    var closed by remember { mutableStateOf(false) }
    val close = { if (!closed) { closed = true; onClose() } }
    // The habits and goals load after the first frame; wait for them rather than open on empty lists.
    val habit = s.habits.firstOrNull { it.id == habitId }
    if (!s.loaded || !g.loaded || (habitId != null && habit == null)) {
        Box(Modifier.fillMaxSize().background(DaybreakColors.cream))
        return
    }
    if (habit == null) {
        PromisePage(
            PromiseRules.fresh(s.habits.map { it.title } + g.goals.map { it.title }, days = null),
            PromiseLimits.new(daily = s.habits.size, weekly = g.goals.size),
            onDone = { d, saved -> if (d.weekly) goals.add(d) else vm.addHabit(d.habit()); saved() },
            onClose = close,
            form = g.form,
        )
        return
    }
    PromisePage(
        PromiseRules.ofHabit(habit.title, habit.proofType, habit.targetValue, habit.unit),
        PromiseLimits.SAVED_HABIT,
        onDone = { d, saved -> vm.saveHabit(habit, d.habit()); saved() },
        onClose = close,
        form = g.form,
        remove = PromiseRemove(HabitsCopy.REMOVE, HabitLimits.canRemove(s.habits.size), HabitsCopy.KEEP_ONE) {
            if (!closed) vm.requestRemoveHabit(habit.id, onRemoved = close)
        },
    )
    LockChangeConfirmDialog(vm)
}
