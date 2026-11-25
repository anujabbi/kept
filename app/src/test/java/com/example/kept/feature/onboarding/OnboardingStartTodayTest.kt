package com.example.kept.feature.onboarding

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.ui.HabitTemplate
import com.example.kept.core.ui.HabitTemplates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * What Continue on step 1 and "Start today" write (#207), through the real repositories on an
 * in-memory Room database: every-day habits as daily habits, some-days habits as weekly goals
 * that carry the habit's target. Monday 23 November 2026, so a goal made today still owes days.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingStartTodayTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-23T09:00:00Z"), ZoneId.of("UTC"))
    private val time = TimeSource(clock)
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), KeptPreferences(ctx), time)
    private val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)

    @After fun close() = db.close()

    /** Continue on step 1, then "Start today": the two writes the ViewModel makes. */
    private fun startToday(s: OnboardingState) = runBlocking {
        habits.replaceAll(s.dailyHabitRows(time.nowMillis()))
        weekly.addOnboardingGoals(s.weeklyGoalPlans)
    }

    private fun listRows() = runBlocking {
        weekly.observeGoals().first().map { WeeklyGoalCopy.listRow(it.title, WeeklyGoalCopy.target(it.targetValue, it.unit), it.daysPerWeek) }
    }

    @Test fun `Read daily and Exercise at 4 days a week`() {
        startToday(OnboardingState(step = OnboardingStep.READY))
        assertEquals(listOf("Read"), runBlocking { habits.observeHabits().first() }.map { it.title })
        assertEquals(listOf("Exercise · 30 min · 4 days a week"), listRows())
        val goal = runBlocking { weekly.observeGoals().first() }.single()
        assertEquals(30, goal.targetValue)
        assertEquals("min", goal.unit)
        assertEquals("2026-11-23", goal.effectiveFrom)
    }

    @Test fun `logging the Exercise session is one tap and counts one of the four days`() {
        startToday(OnboardingState(step = OnboardingStep.READY))
        val id = runBlocking { weekly.observeGoals().first() }.single().id
        assertTrue(runBlocking { weekly.logToday(id) })
        val week = runBlocking { weekly.thisWeek() }.single().week
        assertEquals(1, week.done)
        assertTrue(week.doneToday)
    }

    @Test fun `each weekly habit carries its target, and a photo habit has none`() {
        val walk = HabitTemplate("Walk", "walk", ProofType.MANUAL, 20, "min")
        val s = OnboardingState(
            step = OnboardingStep.READY,
            chosen = listOf(HabitTemplates.list[1], HabitTemplates.list[4], walk, HabitTemplates.list[2]),
            weeklyDays = mapOf("Read" to 3, "Tidy room" to 2, "Walk" to 5),
        )
        startToday(s)
        assertEquals(
            listOf(
                "Read · 10 pages · 3 days a week",
                "Tidy room · 2 days a week",
                "Walk · 20 min · 5 days a week",
            ),
            listRows(),
        )
        assertEquals(listOf("Practice"), runBlocking { habits.observeHabits().first() }.map { it.title })
    }

    @Test fun `with every habit every day no weekly goal is written`() {
        startToday(OnboardingState(step = OnboardingStep.READY, weeklyDays = emptyMap()))
        assertEquals(emptyList<String>(), listRows())
        assertEquals(listOf("Exercise", "Read"), runBlocking { habits.observeHabits().first() }.map { it.title })
    }
}
