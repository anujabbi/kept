package com.example.kept.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.db.WeeklyGoalEntryEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.RolloverEngine
import com.example.kept.core.domain.SprigState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The rollover on a must-do day (issue #165, story #176): the real [RolloverRunner] and
 * repositories on an in-memory Room database, with a clock the test moves. What is read back is
 * what History, the recap and Home's last seven days read: `day_records`, and the streak.
 *
 * The week is Sunday 22 to Saturday 28 November 2026. One daily habit, "Read". The shield's week
 * is the ISO one, Monday 23 to Sunday 29 (`2026-W48`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RolloverWeeklyStoreTest {

    private class TestClock(var now: Instant, var zoneId: ZoneId = ZoneId.of("UTC")) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = zoneId
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

    private val clock = TestClock(Instant.parse("2026-11-25T08:00:00Z"))
    private val time = TimeSource(clock)
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = KeptPreferences(ctx)
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
    private val sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time)
    private val analytics = Recorder()
    private val recaps = mutableListOf<RolloverEngine.DaySummary>()
    private val runner = RolloverRunner(
        prefs, db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), db.lockBreakDao(), db.protectionGapDao(),
        sprig, habits, time, RecapNotifier { summary, _ -> recaps += summary }, analytics,
        db.weeklyGoalDao(), db.weeklyGoalEntryDao(),
    )

    private var readId = 0L

    @Before fun setUp() = runBlocking {
        prefs.clearAll()
        prefs.updateSettings { it.copy(onboardingDone = true, firstUseDate = LocalDate.parse("2026-11-01"), lockFromMinute = 7 * 60, dueMinute = 21 * 60) }
        readId = habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
        // Tuesday is closed: a five day streak, and this ISO week's shield already spent.
        settled(lastRollover = "2026-11-24", shield = false)
    }

    @After fun close() = db.close()

    private suspend fun settled(lastRollover: String, shield: Boolean, streak: Int = 5) = prefs.updateSprig {
        SprigState(level = 3, streakDays = streak, bestStreak = streak, shieldAvailable = shield, shieldWeekKey = "2026-W48", lastRolloverDate = LocalDate.parse(lastRollover))
    }

    /** A local date and time in the clock's zone, as an instant. */
    private fun local(day: String, hhmm: String): Instant = LocalDateTime.parse("${day}T$hhmm:00").atZone(clock.zoneId).toInstant()

    private fun goTo(day: String, hhmm: String) { clock.now = local(day, hhmm) }

    /** The daily habit, ticked on [day] at [hhmm]. */
    private suspend fun read(day: String, hhmm: String = "09:00") {
        goTo(day, hhmm)
        assertTrue(habits.markDone(readId) != null)
    }

    /** A goal set on 1 November, so every day of these weeks counts in full. */
    private suspend fun goal(title: String = "Exercise", days: Int = 4, from: String = "2026-11-01"): Long =
        db.weeklyGoalDao().insert(WeeklyGoalEntity(title = title, iconKey = "run", daysPerWeek = days, createdAt = 0, effectiveFrom = from, sortOrder = db.weeklyGoalDao().active().size))

    /** Today's session, logged on [day] at [hhmm] the way the app logs it: keyed by the local date. */
    private suspend fun session(goalId: Long, day: String, hhmm: String = "10:00") {
        goTo(day, hhmm)
        assertEquals(day, time.todayKey())
        assertTrue(db.weeklyGoalEntryDao().insertIfAbsent(WeeklyGoalEntryEntity(goalId = goalId, date = time.todayKey(), completedAt = time.nowMillis())) > 0)
    }

    /** Runs the rollover on [day] just after midnight. */
    private suspend fun rollOn(day: String, hhmm: String = "00:05"): List<RolloverEngine.Output> {
        goTo(day, hhmm)
        return runner.runPending()
    }

    private suspend fun record(day: String): DayRecordEntity = db.dayRecordDao().get(day)!!
    private suspend fun streak(): Int = prefs.currentSprig().streakDays
    private fun kept(): List<Any?> = analytics.events.filter { it.first == "rollover" }.map { it.second["kept"] }

    /** "kept", "shield" or "missed", with the weekly columns: what the day's row came to. */
    private suspend fun day(day: String): String {
        val r = record(day)
        val what = when {
            r.countedForStreak -> "kept"
            r.shieldConsumed -> "shield"
            else -> "missed"
        }
        return "$what ${r.weeklyDone}/${r.weeklyDue}" + if (r.weeklyMissed.isEmpty()) "" else " ${r.weeklyMissed.replace("\n", "+")}"
    }

    // --- both outcomes, and the shield ---------------------------------------------------------

    @Test fun `a must-do day with the habit and the session done in time is kept`() = runBlocking {
        val g = goal()
        read("2026-11-25")
        session(g, "2026-11-25")

        val out = rollOn("2026-11-26")

        assertEquals(1, out.size)
        assertEquals("kept 1/1", day("2026-11-25"))
        assertEquals(6, streak())
        assertTrue(record("2026-11-25").finalized)
        assertEquals(listOf<Any?>(true), kept())
        assertTrue(recaps.single().countedForStreak)
    }

    @Test fun `a must-do day with the habit done and the session skipped is missed and resets the streak`() = runBlocking {
        goal()
        read("2026-11-25")

        rollOn("2026-11-26")

        val r = record("2026-11-25")
        assertEquals("missed 0/1 Exercise", day("2026-11-25"))
        assertEquals("the habit was done; only the session was not", 1 to 1, r.habitsDone to r.habitsTotal)
        assertEquals(0, r.streakEnd)
        assertEquals(0, streak())
        assertEquals("rollover.kept follows the streak", listOf<Any?>(false), kept())
        assertTrue(recaps.single().streakReset)
    }

    @Test fun `the weekly shield covers a skipped must-do day`() = runBlocking {
        settled(lastRollover = "2026-11-24", shield = true)
        goal()
        read("2026-11-25")

        rollOn("2026-11-26")

        assertEquals("shield 0/1 Exercise", day("2026-11-25"))
        assertEquals(5, streak())
        assertFalse(prefs.currentSprig().shieldAvailable)
        assertEquals(listOf<Any?>(false), kept())
    }

    @Test fun `a day that is not a must-do day ignores the goal, and a day with no goal is as before`() = runBlocking {
        // Tuesday rolled, Wednesday with no goal at all.
        read("2026-11-25")
        rollOn("2026-11-26")
        assertEquals("kept 0/0", day("2026-11-25"))
        // A goal of 2 a week, nothing logged: Thursday has 3 days left, so it is free.
        goal(days = 2)
        read("2026-11-26")
        rollOn("2026-11-27")
        assertEquals("kept 0/0", day("2026-11-26"))
        assertEquals(7, streak())
    }

    // --- the day is judged as it was, whenever the rollover runs --------------------------------

    @Test fun `a catch-up over four days and a Sunday judges each day in its own week`() = runBlocking {
        // 2 a week, nothing logged by Thursday. Thursday is closed; then no rollover runs until Tuesday.
        settled(lastRollover = "2026-11-26", shield = false)
        val g = goal(days = 2)
        // Friday: 2 days left, 2 needed. Habit and session done.
        read("2026-11-27"); session(g, "2026-11-27")
        // Saturday: 1 day left, 1 needed. The habit is done, the session is not.
        read("2026-11-28")
        // Sunday: a new week, 7 days for 2. Habit done, no session, nothing due.
        read("2026-11-29")
        // Monday: a session, though none was due.
        read("2026-11-30"); session(g, "2026-11-30")

        // Tuesday morning: this week stands at 1 of 2 with 5 days left, which says nothing about last week.
        val out = rollOn("2026-12-01", "08:00")

        assertEquals(4, out.size)
        assertEquals("kept 1/1", day("2026-11-27"))
        assertEquals("missed 0/1 Exercise", day("2026-11-28"))
        assertEquals("kept 0/0", day("2026-11-29"))
        assertEquals("kept 0/0", day("2026-11-30"))
        assertEquals(listOf(6, 0, 1, 2), listOf("2026-11-27", "2026-11-28", "2026-11-29", "2026-11-30").map { record(it).streakEnd })
        assertEquals(listOf<Any?>(true, false, true, true), kept())
        assertEquals("the recap is about the day the streak was lost", LocalDate.parse("2026-11-28"), recaps.single().date)
        assertEquals(listOf("Exercise"), recaps.single().weeklyMissed)
    }

    @Test fun `days the phone was off are missed with their must-do sessions recorded`() = runBlocking {
        // Example A's week, and the phone off from Wednesday: nothing ticked, nothing logged.
        goal()
        rollOn("2026-11-28", "08:00")
        // Wednesday to Friday each needed a session: 4 in 4 days, then one the week could no longer make up.
        assertEquals(listOf("missed 0/1 Exercise", "missed 0/1 Exercise", "missed 0/1 Exercise"), listOf("2026-11-25", "2026-11-26", "2026-11-27").map { day(it) })
        assertEquals(0, record("2026-11-25").habitsDone)
    }

    @Test fun `running the rollover again changes nothing`() = runBlocking {
        goal()
        read("2026-11-25")
        rollOn("2026-11-26")
        val first = record("2026-11-25")
        val state = prefs.currentSprig()

        // Again at once, and again later the same day after the session is logged a day late.
        assertEquals(emptyList<RolloverEngine.Output>(), runner.runPending())
        db.weeklyGoalEntryDao().insertIfAbsent(WeeklyGoalEntryEntity(goalId = 1, date = "2026-11-26", completedAt = time.nowMillis()))
        assertEquals(emptyList<RolloverEngine.Output>(), rollOn("2026-11-26", "18:00"))

        assertEquals(first, record("2026-11-25"))
        assertEquals(state, prefs.currentSprig())
        assertEquals(1, kept().size)
        assertEquals(1, recaps.size)
    }

    @Test fun `with no daily habit a must-do session alone does not make the day count`() = runBlocking {
        db.habitDao().deactivate(readId)
        val g = goal()
        session(g, "2026-11-25")

        rollOn("2026-11-26")

        val r = record("2026-11-25")
        assertEquals("missed 1/1", day("2026-11-25"))
        assertEquals(0, r.habitsTotal)
        assertEquals(listOf<Any?>(false), kept())
    }

    // --- goals changed between the day and its rollover ----------------------------------------

    @Test fun `the rollover reads the goals as they are when it runs`() = runBlocking {
        // Wednesday's session was skipped on two goals. Before the rollover runs, one is deleted and the other lowered to 2.
        val gone = goal("Exercise", 4)
        val lowered = goal("Read Dune", 4)
        read("2026-11-25")
        goTo("2026-11-26", "00:01")
        db.weeklyGoalDao().deactivate(gone)
        assertEquals(1, db.weeklyGoalDao().updateActive(lowered, "Read Dune", "book", 2))

        rollOn("2026-11-26")

        // Nothing stores what a goal asked on a past day, so neither is held against Wednesday.
        assertEquals("kept 0/0", day("2026-11-25"))
    }

    // --- local dates, not elapsed hours --------------------------------------------------------

    @Test fun `a 23 hour day is still one day with the give-up time at 9 pm local`() = runBlocking {
        // New York, Sunday 8 March 2026: clocks go forward at 2 am. 7 a week, so every day is a must-do day.
        clock.zoneId = ZoneId.of("America/New_York")
        prefs.updateSprig { it.copy(lastRolloverDate = LocalDate.parse("2026-03-07"), shieldWeekKey = "2026-W10", shieldAvailable = false) }
        val g = goal(days = 7, from = "2026-02-01")
        read("2026-03-08"); session(g, "2026-03-08", "20:30")
        read("2026-03-09"); session(g, "2026-03-09", "21:30")

        rollOn("2026-03-10")

        assertEquals("kept 1/1", day("2026-03-08"))
        // Monday starts a new ISO week, so the refilled shield takes the late session's day.
        assertEquals("shield 0/1 Exercise", day("2026-03-09"))
    }

    @Test fun `a session stays on the date it was logged for after a zone change`() = runBlocking {
        // Logged at noon on Wednesday in New York; the rollover runs on Thursday in Los Angeles.
        clock.zoneId = ZoneId.of("America/New_York")
        val g = goal()
        read("2026-11-25", "11:00"); session(g, "2026-11-25", "12:00")
        clock.zoneId = ZoneId.of("America/Los_Angeles")

        rollOn("2026-11-26", "06:00")

        assertEquals("kept 1/1", day("2026-11-25"))
        assertEquals(listOf("2026-11-25"), db.weeklyGoalEntryDao().between("2026-11-01", "2026-12-31").map { it.date })
    }
}
