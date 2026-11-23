package com.example.kept.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * On a must-do day the promise is the habits and the session (issue #165, story #176). What
 * finishing a day sets off (the day's level, the "Promise kept" moment left for KEPT, the shade's
 * "All habits done", and `day_completed`) waits for whichever of the two comes last, and none of
 * it happens for a day whose session is skipped or logged after the give-up time.
 *
 * The real [HabitActions] and repositories on an in-memory Room database. Wednesday 25 November
 * 2026 with "Exercise, 4 days a week" and nothing logged since Sunday: example A's must-do day.
 * One daily habit, the 7:00 am to 9:00 pm window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HabitActionsWeeklyTest {

    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    private class Recorder : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override val enabled = true
        override fun capture(event: String, properties: Map<String, Any>) { events += event to properties }
        override fun screen(name: String, properties: Map<String, Any>) = Unit
        override fun register(key: String, value: Any) = Unit
        override fun restore(enabled: Boolean) = Unit
        override fun setEnabled(enabled: Boolean) = Unit
    }

    private val clock = TestClock(Instant.parse("2026-11-25T09:00:00Z"))
    private val time = TimeSource(clock)
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = KeptPreferences(ctx)
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
    private val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)
    private val analytics = Recorder()
    private var shadeCalls = 0
    private val actions = HabitActions(
        habits = habits,
        sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time),
        prefs = prefs,
        time = time,
        analytics = analytics,
        dayCompletion = object : DayCompletionReactor { override suspend fun onDayCompleted() { shadeCalls++ } },
        weeklyGoals = weekly,
    )

    private var read = 0L

    @Before fun setUp() = runBlocking {
        prefs.clearAll()
        prefs.updateSettings { it.copy(onboardingDone = true, lockFromMinute = 7 * 60, dueMinute = 21 * 60) }
        prefs.updateSprig { SprigState(level = 3, streakDays = 5) }
        read = habits.addHabit("Read", "book", ProofType.MANUAL, 10, "pages")
    }

    @After fun close() = db.close()

    private suspend fun goal(title: String = "Exercise", days: Int = 4): Long =
        db.weeklyGoalDao().insert(WeeklyGoalEntity(title = title, iconKey = "run", daysPerWeek = days, createdAt = 0, effectiveFrom = "2026-11-01", sortOrder = days))

    private fun at(hhmm: String) { clock.now = Instant.parse("2026-11-25T$hhmm:00Z") }

    private fun completed(): List<Map<String, Any>> = analytics.events.filter { it.first == "day_completed" }.map { it.second }
    private suspend fun level(): Int = prefs.currentSprig().level
    private suspend fun pending(): String? = prefs.currentSettings().celebrationPendingDate

    /** Nothing a finished day sets off has happened. */
    private suspend fun assertDayOpen(why: String) {
        assertEquals("$why: day_completed", emptyList<Map<String, Any>>(), completed())
        assertEquals("$why: the level", 3, level())
        assertNull("$why: the moment", pending())
        assertEquals("$why: the shade", 0, shadeCalls)
    }

    @Test fun `the last habit does not finish a must-do day, the session does`() = runBlocking {
        val g = goal()
        val event = actions.complete(read)!!
        assertTrue("the habits themselves are all done, in time", event.allDoneNow && event.beforeDue)
        assertDayOpen("habits done, session not logged")

        at("10:00")
        assertTrue(actions.logWeekly(g))

        assertEquals(listOf(mapOf<String, Any>("habit_count" to 1, "minutes_before_due" to 660)), completed())
        assertEquals(4, level())
        assertEquals("2026-11-25", pending())
        assertEquals(1, shadeCalls)
    }

    @Test fun `with the session logged first, the last habit finishes the day`() = runBlocking {
        val g = goal()
        assertTrue(actions.logWeekly(g))
        assertDayOpen("session logged, a habit left")

        actions.complete(read)

        assertEquals(1, completed().size)
        assertEquals(4, level())
        assertEquals("2026-11-25", pending())
        assertEquals(1, shadeCalls)
    }

    @Test fun `with two must-do goals the day waits for both`() = runBlocking {
        val exercise = goal("Exercise", 4)
        val dune = goal("Read Dune", 5)
        actions.complete(read)
        actions.logWeekly(exercise)
        assertDayOpen("one of two sessions logged")
        actions.logWeekly(dune)
        assertEquals(1, completed().size)
        assertEquals(4, level())
    }

    @Test fun `undoing the must-do session opens the day again and takes its level back`() = runBlocking {
        val g = goal()
        actions.complete(read)
        actions.logWeekly(g)
        assertEquals(4, level())

        assertTrue(actions.undoWeekly(g))
        assertEquals(3, level())
        assertNull("nothing left to celebrate", pending())
        assertFalse("there is no session to undo twice", actions.undoWeekly(g))
        assertEquals(3, level())

        // Logged again: the day is kept again, one level, not two.
        actions.logWeekly(g)
        assertEquals(4, level())
        assertEquals("2026-11-25", pending())
    }

    @Test fun `a second tap logs nothing and finishes nothing twice`() = runBlocking {
        val g = goal()
        actions.complete(read)
        assertTrue(actions.logWeekly(g))
        assertFalse(actions.logWeekly(g))
        assertFalse("a goal that is not there", actions.logWeekly(99))
        assertEquals(1, completed().size)
        assertEquals(1, shadeCalls)
        assertEquals(4, level())
    }

    @Test fun `a session logged after the give-up time does not finish the day`() = runBlocking {
        val g = goal()
        actions.complete(read)
        at("21:00")
        assertTrue("it is stored, and counts toward the week", actions.logWeekly(g))
        assertEquals(1, weekly.thisWeek().single().week.done)
        assertDayOpen("the session came at the give-up time")
    }

    @Test fun `on a day that is not a must-do day the habits finish it and a session changes nothing`() = runBlocking {
        // 2 a week on a Wednesday with nothing logged: 4 days left, so today can be skipped.
        val g = goal(days = 2)
        actions.complete(read)
        assertEquals(1, completed().size)
        assertEquals(4, level())
        assertEquals("2026-11-25", pending())

        assertTrue(actions.logWeekly(g))
        assertEquals("the session is not what finished the day", 1, completed().size)
        assertEquals(1, shadeCalls)
        assertTrue(actions.undoWeekly(g))
        assertEquals("undoing it takes nothing back", 4, level())
        assertEquals("2026-11-25", pending())
    }

    @Test fun `no habits ticked, the session alone finishes nothing`() = runBlocking {
        val g = goal()
        actions.logWeekly(g)
        assertDayOpen("session logged, the habit not ticked")
    }
}
