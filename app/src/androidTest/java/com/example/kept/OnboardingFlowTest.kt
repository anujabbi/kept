package com.example.kept

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.lock.Permissions
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
    @Inject lateinit var permissions: Permissions

    @Before fun setUp() { hilt.inject(); resetAppState(db, prefs) }

    @Test fun onboarding_completes_and_persists() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Welcome first (#132), then step 1.
            compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_welcome")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Habits first. Phone after.").assertIsDisplayed()
            compose.onNodeWithTag("welcome_start").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_habits")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Exercise").assertIsDisplayed()
            compose.onNodeWithContentDescription("Step 1 of 5").assertExists()
            // Past the old cap of four (issue #38): tick every template, six in all, and the
            // "Add your own" row must still be offered because the cap is now ten.
            listOf("Practice", "Study", "Tidy room", "Sleep by 11").forEach {
                compose.onNodeWithTag("page_body").performScrollToNode(androidx.compose.ui.test.hasTestTag("onboarding_habit_$it"))
                compose.onNodeWithTag("onboarding_habit_$it").performClick()
            }
            compose.onNodeWithTag("page_body").performScrollToNode(androidx.compose.ui.test.hasTestTag("onboarding_add_own"))
            compose.onNodeWithTag("onboarding_add_own").assertIsDisplayed()
            compose.onNodeWithTag("onboarding_habit_limit").assertDoesNotExist()
            compose.onNodeWithTag("onboarding_habit_count").assertTextEquals("6 habits picked")
            compose.onNodeWithTag("onboarding_next").performClick()          // habits -> lock window
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_lock_window")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("lock_from_value").assertTextEquals("7:00\u00A0am")
            compose.onNodeWithTag("onboarding_next").performClick()          // lock window -> exceptions
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_exceptions")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("onboarding_next").performClick()          // exceptions -> permissions
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_permissions")).fetchSemanticsNodes().isNotEmpty() }
            // A fresh emulator denies usage access, overlay and POST_NOTIFICATIONS alike, so the
            // three-permission gate (issue #40) holds the button back and the line names all three.
            compose.onNodeWithTag("onboarding_next").assertTextEquals("Continue without the lock")
            compose.onNodeWithTag("onboarding_permissions_warning")
                .assert(androidx.compose.ui.test.hasText("notifications", substring = true))
                .assert(androidx.compose.ui.test.hasText("usage access", substring = true))
            // The button sits in a fixed footer (#132), so it is always on screen.
            compose.onNodeWithTag("onboarding_next").performClick()          // permissions -> how Sprig works
            compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("onboarding_sprig")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Step 5 of 5").assertExists()
            compose.onNodeWithTag("onboarding_finish").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }

            val settings = runBlocking { prefs.currentSettings() }
            assertTrue(settings.onboardingDone)
            // The lock window step was not touched (#132), so the window and break length are the defaults.
            assertEquals(7 * 60, settings.lockFromMinute)
            assertEquals(21 * 60, settings.dueMinute)
            assertEquals(30, settings.breakDurationMin)
            val today = runBlocking { habits.today() }
            assertEquals(6, today.total)

            // First landing (issue #41): the one-time card sits at the top of Home, and its
            // reminder line follows the live notifications grant rather than onboarding's answer.
            compose.onNodeWithText("That's it, you're all set.").assertIsDisplayed()
            val expectedLine = if (permissions.notificationsGranted()) {
                "You can close the app now. Sprig keeps watch and reminds you when it matters."
            } else {
                "You can close the app now. Sprig keeps watch, but reminders are off until notifications are turned on in Settings."
            }
            compose.onNodeWithText(expectedLine).assertIsDisplayed()
            compose.onNodeWithText("Got it").assertIsDisplayed()
            assertTrue(runBlocking { prefs.currentSettings() }.firstLandingPending)

            // A configuration change recreates the activity but is not a second visit.
            it.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("That's it, you're all set.").assertIsDisplayed()
        }
        // Closing the scenario stops Home, which is the second-visit boundary. The next launch,
        // the same as a relaunch after a swipe-away, is the everyday Home.
        compose.waitUntil(10_000) { !runBlocking { prefs.currentSettings() }.firstLandingPending }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home_first_landing").assertDoesNotExist()
        }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithText(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true))
