package com.example.kept.core.notify

import com.example.kept.core.data.LockState
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.MustDoNotice
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts and takes down the weekly goals' must-do notifications (issue #165, story #175). The
 * Android adapter around [MustDoNotice.decide]: it gathers the lock state, the stored once-a-day
 * marks and what is in the shade, and does what the decision says.
 *
 * The lock service calls [check] from its tick, so the notification arrives with the lock: at
 * the lock start on a must-do day, or as soon as the state appears later in the window.
 */
@Singleton
class MustDoNotifier @Inject constructor(
    private val prefs: KeptPreferences,
    private val notifications: KeptNotifications,
) {
    suspend fun check(state: LockState, localTime: LocalTime) {
        val decision = MustDoNotice.decide(
            onboardingDone = state.settings.onboardingDone,
            windowActive = LockPolicy.isWindowActive(localTime, state.snapshot(emptySet(), null)),
            goals = state.weekly.map { MustDoNotice.Goal(it.id, it.goal.title, it.week) },
            notified = prefs.mustDoNotified(),
            showing = notifications.mustDoShowing(),
        )
        decision.clear.forEach { notifications.clearMustDo(it) }
        // Marked only once it is in the shade: with notifications not allowed nothing is stored,
        // so allowing them later the same day still gets today's notice.
        decision.post.forEach { if (notifications.mustDo(it.goalId, it.title, it.body)) prefs.markMustDoNotified(it.goalId, it.date) }
    }
}
