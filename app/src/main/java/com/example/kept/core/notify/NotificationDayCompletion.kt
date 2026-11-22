package com.example.kept.core.notify

import com.example.kept.core.data.DayCompletionReactor
import com.example.kept.core.data.ForegroundSignal
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GiveUpTime
import com.example.kept.core.domain.WeeklyHold
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the shade does when the day is finished on time (issue #9).
 *
 * Two things, from every path — a tick in the app, on the lock screen, or from a "Mark done"
 * button:
 * 1. The reminder goes away. Leaving it up meant a stale "1 habit to go" sitting next to "All
 *    habits done", with live buttons for habits that were no longer outstanding.
 * 2. If nothing of KEPT is on screen there is no way to show the full-screen celebration, so
 *    "Apps are open. Promise kept." is posted instead and the moment waits for the next open.
 *    Not while a weekly goal's must-do session still holds the lock (issue #165): the apps are
 *    not open then, and the lock's own notification says what is left.
 */
@Singleton
class NotificationDayCompletion @Inject constructor(
    private val notifications: KeptNotifications,
    private val foreground: ForegroundSignal,
    private val weeklyGoals: WeeklyGoalRepository,
    private val prefs: KeptPreferences,
    private val time: TimeSource,
) : DayCompletionReactor {

    override suspend fun onDayCompleted() {
        runCatching { notifications.clearReminder() }
        if (!foreground.isForeground && !weeklySessionLeft()) runCatching { notifications.dayComplete() }
    }

    /** Unknown reads as nothing left, so a failed read never costs the "Promise kept" notice. */
    private suspend fun weeklySessionLeft(): Boolean = runCatching {
        val s = prefs.currentSettings()
        val pastDue = GiveUpTime.isPastDue(time.minuteOfDay(), s.lockFromMinute, s.dueMinute)
        WeeklyHold.left(weeklyGoals.thisWeek().map { it.week }, pastDue) > 0
    }.getOrDefault(false)
}
