package com.example.kept.core.data

import javax.inject.Inject
import javax.inject.Singleton

/** Use-cases that touch several repositories. UI talks to this, not to repositories directly. */
@Singleton
class HabitActions @Inject constructor(
    private val habits: HabitRepository,
    private val sprig: SprigRepository,
    private val buddy: BuddyRepository,
) {
    suspend fun complete(habitId: Long, photoPath: String? = null): HabitEvent? {
        val event = habits.markDone(habitId, photoPath) ?: return null
        react(event)
        return event
    }

    suspend fun addTimerSeconds(habitId: Long, seconds: Int): HabitEvent? {
        val event = habits.addTimerSeconds(habitId, seconds) ?: return null
        react(event)
        return event
    }

    suspend fun undo(habitId: Long): HabitEvent? {
        val event = habits.undo(habitId) ?: return null
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
