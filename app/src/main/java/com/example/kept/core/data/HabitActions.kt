package com.example.kept.core.data

import com.posthog.PostHog
import javax.inject.Inject
import javax.inject.Singleton

/** Use-cases that touch several repositories. UI talks to this, not to repositories directly. */
@Singleton
class HabitActions @Inject constructor(
    private val habits: HabitRepository,
    private val sprig: SprigRepository,
    private val buddy: BuddyRepository,
) {
    suspend fun complete(habitId: Long, photoPath: String? = null): HabitEvent.Completed? {
        val event = habits.markDone(habitId, photoPath) ?: return null
        PostHog.capture("habit_completed", properties = mapOf("before_due" to event.beforeDue))
        react(event)
        return event
    }

    suspend fun undo(habitId: Long): HabitEvent.Undone? {
        val event = habits.undo(habitId) ?: return null
        PostHog.capture("habit_completion_undone")
        sprig.onHabitEvent(event)
        return event
    }

    private suspend fun react(event: HabitEvent) {
        sprig.onHabitEvent(event)
        if (event is HabitEvent.Completed && event.allDoneNow) {
            buddy.simulateBuddyReaction("done")
        }
    }
}
