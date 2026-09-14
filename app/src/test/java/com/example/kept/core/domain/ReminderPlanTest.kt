package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a reminder is worth showing, and when one already on screen has to come down (issue #9).
 * This is the branching `ReminderPoster` used to hold inline, where it needed a DataStore, a
 * database and a notification manager to reach.
 */
class ReminderPlanTest {

    private val twoHabits = listOf(ReminderPlan.Habit(1, "Exercise"), ReminderPlan.Habit(2, "Read"))

    private fun decide(
        kind: ReminderKind = ReminderKind.LAST_CALL,
        onboardingDone: Boolean = true,
        remindersEnabled: Boolean = true,
        remaining: List<ReminderPlan.Habit> = twoHabits,
        totalHabits: Int = 2,
        minutesLeft: Int = 30,
        streakDays: Int = 4,
        dueMinute: Int = 21 * 60,
    ) = ReminderPlan.decide(
        kind, onboardingDone, remindersEnabled, remaining, totalHabits, minutesLeft, streakDays, dueMinute,
    )

    @Test fun `a live rung is posted with copy and a button per habit`() {
        val d = decide() as ReminderPlan.Decision.Post
        assertEquals("30 minutes left. 4-day streak on the line.", d.title)
        assertEquals("2 habits to go. Apps stay locked until then.", d.body)
        assertEquals(listOf(ReminderPlan.Action(1, "Done: Exercise"), ReminderPlan.Action(2, "Done: Read")), d.actions)
        assertTrue(!d.openApp)
    }

    @Test fun `one habit gets an unnamed button`() {
        val d = decide(remaining = listOf(ReminderPlan.Habit(9, "Read")), totalHabits = 1) as ReminderPlan.Decision.Post
        assertEquals(listOf(ReminderPlan.Action(9, "Mark done")), d.actions)
    }

    @Test fun `a fourth habit spends the last slot on Open KEPT`() {
        val four = (1L..4L).map { ReminderPlan.Habit(it, "H$it") }
        val d = decide(remaining = four, totalHabits = 4) as ReminderPlan.Decision.Post
        assertEquals(listOf(1L, 2L), d.actions.map { it.habitId })
        assertTrue(d.openApp)
    }

    @Test fun `reminders switched off skip and take down what is on screen`() {
        assertEquals(ReminderPlan.Decision.Skip(clearExisting = true), decide(remindersEnabled = false))
    }

    @Test fun `a day already finished skips and takes down what is on screen`() {
        assertEquals(ReminderPlan.Decision.Skip(clearExisting = true), decide(remaining = emptyList()))
    }

    @Test fun `no habits at all skips and takes down what is on screen`() {
        assertEquals(
            ReminderPlan.Decision.Skip(clearExisting = true),
            decide(remaining = emptyList(), totalHabits = 0),
        )
    }

    @Test fun `past the give-up time there is no time left to nudge about`() {
        assertEquals(ReminderPlan.Decision.Skip(clearExisting = true), decide(minutesLeft = 0))
        assertEquals(ReminderPlan.Decision.Skip(clearExisting = true), decide(minutesLeft = -5))
    }

    @Test fun `before onboarding there is nothing to skip and nothing to clear`() {
        assertEquals(ReminderPlan.Decision.Skip(clearExisting = false), decide(onboardingDone = false))
    }

    @Test fun `the morning rung counts things rather than minutes`() {
        val d = decide(kind = ReminderKind.MORNING, minutesLeft = 840) as ReminderPlan.Decision.Post
        assertEquals("2 things today", d.title)
        assertEquals("Apps lock until they're done. You have until 9:00 pm.", d.body)
    }
}
