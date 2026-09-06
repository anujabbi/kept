package com.example.kept

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class HomeAndBuddyTest {
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
            compose.onNodeWithText("18/30").assertIsDisplayed()
        }
    }

    @Test fun buddy_empty_state_pairs_with_a_code() {
        runBlocking {
            prefs.updateSettings { it.copy(onboardingDone = true, firstUseDate = java.time.LocalDate.now()) }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Buddy")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Buddy").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("invite_code")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("invite_code").assertIsDisplayed()
            compose.onNodeWithTag("enter_code").performClick()
            compose.onNodeWithTag("code_input").performTextInput("ABC-123")
            compose.onNodeWithTag("pair_button").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("buddy_paired")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("9 day streak", substring = true).assertIsDisplayed()
        }
    }
}
