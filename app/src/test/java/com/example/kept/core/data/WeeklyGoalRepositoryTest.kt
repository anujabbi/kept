package com.example.kept.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Weekly goals over a real in-memory Room database (issue #165, story #172): one session a day
 * however often it is logged, undo, the limit of three, and progress counted from the entries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeeklyGoalRepositoryTest {

    /** A clock the test moves: the instant, and the zone the phone is in. */
    private class TestClock(var now: Instant, var zoneId: ZoneId) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
    }

    private val utc = ZoneId.of("UTC")
    // Wednesday 25 November 2026, noon. The week is Sunday 22 to Saturday 28.
    private val clock = TestClock(Instant.parse("2026-11-25T12:00:00Z"), utc)
    private val time = TimeSource(clock)
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val goals = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)

    @After fun close() = db.close()

    private fun moveTo(instant: String) { clock.now = Instant.parse(instant) }

    private suspend fun entryRows(): List<String> =
        db.weeklyGoalEntryDao().between("0000-01-01", "9999-12-31").sortedBy { it.date }.map { "${it.goalId} ${it.date}" }

    private suspend fun week(id: Long) = goals.thisWeek().single { it.id == id }.week

    // --- add, edit, remove ---------------------------------------------------------------------

    @Test fun a_new_goal_counts_from_today_and_takes_its_icon_from_the_name() = runBlocking {
        val id = goals.addGoal("  Exercise ", 4)!!
        val goal = goals.observeGoals().first().single()
        assertEquals(id, goal.id)
        assertEquals("Exercise", goal.title)
        assertEquals("run", goal.iconKey)
        assertEquals(4, goal.daysPerWeek)
        assertTrue(goal.isActive)
        assertEquals("2026-11-25", goal.effectiveFrom)
        assertEquals(clock.now.toEpochMilli(), goal.createdAt)
        // Created on a Wednesday: the first week asks for 3, and the creation day is not must-do.
        val w = week(id)
        assertEquals(3, w.target)
        assertFalse(w.isMustDay)
    }

    @Test fun a_fourth_active_goal_is_refused() = runBlocking {
        repeat(WeeklyGoalRules.MAX_GOALS) { assertNotNull("goal ${it + 1}", goals.addGoal("Goal ${it + 1}", 3)) }
        assertNull(goals.addGoal("Goal 4", 3))
        assertEquals(3, db.weeklyGoalDao().active().size)
    }

    @Test fun two_saves_at_two_goals_add_only_one() = runBlocking {
        repeat(2) { goals.addGoal("Goal ${it + 1}", 3) }
        val results = (1..2).map { async(Dispatchers.IO) { goals.addGoal("Late $it", 3) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(3, db.weeklyGoalDao().active().size)
    }

    @Test fun a_removed_goal_makes_room_again_and_keeps_its_sessions() = runBlocking {
        val ids = (1..3).map { goals.addGoal("Goal $it", 3)!! }
        goals.logToday(ids[0])
        assertNull(goals.addGoal("Goal 4", 3))
        goals.removeGoal(ids[0])
        assertEquals(ids.drop(1), goals.thisWeek().map { it.id })
        assertNotNull(goals.addGoal("Goal 4", 3))
        assertEquals(listOf("${ids[0]} 2026-11-25"), entryRows())
        assertFalse("a removed goal takes no session", goals.logToday(ids[0]))
    }

    @Test fun weekly_goals_and_daily_habits_have_separate_limits() = runBlocking {
        val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), KeptPreferences(ctx), time)
        repeat(HabitLimits.MAX_HABITS) { assertNotNull(habits.addHabitWithinLimit("Habit $it", "write", ProofType.MANUAL, 1, "times")) }
        repeat(WeeklyGoalRules.MAX_GOALS) { assertNotNull(goals.addGoal("Goal $it", 3)) }
        assertEquals(HabitLimits.MAX_HABITS, db.habitDao().active().size)
        assertNull(habits.addHabitWithinLimit("One more", "write", ProofType.MANUAL, 1, "times"))
        assertNull(goals.addGoal("One more", 3))
    }

    @Test fun a_blank_or_too_long_name_or_days_outside_1_to_7_is_not_saved() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { goals.addGoal("   ", 3) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { goals.addGoal("x".repeat(29), 3) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { goals.addGoal("Read", 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { goals.addGoal("Read", 8) } }
        assertEquals(0, db.weeklyGoalDao().active().size)
        assertNotNull(goals.addGoal("x".repeat(28), 7))
    }

    @Test fun editing_the_target_mid_week_applies_now_and_keeps_logged_sessions() = runBlocking {
        moveTo("2026-11-16T12:00:00Z") // set the week before, so this week has the full target
        val id = goals.addGoal("Exercise", 4)!!
        moveTo("2026-11-23T12:00:00Z") // Monday
        assertTrue(goals.logToday(id))
        moveTo("2026-11-26T12:00:00Z") // Thursday
        val before = week(id)
        assertEquals(3, before.needed)
        assertTrue(before.isMustDay)

        assertTrue(goals.updateGoal(id, "Read", 2))

        val after = week(id)
        assertEquals(2, after.target)
        assertEquals(1, after.done)
        assertEquals(1, after.needed)
        assertEquals(3, after.remaining)
        assertFalse(after.isMustDay)
        val goal = db.weeklyGoalDao().byId(id)!!
        assertEquals("Read", goal.title)
        assertEquals("book", goal.iconKey)
        assertEquals("the start date does not move", "2026-11-16", goal.effectiveFrom)
        assertFalse(goals.updateGoal(id + 99, "Read", 2))
    }

    // --- sessions ------------------------------------------------------------------------------

    @Test fun logging_today_twice_stores_one_row_and_undo_removes_it() = runBlocking {
        val id = goals.addGoal("Exercise", 4)!!
        assertEquals(0, week(id).done)

        assertTrue(goals.logToday(id))
        assertFalse("the second tap stores nothing", goals.logToday(id))

        assertEquals(listOf("$id 2026-11-25"), entryRows())
        assertEquals(1, week(id).done)
        assertTrue(week(id).doneToday)

        assertTrue(goals.undoToday(id))
        assertEquals(emptyList<String>(), entryRows())
        assertEquals(0, week(id).done)
        assertFalse(week(id).doneToday)
        assertFalse("nothing left to undo", goals.undoToday(id))
    }

    @Test fun each_goal_has_its_own_session_and_a_new_day_takes_another() = runBlocking {
        val a = goals.addGoal("Exercise", 4)!!
        val b = goals.addGoal("Read", 2)!!
        goals.logToday(a)
        assertEquals(1, week(a).done)
        assertEquals(0, week(b).done)
        moveTo("2026-11-26T12:00:00Z")
        assertTrue(goals.logToday(a))
        assertTrue(goals.logToday(b))
        assertEquals(2, week(a).done)
        assertEquals(1, week(b).done)
        assertEquals(listOf("$a 2026-11-25", "$a 2026-11-26", "$b 2026-11-26"), entryRows())
    }

    @Test fun undo_only_touches_today() = runBlocking {
        val id = goals.addGoal("Exercise", 4)!!
        goals.logToday(id)
        moveTo("2026-11-26T12:00:00Z")
        assertFalse(goals.undoToday(id))
        assertEquals(listOf("$id 2026-11-25"), entryRows())
    }

    @Test fun the_count_resets_on_sunday_and_the_old_sessions_stay_stored() = runBlocking {
        moveTo("2026-11-16T12:00:00Z")
        val id = goals.addGoal("Exercise", 4)!!
        moveTo("2026-11-25T12:00:00Z")
        goals.logToday(id)
        moveTo("2026-11-28T23:59:00Z") // Saturday night
        assertEquals(1, week(id).done)
        moveTo("2026-11-29T00:00:00Z") // Sunday
        val w = week(id)
        assertEquals(LocalDate.parse("2026-11-29"), w.weekStart)
        assertEquals(0, w.done)
        assertEquals(4, w.needed)
        assertEquals(7, w.remaining)
        assertFalse(w.isMustDay)
        assertEquals(listOf("$id 2026-11-25"), entryRows())
    }

    @Test fun observeThisWeek_follows_a_session_and_an_undo() = runBlocking {
        val id = goals.addGoal("Exercise", 4)!!
        assertEquals(0, goals.observeThisWeek().first().single().week.done)
        goals.logToday(id)
        val logged = goals.observeThisWeek().first().single()
        assertEquals(id, logged.goal.id)
        assertEquals(1, logged.week.done)
        goals.undoToday(id)
        assertEquals(0, goals.observeThisWeek().first().single().week.done)
        goals.removeGoal(id)
        assertEquals(emptyList<WeeklyGoalWeek>(), goals.observeThisWeek().first())
    }

    // --- dates ---------------------------------------------------------------------------------

    /**
     * Saturday 21 November, 6:30 pm in Los Angeles is already Sunday 22 in London. The session is
     * stored under the local date it was logged on and stays there after the phone changes zone.
     */
    @Test fun a_zone_change_moves_today_but_not_a_logged_session() = runBlocking {
        clock.zoneId = ZoneId.of("America/Los_Angeles")
        moveTo("2026-11-09T20:00:00Z")
        val id = goals.addGoal("Exercise", 4)!!
        moveTo("2026-11-22T02:30:00Z")
        assertEquals(LocalDate.parse("2026-11-21"), time.today())
        goals.logToday(id)
        val la = week(id)
        assertEquals(LocalDate.parse("2026-11-15"), la.weekStart)
        assertEquals(1, la.remaining)
        assertEquals(1, la.done)
        assertTrue(la.doneToday)

        clock.zoneId = ZoneId.of("Europe/London") // the same instant, read in the new zone
        assertEquals(LocalDate.parse("2026-11-22"), time.today())
        val london = week(id)
        assertEquals(LocalDate.parse("2026-11-22"), london.weekStart)
        assertEquals(7, london.remaining)
        assertEquals("Saturday's session belongs to last week", 0, london.done)
        assertFalse(london.doneToday)
        assertEquals(listOf("$id 2026-11-21"), entryRows())
        // Today in the new zone takes its own session.
        assertTrue(goals.logToday(id))
        assertEquals(listOf("$id 2026-11-21", "$id 2026-11-22"), entryRows())
    }

    /** The 23-hour Sunday of 8 March 2026 in New York: one session on it, and Monday is the next day. */
    @Test fun the_dst_start_day_takes_one_session_like_any_other() = runBlocking {
        clock.zoneId = ZoneId.of("America/New_York")
        moveTo("2026-03-02T17:00:00Z")
        val id = goals.addGoal("Exercise", 7)!!
        moveTo("2026-03-08T06:30:00Z") // 1:30 am, before the clocks jump
        assertTrue(week(id).isMustDay)
        assertEquals(7, week(id).remaining)
        assertTrue(goals.logToday(id))
        moveTo("2026-03-08T07:30:00Z") // 3:30 am, after
        assertFalse(goals.logToday(id))
        assertEquals(7, week(id).remaining)
        moveTo("2026-03-09T03:59:00Z") // 11:59 pm Sunday, 23 hours after midnight
        assertEquals(7, week(id).remaining)
        moveTo("2026-03-09T04:00:00Z") // Monday
        assertEquals(6, week(id).remaining)
        assertTrue(week(id).isMustDay)
    }
}
