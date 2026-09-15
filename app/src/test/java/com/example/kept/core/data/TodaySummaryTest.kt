package com.example.kept.core.data

import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.domain.ProofType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The give-up time as the UI sees it (issue #7): a late tick is done, but does not count. */
class TodaySummaryTest {
    private val due = 1_000_000L

    private fun habit(id: Long, title: String) =
        HabitEntity(id = id, title = title, iconKey = "run", proofType = ProofType.MANUAL, targetValue = 1, unit = "", createdAt = 0)

    private fun entry(habitId: Long, at: Long?) =
        HabitEntryEntity(habitId = habitId, date = "2026-09-05", progressValue = if (at == null) 0 else 1, completedAt = at)

    private fun today(vararg completedAt: Long?) = TodaySummary(
        completedAt.mapIndexed { i, at ->
            val id = i + 1L
            HabitToday(habit(id, "Habit $id"), at?.let { entry(id, it) }, due)
        },
    )

    @Test fun `a tick before the give-up time counts`() {
        val s = today(due - 1)
        assertTrue(s.habits[0].countsToday)
        assertFalse(s.habits[0].late)
        assertTrue(s.allDoneOnTime)
        assertFalse(s.anyLate)
    }

    @Test fun `a tick at or after the give-up time is done but late`() {
        val s = today(due)
        assertTrue(s.habits[0].isDone)
        assertFalse(s.habits[0].countsToday)
        assertTrue(s.habits[0].late)
        assertTrue(s.allDone)
        assertFalse(s.allDoneOnTime)
        assertTrue(s.anyLate)
    }

    @Test fun `one late habit is enough to stop the day counting`() {
        val s = today(due - 5_000, due + 5_000)
        assertEquals(2, s.done)
        assertEquals(1, s.doneOnTime)
        assertTrue(s.allDone)
        assertFalse(s.allDoneOnTime)
    }

    @Test fun `an untouched habit is neither done nor late`() {
        val s = today(null)
        assertFalse(s.habits[0].isDone)
        assertFalse(s.habits[0].late)
        assertFalse(s.allDoneOnTime)
        assertFalse(s.anyLate)
    }
}
