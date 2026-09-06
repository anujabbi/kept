package com.example.kept

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var db: KeptDatabase
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var habits: HabitRepository

    @Before fun setUp() { hilt.inject(); resetAppState(db, prefs) }

    @Test fun onboarding_completes_and_persists() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_habits")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Exercise").assertIsDisplayed()
            compose.onNodeWithTag("onboarding_next").performClick()          // habits -> rule
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_rule")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("onboarding_next").performClick()          // rule -> exceptions
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_exceptions")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("onboarding_next").performClick()          // exceptions -> permissions
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_permissions")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("onboarding_next").performClick()          // permissions -> meet sprig
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_sprig")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("onboarding_finish").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }

            val settings = runBlocking { prefs.currentSettings() }
            assertTrue(settings.onboardingDone)
            val today = runBlocking { habits.today() }
            assertEquals(2, today.total)
        }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithText(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true))
