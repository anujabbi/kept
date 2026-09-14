package com.example.kept.core.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kept.core.data.CompletionSource
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ReminderActions
import com.example.kept.core.domain.ReminderCopy
import com.example.kept.core.domain.ReminderKind
import com.example.kept.core.domain.ReminderLadder
import com.example.kept.core.notify.KeptNotifications
import com.posthog.PostHog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts one rung of the reminder ladder (issue #9). Shared by [AlarmReceiver], which
 * fires a rung, and [NotificationActionReceiver], which redraws it after a habit was ticked from
 * its buttons.
 */
@Singleton
class ReminderPoster @Inject constructor(
    private val habits: HabitRepository,
    private val prefs: KeptPreferences,
    private val notifications: KeptNotifications,
    private val time: TimeSource,
) {
    /**
     * Returns true when a notification was posted. Nothing is posted when reminders are off, there
     * are no habits, every habit is already done, or the give-up time has passed — the point of a
     * reminder is the time still left.
     *
     * [alertOnce] suppresses the buzz for a redraw, so ticking one habit off four does not sound
     * like a new reminder.
     */
    suspend fun post(kind: ReminderKind, alertOnce: Boolean = false): Boolean {
        val s = prefs.currentSettings()
        if (!s.onboardingDone || !s.remindersEnabled) return false
        val today = habits.today()
        if (today.total == 0 || today.allDone) {
            notifications.clearReminder()
            return false
        }
        val now = time.now()
        val deadline = ReminderLadder.deadlineAhead(now, time.today(), s.lockFromMinute, s.dueMinute, time.zone())
        val minutesLeft = Duration.between(now, deadline).toMinutes().toInt()
        if (minutesLeft <= 0) return false

        val remaining = today.habits.filter { !it.isDone }
        val streak = prefs.currentSprig().streakDays
        val plan = ReminderActions.plan(remaining.map { it.id })
        val single = plan.markDone.size == 1 && !plan.openApp
        val actions = plan.markDone.mapNotNull { id ->
            remaining.firstOrNull { it.id == id }?.let {
                KeptNotifications.MarkDoneAction(id, ReminderCopy.actionLabel(it.habit.title, single))
            }
        }
        return notifications.reminder(
            kind = kind,
            title = ReminderCopy.title(kind, remaining.size, minutesLeft, streak),
            body = ReminderCopy.body(kind, remaining.size, s.dueMinute),
            actions = actions,
            openApp = plan.openApp,
            alertOnce = alertOnce,
        )
    }
}

/**
 * Handles the "Mark done" buttons on a reminder (issue #9). A BroadcastReceiver with a Hilt entry
 * point, like [AlarmReceiver], so the tick works with no KEPT process running: the system starts
 * one, Hilt builds the graph, and [HabitActions.complete] is idempotent, so a second tap on a
 * button the shade has not redrawn yet does nothing.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "com.example.kept.action.MARK_DONE"
        const val EXTRA_HABIT_ID = "habit_id"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun actions(): HabitActions
        fun poster(): ReminderPoster
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_DONE) return
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (habitId <= 0L) return
        val kindValue = intent.getStringExtra(KeptNotifications.EXTRA_REMINDER_KIND)
        val kind = ReminderKind.entries.firstOrNull { it.eventValue == kindValue } ?: ReminderKind.BEFORE_DUE
        val deps = runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        }.getOrNull() ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PostHog.capture(
                    "reminder_action_tapped",
                    properties = mapOf("kind" to kind.eventValue, "action" to "mark_done"),
                )
                deps.actions().complete(habitId, source = CompletionSource.NOTIFICATION)
                // Either the day is done, and this clears the reminder, or the buttons have to lose
                // the habit that was just ticked.
                deps.poster().post(kind, alertOnce = true)
            } finally {
                pending.finish()
            }
        }
    }
}
