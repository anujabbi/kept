package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.domain.PromiseLimits
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.domain.WeeklyGoalRules
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakType

private val D = DaybreakColors

/**
 * Add a weekly goal, or edit goal [goalId], on the promise (#218). A new promise starts at
 * [com.example.kept.core.domain.WeeklyGoalRules.DEFAULT_DAYS] a week; picking "Every day" saves
 * it as a daily habit instead. An existing goal opens filled in and saving keeps this week's
 * sessions; picking "Every day" makes it a daily habit (#236), done today when today's session
 * is logged. That never opens the lock, so it never asks.
 *
 * While the goal holds the lock (today is its must-do day and the daily lock is in force),
 * lowering its days a week or deleting it goes through the lock-change confirm instead
 * ([SettingsViewModel.requestWeeklyGoalChange], issue #165): Cancel keeps the goal as it is and
 * the page open, with no cheer. Otherwise deleting asks "Delete this weekly goal?" first.
 *
 * A goal removed elsewhere while this page is open closes the page: nothing is left to edit, and
 * a save would change nothing. A goal this page made a daily habit stays on the page, as last
 * seen, while Sprig cheers.
 */
@Composable
fun WeeklyGoalEditScreen(
    goalId: Long?,
    onClose: () -> Unit,
    vm: WeeklyGoalsViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val habits by settings.state.collectAsStateWithLifecycle()
    // Close once: a second tap while the page is leaving must not save, delete or pop again.
    var closed by remember { mutableStateOf(false) }
    val close = { if (!closed) { closed = true; onClose() } }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var switched by remember { mutableStateOf(false) }
    val current = s.goals.firstOrNull { it.id == goalId }
    // Seen once, then gone: removed from somewhere else (or by this page, which is closing anyway).
    val seen = remember { arrayOfNulls<WeeklyGoalEntity>(1) }
    if (current != null) seen[0] = current
    val goal = current ?: seen[0]?.takeIf { switched }
    val gone = !switched && WeeklyGoalEditGone.of(s.loaded, goalId, current != null, seen[0] != null)
    LaunchedEffect(gone) { if (gone) close() }
    when {
        // The goals and habits load after the first frame; wait rather than open on empty lists.
        !s.loaded || !habits.loaded || (goalId != null && goal == null) -> Box(Modifier.fillMaxSize().background(D.cream))
        goal == null -> PromisePage(
            PromiseRules.fresh(habits.habits.map { it.title } + s.goals.map { it.title }, days = WeeklyGoalRules.DEFAULT_DAYS),
            PromiseLimits.new(daily = habits.habits.size, weekly = s.goals.size),
            onDone = { d, saved -> if (d.weekly) vm.add(d) else settings.addHabit(d.habit()); saved() },
            onClose = close,
            form = s.form,
        )
        else -> PromisePage(
            PromiseRules.ofGoal(goal.title, goal.daysPerWeek, goal.targetValue, goal.unit),
            PromiseLimits.savedGoal(daily = habits.habits.size),
            onDone = { d, saved ->
                if (!d.weekly) {
                    switched = true
                    settings.toDaily(goal.id, d)
                    saved()
                } else {
                    settings.requestWeeklyGoalChange(goal.id, goal.daysPerWeek, d.days) { if (!closed) { vm.save(goal.id, d); saved() } }
                }
            },
            onClose = close,
            form = s.form,
            remove = PromiseRemove(WeeklyGoalCopy.DELETE, allowed = true) {
                if (!closed) {
                    // A delete held for the lock-change confirm is asked there, once; otherwise ask here.
                    if (settings.weeklyGoalConfirm(goal.id, goal.daysPerWeek, null) != null) {
                        settings.requestWeeklyGoalChange(goal.id, goal.daysPerWeek, null) { if (!closed) { vm.remove(goal.id); close() } }
                    } else {
                        confirmDelete = true
                    }
                }
            },
        )
    }
    if (confirmDelete && goal != null) {
        DeleteGoalConfirm(
            WeeklyGoalCopy.deleteBody(goal.title, goal.daysPerWeek),
            onConfirm = { confirmDelete = false; if (!closed) { vm.remove(goal.id); close() } },
            onCancel = { confirmDelete = false },
        )
    }
    LockChangeConfirmDialog(settings)
}

/**
 * Whether the edit page's goal has gone away under it: the goals are loaded, the page is for an
 * existing goal, that goal was there and no longer is. Before it has been seen the page is still
 * waiting for its first read, and an unknown id simply stays blank, as before.
 */
object WeeklyGoalEditGone {
    fun of(loaded: Boolean, goalId: Long?, present: Boolean, seenBefore: Boolean): Boolean =
        loaded && goalId != null && !present && seenBefore
}

/** "Delete this weekly goal?": the settings confirm's cream card, a red "Delete goal" and "Cancel". */
@Composable
internal fun DeleteGoalConfirm(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(WeeklyGoalCopy.DELETE_TITLE, style = DaybreakType.section, color = D.ink) },
        text = { Text(message, Modifier.testTag("goal_delete_message"), style = DaybreakType.body, color = D.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.heightIn(min = 48.dp).testTag("goal_delete_confirm")) {
                Text(WeeklyGoalCopy.DELETE, style = DaybreakType.button, fontWeight = FontWeight.Bold, color = D.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, Modifier.heightIn(min = 48.dp).testTag("goal_delete_cancel")) { Text(WeeklyGoalCopy.CANCEL, style = DaybreakType.button, color = D.ink) }
        },
        containerColor = D.cream,
    )
}
