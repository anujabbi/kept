package com.example.kept.feature.lock

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.TestViewModels
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.DayCompletionReactor
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.di.drainAndCancel
import com.example.kept.core.domain.LockMode
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.LockReason
import com.example.kept.core.domain.LockScreenKind
import com.example.kept.core.domain.ProofType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The must-do lock against what is stored (issue #165, story #175): the real repositories and
 * [LockViewModel] on an in-memory Room database, with a clock the test moves. The goal is
 * "Exercise, 4 days a week", set on 1 November; the week is Sunday 22 to Saturday 28 November
 * 2026, the window 7:00 am to 9:00 pm, and the one daily habit ("Read") is ticked unless a test
 * says otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeeklyLockStoreTest {

    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    private class Recorder : Analytics {
        // Written from the database threads and the main thread at once.
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        override val enabled = true
        override fun capture(event: String, properties: Map<String, Any>) { events += event }
        override fun screen(name: String, properties: Map<String, Any>) { events += "screen:$name" }
        override fun register(key: String, value: Any) = Unit
        override fun restore(enabled: Boolean) = Unit
        override fun setEnabled(enabled: Boolean) = Unit
    }

    // Wednesday 25 November 2026, 9:00 am: example A's day, inside the window.
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
    private val actions = HabitActions(habits, sprig, prefs, time, analytics, object : DayCompletionReactor { override suspend fun onDayCompleted() = Unit }, weekly)
    private val blocked = "com.android.chrome"

    private var readId = 0L

    @Before fun setUp() {
        runBlocking {
            prefs.clearAll()
            prefs.updateSettings { it.copy(onboardingDone = true) }
            readId = habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
            habits.markDone(readId)
        }
    }

    /** Nothing of this test may still be running, or writing the shared preferences, when the next one starts (issue #146). */
    @After fun close() {
        viewModels.clear()
        appScope.drainAndCancel()
        db.close()
        assertEquals("work on the application scope failed", emptyList<Throwable>(), failures.toList())
    }

    /** Moves the clock, and ticks the daily habit for that day so only the weekly goal is in question. */
    private suspend fun at(day: String, time: String = "09:00") {
        clock.now = Instant.parse("${day}T$time:00Z")
        habits.markDone(readId)
    }

    /** A goal set on 1 November, so every day of this week counts in full. */
    private suspend fun goal(title: String = "Exercise", days: Int = 4): Long =
        db.weeklyGoalDao().insert(WeeklyGoalEntity(title = title, iconKey = "run", daysPerWeek = days, createdAt = 0, effectiveFrom = "2026-11-01", sortOrder = days))

    private suspend fun logOn(goalId: Long, day: String) {
        val back = clock.now
        clock.now = Instant.parse("${day}T09:00:00Z")
        assertTrue(weekly.logToday(goalId))
        clock.now = back
    }

    private suspend fun entries(): List<String> = db.weeklyGoalEntryDao().between("2026-01-01", "2027-12-31").map { "${it.goalId}@${it.date}" }.sorted()

    private suspend fun snapshot() = lock.currentState().snapshot(emptySet(), null)
    private suspend fun locked(): Boolean = LockPolicy.shouldLock(blocked, time.now(), time.localTime(), snapshot())
    private suspend fun reason(): LockReason? = LockPolicy.lockReason(time.now(), time.localTime(), snapshot())

    /** The lock screen's state for Chrome right now, as `LockViewModel.state` computes it. */
    private suspend fun ui(): LockUi = lockUiFlow(lock.observeState(), flowOf(sprig.state.first()), flowOf(0L), MutableStateFlow(blocked), time).first()

    /** The application scope as the app builds it: a failed write does not cancel the others. Failures are kept and checked. */
    private val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, t -> failures += t })

    private val viewModels = TestViewModels()

    private fun viewModel() = viewModels.create { LockViewModel(SavedStateHandle(), lock, sprig, actions, time, appScope, analytics) }
        .also { it.setBlocked(blocked, "Chrome") }

    /** Runs the main looper until [done], for work that hops back to it from a database thread. */
    private fun await(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("timed out waiting for $what", System.currentTimeMillis() < end)
            Thread.sleep(10)
        }
    }

    // --- Example A ---

    @Test fun `example A - Wednesday with nothing logged locks and shows the weekly screen`() = runBlocking {
        goal()
        assertTrue(lock.currentState().weeklyMustDoIncomplete)
        assertTrue(locked())
        assertEquals(LockReason.DAILY, reason())
        val ui = ui()
        assertEquals(LockMode.LOCKED, ui.mode)
        assertEquals(LockScreenKind.WEEKLY, ui.kind)
        assertEquals("4 days left, 4 to go.", ui.weekly!!.headline)
        assertEquals("No exercise yet this week. Do today's session to unlock.", ui.weekly!!.line)
        assertEquals("Mark today's exercise done", ui.weekly!!.button)
    }

    @Test fun `marking today's exercise done logs one session, lifts the lock and opens the app`() = runBlocking {
        val id = goal()
        val vm = viewModel()
        var opened = 0
        vm.markWeeklyDone(id) { opened++ }
        await("the app to open") { opened == 1 }
        assertEquals(listOf("$id@2026-11-25"), entries())
        assertFalse(locked())
        assertEquals("1 of 4", weekly.thisWeek().single().week.let { "${it.done} of ${it.target}" })
        // A second tap stores nothing: one session a day counts.
        assertFalse(weekly.logToday(id))
        assertEquals(1, entries().size)
        assertEquals(1, opened)
    }

    @Test fun `it is not locked before the window or after the give-up time, and the screen goes away`() = runBlocking {
        goal()
        at("2026-11-25", "06:59")
        assertFalse(locked())
        at("2026-11-25", "21:00")
        assertFalse(locked())
        assertTrue("still a must-do day, but the lock is over", lock.currentState().weeklyMustDoIncomplete)
        assertEquals(LockMode.DISMISS, ui().mode)
        at("2026-11-25", "20:59")
        assertTrue(locked())
    }

    // --- Examples B, C, D and the edges ---

    @Test fun `example B - one session on Sunday, Monday does not lock`() = runBlocking {
        val id = goal()
        logOn(id, "2026-11-22")
        at("2026-11-23")
        assertFalse(lock.currentState().weeklyMustDoIncomplete)
        assertFalse(locked())
    }

    @Test fun `example C - Thursday and Friday lock, the next Sunday does not`() = runBlocking {
        val id = goal()
        logOn(id, "2026-11-25")
        at("2026-11-26")
        assertTrue(locked())
        assertEquals("3 days left, 3 to go.", ui().weekly!!.headline)
        assertEquals("1 of 4 done. Do today's session to unlock.", ui().weekly!!.line)
        // Thursday skipped past the give-up time: Friday needs 3 in 2 days.
        at("2026-11-27")
        assertTrue(locked())
        assertEquals("2 days left, 3 to go.", ui().weekly!!.headline)
        assertEquals("You can't hit 4 this week, but today still counts.", ui().weekly!!.line)
        at("2026-11-29")
        assertFalse(lock.currentState().weeklyMustDoIncomplete)
        assertFalse(locked())
    }

    @Test fun `example D - 4 done by Thursday, Friday and Saturday never lock`() = runBlocking {
        val id = goal()
        for (d in listOf("22", "23", "24", "26")) logOn(id, "2026-11-$d")
        for (d in listOf("27", "28")) {
            at("2026-11-$d")
            assertFalse("the ${d}th", locked())
        }
    }

    @Test fun `target 7 locks every day until that day's session is logged`() = runBlocking {
        val id = goal(days = 7)
        for (d in 22..28) {
            at("2026-11-$d")
            assertTrue("the ${d}th before its session", locked())
            assertTrue(weekly.logToday(id))
            assertFalse("the ${d}th after its session", locked())
        }
    }

    @Test fun `a goal created today does not lock today, and locks tomorrow`() = runBlocking {
        weekly.addGoal("Exercise", 7)
        assertFalse(locked())
        at("2026-11-26")
        assertTrue(locked())
        assertEquals("3 days left, 3 to go.", ui().weekly!!.headline)
    }

    @Test fun `a Saturday must-do day reads in the singular`() = runBlocking {
        val id = goal()
        for (d in listOf("22", "23", "24")) logOn(id, "2026-11-$d")
        at("2026-11-28")
        assertTrue(locked())
        assertEquals("1 day left, 1 to go.", ui().weekly!!.headline)
    }

    // --- undo, edits, removal ---

    @Test fun `undoing today's session brings the lock back`() = runBlocking {
        val id = goal()
        assertTrue(weekly.logToday(id))
        assertFalse(locked())
        assertTrue(weekly.undoToday(id))
        assertTrue(locked())
        assertEquals(emptyList<String>(), entries())
    }

    @Test fun `lowering 4 to 2 lifts the lock and raising it back locks again`() = runBlocking {
        val id = goal()
        assertTrue(locked())
        assertTrue(weekly.updateGoal(id, "Exercise", 2, null, null))
        assertFalse("needs 2 with 4 days left", locked())
        assertTrue(weekly.updateGoal(id, "Exercise", 4, null, null))
        assertTrue("raised: today is a must-do day again", locked())
    }

    @Test fun `deleting the goal lifts the lock`() = runBlocking {
        val id = goal()
        assertTrue(locked())
        weekly.removeGoal(id)
        assertFalse(locked())
        assertFalse(lock.currentState().weeklyMustDoIncomplete)
    }

    @Test fun `a goal removed while its lock screen is up - the screen recomputes and no session is logged`() = runBlocking {
        val id = goal()
        val vm = viewModel()
        assertEquals(LockScreenKind.WEEKLY, ui().kind)
        // Removed from Settings while the lock screen still shows its button.
        weekly.removeGoal(id)
        assertNull("nothing is left of the weekly variant", ui().weekly)
        assertEquals("the habits were done in time, so the screen opens", LockMode.OPEN, ui().mode)
        // The stale button is tapped anyway: the repository reports it and nothing is stored.
        assertFalse(weekly.logToday(id))
        var opened = 0
        vm.markWeeklyDone(id) { opened++ }
        await("the tap to be answered") { opened == 1 }
        assertEquals(emptyList<String>(), entries())
        assertFalse(locked())
    }

    @Test fun `an edit of a goal that was removed elsewhere reports it and changes nothing`() = runBlocking {
        val id = goal()
        weekly.removeGoal(id)
        assertFalse(weekly.updateGoal(id, "Exercise", 2, null, null))
        assertTrue(weekly.observeGoals().first().isEmpty())
        assertEquals(4, db.weeklyGoalDao().byId(id)!!.daysPerWeek)
    }

    // --- more than one reason ---

    @Test fun `two goals must-do on one day - the first tap keeps the lock, the second opens the app`() = runBlocking {
        val exercise = goal("Exercise", 4)
        val practice = goal("Practice", 7)
        for (d in listOf("22", "23", "24")) logOn(practice, "2026-11-$d")
        assertEquals(listOf(exercise, practice), lock.currentState().weeklyMustDo.map { it.id })
        assertEquals("Mark today's exercise done", ui().weekly!!.button)
        val vm = viewModel()
        var opened = 0
        vm.markWeeklyDone(exercise) { opened++ }
        await("the first session to be stored") { runBlocking { entries().contains("$exercise@2026-11-25") } && !vm.openingApp }
        assertEquals("the lock still holds for Practice", 0, opened)
        assertTrue(locked())
        assertEquals("Mark today's practice done", ui().weekly!!.button)
        assertEquals("3 of 7 done. Do today's session to unlock.", ui().weekly!!.line)
        vm.markWeeklyDone(practice) { opened++ }
        await("the app to open") { opened == 1 }
        assertFalse(locked())
    }

    @Test fun `a habit and a must-do session - ticking only the habit keeps the lock, logging the session too lifts it`() = runBlocking {
        val id = goal()
        habits.undo(readId)
        val both = ui()
        assertEquals(LockScreenKind.HABITS, both.kind)
        assertEquals(listOf(id), both.weeklyRows.map { it.id })
        assertTrue(locked())
        // Only the habit: still locked, now on the weekly variant.
        val vm = viewModel()
        vm.complete(readId)
        await("the tick to be stored") { runBlocking { habits.today().allDone } }
        assertTrue(locked())
        assertEquals(LockMode.LOCKED, ui().mode)
        assertEquals(LockScreenKind.WEEKLY, ui().kind)
        // The session too: open.
        vm.logWeekly(id)
        await("the session to be stored") { runBlocking { entries().isNotEmpty() } }
        assertFalse(locked())
        assertEquals(LockMode.OPEN, ui().mode)
    }

    @Test fun `only the session logged - the habit still holds the lock and the row can be undone`() = runBlocking {
        val id = goal()
        habits.undo(readId)
        val vm = viewModel()
        vm.logWeekly(id)
        await("the session to be stored") { runBlocking { entries().isNotEmpty() } }
        assertTrue(locked())
        assertEquals(listOf(id), ui().weeklyRows.map { it.id })
        assertTrue(ui().weeklyRows.single().week.doneToday)
        vm.undoWeekly(id)
        await("the undo to be stored") { runBlocking { entries().isEmpty() } }
        assertTrue(lock.currentState().weeklyMustDoIncomplete)
    }

    @Test fun `a tick that finishes the habits while a session is left finishes nothing, and the session leaves the moment for KEPT`() = runBlocking {
        val goalId = goal()
        habits.undo(readId)
        val vm = viewModel()
        // The screen is up and showing the habit and the session, as the activity keeps it.
        val watching = CoroutineScope(Dispatchers.Unconfined).launch { vm.state.collect { } }
        await("the lock screen's state") { vm.state.value.let { it.loaded && it.remaining == 1 && it.weeklyMustDo.size == 1 } }
        vm.complete(readId)
        await("the tick to be stored") { runBlocking { habits.today().allDone } }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("the screen is still locked, so the moment does not go up", vm.moment.value || vm.finishing)
        assertNull("not claimed: it has not played", prefs.currentSettings().celebrationShownDate)
        // The session is the other half of today's promise (story #176): the day is not finished yet.
        assertNull("nothing pending for KEPT yet", prefs.currentSettings().celebrationPendingDate)
        assertFalse(analytics.events.contains("day_completed"))

        // The session, logged from its row on this screen, finishes the day; the moment waits for KEPT.
        vm.logWeekly(goalId)
        await("the session to be stored") { runBlocking { prefs.currentSettings().celebrationPendingDate != null } }
        watching.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("2026-11-25", prefs.currentSettings().celebrationPendingDate)
        assertNull("still not played here", prefs.currentSettings().celebrationShownDate)
        assertEquals("finished once, by whichever came last", 1, analytics.events.count { it == "day_completed" })
        assertFalse(analytics.events.contains("screen:celebration"))
    }

    // --- breaks and night lockdown ---

    @Test fun `a break on a weekly lock pauses it, costs what a break costs and logs no session`() = runBlocking {
        goal()
        sprig.write(sprig.current().copy(level = 5))
        assertTrue(locked())
        val taken = lock.breakLock()
        assertEquals(30 * 60_000L, taken.unlockedUntil - taken.timestamp)
        assertFalse("paused", locked())
        assertEquals(emptyList<String>(), entries())
        assertTrue("still a must-do day", lock.currentState().weeklyMustDoIncomplete)
        assertEquals(1, lock.currentState().breaksUsedThisWeek)
        assertEquals(4, sprig.current().level)
        assertTrue(sprig.current().wilted)
        assertEquals(1, db.dayRecordDao().get("2026-11-25")!!.breaksUsed)
        // The break runs out: the session is still owed, so the lock is back.
        at("2026-11-25", "09:31")
        assertTrue(locked())
    }

    @Test fun `night lockdown shows its own screen while its window is open, then the weekly lock shows`() = runBlocking {
        goal()
        // Daily window from 6:00 am, night lockdown until 7:00 am: they overlap for an hour.
        prefs.updateSettings { it.copy(lockFromMinute = 6 * 60, nightLockEnabled = true) }
        at("2026-11-25", "06:30")
        assertEquals(LockReason.NIGHT, reason())
        assertEquals(LockScreenKind.NIGHT, ui().kind)
        assertNull(ui().weekly)
        at("2026-11-25", "07:00")
        assertEquals(LockReason.DAILY, reason())
        assertEquals(LockScreenKind.WEEKLY, ui().kind)
    }

    @Test fun `an exception still opens during a weekly lock`() = runBlocking {
        goal()
        assertTrue(locked())
        lock.addException(blocked, "Chrome")
        assertFalse(locked())
        assertTrue(LockPolicy.shouldLock("com.instagram.android", time.now(), time.localTime(), snapshot()))
        assertFalse(LockPolicy.shouldLock("com.android.dialer", time.now(), time.localTime(), lock.currentState().snapshot(setOf("com.android.dialer"), null)))
    }
}
