package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * When the must-do notification is posted and taken down (issue #165, The mechanic; story #175):
 * once per goal per day, at the lock start or as soon as the state appears later in the window.
 * The stored mark (goal id to date) is an input, so "once" holds across a restart of the process.
 */
class MustDoNoticeTest {
    private val sunday = LocalDate.of(2026, 11, 22)
    private val wednesday = sunday.plusDays(3)
    private val longAgo = LocalDate.of(2026, 11, 1)

    private fun goal(id: Long, target: Int, today: LocalDate, vararg done: LocalDate, activity: String = "Exercise", from: LocalDate = longAgo) =
        MustDoNotice.Goal(id, activity, WeeklyGoalRules.week(WeeklyGoalRules.Goal(target, from), done.toSet(), today))

    private fun decide(
        goals: List<MustDoNotice.Goal>,
        windowActive: Boolean = true,
        notified: Map<Long, String> = emptyMap(),
        showing: Set<Long> = emptySet(),
        onboardingDone: Boolean = true,
    ) = MustDoNotice.decide(onboardingDone, windowActive, goals, notified, showing)

    @Test fun `at the lock start on a must-do day it posts example A's words`() {
        val d = decide(listOf(goal(1, 4, wednesday)))
        assertEquals(
            listOf(
                MustDoNotice.Post(
                    goalId = 1, date = "2026-11-25", title = "No exercise yet this week",
                    body = "Only 4 days left and you need 4. Your apps are locked until today's session is done.",
                ),
            ),
            d.post,
        )
        assertTrue(d.clear.isEmpty())
    }

    @Test fun `it waits for the lock window to open`() {
        assertTrue(decide(listOf(goal(1, 4, wednesday)), windowActive = false).nothing)
    }

    @Test fun `once posted it is not posted again that day`() {
        val d = decide(listOf(goal(1, 4, wednesday)), notified = mapOf(1L to "2026-11-25"), showing = setOf(1L))
        assertTrue(d.nothing)
    }

    @Test fun `the mark outlives the process - a fresh start with it stored posts nothing`() {
        // Nothing in memory, the notification swiped away, only the stored mark is left.
        assertTrue(decide(listOf(goal(1, 4, wednesday)), notified = mapOf(1L to "2026-11-25"), showing = emptySet()).nothing)
    }

    @Test fun `yesterday's mark does not hold back today's`() {
        val thursday = wednesday.plusDays(1)
        val d = decide(listOf(goal(1, 4, thursday)), notified = mapOf(1L to "2026-11-25"))
        assertEquals("2026-11-26", d.post.single().date)
        assertEquals("No exercise yet this week", d.post.single().title)
    }

    @Test fun `no notification on a day that is not must-do`() {
        // Example B, Monday; example D, Friday; the Sunday after example C.
        assertTrue(decide(listOf(goal(1, 4, sunday.plusDays(1), sunday))).nothing)
        assertTrue(decide(listOf(goal(1, 4, sunday.plusDays(5), sunday, sunday.plusDays(1), sunday.plusDays(2), sunday.plusDays(4)))).nothing)
        assertTrue(decide(listOf(goal(1, 4, sunday.plusDays(7), wednesday))).nothing)
    }

    @Test fun `no notification on the day the goal is set`() {
        assertTrue(decide(listOf(goal(1, 7, wednesday, from = wednesday))).nothing)
    }

    @Test fun `no notification once the session is logged, and one already up comes down`() {
        val logged = listOf(goal(1, 4, wednesday, wednesday))
        assertTrue(decide(logged).nothing)
        val d = decide(logged, notified = mapOf(1L to "2026-11-25"), showing = setOf(1L))
        assertTrue(d.post.isEmpty())
        assertEquals(listOf(1L), d.clear)
    }

    @Test fun `an undo after the notification does not post a second one that day`() {
        // Posted, logged (cleared), undone: must-do again with today's mark stored.
        assertTrue(decide(listOf(goal(1, 4, wednesday)), notified = mapOf(1L to "2026-11-25")).nothing)
    }

    @Test fun `the state appearing later in the window posts right away`() {
        // 2 a week at noon: nothing. Raised to 4: today is must-do now.
        assertTrue(decide(listOf(goal(1, 2, wednesday))).nothing)
        assertEquals(1, decide(listOf(goal(1, 4, wednesday))).post.size)
    }

    @Test fun `the give-up time takes it down and nothing is posted after it`() {
        val d = decide(listOf(goal(1, 4, wednesday)), windowActive = false, notified = mapOf(1L to "2026-11-25"), showing = setOf(1L))
        assertTrue(d.post.isEmpty())
        assertEquals(listOf(1L), d.clear)
        // The state first appearing after the give-up time: the lock is over for today.
        assertTrue(decide(listOf(goal(1, 4, wednesday)), windowActive = false).nothing)
    }

    @Test fun `a goal removed or lowered while its notification is up takes it down`() {
        assertEquals(listOf(1L), decide(emptyList(), notified = mapOf(1L to "2026-11-25"), showing = setOf(1L)).clear)
        assertEquals(listOf(1L), decide(listOf(goal(1, 2, wednesday)), notified = mapOf(1L to "2026-11-25"), showing = setOf(1L)).clear)
    }

    @Test fun `each goal gets its own, once`() {
        val goals = listOf(goal(1, 4, wednesday), goal(2, 7, wednesday, sunday, sunday.plusDays(1), sunday.plusDays(2), activity = "Practice"), goal(3, 2, wednesday, activity = "Read"))
        val d = decide(goals, notified = mapOf(1L to "2026-11-25"), showing = setOf(1L))
        assertEquals(listOf(2L), d.post.map { it.goalId })
        assertEquals("3 of 7 practice so far this week", d.post.single().title)
        assertTrue(d.clear.isEmpty())
    }

    @Test fun `nothing is posted before onboarding is done`() {
        assertTrue(decide(listOf(goal(1, 4, wednesday)), onboardingDone = false).nothing)
    }
}
