package com.example.kept

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import com.example.kept.core.ui.KeptTheme
import com.example.kept.feature.lock.BreakLockScreen
import com.example.kept.feature.lock.LockScreen
import com.example.kept.feature.lock.LockUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun sampleState(): LockUi {
        val exercise = HabitEntity(id = 1, title = "Exercise", iconKey = "run", proofType = ProofType.TIMER, targetValue = 30, unit = "min", createdAt = 0)
        val read = HabitEntity(id = 2, title = "Read", iconKey = "book", proofType = ProofType.MANUAL, targetValue = 10, unit = "pages", createdAt = 0)
        val today = TodaySummary(listOf(HabitToday(exercise, HabitEntryEntity(habitId = 1, date = "2026-09-05", progressValue = 18 * 60)), HabitToday(read, null)))
        val lock = LockState(Settings(onboardingDone = true), today, null, 1, emptySet(), false)
        return LockUi(loaded = true, lock = lock, sprig = SprigState(level = 4, streakDays = 12), lockActive = true)
    }

    @Test fun lock_screen_shows_remaining_habits() {
        compose.setContent { KeptTheme { LockScreen(sampleState(), "Instagram", {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Instagram is locked").assertIsDisplayed()
        compose.onNodeWithText("12 minutes left to go").assertIsDisplayed()
        compose.onNodeWithText("Exercise 30 min").assertIsDisplayed()
        compose.onNodeWithText("Read 10 pages").assertIsDisplayed()
        compose.onNodeWithTag("emergency_call").assertIsDisplayed()
        compose.onNodeWithTag("break_lock").assertIsDisplayed()
    }

    @Test fun break_lock_requires_countdown_before_unlock() {
        compose.mainClock.autoAdvance = false
        compose.setContent { KeptTheme { BreakLockScreen(sampleState(), onCancel = {}, onConfirm = {}, countdownSeconds = 3) } }
        compose.onNodeWithText("This drops Sprig to level 3").assertIsDisplayed()
        compose.onNodeWithTag("unlock_anyway").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag("unlock_anyway").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithTag("unlock_anyway").assertIsEnabled()
        compose.onNodeWithText("Never mind, go back").assertIsDisplayed()
    }
}
