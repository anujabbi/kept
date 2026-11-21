package com.example.kept.core.data

import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.MomentRules
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
    private val prefs: KeptPreferences,
    private val time: TimeSource,
    private val analytics: Analytics,
    /** What the shade does about a finished day. An interface so this class stays Android-free. */
    private val dayCompletion: DayCompletionReactor,
) {
    /**
     * Ticks a habit off. Idempotent: [HabitRepository.markDone] returns null for a habit that is
     * already done, so a "Mark done" notification action tapped twice completes once, fires one
     * event and grants one level.
     *
     * [momentShown] is true when the screen the tick came from plays the "Promise kept" moment
     * itself (the lock screen, issue #130); a day finished that way leaves no celebration pending
     * for KEPT. That screen then calls [claimMoment] before it plays it.
     */
    suspend fun complete(
        habitId: Long,
        photoPath: String? = null,
        source: CompletionSource = CompletionSource.APP,
        momentShown: Boolean = false,
    ): HabitEvent.Completed? {
        val event = habits.markDone(habitId, photoPath) ?: return null
        analytics.capture(
            "habit_completed",
            mapOf(
                "before_due" to event.beforeDue,
                "minutes_after_lock_start" to minutesAfterLockStart(),
                "source" to source.eventValue,
            ),
        )
        if (MomentRules.finishedInTime(event.allDoneNow, event.beforeDue)) onDayCompleted(event, momentShown)
        sprig.onHabitEvent(event)
        return event
    }

    suspend fun undo(habitId: Long): HabitEvent.Undone? {
        val event = habits.undo(habitId) ?: return null
        analytics.capture("habit_completion_undone")
        // An undone day has nothing left to celebrate; drop a celebration that never got shown.
        prefs.updateSettings { it.copy(celebrationPendingDate = null) }
        sprig.onHabitEvent(event)
        return event
    }

    /**
     * Every habit ticked, and in time. Worth a moment (issue #9). When the tick came from a screen
     * that plays the moment itself ([momentShown]: the lock screen, issue #130), or the moment
     * already played today, nothing is left pending, so KEPT does not celebrate the day again.
     */
    private suspend fun onDayCompleted(event: HabitEvent.Completed, momentShown: Boolean) {
        analytics.capture(
            "day_completed",
            mapOf(
                "habit_count" to event.habitCount,
                "minutes_before_due" to event.minutesBeforeDue,
            ),
        )
        val today = time.todayKey()
        prefs.updateSettings { it.copy(celebrationPendingDate = MomentRules.pendingAfterFinish(momentShown, it.celebrationShownDate, today)) }
        dayCompletion.onDayCompleted()
    }

    /**
     * Takes today's "Promise kept" moment for the caller: true, and it is recorded as played, if it
     * has not played today in KEPT or on the lock screen; false if it has. Whoever gets true plays
     * it; nobody else does that day (issue #130). Clears anything pending for KEPT too.
     */
    suspend fun claimMoment(): Boolean {
        val today = time.todayKey()
        var claimed = false
        prefs.updateSettings {
            claimed = MomentRules.mayPlay(it.celebrationShownDate, today)
            if (claimed) it.copy(celebrationShownDate = today, celebrationPendingDate = null) else it
        }
        return claimed
    }

    /**
     * How long into the lock window the tick landed. Negative when the habit was done before apps
     * lock at all, which is the answer to "do people get ahead of it or wait to be forced?".
     */
    private suspend fun minutesAfterLockStart(): Int =
        time.minuteOfDay() - prefs.currentSettings().lockFromMinute
}
