package com.example.kept.core.domain

import com.example.kept.core.domain.WeeklyGoalRules.DayState
import com.example.kept.core.domain.WeeklyGoalRules.Goal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Weekly goals (issue #165, story #172): the worked examples A to D, target 7, the first partial
 * week, a mid-week edit, and the date edge cases. The week used is Sunday 22 to Saturday 28
 * November 2026.
 */
class WeeklyGoalRulesTest {
    private val sun = LocalDate.parse("2026-11-22")
    private val mon = sun.plusDays(1)
    private val tue = sun.plusDays(2)
    private val wed = sun.plusDays(3)
    private val thu = sun.plusDays(4)
    private val fri = sun.plusDays(5)
    private val sat = sun.plusDays(6)
    private val nextSun = sun.plusDays(7)
    private val week = listOf(sun, mon, tue, wed, thu, fri, sat)

    /** A goal that started counting weeks ago. */
    private fun goal(days: Int) = Goal(daysPerWeek = days, effectiveFrom = LocalDate.parse("2026-11-01"))

    private fun state(goal: Goal, today: LocalDate, vararg logged: LocalDate) = WeeklyGoalRules.week(goal, logged.toSet(), today)

    // --- the week ------------------------------------------------------------------------------

    @Test fun weekStart_isThePreviousOrSameSunday() {
        assertEquals(DayOfWeek.SUNDAY, sun.dayOfWeek)
        week.forEach { assertEquals(sun, WeeklyGoalRules.weekStart(it)) }
        assertEquals(nextSun, WeeklyGoalRules.weekStart(nextSun))
        assertEquals(sat, WeeklyGoalRules.weekEnd(sun))
        assertEquals(sat, WeeklyGoalRules.weekEnd(sat))
    }

    @Test fun remaining_is7OnSundayDownTo1OnSaturday() {
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1), week.map { WeeklyGoalRules.remaining(it) })
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1), week.map { state(goal(4), it).remaining })
        assertEquals(7, WeeklyGoalRules.remaining(nextSun))
    }

    @Test fun needed_isTargetMinusDone_flooredAtZero() {
        assertEquals(4, WeeklyGoalRules.needed(4, 0))
        assertEquals(1, WeeklyGoalRules.needed(4, 3))
        assertEquals(0, WeeklyGoalRules.needed(4, 4))
        assertEquals(0, WeeklyGoalRules.needed(2, 3))
    }

    // --- worked examples -----------------------------------------------------------------------

    @Test fun exampleA_target4_nothingLogged_wednesdayIsTheFirstMustDoDay() {
        listOf(sun, mon, tue).forEach {
            val s = state(goal(4), it)
            assertEquals(4, s.needed)
            assertFalse("$it", s.isMustDay)
            assertTrue(s.canStillHitTarget)
        }
        val w = state(goal(4), wed)
        assertEquals(4, w.target)
        assertEquals(0, w.done)
        assertEquals(4, w.remaining)
        assertEquals(4, w.needed)
        assertTrue(w.isMustDay)
        assertTrue(w.canStillHitTarget)
    }

    @Test fun exampleB_target4_oneOnSunday_mondayIsNotMustDo() {
        val s = state(goal(4), mon, sun)
        assertEquals(1, s.done)
        assertEquals(6, s.remaining)
        assertEquals(3, s.needed)
        assertFalse(s.isMustDay)
    }

    @Test fun exampleC_wednesdayLogged_thursdayMustDo_thursdaySkipped_fridayCannotHit_sundayResets() {
        val t = state(goal(4), thu, wed)
        assertEquals(3, t.remaining)
        assertEquals(3, t.needed)
        assertTrue(t.isMustDay)
        assertTrue(t.canStillHitTarget)

        val f = state(goal(4), fri, wed)
        assertEquals(2, f.remaining)
        assertEquals(3, f.needed)
        assertTrue(f.isMustDay)
        assertFalse("can't hit the target", f.canStillHitTarget)

        val s = state(goal(4), nextSun, wed)
        assertEquals(nextSun, s.weekStart)
        assertEquals(0, s.done)
        assertEquals(4, s.target)
        assertEquals(4, s.needed)
        assertEquals(7, s.remaining)
        assertFalse(s.isMustDay)
        assertTrue(s.canStillHitTarget)
    }

    @Test fun exampleD_target4_fourDoneByThursday_fridayAndSaturdayNeedNothing() {
        listOf(fri, sat).forEach {
            val s = state(goal(4), it, sun, mon, wed, thu)
            assertEquals(4, s.done)
            assertEquals(0, s.needed)
            assertFalse("$it", s.isMustDay)
            assertTrue(s.canStillHitTarget)
        }
    }

    // --- target 7 ------------------------------------------------------------------------------

    @Test fun target7_everyDayIsMustDoUntilItsSessionIsLogged() {
        week.forEachIndexed { i, day ->
            val before = week.take(i).toTypedArray()
            assertTrue("$day before its session", state(goal(7), day, *before).isMustDay)
            val after = state(goal(7), day, *before, day)
            assertFalse("$day once logged", after.isMustDay)
            assertTrue(after.doneToday)
            assertTrue(after.canStillHitTarget)
        }
    }

    @Test fun target7_aSkippedDayPutsTheTargetOutOfReach_andTheRestStillMustDo() {
        val s = state(goal(7), tue, sun)
        assertEquals(6, s.needed)
        assertEquals(5, s.remaining)
        assertTrue(s.isMustDay)
        assertFalse(s.canStillHitTarget)
    }

    // --- once today is logged ------------------------------------------------------------------

    @Test fun aLoggedMustDoDayIsNoLongerMustDo_evenWhenTheTargetIsOutOfReach() {
        // Example C's Friday, then the session is logged: 2 needed, Saturday is the only day left.
        val s = state(goal(4), fri, wed, fri)
        assertEquals(2, s.done)
        assertEquals(2, s.needed)
        assertEquals(2, s.remaining)
        assertFalse(s.isMustDay)
        assertFalse(s.canStillHitTarget)
        // Undo brings the must-do day back.
        assertTrue(state(goal(4), fri, wed).isMustDay)
    }

    @Test fun sessionsOfOtherWeeksAndOfLaterDaysAreNotCounted() {
        val s = state(goal(4), wed, sun.minusDays(1), sun.minusDays(7), nextSun, thu)
        assertEquals(0, s.done)
        assertTrue(s.isMustDay)
    }

    // --- first partial week --------------------------------------------------------------------

    @Test fun createdWednesday_target4_firstWeekTargetIs3_andWednesdayNeverMustDo() {
        val g = Goal(4, effectiveFrom = wed)
        val w = state(g, wed)
        assertEquals(3, w.target)
        assertEquals(3, w.needed)
        assertFalse("the creation day never locks", w.isMustDay)

        val t = state(g, thu)
        assertEquals(3, t.remaining)
        assertEquals(3, t.needed)
        assertTrue(t.isMustDay)
    }

    @Test fun createdWednesday_aSessionThatDayCounts() {
        val g = Goal(4, effectiveFrom = wed)
        val t = state(g, thu, wed)
        assertEquals(3, t.target)
        assertEquals(1, t.done)
        assertEquals(2, t.needed)
        assertEquals(3, t.remaining)
        assertFalse(t.isMustDay)
    }

    @Test fun theCreationDayIsNeverMustDo_evenAtTarget7OnASunday() {
        val g = Goal(7, effectiveFrom = sun)
        val s = state(g, sun)
        assertEquals(6, s.target)
        assertFalse(s.isMustDay)
        assertTrue(state(g, mon).isMustDay)
    }

    @Test fun createdSaturday_targetIs0ThatWeek_andFullFromTheNextSunday() {
        val g = Goal(4, effectiveFrom = sat)
        val s = state(g, sat)
        assertEquals(0, s.target)
        assertEquals(0, s.needed)
        assertFalse(s.isMustDay)
        assertTrue(s.canStillHitTarget)

        val next = state(g, nextSun)
        assertEquals(4, next.target)
        assertEquals(4, next.needed)
        assertFalse(next.isMustDay)
        assertTrue("Wednesday of the first full week", state(g, nextSun.plusDays(3)).isMustDay)
    }

    @Test fun firstWeekTarget_isTheSmallerOfDaysPerWeekAndDaysAfterTheCreationDay() {
        // Created Sunday..Saturday: 6, 5, 4, 3, 2, 1, 0 days come after.
        assertEquals(listOf(6, 5, 4, 3, 2, 1, 0), week.map { WeeklyGoalRules.targetFor(7, it, it) })
        assertEquals(listOf(2, 2, 2, 2, 2, 1, 0), week.map { WeeklyGoalRules.targetFor(2, it, it) })
        assertEquals("later the same week", 3, WeeklyGoalRules.targetFor(4, wed, sat))
        assertEquals("the next week", 4, WeeklyGoalRules.targetFor(4, wed, nextSun))
        assertEquals("a week before the goal started", 0, WeeklyGoalRules.targetFor(4, nextSun, sat))
    }

    // --- the week strip ------------------------------------------------------------------------

    @Test fun strip_isSundayToSaturday_doneSkippedTodayComingUp() {
        val s = state(goal(4), wed, mon)
        assertEquals(week, s.days.map { it.date })
        assertEquals(
            listOf(DayState.SKIPPED, DayState.DONE, DayState.SKIPPED, DayState.TODAY, DayState.COMING_UP, DayState.COMING_UP, DayState.COMING_UP),
            s.days.map { it.state },
        )
        assertEquals(listOf(wed), s.days.filter { it.isToday }.map { it.date })
    }

    @Test fun strip_todayLoggedIsDone_andStillMarkedToday() {
        val s = state(goal(4), wed, wed)
        val today = s.days.single { it.isToday }
        assertEquals(wed, today.date)
        assertEquals(DayState.DONE, today.state)
    }

    @Test fun strip_daysBeforeTheGoalAndTheCreationDayAreNotSkips() {
        val g = Goal(4, effectiveFrom = tue)
        val s = state(g, fri, thu)
        assertEquals(
            listOf(DayState.NOT_COUNTED, DayState.NOT_COUNTED, DayState.NOT_COUNTED, DayState.SKIPPED, DayState.DONE, DayState.TODAY, DayState.COMING_UP),
            s.days.map { it.state },
        )
        // A session on the creation day shows as done.
        assertEquals(DayState.DONE, state(g, fri, tue).days[2].state)
    }

    // --- dates: DST and zones ------------------------------------------------------------------

    /** US clocks go back on Sunday 1 November 2026: that Sunday is 25 hours long. */
    @Test fun dstEndWeek_remainingIsUnchangedByThe25HourDay() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.parse("2026-11-01")
        assertEquals(25, Duration.between(start.atStartOfDay(zone), start.plusDays(1).atStartOfDay(zone)).toHours())
        val days = (0L..6L).map { start.plusDays(it) }
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1), days.map { WeeklyGoalRules.remaining(it) })
        days.forEach { assertEquals(start, WeeklyGoalRules.weekStart(it)) }
        // 24 hours after Sunday's midnight is still Sunday (11 pm): counting hours would be early.
        val stillSunday = start.atStartOfDay(zone).toInstant().plusSeconds(24 * 3600L)
        assertEquals(start, stillSunday.atZone(zone).toLocalDate())
        assertEquals(7, WeeklyGoalRules.remaining(stillSunday.atZone(zone).toLocalDate()))
        assertEquals(6, WeeklyGoalRules.remaining(stillSunday.plusSeconds(3600).atZone(zone).toLocalDate()))
        val g = Goal(4, effectiveFrom = start.minusDays(30))
        assertFalse(state(g, start.plusDays(2)).isMustDay)
        assertTrue(state(g, start.plusDays(3)).isMustDay)
    }
}
