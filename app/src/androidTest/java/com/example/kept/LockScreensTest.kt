package com.example.kept

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
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
import com.example.kept.feature.lock.LockActions
import com.example.kept.feature.lock.LockScreen
import com.example.kept.feature.lock.LockUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockScreensTest {
    @get:Rule val compose = createComposeRule()

    private fun sampleState(): LockUi {
        val exercise = HabitEntity(id = 1, title = "Exercise", iconKey = "run", proofType = ProofType.MANUAL, targetValue = 30, unit = "min", createdAt = 0)
        val read = HabitEntity(id = 2, title = "Read", iconKey = "book", proofType = ProofType.MANUAL, targetValue = 10, unit = "pages", createdAt = 0)
        val today = TodaySummary(listOf(HabitToday(exercise, HabitEntryEntity(habitId = 1, date = "2026-09-05", progressValue = 18 * 60)), HabitToday(read, null)))
        val lock = LockState(Settings(onboardingDone = true), today, null, 1, emptySet(), false)
        return LockUi(loaded = true, lock = lock, sprig = SprigState(level = 4, streakDays = 12), lockActive = true)
    }

    @Test fun lock_screen_shows_remaining_habits() {
        compose.setContent { KeptTheme { LockScreen(sampleState(), "Instagram", LockActions()) } }
        compose.onNodeWithTag("lock_headline").assertTextEquals("Instagram can wait.")
        compose.onNodeWithText("Finish your promised habits to unlock.").assertIsDisplayed()
        compose.onNodeWithTag("lock_count").assertTextEquals("1 of 2")
        compose.onNodeWithText("Exercise").assertIsDisplayed()
        compose.onNodeWithText("Read").assertIsDisplayed()
        // The dialer button says what it is, not "Emergency call" (issue #17).
        compose.onNodeWithTag("emergency_call").assertIsDisplayed().assertTextEquals("Phone")
        compose.onNodeWithTag("break_lock").assertIsDisplayed().assertTextEquals("Break the lock")
        compose.onNodeWithText("Open KEPT").assertDoesNotExist()
    }

    @Test fun break_lock_requires_countdown_before_unlock() {
        // The countdown itself lives in LockViewModel (BreakTimer); the screen shows what it is given.
        val left = androidx.compose.runtime.mutableIntStateOf(3)
        compose.setContent { KeptTheme { BreakLockScreen(sampleState(), secondsLeft = left.intValue, onCancel = {}, onConfirm = {}) } }
        compose.onNodeWithText("Break the lock?").assertIsDisplayed()
        compose.onNodeWithText("Sprig drops a level and wilts until tomorrow.").assertIsDisplayed()
        compose.onNodeWithTag("unlock_anyway").assertIsNotEnabled().assertTextEquals("Unlock anyway (3s)")
        left.intValue = 1
        compose.onNodeWithTag("unlock_anyway").assertIsNotEnabled()
        left.intValue = 0
        compose.onNodeWithTag("unlock_anyway").assertIsEnabled().assertTextEquals("Unlock for 30 min")
        compose.onNodeWithText("Never mind, go back").assertIsDisplayed()
    }
}
