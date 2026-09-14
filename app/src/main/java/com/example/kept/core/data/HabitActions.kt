package com.example.kept.core.data

import com.example.kept.core.AppForeground
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.notify.KeptNotifications
import com.posthog.PostHog
import javax.inject.Inject
import javax.inject.Singleton

/** Where a tick came from. The string is the `source` property on `habit_completed` (issue #9). */
enum class CompletionSource(val eventValue: String) {
    APP("app"),
    NOTIFICATION("notification"),
}

/** Use-cases that touch several repositories. UI talks to this, not to repositories directly. */
@Singleton
class HabitActions @Inject constructor(
    private val habits: HabitRepository,
    private val sprig: SprigRepository,
    private val buddy: BuddyRepository,
    private val prefs: KeptPreferences,
    private val notifications: KeptNotifications,
    private val foreground: AppForeground,
    private val time: TimeSource,
) {
    /**
     * Ticks a habit off. Idempotent: [HabitRepository.markDone] returns null for a habit that is
     * already done, so a "Mark done" notification action tapped twice completes once, fires one
     * event and grants one level.
     */
    suspend fun complete(
        habitId: Long,
        photoPath: String? = null,
        source: CompletionSource = CompletionSource.APP,
    ): HabitEvent.Completed? {
        val event = habits.markDone(habitId, photoPath) ?: return null
        PostHog.capture(
            "habit_completed",
            properties = mapOf("before_due" to event.beforeDue, "source" to source.eventValue),
        )
        if (event.allDoneNow && event.beforeDue) onDayCompleted(event)
        react(event)
        return event
    }

    suspend fun undo(habitId: Long): HabitEvent.Undone? {
        val event = habits.undo(habitId) ?: return null
        PostHog.capture("habit_completion_undone")
        // An undone day has nothing left to celebrate; drop a celebration that never got shown.
        prefs.updateSettings { it.copy(celebrationPendingDate = null) }
        sprig.onHabitEvent(event)
        return event
    }

    /** Every habit ticked, and in time. Worth a moment (issue #9). */
    private suspend fun onDayCompleted(event: HabitEvent.Completed) {
        PostHog.capture(
            "day_completed",
            properties = mapOf(
                "habit_count" to event.habitCount,
                "minutes_before_due" to event.minutesBeforeDue,
            ),
        )
        prefs.updateSettings { it.copy(celebrationPendingDate = time.todayKey()) }
        // Nothing on screen to celebrate on: say it in the shade instead, and hold the full-screen
        // moment until KEPT is opened.
        if (!foreground.isForeground) runCatching { notifications.dayComplete() }
    }

    private suspend fun react(event: HabitEvent) {
        sprig.onHabitEvent(event)
        if (event is HabitEvent.Completed && event.allDoneNow) {
            buddy.simulateBuddyReaction("done")
        }
    }
}
