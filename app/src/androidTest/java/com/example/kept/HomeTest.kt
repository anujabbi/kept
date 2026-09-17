package com.example.kept

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.data.DebugSeeder
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var db: KeptDatabase
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var seeder: DebugSeeder

    @Before fun setUp() { hilt.inject(); resetAppState(db, prefs) }

    @Test fun home_reflects_seeded_state() {
        runBlocking { seeder.seedIfNeeded() }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("day streak", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("12 day streak").assertIsDisplayed()
            compose.onNodeWithText("Exercise 30 min").assertIsDisplayed()
            compose.onNodeWithText("Read 10 pages").assertIsDisplayed()
            compose.onAllNodesWithText("Tap when done", substring = true)[0].assertIsDisplayed()
        }
    }

    @Test fun home_says_it_is_too_late_once_the_give_up_time_has_passed() {
        // A non-wrapping window whose give-up minute is the current one, so "now" is always at or
        // after it (issue #7). Keeping lockFrom <= due matters: a wrapping window does not expire
        // during its own calendar day, so it is never too late.
        runBlocking {
            seeder.seedIfNeeded()
            val nowMinute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            prefs.updateSettings { it.copy(lockFromMinute = 0, dueMinute = nowMinute) }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("Too late for today", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Too late for today", substring = true).assertIsDisplayed()
            // The checklist still works: the habits are there to tick.
            compose.onAllNodesWithText("Tap when done", substring = true)[0].assertIsDisplayed()
        }
    }
}
