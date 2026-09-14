package com.example.kept.core.notify

import com.example.kept.core.data.DayCompletionReactor
import com.example.kept.core.data.ForegroundSignal
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
 */
@Singleton
class NotificationDayCompletion @Inject constructor(
    private val notifications: KeptNotifications,
    private val foreground: ForegroundSignal,
) : DayCompletionReactor {

    override suspend fun onDayCompleted() {
        runCatching { notifications.clearReminder() }
        if (!foreground.isForeground) runCatching { notifications.dayComplete() }
    }
}
