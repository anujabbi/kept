package com.example.kept.feature.settings

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.TestViewModels
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.di.drainAndCancel
import com.example.kept.core.domain.ProofType
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
 * Settings → add or edit a habit and add or edit a weekly goal, on the promise (#218), with the
 * real [SettingsViewModel] and [WeeklyGoalsViewModel] over an in-memory Room database: "every
 * day" saves a daily habit and a day count a weekly goal, from either list, with the amount and
 * unit chosen; an edit opens filled in and keeps the habit's history. Wednesday 25 November 2026.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class PromiseSettingsTest {

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val viewModels = TestViewModels()

    private val settings by lazy {
        viewModels.create {
            SettingsViewModel(
                ctx, prefs, habits, lock, sprig, DayRepository(db.dayRecordDao(), time), InstalledAppsSource(ctx), AllowlistResolver(ctx),
                WorkScheduler(ctx, prefs, time), ReminderPoster(habits, prefs, KeptNotifications(ctx), time), time, Permissions(ctx), NoAnalytics, scope,
            )
        }
    }
    private val goals by lazy { viewModels.create { WeeklyGoalsViewModel(weekly, sprig, scope) } }

    private var readId = 0L
    private var closed by mutableStateOf(0)

    @Before fun setUp() {
        runBlocking {
            prefs.clearAll()
            prefs.updateSettings { it.copy(onboardingDone = true) }
            readId = habits.addHabit("Read", "book", ProofType.MANUAL, 10, "pages")
            habits.markDone(readId)
            db.weeklyGoalDao().insert(WeeklyGoalEntity(title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 0, effectiveFrom = "2026-11-01", targetValue = 30, unit = "min"))
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
            rule.waitForIdle()
            assertTrue("timed out waiting for $what", System.currentTimeMillis() < end)
            Thread.sleep(10)
        }
    }

    private fun habitRows() = runBlocking { habits.observeHabits().first() }.map { Triple(it.title, it.targetValue, it.unit) }
    private fun goalRows() = runBlocking { weekly.observeGoals().first() }.map { WeeklyGoalCopy.listRow(it.title, WeeklyGoalCopy.target(it.targetValue, it.unit), it.daysPerWeek).replace(WeeklyGoalCopy.NBSP, ' ') }

    private fun showHabit(id: Long?) {
        rule.setContent { Shot { if (closed == 0) HabitEditScreen(id, onClose = { closed++ }, vm = settings, goals = goals) } }
        await("the promise") { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("promise_done")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun showGoal(id: Long?) {
        rule.setContent { Shot { if (closed == 0) WeeklyGoalEditScreen(id, onClose = { closed++ }, vm = goals, settings = settings) } }
        await("the promise") { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("promise_done")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pick(blank: String, option: String) {
        rule.onNodeWithTag("promise_blank_$blank").performClick()
        rule.onNodeWithTag("promise_option_$option").performClick()
    }

    private fun done() {
        rule.onNodeWithTag("promise_done").performClick()
        rule.mainClock.advanceTimeBy(PROMISE_CHEER_MS + 100)
        await("the page to close") { closed == 1 }
    }

    @Test fun add_a_habit_every_day_saves_a_daily_habit_with_the_amount_and_unit_chosen() {
        showHabit(null)
        // Read and Exercise are kept already, so a new promise starts on Practice, every day.
        assertEquals("I will practice for 20 minutes, every day.", promiseSentence(rule))
        pick("unit", "times")
        pick("amount", "3")
        done()
        await("the habit") { habitRows().size == 2 }
        assertEquals(listOf(Triple("Read", 10, "pages"), Triple("Practice", 3, "times")), habitRows())
        assertEquals(listOf("Exercise · 30 min · 4 days a week"), goalRows())
    }

    @Test fun add_a_habit_with_a_day_count_saves_a_weekly_goal_instead() {
        showHabit(null)
        pick("days", "days_2")
        pick("amount", "45")
        done()
        await("the goal") { goalRows().size == 2 }
        assertEquals(listOf("Exercise · 30 min · 4 days a week", "Practice · 45 min · 2 days a week"), goalRows())
        assertEquals(1, habitRows().size)
    }

    @Test fun add_a_weekly_goal_starts_at_3_days_and_every_day_saves_a_daily_habit() {
        showGoal(null)
        assertEquals("I will practice for 20 minutes, 3 days a week.", promiseSentence(rule))
        pick("days", "days_0")
        done()
        await("the habit") { habitRows().size == 2 }
        assertEquals("Practice", habitRows()[1].first)
        assertEquals(1, goalRows().size)
    }

    @Test fun add_a_weekly_goal_saves_it_with_its_amount_and_unit() {
        showGoal(null)
        pick("activity", "Read")
        pick("amount", "20")
        pick("days", "days_5")
        done()
        await("the goal") { goalRows().size == 2 }
        assertEquals("Read · 20 pages · 5 days a week", goalRows()[1])
    }

    @Test fun at_three_weekly_goals_add_a_habit_keeps_the_weekly_choices_off() {
        runBlocking { listOf("Study", "Practice").forEach { weekly.addGoal(it, 2, 20, "min") } }
        showHabit(null)
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_3").assertIsNotEnabled()
        rule.onNodeWithTag("promise_limit").assertTextEquals(WeeklyGoalCopy.LIMIT_REACHED)
    }

    @Test fun editing_a_habit_opens_it_filled_in_and_saving_keeps_its_history() {
        showHabit(readId)
        assertEquals("I will read 10 pages, every day.", promiseSentence(rule))
        pick("amount", "20")
        done()
        await("the save") { habitRows().single().second == 20 }
        val read = runBlocking { habits.observeHabits().first() }.single()
        assertEquals(readId, read.id)
        assertEquals("today's tick is still there", 1, runBlocking { db.habitEntryDao().forDate("2026-11-25") }.count { it.habitId == readId })
    }

    @Test fun editing_a_goal_opens_it_filled_in_and_saving_keeps_its_id() {
        val id = runBlocking { weekly.observeGoals().first() }.single().id
        showGoal(id)
        assertEquals("I will exercise for 30 minutes, 4 days a week.", promiseSentence(rule))
        pick("amount", "60")
        done()
        await("the save") { goalRows().single() == "Exercise · 60 min · 4 days a week" }
        assertEquals(id, runBlocking { weekly.observeGoals().first() }.single().id)
    }

    // Delete goal and its confirm (issue #165), on the promise. The goal's session is logged
    // first, so it holds no lock and only the usual question is asked.

    private fun showExercise(): Long {
        val id = runBlocking { weekly.observeGoals().first() }.single().id
        runBlocking { weekly.logToday(id) }
        showGoal(id)
        return id
    }

    private fun active(id: Long) = runBlocking { weekly.observeGoals().first() }.any { it.id == id }

    private fun confirmText() = rule.onNodeWithTag("goal_delete_message").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }.replace(WeeklyGoalCopy.NBSP, ' ')

    @Test fun delete_asks_first_and_cancel_keeps_the_goal() {
        val id = showExercise()
        rule.onNodeWithTag("goal_delete_confirm").assertDoesNotExist()
        rule.onNodeWithTag("promise_remove").assertTextEquals("Delete goal").performClick()
        rule.onNodeWithText("Delete this weekly goal?").assertExists()
        assertEquals("Exercise, 4 days a week will be removed from your weekly goals.", confirmText())
        assertTrue("nothing is deleted before the answer", active(id))
        rule.onNodeWithTag("goal_delete_cancel").performClick()
        rule.onNodeWithTag("goal_delete_confirm").assertDoesNotExist()
        assertTrue(active(id))
        assertEquals(0, closed)
        // Still on the page, and it still works.
        rule.onNodeWithTag("promise_done").assertIsEnabled()
    }

    @Test fun confirming_delete_deletes_once() {
        val id = showExercise()
        rule.onNodeWithTag("promise_remove").performClick()
        rule.onNodeWithTag("goal_delete_confirm").performClick()
        await("the delete") { !active(id) && closed == 1 }
        rule.onNodeWithTag("goal_delete_confirm").assertDoesNotExist()
        assertEquals(emptyList<String>(), goalRows())
        assertEquals("the page closed once", 1, closed)
    }

    @Test fun the_confirm_names_the_saved_goal_not_the_unsaved_edit() {
        showExercise()
        pick("activity", "Study")
        pick("days", "days_6")
        rule.onNodeWithTag("promise_remove").performClick()
        assertEquals("Exercise, 4 days a week will be removed from your weekly goals.", confirmText())
    }
}
