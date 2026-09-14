package com.example.kept.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.kept.BuildConfig
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.CompletionSource
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.ReminderKind
import com.example.kept.core.domain.ReminderLadder
import com.example.kept.core.domain.ReminderPlan
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
            remaining = today.habits.filter { !it.isDone }
                .map { ReminderPlan.Habit(it.id, it.habit.title, it.habit.proofType) },
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
        /** Namespaced by applicationId so it can never collide with another app's broadcast. */
        val ACTION_MARK_DONE: String = BuildConfig.APPLICATION_ID + ".action.MARK_DONE"
        const val EXTRA_HABIT_ID = "habit_id"
        private const val TAG = "NotifActionReceiver"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun actions(): HabitActions
        fun poster(): ReminderPoster
        fun analytics(): Analytics
        fun habits(): HabitRepository
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
                // A notification tap must never crash the app. This runs on a bare scope with no
                // handler, so a failure inside `complete` — a database error, a habit deleted
                // between the draw and the tap — would otherwise be an uncaught exception on a
                // process the user did not even know was running.
                runCatching {
                    // Belt and braces behind [ReminderActions.plan], which never draws a button for
                    // a photo habit: a notification already in the shade when the habit was edited
                    // to PHOTO would otherwise tick it with no proof. Redraw and stop.
                    val outstanding = deps.habits().today().habits.firstOrNull { it.id == habitId }
                    if (outstanding != null && outstanding.habit.proofType != ProofType.MANUAL) {
                        Log.w(TAG, "ignoring mark-done for a habit that needs photo proof")
                        deps.poster().post(kind, alertOnce = true)
                        return@runCatching
                    }
                    deps.analytics().capture(
                        "reminder_action_tapped",
                        mapOf("kind" to kind.eventValue, "action" to "mark_done"),
                    )
                    deps.actions().complete(habitId, source = CompletionSource.NOTIFICATION)
                    // Either the day is done, and the decision clears the reminder, or the buttons
                    // have to lose the habit that was just ticked.
                    deps.poster().post(kind, alertOnce = true)
                }.onFailure { Log.w(TAG, "mark-done action failed for habit $habitId", it) }
            } finally {
                pending.finish()
            }
        }
    }
}
