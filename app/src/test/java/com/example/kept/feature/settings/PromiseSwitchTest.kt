package com.example.kept.feature.settings

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.TestViewModels
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.PromiseRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.db.WeeklyGoalEntryEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.di.drainAndCancel
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseSwitch
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyCard
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.lock.AllowlistResolver
import com.example.kept.core.lock.InstalledAppsSource
import com.example.kept.core.lock.Permissions
import com.example.kept.core.notify.KeptNotifications
import com.example.kept.core.notify.ReminderPoster
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Switching a saved promise between every day and some days a week (#236), with the real
 * [HabitEditScreen], [WeeklyGoalEditScreen] and [PromisesScreen], the real [SettingsViewModel]
 * and [WeeklyGoalsViewModel], over an in-memory Room database.
 *
 * Wednesday 25 November 2026, 9:00 am, inside the 7:00 am to 9:00 pm lock window. Your promises
 * holds, in this order: Read (10 pages, every day, set 1 November), Exercise (30 min, 4 days a
 * week) and Drink water (8 glasses, every day). Read was done Monday and Tuesday; nothing is done
 * today, so the daily lock holds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class PromiseSwitchTest {

    @get:Rule val rule = createComposeRule()

    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    private object NoAnalytics : Analytics {
        override val enabled = false
        override fun capture(event: String, properties: Map<String, Any>) = Unit
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
    private val sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time)
    private val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)
    private val lock = LockRepository(db.allowedAppDao(), db.lockBreakDao(), db.protectionGapDao(), db.dayRecordDao(), prefs, habits, sprig, time, weekly)
    private val promises = PromiseRepository(db.promiseDao(), habits, time)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val viewModels = TestViewModels()

    private val settings by lazy {
        viewModels.create {
            SettingsViewModel(
                ctx, prefs, habits, lock, sprig, DayRepository(db.dayRecordDao(), time), InstalledAppsSource(ctx), AllowlistResolver(ctx),
                WorkScheduler(ctx, prefs, time), ReminderPoster(habits, prefs, KeptNotifications(ctx), time), time, Permissions(ctx), NoAnalytics,
                promises, scope,
            )
        }
    }
    private val goals by lazy { viewModels.create { WeeklyGoalsViewModel(weekly, sprig, scope) } }

    private var readId = 0L
    private var exerciseId = 0L
    private var closed by mutableStateOf(0)

    /** A closed day in History, before any switch. */
    private val monday = DayRecordEntity(date = "2026-11-23", habitsDone = 2, habitsTotal = 2, countedForStreak = true, streakEnd = 5, finalized = true)

    @Before fun setUp() {
        runBlocking {
            prefs.clearAll()
            prefs.updateSettings { it.copy(onboardingDone = true) }
            val nov1 = Instant.parse("2026-11-01T08:00:00Z").toEpochMilli()
            readId = db.habitDao().insert(HabitEntity(title = "Read", iconKey = "book", proofType = ProofType.MANUAL, targetValue = 10, unit = "pages", sortOrder = 0, createdAt = nov1))
            exerciseId = db.weeklyGoalDao().insert(
                WeeklyGoalEntity(title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = nov1, effectiveFrom = "2026-11-01", sortOrder = 1, targetValue = 30, unit = "min"),
            )
            db.habitDao().insert(HabitEntity(title = "Drink water", iconKey = "water", proofType = ProofType.MANUAL, targetValue = 8, unit = "glasses", sortOrder = 2, createdAt = nov1))
            for ((date, at) in listOf("2026-11-23" to "2026-11-23T18:00:00Z", "2026-11-24" to "2026-11-24T19:00:00Z")) {
                db.habitEntryDao().upsert(HabitEntryEntity(habitId = readId, date = date, progressValue = 10, completedAt = Instant.parse(at).toEpochMilli()))
            }
            db.dayRecordDao().upsert(monday)
        }
    }

    @After fun close() {
        viewModels.clear()
        scope.drainAndCancel()
        db.close()
    }

    private fun await(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            assertTrue("timed out waiting for $what", System.currentTimeMillis() < end)
            Thread.sleep(10)
        }
    }

    private fun habitTitles() = runBlocking { habits.observeHabits().first() }.map { it.title }
    private fun goalTitles() = runBlocking { weekly.observeGoals().first() }.map { it.title }
    private fun nb(s: String) = s.replace(WeeklyGoalCopy.NBSP, ' ')

    /** The edit page for habit [id] (or goal [goal]), loaded, with the lock hold already read. */
    private fun show(id: Long, goal: Boolean = false, held: Boolean = true) {
        rule.setContent {
            Shot {
                val hold by settings.lockHold.collectAsStateWithLifecycle()
                hold.daily
                if (closed == 0) {
                    if (goal) WeeklyGoalEditScreen(id, onClose = { closed++ }, vm = goals, settings = settings)
                    else HabitEditScreen(id, onClose = { closed++ }, vm = settings, goals = goals)
                }
            }
        }
        await("the promise") { rule.onAllNodes(hasTestTag("promise_done")).fetchSemanticsNodes().isNotEmpty() }
        await("the lock hold") { settings.lockHold.value.daily == held }
    }

    private fun pickDays(n: Int) {
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_$n").performClick()
    }

    private fun done() = rule.onNodeWithTag("promise_done").performClick()

    /** Waits for the lock-change confirm: the switch reads the habit's week before it asks. */
    private fun awaitConfirm() = await("the confirm") { rule.onAllNodes(hasTestTag("change_anyway")).fetchSemanticsNodes().isNotEmpty() }

    // --- every day → weekly ----------------------------------------------------------------------

    @Test fun every_day_to_4_days_a_week_on_wednesday_carries_monday_and_tuesday_so_home_shows_2_of_4() {
        show(readId)
        pickDays(4)
        done()
        // Read is not done today and 2 of 4 leaves today free, so the switch would open apps: it asks.
        awaitConfirm()
        rule.onNodeWithText(PromiseSwitch.TO_WEEKLY_WHILE_LOCKED).assertExists()
        rule.onNodeWithTag("change_anyway").performClick()
        await("the switch") { "Read" in goalTitles() }
        await("the page to close") { closed == 1 }

        assertEquals(listOf("Drink water"), habitTitles())
        val read = runBlocking { weekly.thisWeek() }.single { it.goal.title == "Read" }
        assertEquals(4, read.goal.daysPerWeek)
        assertEquals("10 pages a session, as the habit had", 10 to "pages", read.goal.targetValue to read.goal.unit)
        assertEquals("book", read.goal.iconKey)
        assertEquals(2, read.week.done)
        assertEquals(4, read.week.target)
        assertFalse("2 of 4 with 4 days left: today is free", read.week.isMustDay)
        val card = WeeklyCard.of(read.goal.title, read.week, goalTarget = WeeklyGoalCopy.target(read.goal.targetValue, read.goal.unit))
        assertTrue(nb(card.title), nb(card.title).endsWith("2 of 4 this week"))
    }

    @Test fun cancel_on_the_lock_confirm_changes_nothing_and_keeps_the_page() {
        show(readId)
        pickDays(3)
        done()
        awaitConfirm()
        rule.onNodeWithText("Apps are locked right now").assertExists()
        rule.onNodeWithTag("change_cancel").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        assertEquals(listOf("Read", "Drink water"), habitTitles())
        assertEquals(listOf("Exercise"), goalTitles())
        assertEquals(0, closed)
        rule.onNodeWithTag("promise_done").assertIsEnabled()
    }

    @Test fun a_switch_that_keeps_the_lock_asks_nothing_and_todays_tick_is_todays_session() {
        runBlocking { habits.markDone(readId) }
        show(readId)
        pickDays(2)
        done()
        await("the switch") { "Read" in goalTitles() }
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        await("the page to close") { closed == 1 }
        val read = runBlocking { weekly.thisWeek() }.single { it.goal.title == "Read" }
        assertTrue(read.week.doneToday)
        assertEquals("Monday, Tuesday and today", 3, read.week.done)
    }

    // --- weekly → every day ----------------------------------------------------------------------

    @Test fun weekly_to_every_day_with_todays_session_logged_is_done_today_and_asks_nothing() {
        runBlocking { weekly.logToday(exerciseId) }
        show(exerciseId, goal = true)
        pickDays(0)
        done()
        await("the switch") { "Exercise" in habitTitles() }
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        await("the page to close") { closed == 1 }
        assertEquals(listOf<String>(), goalTitles())
        val exercise = runBlocking { habits.today() }.habits.single { it.habit.title == "Exercise" }
        assertTrue("today's session is today done", exercise.isDone)
        assertEquals(30 to "min", exercise.habit.targetValue to exercise.habit.unit)
        assertEquals("run", exercise.habit.iconKey)
    }

    @Test fun weekly_to_every_day_with_no_session_today_is_not_done_yet() {
        show(exerciseId, goal = true)
        pickDays(0)
        done()
        await("the switch") { "Exercise" in habitTitles() }
        val exercise = runBlocking { habits.today() }.habits.single { it.habit.title == "Exercise" }
        assertFalse(exercise.isDone)
    }

    // --- place, history, limits ------------------------------------------------------------------

    @Test fun a_switched_promise_keeps_its_place_in_the_list_and_its_past_stays() {
        fun order(): List<String> = runBlocking {
            com.example.kept.core.domain.PromiseList.ordered(habits.observeHabits().first().map { it.promise() } + weekly.observeGoals().first().map { it.promise() })
        }.map { "${it.title} ${it.chip.let(::nb)}" }
        assertEquals(listOf("Read Every day", "Exercise 4 days a week", "Drink water Every day"), order())

        runBlocking {
            promises.toDaily(exerciseId, PromiseDraft("Exercise", 30, "min", days = null))
            promises.toWeekly(readId, PromiseDraft("Read", 10, "pages", days = 3))
        }
        assertEquals(listOf("Read 3 days a week", "Exercise Every day", "Drink water Every day"), order())

        // The page shows the same order, top to bottom.
        rule.setContent { Shot { PromisesScreen({}, {}, {}, {}, vm = settings, goals = goals) } }
        await("the list") { rule.onAllNodes(hasTestTag("promises_count")).fetchSemanticsNodes().isNotEmpty() }
        val tops = listOf("Read", "Exercise", "Drink water").map { t ->
            rule.onNodeWithText(t, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(tops.sorted(), tops)
        rule.onNodeWithTag("promises_count").assertTextEquals("2 daily habits · 1 weekly goal")

        // Nothing is deleted: the old rows are inactive with their days, and History's days are as they were.
        assertFalse(runBlocking { db.habitDao().byId(readId) }!!.isActive)
        assertEquals(2, runBlocking { db.habitEntryDao().between("2026-11-01", "2026-11-30") }.count { it.habitId == readId })
        assertFalse(runBlocking { db.weeklyGoalDao().byId(exerciseId) }!!.isActive)
        assertEquals(listOf(monday), runBlocking { db.dayRecordDao().between("2026-11-01", "2026-11-24") })
    }

    @Test fun at_three_weekly_goals_a_habit_cannot_become_weekly_and_says_why() {
        runBlocking { listOf("Study", "Practice").forEach { weekly.addGoal(it, 2, 20, "min") } }
        show(readId)
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_3").assertIsNotEnabled()
        rule.onNodeWithTag("promise_option_days_0").assertIsEnabled()
        rule.onNodeWithTag("promise_limit").assertTextEquals(WeeklyGoalCopy.LIMIT_REACHED)
    }

    @Test fun at_ten_habits_a_goal_cannot_become_every_day() {
        runBlocking { (1..8).forEach { habits.addHabit("Habit $it", "star", ProofType.MANUAL, 1, "times") } }
        show(exerciseId, goal = true)
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_0").assertIsNotEnabled()
        rule.onNodeWithTag("promise_option_days_4").assertIsEnabled()
        rule.onNodeWithTag("promise_limit").assertTextEquals(HabitLimits.LIMIT_REACHED)
    }

    @Test fun at_ten_habits_add_cannot_be_every_day_either() {
        runBlocking { (1..8).forEach { habits.addHabit("Habit $it", "star", ProofType.MANUAL, 1, "times") } }
        rule.setContent { Shot { WeeklyGoalEditScreen(null, onClose = {}, vm = goals, settings = settings) } }
        await("add") { rule.onAllNodes(hasTestTag("promise_done")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_0").assertIsNotEnabled()
        rule.onNodeWithTag("promise_limit").assertTextEquals(HabitLimits.LIMIT_REACHED)
    }

    @Test fun the_last_every_day_habit_cannot_become_weekly() {
        runBlocking { db.habitDao().deactivate(db.habitDao().active().single { it.title == "Drink water" }.id) }
        show(readId)
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_3").assertIsNotEnabled()
        rule.onNodeWithTag("promise_limit").assertTextEquals("Keep at least one habit every day.")
    }

    @Test fun a_switch_that_finds_no_room_changes_nothing_at_all() = runBlocking {
        // Two Add pages, or Home and Settings, can fill the goals after the promise opened.
        listOf("Study", "Practice").forEach { weekly.addGoal(it, 2, 20, "min") }
        assertNull(promises.toWeekly(readId, PromiseDraft("Read", 10, "pages", days = 3)))
        assertEquals(listOf("Read", "Drink water"), habitTitles())
        assertEquals(listOf("Exercise", "Study", "Practice"), goalTitles())
        assertEquals(emptyList<WeeklyGoalEntryEntity>(), db.weeklyGoalEntryDao().between("2026-11-22", "2026-11-28"))
    }
}
