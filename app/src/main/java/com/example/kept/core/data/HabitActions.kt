package com.example.kept.core.data

import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GiveUpTime
import com.example.kept.core.domain.MomentRules
import com.example.kept.core.domain.WeeklyHold
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val weeklyGoals: WeeklyGoalRepository,
) {
    /**
     * One tick, session or undo at a time. On a must-do day the last habit and the session can be
     * tapped a moment apart, from Home and from the lock screen; each asks "is the day finished
     * now?" after its own write, and without this both could answer yes and finish it twice.
     */
    private val dayLock = Mutex()

    /**
     * Ticks a habit off. Idempotent: [HabitRepository.markDone] returns null for a habit that is
     * already done, so a "Mark done" notification action tapped twice completes once, fires one
     * event and grants one level.
     *
     * [momentShown] is true when the screen the tick came from plays the "Promise kept" moment
     * itself (the lock screen, issue #130); a day finished that way leaves no celebration pending
     * for KEPT. That screen then calls [claimMoment] before it plays it.
     *
     * On a must-do day the habits are half of the promise (issue #165, story #176): the tick
     * that finishes them does not finish the day, earns no level and reports no `day_completed`
     * while a must-do session is not logged in time. Logging it does ([logWeekly]).
     */
    suspend fun complete(
        habitId: Long,
        photoPath: String? = null,
        source: CompletionSource = CompletionSource.APP,
        momentShown: Boolean = false,
    ): HabitEvent.Completed? = dayLock.withLock {
        val event = habits.markDone(habitId, photoPath) ?: return null
        analytics.capture(
            "habit_completed",
            mapOf(
                "before_due" to event.beforeDue,
                "minutes_after_lock_start" to minutesAfterLockStart(),
                "source" to source.eventValue,
            ),
        )
        // Only the tick that finishes the habits in time has to ask about the weekly goals.
        val habitsFinished = MomentRules.finishedInTime(event.allDoneNow, event.beforeDue)
        val weeklyKept = !habitsFinished || WeeklyHold.allInTime(weeklyGoals.thisWeek().mustDoLoggedAt(), dueMillis())
        if (habitsFinished && weeklyKept) onDayCompleted(event.habitCount, event.minutesBeforeDue, momentShown)
        sprig.onHabitEvent(event, weeklyKept)
        event
    }

    /**
     * Logs today's session of a weekly goal. One session a day counts: a second call, or a goal
     * that is gone, stores nothing and returns false.
     *
     * When today is that goal's must-do day and this was the last thing the day was waiting for
     * (every habit ticked in time, every other must-do session logged, the give-up time not yet
     * passed), the session finishes the day exactly as the last habit does: the level, the
     * "Promise kept" moment left pending for KEPT, `day_completed` and the shade (story #176).
     * On any other day it is progress toward the week and nothing else.
     */
    suspend fun logWeekly(goalId: Long): Boolean = dayLock.withLock {
        if (!weeklyGoals.logToday(goalId)) return false
        val weeks = weeklyGoals.thisWeek()
        val due = dueMillis()
        val mustDo = weeks.firstOrNull { it.id == goalId }?.week?.todayCounts == true
        // This goal's own session is one of those asked about, so a late one finishes nothing.
        if (mustDo && WeeklyHold.allInTime(weeks.mustDoLoggedAt(), due)) {
            val today = habits.today()
            if (today.allDoneOnTime) {
                sprig.onDayKeptByWeeklySession()
                onDayCompleted(today.total, ((due - time.nowMillis()) / 60_000L).coerceAtLeast(0L).toInt(), momentShown = false)
            }
        }
        true
    }

    /**
     * Removes today's session; false when there was none. Undoing a must-do session opens the
     * day again, like unticking a habit: nothing is left to celebrate and the day's level goes
     * back if it gave one. On a day that is not the goal's must-do day nothing else changes.
     */
    suspend fun undoWeekly(goalId: Long): Boolean = dayLock.withLock {
        val mustDo = weeklyGoals.thisWeek().firstOrNull { it.id == goalId }?.week?.todayCounts == true
        if (!weeklyGoals.undoToday(goalId)) return false
        if (mustDo) {
            prefs.updateSettings { it.copy(celebrationPendingDate = null) }
            sprig.onWeeklySessionUndone()
        }
        true
    }

    /** Today's give-up time, from the settings in effect now, as the rollover will judge it. */
    private suspend fun dueMillis(): Long {
        val s = prefs.currentSettings()
        return GiveUpTime.instantFor(time.today(), s.lockFromMinute, s.dueMinute, time.zone()).toEpochMilli()
    }

    suspend fun undo(habitId: Long): HabitEvent.Undone? = dayLock.withLock {
        val event = habits.undo(habitId) ?: return null
        analytics.capture("habit_completion_undone")
        // An undone day has nothing left to celebrate; drop a celebration that never got shown.
        prefs.updateSettings { it.copy(celebrationPendingDate = null) }
        sprig.onHabitEvent(event)
        event
    }

    /**
     * Every habit ticked and every must-do session logged, in time. Worth a moment (issue #9).
     * When the tick came from a screen that plays the moment itself ([momentShown]: the lock
     * screen, issue #130), or the moment already played today, nothing is left pending, so KEPT
     * does not celebrate the day again. `habit_count` stays the number of habits: a weekly
     * session adds no property (story #176).
     */
    private suspend fun onDayCompleted(habitCount: Int, minutesBeforeDue: Int, momentShown: Boolean) {
        analytics.capture(
            "day_completed",
            mapOf(
                "habit_count" to habitCount,
                "minutes_before_due" to minutesBeforeDue,
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
