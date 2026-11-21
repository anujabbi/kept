package com.example.kept.feature.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "See my Sprigs" leaves the recap for the Sprig tab (QA on #129): with the real nav options from
 * [openTab] and [leaveRecapForSprigs], the recap is never saved into Home's stack, so the next tap
 * on Today shows Home alone, and Back from the Sprig tab goes to Home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecapNavigationTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var nav: NavHostController

    @Before fun setUp() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { Text("home") }
                composable(Routes.GALLERY) { Text("gallery") }
                composable(Routes.SETTINGS) { Text("settings") }
                composable(Routes.RECAP, arguments = listOf(navArgument("date") { type = NavType.StringType })) { Text("recap") }
            }
        }
        rule.waitForIdle()
    }

    private fun stack(): List<String?> =
        nav.currentBackStack.value.filter { it.destination !is NavGraph }.map { it.destination.route }

    private fun act(block: () -> Unit) {
        rule.runOnUiThread(block)
        rule.waitForIdle()
    }

    @Test fun seeMySprigsLeavesTheRecapForTheSprigTab() {
        act { nav.navigate(Routes.recap("2026-11-20")) }
        assertEquals(listOf(Routes.HOME, Routes.RECAP), stack())
        act { nav.leaveRecapForSprigs() }
        assertEquals(listOf(Routes.HOME, Routes.GALLERY), stack())
        act { nav.popBackStack() }
        assertEquals("Back from the Sprig tab goes Home", listOf(Routes.HOME), stack())
    }

    @Test fun theTodayTabDoesNotBringTheRecapBack() {
        act { nav.navigate(Routes.recap("2026-11-20")) }
        act { nav.leaveRecapForSprigs() }
        act { nav.openTab(Routes.HOME) }
        assertEquals(listOf(Routes.HOME), stack())
        act { nav.openTab(Routes.SETTINGS) }
        act { nav.openTab(Routes.HOME) }
        assertEquals(listOf(Routes.HOME), stack())
    }

    @Test fun fromARecapOpenedInSettingsHistory() {
        act { nav.openTab(Routes.SETTINGS) }
        act { nav.navigate(Routes.recap("2026-11-20")) }
        act { nav.leaveRecapForSprigs() }
        assertEquals(listOf(Routes.HOME, Routes.GALLERY), stack())
        act { nav.openTab(Routes.SETTINGS) }
        assertEquals("Settings comes back without the recap", listOf(Routes.HOME, Routes.SETTINGS), stack())
        act { nav.openTab(Routes.HOME) }
        assertEquals(listOf(Routes.HOME), stack())
    }
}
