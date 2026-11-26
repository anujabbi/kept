package com.example.kept.feature.settings

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.di.drainAndCancel
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseLimits
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.domain.WeeklyGoalLockEdit
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
 * Lowering or deleting a weekly goal that holds the lock goes through the lock-change confirm
 * (issue #165, Edge cases; story #175): the real [WeeklyGoalEditScreen] with the real
 * [SettingsViewModel] and [WeeklyGoalsViewModel] on an in-memory Room database. Wednesday 25
 * November 2026, 9:00 am, inside the 7:00 am to 9:00 pm window; the one daily habit is ticked,
 * so "Exercise, 4 days a week" with nothing logged is what holds the lock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class WeeklyGoalLockConfirmTest {

    @get:Rule val rule = createComposeRule()

    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    private class Recorder : Analytics {
        val events = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()
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
    private val sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time)
    private val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)
    private val lock = LockRepository(db.allowedAppDao(), db.lockBreakDao(), db.protectionGapDao(), db.dayRecordDao(), prefs, habits, sprig, time, weekly)
    private val analytics = Recorder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val viewModels = TestViewModels()

    private val settings by lazy {
        viewModels.create {
            SettingsViewModel(
                ctx, prefs, habits, lock, sprig, DayRepository(db.dayRecordDao(), time), InstalledAppsSource(ctx), AllowlistResolver(ctx),
                WorkScheduler(ctx, prefs, time), ReminderPoster(habits, prefs, KeptNotifications(ctx), time), time, Permissions(ctx), analytics, scope,
            )
        }
    }
    private val goals by lazy { viewModels.create { WeeklyGoalsViewModel(weekly, sprig, scope) } }

    private var goalId = 0L
    private var closed by mutableStateOf(0)

    @Before fun setUp() {
        runBlocking {
            prefs.clearAll()
            prefs.updateSettings { it.copy(onboardingDone = true) }
            habits.markDone(habits.addHabit("Read", "book", ProofType.MANUAL, 1, ""))
            goalId = db.weeklyGoalDao().insert(WeeklyGoalEntity(title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 0, effectiveFrom = "2026-11-01"))
        }
    }

    /** Nothing of this test may still be running, or reading its database, when the next one starts (issue #146). */
    @After fun close() {
        viewModels.clear()
        scope.drainAndCancel()
        db.close()
    }

    private fun locked(): Boolean = runBlocking {
        LockPolicy.shouldLock("com.android.chrome", time.now(), time.localTime(), lock.currentState().snapshot(emptySet(), null))
    }
    private fun days(): Int = runBlocking { db.weeklyGoalDao().byId(goalId)!!.daysPerWeek }
    private fun active(): Boolean = runBlocking { weekly.observeGoals().first().any { it.id == goalId } }
    private fun lockEvents() = analytics.events.filter { it.first == "settings_changed_during_lock" }.map { it.second["setting"] }

    private fun await(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            // Sprig's cheer after a save runs on the compose clock before the page closes.
            rule.mainClock.advanceTimeBy(100)
            rule.waitForIdle()
            assertTrue("timed out waiting for $what", System.currentTimeMillis() < end)
            Thread.sleep(10)
        }
    }

    /** The edit page for the goal, up and loaded, with the lock hold it will be judged by already read. */
    private fun showEdit(expectHeld: Boolean) {
        rule.setContent {
            Shot {
                val hold by settings.lockHold.collectAsStateWithLifecycle()
                // Read so the test can wait for it.
                hold.daily
                if (closed == 0) WeeklyGoalEditScreen(goalId, onClose = { closed++ }, vm = goals, settings = settings)
            }
        }
        await("the edit page") { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("promise_done")).fetchSemanticsNodes().isNotEmpty() }
        await("the lock hold") { settings.lockHold.value.weeklyGoalHolds(goalId) == expectHeld }
    }

    /** Opens the days list and picks [n] a week, as a tap on "4 days a week" and then on the choice. */
    private fun pickDays(n: Int) {
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_$n").performClick()
    }

    private fun done() = rule.onNodeWithTag("promise_done").performClick()

    @Test fun `4 to 2 while the goal holds the lock asks first, and cancel keeps the target and the lock`() {
        assertTrue(locked())
        showEdit(expectHeld = true)
        pickDays(2)
        done()
        rule.onNodeWithText("Apps are locked right now").assertExists()
        rule.onNodeWithText(WeeklyGoalLockEdit.LOWER_WHILE_LOCKED).assertExists()
        assertEquals("nothing is saved before the answer", 4, days())
        rule.onNodeWithTag("change_cancel").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        assertEquals(4, days())
        assertTrue(locked())
        assertEquals("the page stays open and usable", 0, closed)
        assertTrue(lockEvents().isEmpty())
        // Asked again, and this time confirmed.
        done()
        rule.onNodeWithText(WeeklyGoalLockEdit.LOWER_WHILE_LOCKED).assertExists()
        rule.onNodeWithTag("change_anyway").performClick()
        await("the save") { days() == 2 }
        assertFalse("needs 2 with 4 days left: the lock lifts", locked())
        await("the page to close") { closed == 1 }
        assertEquals(listOf<Any?>("weekly_goal"), lockEvents())
    }

    @Test fun `7 to 5 asks too, and confirming keeps the lock because 5 are still needed in 4 days`() {
        runBlocking { weekly.updateGoal(goalId, "Exercise", 7, null, null) }
        showEdit(expectHeld = true)
        pickDays(5)
        done()
        rule.onNodeWithText(WeeklyGoalLockEdit.LOWER_WHILE_LOCKED).assertExists()
        rule.onNodeWithTag("change_anyway").performClick()
        await("the save") { days() == 5 }
        assertTrue("needed is not below remaining, so the lock stays", locked())
    }

    @Test fun `deleting the goal while it holds the lock asks the same way, once`() {
        showEdit(expectHeld = true)
        rule.onNodeWithTag("promise_remove").performClick()
        rule.onNodeWithText("Apps are locked right now").assertExists()
        rule.onNodeWithText(WeeklyGoalLockEdit.DELETE_WHILE_LOCKED).assertExists()
        rule.onNodeWithText("Delete this weekly goal?").assertDoesNotExist()
        rule.onNodeWithTag("change_cancel").performClick()
        rule.waitForIdle()
        assertTrue(active())
        assertTrue(locked())
        assertEquals(0, closed)
        rule.onNodeWithTag("promise_remove").performClick()
        rule.onNodeWithTag("change_anyway").performClick()
        await("the delete") { !active() }
        assertFalse(locked())
        await("the page to close") { closed == 1 }
        assertEquals(listOf<Any?>("weekly_goal"), lockEvents())
    }

    @Test fun `raising the target or renaming the goal asks nothing even while it holds the lock`() {
        showEdit(expectHeld = true)
        pickDays(6)
        done()
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        await("the save") { days() == 6 }
        await("the page to close") { closed == 1 }
        assertTrue(locked())
        assertTrue(lockEvents().isEmpty())
    }

    @Test fun `raising a target so today becomes must-do locks with no confirm`() {
        runBlocking { weekly.updateGoal(goalId, "Exercise", 2, null, null) }
        assertFalse(locked())
        showEdit(expectHeld = false)
        pickDays(4)
        done()
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        await("the save") { days() == 4 }
        assertTrue("the lock is on from the next tick", locked())
    }

    @Test fun `lowering or deleting a goal that does not hold the lock asks only the usual delete question`() {
        // Its session is logged: today is no longer its must-do day.
        runBlocking { weekly.logToday(goalId) }
        assertFalse(locked())
        showEdit(expectHeld = false)
        rule.onNodeWithTag("promise_remove").performClick()
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        rule.onNodeWithText("Delete this weekly goal?").assertExists()
        rule.onNodeWithTag("goal_delete_cancel").performClick()
        pickDays(2)
        done()
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        await("the save") { days() == 2 }
        assertTrue(lockEvents().isEmpty())
    }

    @Test fun `after the give-up time the goal holds nothing, so lowering it asks nothing`() {
        clock.now = Instant.parse("2026-11-25T21:30:00Z")
        assertFalse(locked())
        showEdit(expectHeld = false)
        pickDays(2)
        done()
        rule.onNodeWithText("Apps are locked right now").assertDoesNotExist()
        await("the save") { days() == 2 }
    }

    @Test fun `a goal removed elsewhere while its edit page is open closes the page and a late save changes nothing`() {
        showEdit(expectHeld = true)
        runBlocking { weekly.removeGoal(goalId) }
        await("the page to close") { closed == 1 }
        assertFalse(runBlocking { weekly.updateGoal(goalId, "Exercise", 2, null, null) })
        assertEquals(4, days())
        assertNull(settings.pendingLockChange.value)
    }

    @Test fun `the page's own rule - gone only once the goal was seen and is no longer there`() {
        assertTrue(WeeklyGoalEditGone.of(loaded = true, goalId = 1, present = false, seenBefore = true))
        assertFalse("still loading", WeeklyGoalEditGone.of(loaded = false, goalId = 1, present = false, seenBefore = false))
        assertFalse("not yet read", WeeklyGoalEditGone.of(loaded = true, goalId = 1, present = false, seenBefore = false))
        assertFalse(WeeklyGoalEditGone.of(loaded = true, goalId = 1, present = true, seenBefore = true))
        assertFalse("a new goal's page", WeeklyGoalEditGone.of(loaded = true, goalId = null, present = false, seenBefore = false))
    }

    @Test fun `a held save does not latch the page - the draft passed on is the one on screen`() {
        val asked = mutableListOf<PromiseDraft>()
        var closedPage = 0
        rule.setContent {
            Shot { PromisePage(PromiseRules.ofGoal("Exercise", 4, 30, "min"), PromiseLimits.SAVED_GOAL, onDone = { d, _ -> asked += d }, onClose = { closedPage++ }) }
        }
        pickDays(2)
        done()
        done()
        assertEquals("each tap asks again: the confirm may have been cancelled", listOf(2, 2), asked.map { it.days })
        rule.onNodeWithTag("promise_sprig").assertDoesNotExist()
        assertEquals(0, closedPage)
    }
}
