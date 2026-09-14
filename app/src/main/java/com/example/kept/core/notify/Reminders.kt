package com.example.kept.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kept.core.data.CompletionSource
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ReminderKind
import com.example.kept.core.domain.ReminderLadder
import com.example.kept.core.domain.ReminderPlan
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
 * Draws one rung of the reminder ladder (issue #9). The Android adapter around
 * [ReminderPlan.decide]: it gathers the inputs, and the pure decision says whether to post, what to
 * say, and whether a reminder already on screen has to come down.
 *
 * It lives in `core/notify` alongside the receiver that its buttons target, so `core/work` depends
 * on `core/notify` and never the other way round.
 */
@Singleton
class ReminderPoster @Inject constructor(
    private val habits: HabitRepository,
    private val prefs: KeptPreferences,
    private val notifications: KeptNotifications,
    private val time: TimeSource,
) {
    /**
     * Returns true when a notification was posted.
     *
     * [alertOnce] suppresses the buzz for a redraw, so ticking one habit off four does not sound
     * like a new reminder.
     */
    suspend fun post(kind: ReminderKind, alertOnce: Boolean = false): Boolean {
        val s = prefs.currentSettings()
        val today = habits.today()
        val now = time.now()
        val deadline = ReminderLadder.deadlineAhead(now, time.today(), s.lockFromMinute, s.dueMinute, time.zone())
        val decision = ReminderPlan.decide(
            kind = kind,
            onboardingDone = s.onboardingDone,
            remindersEnabled = s.remindersEnabled,
            remaining = today.habits.filter { !it.isDone }.map { ReminderPlan.Habit(it.id, it.habit.title) },
            totalHabits = today.total,
            minutesLeft = Duration.between(now, deadline).toMinutes().toInt(),
            streakDays = prefs.currentSprig().streakDays,
            dueMinute = s.dueMinute,
        )
        return when (decision) {
            is ReminderPlan.Decision.Skip -> {
                if (decision.clearExisting) notifications.clearReminder()
                false
            }
            is ReminderPlan.Decision.Post -> notifications.reminder(
                kind = kind,
                title = decision.title,
                body = decision.body,
                actions = decision.actions.map { KeptNotifications.MarkDoneAction(it.habitId, it.label) },
                openApp = decision.openApp,
                alertOnce = alertOnce,
            )
        }
    }

    /**
     * Takes down a reminder that a settings change has just made untrue (issue #9). Turning
     * reminders off cancels the alarms, but a notification already in the shade keeps its live
     * "Mark done" buttons until something removes it.
     */
    fun clear() = runCatching { notifications.clearReminder() }
}

/**
 * Handles the "Mark done" buttons on a reminder (issue #9). A BroadcastReceiver with a Hilt entry
 * point, like `AlarmReceiver`, so the tick works with no KEPT process running: the system starts
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
                // Either the day is done, and the decision clears the reminder, or the buttons have
                // to lose the habit that was just ticked.
                deps.poster().post(kind, alertOnce = true)
            } finally {
                pending.finish()
            }
        }
    }
}
