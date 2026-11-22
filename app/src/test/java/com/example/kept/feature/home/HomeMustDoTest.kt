package com.example.kept.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.TestViewModels
import com.example.kept.core.analytics.NoOpAnalytics
import com.example.kept.core.data.DayCompletionReactor
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyCardCopy
import com.example.kept.core.ui.shots.Shot
import com.example.kept.feature.celebration.CelebrationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * Home agrees with the lock on a must-do day (planner's items on #175, from QA on #174): the
 * headline does not say "Promise kept. Apps are open." while a weekly goal's session holds the
 * lock, and after the give-up time the card stops promising a lock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HomeMustDoTest {

    @get:Rule val rule = createComposeRule()

    private val current = mutableStateOf(HomeFixtures.state(2, 2))

    private fun show(s: HomeUiState) {
        current.value = s
        rule.setContent { Shot { HomeFrame(current.value) } }
        rule.waitForIdle()
    }

    private fun text(tag: String): String =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
            .replace(WeeklyCardCopy.NBSP, ' ')

    /** The card's status line: the one text inside its tagged row. */
    private fun status(): String =
        rule.onNodeWithTag("home_weekly_status_1", useUnmergedTree = true).fetchSemanticsNode().children
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text) }.flatten().joinToString { it.text }

    private fun sprigSays(): String =
        rule.onNodeWithTag("home_sprig").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")

    @Test fun habits_done_and_the_must_do_session_not_logged_reads_one_to_go() {
        show(HomeFixtures.state(2, 2, weekly = listOf(HomeFixtures.todayCounts())))
        assertEquals("One to go.", text("home_headline"))
        assertEquals("Apps unlock when you finish, or at 9:00 pm.", text("home_line"))
        assertEquals("Today counts: apps lock until it's done", status())
        // The day is not finished, so the sky and Sprig do not celebrate it.
        val cheering = sprigSays()
        current.value = HomeFixtures.state(2, 2)
        rule.waitForIdle()
        assertEquals("Promise kept.", text("home_headline"))
        assertTrue("Sprig reads the same waiting on a session ('$cheering') as on a finished day", cheering != sprigSays())
    }

    @Test fun a_habit_and_a_must_do_session_left_reads_two_to_go() {
        show(HomeFixtures.state(2, 1, weekly = listOf(HomeFixtures.todayCounts())))
        assertEquals("Two to go.", text("home_headline"))
    }

    @Test fun the_today_count_is_still_the_habits_alone() {
        val s = HomeFixtures.state(2, 2, weekly = listOf(HomeFixtures.todayCounts()))
        assertEquals(3, s.day.total)
        assertEquals(2, s.today.total)
        assertFalse(s.day.keptOnTime)
        assertTrue(s.today.allDoneOnTime)
    }

    @Test fun once_the_session_is_logged_or_on_a_free_day_home_says_promise_kept() {
        show(HomeFixtures.state(2, 2, weekly = listOf(HomeFixtures.goal(1, "Exercise", 4, 3))))
        assertEquals("Promise kept.", text("home_headline"))
        assertEquals("Apps are open. See you tomorrow.", text("home_line"))
        current.value = HomeFixtures.state(2, 2, weekly = listOf(HomeFixtures.onTrack()))
        rule.waitForIdle()
        assertEquals("Promise kept.", text("home_headline"))
        assertEquals("On track, no lock", status())
    }

    @Test fun after_the_give_up_time_the_card_and_the_headline_agree() {
        show(HomeFixtures.state(2, 2, pastDue = true, weekly = listOf(HomeFixtures.todayCounts())))
        assertEquals("Promise kept.", text("home_headline"))
        assertEquals("Apps are open. See you tomorrow.", text("home_line"))
        assertEquals("Past the give-up time, no lock", status())
        // With a habit left as well, Home's own line and the same card line.
        current.value = HomeFixtures.state(2, 1, pastDue = true, weekly = listOf(HomeFixtures.todayCounts()))
        rule.waitForIdle()
        assertEquals("One to go.", text("home_headline"))
        assertEquals("Apps are open. Today won't count.", text("home_line"))
        assertEquals("Past the give-up time, no lock", status())
    }

    // --- the "Promise kept" moment says "Apps are open.", so it waits for the session too ---

    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    @Test fun the_moment_in_kept_waits_until_the_must_do_session_is_logged() {
        val time = TimeSource(TestClock(Instant.parse("2026-11-25T09:00:00Z")))
        val prefs = KeptPreferences(ctx)
        val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
        val sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time)
        val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)
        val actions = HabitActions(habits, sprig, prefs, time, NoOpAnalytics(), object : DayCompletionReactor { override suspend fun onDayCompleted() = Unit })
        val goalId = runBlocking {
            prefs.clearAll()
            prefs.updateSettings { it.copy(onboardingDone = true) }
            val id = db.weeklyGoalDao().insert(WeeklyGoalEntity(title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 0, effectiveFrom = "2026-11-01"))
            // The last habit is ticked on Home, in time: today's moment is pending for KEPT.
            actions.complete(habits.addHabit("Read", "book", ProofType.MANUAL, 1, ""))
            id
        }
        assertEquals("2026-11-25", runBlocking { prefs.currentSettings().celebrationPendingDate })
        val viewModels = TestViewModels()
        val vm = viewModels.create { CelebrationViewModel(SavedStateHandle(), prefs, habits, sprig, time, actions, NoOpAnalytics(), weekly) }
        val watching = CoroutineScope(Dispatchers.Unconfined).launch { vm.state.collect { } }
        fun settle() = repeat(40) { shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10) }
        settle()
        assertFalse("\"Apps are open.\" while the weekly session holds the lock", vm.state.value.due)
        runBlocking { weekly.logToday(goalId) }
        settle()
        val due = vm.state.value.due
        // Stop the ViewModel's own collection before the database closes under it.
        watching.cancel()
        viewModels.clear()
        assertTrue("the session is logged: the moment plays", due)
    }
}
