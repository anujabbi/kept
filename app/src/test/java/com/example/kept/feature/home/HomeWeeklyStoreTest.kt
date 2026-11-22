package com.example.kept.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.analytics.NoOpAnalytics
import com.example.kept.core.data.DayCompletionReactor
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntryEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyCardCopy
import com.example.kept.core.lock.Permissions
import com.example.kept.core.ui.shots.Shot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Home's weekly card against what is stored (story #174): the real [HomeViewModel] and
 * repositories on an in-memory Room database, with a clock the test moves from day to day. The
 * card is read off the screen and `weekly_goal_entries` off the database after each action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HomeWeeklyStoreTest {

    @get:Rule val rule = createComposeRule()

    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    // Saturday 21 November 2026, the day the goal is set.
    private val clock = TestClock(Instant.parse("2026-11-21T12:00:00Z"))
    private val time = TimeSource(clock)
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = KeptPreferences(ctx)
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
    private val sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time)
    private val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)

    private fun newViewModel() = HomeViewModel(
        habits = habits,
        actions = HabitActions(habits, sprig, prefs, time, NoOpAnalytics(), object : DayCompletionReactor { override suspend fun onDayCompleted() = Unit }),
        sprigRepo = sprig,
        lockRepo = LockRepository(db.allowedAppDao(), db.lockBreakDao(), db.protectionGapDao(), db.dayRecordDao(), prefs, habits, sprig, time),
        dayRepo = DayRepository(db.dayRecordDao(), time),
        prefs = prefs,
        permissions = Permissions(ctx),
        time = time,
        weeklyGoals = weekly,
    )

    /** Home follows whichever ViewModel is current; a new day gets a new one, as a new launch would. */
    private val vm = mutableStateOf<HomeViewModel?>(null)

    @Before fun reset() = runBlocking { prefs.clearAll() }

    @After fun closeDb() = db.close()

    private fun showHome() = rule.setContent {
        Shot {
            val model = vm.value ?: return@Shot
            val s by model.state.collectAsStateWithLifecycle()
            if (s.loaded) {
                // Which day's Home is on screen, for goTo.
                Box(Modifier.testTag("day_${s.date}"))
                HomeFrame(s, HomeActions(onComplete = { model.complete(it) }, onWeeklyDone = { model.logWeekly(it) }, onWeeklyUndo = { model.undoWeekly(it) }))
            }
        }
    }

    private fun goTo(day: String) {
        clock.now = Instant.parse("${day}T12:00:00Z")
        vm.value = newViewModel()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("day_$day")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun entries(): List<WeeklyGoalEntryEntity> = runBlocking { db.weeklyGoalEntryDao().between("2026-01-01", "2027-12-31") }
    private fun dates(): List<String> = entries().map { it.date }.sorted()

    private fun title(id: Long): String =
        rule.onNodeWithTag("home_weekly_title_$id", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString { it.text }.replace(WeeklyCardCopy.NBSP, ' ')

    private fun strip(id: Long): List<String> =
        rule.onNodeWithTag("home_weekly_strip_$id", useUnmergedTree = true).fetchSemanticsNode().children
            .map { it.config[SemanticsProperties.ContentDescription].single() }

    private fun waitForTitle(id: Long, expected: String) {
        rule.waitUntil(10_000) { runCatching { title(id) }.getOrNull() == expected }
    }

    @Test fun a_week_of_exercise_from_home_stores_one_session_a_day() {
        val goal = runBlocking {
            habits.addHabit("Read", "book", ProofType.MANUAL, 10, "pages")
            habits.addHabit("Study", "book", ProofType.MANUAL, 45, "min")
            weekly.addGoal("Exercise", 4)!!
        }
        showHome()

        // Sunday: a new week, nothing logged.
        goTo("2026-11-22")
        assertEquals("Exercise · 0 of 4 this week", title(goal))
        rule.onNodeWithText("On track, no lock").assertExists()

        // Two taps before the first lands store one session.
        vm.value!!.logWeekly(goal)
        vm.value!!.logWeekly(goal)
        waitForTitle(goal, "Exercise · 1 of 4 this week")
        assertEquals(listOf("2026-11-22"), dates())
        assertEquals("Sunday, today, done", strip(goal)[0])

        // Example B: Monday, one session on Sunday.
        goTo("2026-11-23")
        assertEquals("Exercise · 1 of 4 this week", title(goal))
        assertEquals(listOf("Sunday, done", "Monday, today, not done yet", "Tuesday, coming up"), strip(goal).take(3))
        rule.onNodeWithText("On track, no lock").assertExists()

        // Mark Monday done from the card, then tap the done row again, twice.
        val row = rule.onNodeWithTag("home_weekly_today_$goal").performScrollTo()
        row.performClick()
        waitForTitle(goal, "Exercise · 2 of 4 this week")
        assertEquals("Monday, today, done", strip(goal)[1])
        row.performClick()
        row.performClick()
        vm.value!!.logWeekly(goal)
        rule.waitForIdle()
        assertEquals(listOf("2026-11-22", "2026-11-23"), dates())
        assertEquals("Exercise · 2 of 4 this week", title(goal))

        // Undo takes Monday's session away and nothing else.
        row.performTouchInput { longClick() }
        waitForTitle(goal, "Exercise · 1 of 4 this week")
        assertEquals(listOf("2026-11-22"), dates())
        assertEquals("Monday, today, not done yet", strip(goal)[1])

        // The habits and their count never moved.
        rule.onNodeWithText("0 of 2").assertExists()
        assertEquals(2, vm.value!!.state.value.today.total)
        assertEquals(0, vm.value!!.state.value.today.done)
        assertEquals(2, runBlocking { habits.observeToday().first() }.total)

        // The next Sunday: 0 of 4, an empty strip; last week's session is still stored.
        goTo("2026-11-29")
        assertEquals("Exercise · 0 of 4 this week", title(goal))
        assertEquals(listOf("Sunday, today, not done yet") + listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").map { "$it, coming up" }, strip(goal))
        assertEquals(listOf("2026-11-22"), dates())
    }

    @Test fun example_A_nothing_logged_by_wednesday_says_today_counts_and_logging_clears_it() {
        val goal = runBlocking { weekly.addGoal("Exercise", 4)!! }
        showHome()
        goTo("2026-11-25")
        assertEquals("Exercise · 0 of 4 this week", title(goal))
        assertEquals(
            listOf("Sunday, skipped", "Monday, skipped", "Tuesday, skipped", "Wednesday, today, not done yet", "Thursday, coming up"),
            strip(goal).take(5),
        )
        rule.onNodeWithText("Today counts: apps lock until it's done").assertExists()

        rule.onNodeWithTag("home_weekly_today_$goal").performClick()
        waitForTitle(goal, "Exercise · 1 of 4 this week")
        rule.onNodeWithText("On track, no lock").assertExists()
        assertEquals(listOf("2026-11-25"), dates())

        rule.onNodeWithTag("home_weekly_today_$goal").performTouchInput { longClick() }
        waitForTitle(goal, "Exercise · 0 of 4 this week")
        rule.onNodeWithText("Today counts: apps lock until it's done").assertExists()
        assertEquals(emptyList<String>(), dates())
    }

    @Test fun three_goals_keep_their_sessions_apart() {
        val ids = runBlocking { listOf(weekly.addGoal("Exercise", 4)!!, weekly.addGoal("Read", 2)!!, weekly.addGoal("Practice", 7)!!) }
        showHome()
        goTo("2026-11-24")
        ids.forEach { rule.onNodeWithTag("home_weekly_card_$it").assertExists() }
        vm.value!!.logWeekly(ids[1])
        waitForTitle(ids[1], "Read · 1 of 2 this week")
        assertEquals("Exercise · 0 of 4 this week", title(ids[0]))
        assertEquals("Practice · 0 of 7 this week", title(ids[2]))
        assertEquals(listOf(ids[1]), entries().map { it.goalId })
    }
}
