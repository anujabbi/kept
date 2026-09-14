package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reminder wording (issue #9). The middle step used to say a hardcoded "2 hours left" whatever the
 * window was; every duration in the copy is now phrased from the step's real delta.
 */
class ReminderCopyTest {

    @Test fun `durations read short`() {
        assertEquals("1 minute", ReminderCopy.duration(1))
        assertEquals("30 minutes", ReminderCopy.duration(30))
        assertEquals("1 hour", ReminderCopy.duration(60))
        assertEquals("2 hours", ReminderCopy.duration(120))
        assertEquals("1h 45m", ReminderCopy.duration(105))
        assertEquals("14 hours", ReminderCopy.duration(14 * 60))
    }

    @Test fun `the morning step counts the things left`() {
        assertEquals("2 things today", ReminderCopy.title(ReminderKind.MORNING, remaining = 2, minutesBeforeDue = 840, streakDays = 4))
        assertEquals("1 thing today", ReminderCopy.title(ReminderKind.MORNING, remaining = 1, minutesBeforeDue = 840, streakDays = 0))
    }

    @Test fun `the later steps say the real time left`() {
        assertEquals(
            "2 hours left. 4-day streak on the line.",
            ReminderCopy.title(ReminderKind.BEFORE_DUE, remaining = 2, minutesBeforeDue = 120, streakDays = 4),
        )
        assertEquals(
            "30 minutes left. Sprig is waiting.",
            ReminderCopy.title(ReminderKind.LAST_CALL, remaining = 1, minutesBeforeDue = 30, streakDays = 0),
        )
    }

    @Test fun `a short window says what is really left, not two hours`() {
        assertEquals(
            "30 minutes left. Sprig is waiting.",
            ReminderCopy.title(ReminderKind.BEFORE_DUE, remaining = 1, minutesBeforeDue = 30, streakDays = 0),
        )
    }

    @Test fun `bodies match the step`() {
        assertEquals(
            "Apps lock until they're done. You have until 9:00 pm.",
            ReminderCopy.body(ReminderKind.MORNING, remaining = 2, dueMinute = 21 * 60),
        )
        assertEquals(
            "2 habits to go. Apps stay locked until then.",
            ReminderCopy.body(ReminderKind.BEFORE_DUE, remaining = 2, dueMinute = 21 * 60),
        )
        assertEquals(
            "1 habit to go. Apps stay locked until then.",
            ReminderCopy.body(ReminderKind.LAST_CALL, remaining = 1, dueMinute = 21 * 60),
        )
    }

    @Test fun `an action names the habit only when there is more than one`() {
        assertEquals("Mark done", ReminderCopy.actionLabel("Read", single = true))
        assertEquals("Done: Read", ReminderCopy.actionLabel("Read", single = false))
    }
}

/**
 * Android shows at most three notification actions, so a fourth habit has to give up its slot
 * (issue #9).
 */
class ReminderActionPlanTest {

    @Test fun `up to three habits each get a mark-done action`() {
        assertEquals(ReminderActions.Plan(listOf(1L), openApp = false), ReminderActions.plan(listOf(1L)))
        assertEquals(ReminderActions.Plan(listOf(1L, 2L, 3L), openApp = false), ReminderActions.plan(listOf(1L, 2L, 3L)))
    }

    @Test fun `a fourth habit turns the last slot into Open KEPT`() {
        assertEquals(ReminderActions.Plan(listOf(1L, 2L), openApp = true), ReminderActions.plan(listOf(1L, 2L, 3L, 4L)))
    }

    @Test fun `no habits means no actions`() {
        assertEquals(ReminderActions.Plan(emptyList(), openApp = false), ReminderActions.plan(emptyList()))
    }
}
