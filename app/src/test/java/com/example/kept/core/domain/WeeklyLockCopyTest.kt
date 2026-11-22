package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What the weekly lock screen, the notifications, Home and the edit confirm say (issue #165,
 * story #175), for the issue's worked examples. Week of Sunday 22 November 2026.
 */
class WeeklyLockCopyTest {
    private val sunday = LocalDate.of(2026, 11, 22)
    private val wednesday = sunday.plusDays(3)
    private val thursday = sunday.plusDays(4)
    private val friday = sunday.plusDays(5)
    private val saturday = sunday.plusDays(6)
    private val longAgo = LocalDate.of(2026, 11, 1)

    private fun week(target: Int, today: LocalDate, vararg done: LocalDate, from: LocalDate = longAgo) =
        WeeklyGoalRules.week(WeeklyGoalRules.Goal(target, from), done.toSet(), today)

    private fun screen(target: Int, today: LocalDate, vararg done: LocalDate, activity: String = "Exercise") =
        WeeklyLockScreen.of(activity, week(target, today, *done), target)

    /** The count's no-break spaces read as spaces. */
    private fun String.plain(): String = replace(WeeklyLockCopy.NBSP, ' ')

    // --- the weekly lock screen ---

    @Test fun `example A - Wednesday, nothing logged`() {
        val s = screen(4, wednesday)
        assertEquals("4 days left, 4 to go.", s.headline)
        assertEquals("No exercise yet this week. Do today's session to unlock.", s.line)
        assertEquals("Exercise · 0 of 4", s.summary.plain())
        assertEquals("Exercise", s.activity)
        assertEquals("Skip today and tomorrow locks too.", s.hint)
        assertEquals("Mark today's exercise done", s.button)
        assertEquals("Weekly goal", WeeklyLockCopy.CHIP)
        assertEquals("This week", WeeklyLockCopy.SECTION)
        assertEquals("Today", WeeklyLockCopy.TODAY)
    }

    @Test fun `the strip runs Sunday to Saturday with skipped, today and coming up`() {
        val s = screen(4, wednesday)
        assertEquals(listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"), s.days.map { it.label })
        assertEquals(
            listOf("SKIPPED", "SKIPPED", "SKIPPED", "TODAY", "COMING_UP", "COMING_UP", "COMING_UP"),
            s.days.map { it.state.name },
        )
        assertEquals(listOf(false, false, false, true, false, false, false), s.days.map { it.isToday })
        assertEquals("Wednesday, today, not done yet", s.days[3].spoken)
    }

    @Test fun `example C - Thursday after Wednesday's session`() {
        val s = screen(4, thursday, wednesday)
        assertEquals("3 days left, 3 to go.", s.headline)
        assertEquals("1 of 4 done. Do today's session to unlock.", s.line)
        assertEquals("Exercise · 1 of 4", s.summary.plain())
        assertEquals("DONE", s.days[3].state.name)
        assertEquals("TODAY", s.days[4].state.name)
    }

    @Test fun `example C - Friday with Thursday skipped says the target is out of reach`() {
        val s = screen(4, friday, wednesday)
        assertEquals("2 days left, 3 to go.", s.headline)
        assertEquals("You can't hit 4 this week, but today still counts.", s.line)
        assertEquals("Skip today and tomorrow locks too.", s.hint)
        assertEquals("SKIPPED", s.days[4].state.name)
    }

    @Test fun `out of reach with nothing done still says so`() {
        assertEquals("You can't hit 4 this week, but today still counts.", screen(4, friday).line)
    }

    @Test fun `singular on a Saturday`() {
        val s = screen(4, saturday, sunday, sunday.plusDays(1), sunday.plusDays(2))
        assertEquals("1 day left, 1 to go.", s.headline)
        assertEquals("3 of 4 done. Do today's session to unlock.", s.line)
    }

    @Test fun `a Saturday that cannot make it is still singular for the day`() {
        assertEquals("1 day left, 3 to go.", screen(4, saturday, wednesday).headline)
    }

    @Test fun `the hint is left out when tomorrow would not lock`() {
        // Saturday: tomorrow is a new week, 7 days for 4.
        assertNull(screen(4, saturday, sunday, sunday.plusDays(1), sunday.plusDays(2)).hint)
        // A 7-day goal locks on its Sunday too.
        val six = (0..5).map { sunday.plusDays(it.toLong()) }.toTypedArray()
        assertEquals("Skip today and tomorrow locks too.", screen(7, saturday, *six).hint)
    }

    @Test fun `the first partial week uses its reduced target`() {
        // Set on Wednesday at 4 a week: Thursday needs 3 in 3 days.
        val s = WeeklyLockScreen.of("Exercise", week(4, thursday, from = wednesday), 4)
        assertEquals("3 days left, 3 to go.", s.headline)
        assertEquals("Exercise · 0 of 3", s.summary.plain())
        assertEquals(listOf("NOT_COUNTED", "NOT_COUNTED", "NOT_COUNTED", "NOT_COUNTED", "TODAY"), s.days.take(5).map { it.state.name })
    }

    @Test fun `an activity reads as part of the sentence`() {
        assertEquals("exercise", WeeklyLockCopy.inSentence("Exercise"))
        assertEquals("tidy room", WeeklyLockCopy.inSentence("Tidy room"))
        assertEquals("read Dune", WeeklyLockCopy.inSentence("Read Dune"))
        assertEquals("TV-free night", WeeklyLockCopy.inSentence("TV-free night"))
        assertEquals("yoga", WeeklyLockCopy.inSentence(" yoga "))
        assertEquals("Mark today's tidy room done", WeeklyLockCopy.markDone("Tidy room"))
        assertEquals("No TV-free night yet this week. Do today's session to unlock.", WeeklyLockCopy.line("TV-free night", 0, 4, true))
    }

    @Test fun `the summary keeps the activity as written and the count on one line`() {
        assertEquals("Tidy room · 2 of 5", WeeklyLockCopy.summary("Tidy room", 2, 5))
    }

    @Test fun `Sprig stands beside the weekly words below font scale 1,5 and steps aside from there`() {
        assertTrue(LockSprig.weeklyShowsSprig(1f))
        assertTrue(LockSprig.weeklyShowsSprig(1.3f))
        assertFalse(LockSprig.weeklyShowsSprig(1.5f))
        assertFalse(LockSprig.weeklyShowsSprig(2f))
    }

    // --- the must-do notification's words ---

    @Test fun `example A's notification`() {
        val w = week(4, wednesday)
        assertEquals("No exercise yet this week", MustDoNotice.title("Exercise", w.done, w.target))
        assertEquals("Only 4 days left and you need 4. Your apps are locked until today's session is done.", MustDoNotice.body(w.remaining, w.needed))
    }

    @Test fun `the title is progress-aware once a session is logged`() {
        val w = week(4, thursday, wednesday)
        assertEquals("1 of 4 exercise so far this week", MustDoNotice.title("Exercise", w.done, w.target))
        assertEquals("Only 3 days left and you need 3. Your apps are locked until today's session is done.", MustDoNotice.body(w.remaining, w.needed))
    }

    @Test fun `the body is singular on a Saturday and plain when the week is lost`() {
        assertEquals("Only 1 day left and you need 1. Your apps are locked until today's session is done.", MustDoNotice.body(1, 1))
        assertEquals("Only 2 days left and you need 3. Your apps are locked until today's session is done.", MustDoNotice.body(2, 3))
    }

    // --- the foreground notification ---

    private fun notice(
        habitsTotal: Int = 1,
        habitsLeft: Int = 1,
        weeklyLeft: Int = 0,
        active: Boolean = true,
        allDoneOnTime: Boolean = false,
        night: Boolean = false,
        breakUntil: String? = null,
        onboardingDone: Boolean = true,
    ) = LockNotice.of(
        LockNotice.Input(
            onboardingDone = onboardingDone, night = night, nightUntilMinute = 7 * 60, breakUntil = breakUntil,
            habitsTotal = habitsTotal, habitsLeft = habitsLeft, allDoneOnTime = allDoneOnTime, weeklyLeft = weeklyLeft,
            dailyLockActive = active, lockFromMinute = 7 * 60, dueMinute = 21 * 60,
        ),
    )

    @Test fun `the foreground notification counts a habit and a weekly session`() {
        assertEquals("1 habit and 1 weekly session left today" to "Apps locked until 9:00 pm", notice(weeklyLeft = 1))
        assertEquals("2 habits and 2 weekly sessions left today", notice(habitsTotal = 3, habitsLeft = 2, weeklyLeft = 2).first)
        assertEquals("1 habit and 1 weekly session left today", LockNotice.left(1, 1))
    }

    @Test fun `with the habits done it counts the weekly session alone`() {
        assertEquals(
            "1 weekly session left today" to "Apps locked until 9:00 pm",
            notice(habitsLeft = 0, allDoneOnTime = true, weeklyLeft = 1),
        )
        assertEquals("2 weekly sessions left today", notice(habitsLeft = 0, allDoneOnTime = true, weeklyLeft = 2).first)
        assertEquals("1 weekly session left today" to "Apps lock at 7:00 am", notice(habitsLeft = 0, allDoneOnTime = true, weeklyLeft = 1, active = false))
        assertEquals("with no habits at all", "1 weekly session left today", notice(habitsTotal = 0, habitsLeft = 0, weeklyLeft = 1).first)
    }

    @Test fun `with no weekly session left it reads as it did before weekly goals`() {
        assertEquals("1 habit left today" to "Apps locked until 9:00 pm", notice())
        assertEquals("2 habits left today" to "Apps lock at 7:00 am", notice(habitsTotal = 2, habitsLeft = 2, active = false))
        assertEquals("All habits done" to "Apps are open. Promise kept.", notice(habitsLeft = 0, allDoneOnTime = true))
        assertEquals("All habits done" to "Ticked after the give-up time, so today is missed.", notice(habitsLeft = 0, active = false))
        assertEquals("KEPT" to "No habits set for today", notice(habitsTotal = 0, habitsLeft = 0))
        assertEquals("KEPT" to "Finish setup to start locking", notice(onboardingDone = false))
        assertEquals("Lock paused" to "Apps re-lock at 3:30 pm", notice(breakUntil = "3:30 pm"))
    }

    @Test fun `a break on a weekly lock reads lock paused, and night lockdown still comes first`() {
        assertEquals("Lock paused" to "Apps re-lock at 3:30 pm", notice(habitsLeft = 0, allDoneOnTime = true, weeklyLeft = 1, breakUntil = "3:30 pm", active = false))
        assertEquals("Night lockdown" to "Apps open at 7:00 am", notice(weeklyLeft = 1, night = true))
    }

    // --- what Home counts, and the card under it ---

    private fun homeHeadline(habitsTotal: Int, habitsDone: Int, onTime: Boolean, weeks: List<WeeklyGoalRules.Week>, pastDue: Boolean): Pair<String, String> {
        val day = HomeDay.of(habitsTotal, habitsDone, onTime, WeeklyHold.left(weeks, pastDue))
        return HomeCopy.headline(day.total, day.done, day.keptOnTime) to HomeCopy.line(day.total, day.done, "9:00 pm", pastDue, breakActive = false)
    }

    @Test fun `Home does not say promise kept while a must-do session is left`() {
        val w = week(4, wednesday)
        val (headline, line) = homeHeadline(2, 2, onTime = true, weeks = listOf(w), pastDue = false)
        assertEquals("One to go.", headline)
        assertEquals("Apps unlock when you finish, or at 9:00 pm.", line)
        assertEquals("Today counts: apps lock until it's done", WeeklyCard.of("Exercise", w).statusLine)
    }

    @Test fun `Home counts habits and must-do sessions together`() {
        val w = week(4, wednesday)
        assertEquals("Two to go.", homeHeadline(2, 1, onTime = false, weeks = listOf(w), pastDue = false).first)
        assertEquals("Three to go.", homeHeadline(2, 1, onTime = false, weeks = listOf(w, w), pastDue = false).first)
        assertEquals(HomeDay(3, 2, keptOnTime = false), HomeDay.of(2, 2, true, 1))
    }

    @Test fun `once the session is logged Home is back to the habits alone`() {
        val w = week(4, wednesday, wednesday)
        assertEquals("Promise kept." to "Apps are open. See you tomorrow.", homeHeadline(2, 2, onTime = true, weeks = listOf(w), pastDue = false))
        assertEquals(HomeDay(2, 2, keptOnTime = true), HomeDay.of(2, 2, true, WeeklyHold.left(listOf(w), false)))
    }

    @Test fun `a goal that is not must-do changes nothing on Home`() {
        val w = week(4, sunday.plusDays(1), sunday)
        assertEquals(0, WeeklyHold.left(listOf(w), pastDue = false))
        assertEquals("Promise kept.", homeHeadline(1, 1, onTime = true, weeks = listOf(w), pastDue = false).first)
    }

    @Test fun `after the give-up time the card and the headline agree that apps are open`() {
        val w = week(4, wednesday)
        val (headline, line) = homeHeadline(2, 2, onTime = true, weeks = listOf(w), pastDue = true)
        assertEquals("Promise kept.", headline)
        assertEquals("Apps are open. See you tomorrow.", line)
        val card = WeeklyCard.of("Exercise", w, lockEndedToday = true)
        assertEquals(WeeklyCardStatus.LOCK_ENDED, card.status)
        assertEquals("Past the give-up time, no lock", card.statusLine)
        assertFalse("no line promises a lock once it has ended", card.statusLine.contains("apps lock"))
        // Habits left as well: Home's own "today won't count" line, and the same card line.
        assertEquals("One to go." to "Apps are open. Today won't count.", homeHeadline(2, 1, onTime = false, weeks = listOf(w), pastDue = true))
    }

    @Test fun `the give-up time changes nothing for a card that is not must-do or is logged`() {
        assertEquals(WeeklyCardStatus.ON_TRACK, WeeklyCard.of("Exercise", week(4, sunday.plusDays(1), sunday), lockEndedToday = true).status)
        assertEquals(WeeklyCardStatus.ON_TRACK, WeeklyCard.of("Exercise", week(4, wednesday, wednesday), lockEndedToday = true).status)
        assertEquals(WeeklyCardStatus.DONE_TODAY, WeeklyCard.of("Exercise", week(4, friday, friday), lockEndedToday = true).status)
        assertEquals(WeeklyCardStatus.TODAY_COUNTS, WeeklyCard.of("Exercise", week(4, wednesday)).status)
    }

    // --- loosening a goal that holds the lock ---

    @Test fun `lowering or deleting a goal that holds the lock asks first`() {
        val w = week(4, wednesday)
        val holds = WeeklyGoalLockEdit.holdsLock(dailyLockActive = true, week = w)
        assertTrue(holds)
        assertEquals(WeeklyGoalLockEdit.LOWER_WHILE_LOCKED, WeeklyGoalLockEdit.confirm(holds, savedDays = 4, newDays = 2))
        assertEquals(WeeklyGoalLockEdit.LOWER_WHILE_LOCKED, WeeklyGoalLockEdit.confirm(holds, savedDays = 4, newDays = 3))
        assertEquals(WeeklyGoalLockEdit.DELETE_WHILE_LOCKED, WeeklyGoalLockEdit.confirm(holds, savedDays = 4, newDays = null))
        assertTrue(WeeklyGoalLockEdit.LOWER_WHILE_LOCKED.startsWith("Your apps are locked right now."))
        assertTrue(WeeklyGoalLockEdit.DELETE_WHILE_LOCKED.startsWith("Your apps are locked right now."))
    }

    @Test fun `raising it or only renaming it asks nothing`() {
        assertNull(WeeklyGoalLockEdit.confirm(holdsLock = true, savedDays = 4, newDays = 5))
        assertNull(WeeklyGoalLockEdit.confirm(holdsLock = true, savedDays = 4, newDays = 4))
    }

    @Test fun `a goal that does not hold the lock asks nothing`() {
        val w = week(4, wednesday)
        assertFalse("outside the window, on a break or past the give-up time", WeeklyGoalLockEdit.holdsLock(dailyLockActive = false, week = w))
        assertFalse("habits hold the lock, this goal does not", WeeklyGoalLockEdit.holdsLock(dailyLockActive = true, week = week(4, sunday.plusDays(1), sunday)))
        assertFalse("its session is logged", WeeklyGoalLockEdit.holdsLock(dailyLockActive = true, week = week(4, wednesday, wednesday)))
        assertNull(WeeklyGoalLockEdit.confirm(holdsLock = false, savedDays = 4, newDays = 2))
        assertNull(WeeklyGoalLockEdit.confirm(holdsLock = false, savedDays = 4, newDays = null))
    }
}
