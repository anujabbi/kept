package com.example.kept.feature.onboarding

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.TestViewModels
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.lock.Permissions
import com.example.kept.core.ui.HabitTemplate
import com.example.kept.core.ui.HabitTemplates
import com.example.kept.core.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * What "Start today" writes (#207), through the real [OnboardingViewModel.finish] and
 * repositories on an in-memory Room database: every-day habits as daily habits, some-days habits
 * as weekly goals that carry the habit's target. Monday 23 November 2026, so a goal made today
 * still owes days.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingStartTodayTest {
    private object NoAnalytics : Analytics {
        override val enabled = false
        override fun capture(event: String, properties: Map<String, Any>) = Unit
        override fun screen(name: String, properties: Map<String, Any>) = Unit
        override fun register(key: String, value: Any) = Unit
        override fun restore(enabled: Boolean) = Unit
        override fun setEnabled(enabled: Boolean) = Unit
    }

    private val clock = Clock.fixed(Instant.parse("2026-11-23T09:00:00Z"), ZoneId.of("UTC"))
    private val time = TimeSource(clock)
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = KeptPreferences(ctx)
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
    private val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)
    private val viewModels = TestViewModels()

    @After fun close() {
        viewModels.clear()
        db.close()
    }

    /**
     * A fresh onboarding (a new launch), set up by [walk], then "Start today" through the real
     * [OnboardingViewModel.finish], waited for until it calls back.
     */
    private fun startToday(walk: OnboardingViewModel.() -> Unit = {}) {
        val vm = viewModels.create {
            OnboardingViewModel(ctx, prefs, habits, weekly, WorkScheduler(ctx, prefs, time), time, Permissions(ctx), NoAnalytics)
        }
        vm.walk()
        var done = false
        vm.finish { done = true }
        val end = System.currentTimeMillis() + 10_000
        while (!done) {
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("timed out waiting for Start today", System.currentTimeMillis() < end)
            Thread.sleep(10)
        }
    }

    /** Opens [title]'s promise, picks [days] a week (null for every day) and taps Done, as the screen does. */
    private fun OnboardingViewModel.someDays(title: String, days: Int?) {
        openPromise(title)
        promiseDone(state.value.promise!!.draft.withDays(days))
        closePromise()
    }

    /** "Add your own" with [name] typed, 20 minutes, every day. */
    private fun OnboardingViewModel.addOwn(name: String) {
        openAddHabit()
        promiseDone(state.value.promise!!.draft.withActivity(name))
        closePromise()
    }

    private fun listRows() = runBlocking {
        weekly.observeGoals().first().map { WeeklyGoalCopy.listRow(it.title, WeeklyGoalCopy.target(it.targetValue, it.unit), it.daysPerWeek) }
    }

    private fun habitTitles() = runBlocking { habits.observeHabits().first() }.map { it.title }

    @Test fun `Read daily and Exercise at 4 days a week`() {
        startToday()
        assertEquals(listOf("Read"), habitTitles())
        assertEquals(listOf("Exercise · 30 min · 4 days a week"), listRows())
        val goal = runBlocking { weekly.observeGoals().first() }.single()
        assertEquals(30, goal.targetValue)
        assertEquals("min", goal.unit)
        assertEquals("2026-11-23", goal.effectiveFrom)
        assertTrue(runBlocking { prefs.currentSettings() }.onboardingDone)
    }

    /**
     * The process dying after the goals were written but before onboarding was marked done brings
     * onboarding back on the next launch; finishing it again must not add the goals twice
     * (review on #210).
     */
    @Test fun `Start today run twice still leaves one Exercise goal`() {
        startToday()
        startToday()
        assertEquals(listOf("Exercise · 30 min · 4 days a week"), listRows())
        assertEquals(listOf("Read"), habitTitles())
    }

    /** With three weekly goals, an appending retry would hit the limit and drop goals silently. */
    @Test fun `a retry with three weekly goals keeps all three once`() {
        val threeWeekly: OnboardingViewModel.() -> Unit = {
            toggle(HabitTemplates.list[2])
            toggle(HabitTemplates.list[3])
            someDays("Practice", 2)
            someDays("Study", 5)
        }
        startToday(threeWeekly)
        startToday(threeWeekly)
        assertEquals(listOf("Exercise", "Practice", "Study"), runBlocking { weekly.observeGoals().first() }.map { it.title })
    }

    @Test fun `logging the Exercise session is one tap and counts one of the four days`() {
        startToday()
        val id = runBlocking { weekly.observeGoals().first() }.single().id
        assertTrue(runBlocking { weekly.logToday(id) })
        val week = runBlocking { weekly.thisWeek() }.single().week
        assertEquals(1, week.done)
        assertTrue(week.doneToday)
    }

    @Test fun `each weekly habit carries its target, and a photo habit has none`() {
        startToday {
            toggle(HabitTemplates.list[0]) // Exercise off
            toggle(HabitTemplates.list[2]) // Practice on, every day
            toggle(HabitTemplates.list[4]) // Tidy room on
            addOwn("Walk")
            someDays("Read", 3)
            someDays("Tidy room", 2)
            someDays("Walk", 5)
        }
        assertEquals(
            listOf(
                "Read · 10 pages · 3 days a week",
                "Tidy room · 2 days a week",
                "Walk · 20 min · 5 days a week",
            ),
            listRows(),
        )
        assertEquals(listOf("Practice"), habitTitles())
    }

    @Test fun `with every habit every day no weekly goal is written`() {
        startToday {
            someDays("Exercise", null)
        }
        assertEquals(emptyList<String>(), listRows())
        assertEquals(listOf("Exercise", "Read"), habitTitles())
    }
}
