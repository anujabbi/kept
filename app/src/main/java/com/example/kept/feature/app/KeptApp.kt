package com.example.kept.feature.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.analytics.Screens
import com.example.kept.core.ui.KeptTab
import com.example.kept.core.ui.KeptTabBar
import com.example.kept.core.ui.KeptTheme
import com.example.kept.feature.celebration.CelebrationHost
import com.example.kept.feature.gallery.EvolutionRevealScreen
import com.example.kept.feature.gallery.GalleryScreen
import com.example.kept.feature.gallery.RoadmapScreen
import com.example.kept.feature.home.HomeScreen
import com.example.kept.feature.onboarding.OnboardingScreen
import com.example.kept.feature.recap.RecapScreen
import com.example.kept.feature.settings.ExceptionsScreen
import com.example.kept.feature.settings.HabitsEditScreen
import com.example.kept.feature.settings.PermissionsScreen
import com.example.kept.feature.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val RECAP = "recap/{date}"
    const val EXCEPTIONS = "exceptions"
    const val HABITS = "habits"
    const val PERMISSIONS = "permissions"
    const val REVEAL = "reveal/{galleryId}"
    const val ROADMAP = "roadmap"

    fun recap(date: String) = "recap/$date"
    fun reveal(id: Long) = "reveal/$id"
}

/**
 * The `$screen_name` for a nav destination (issue #10). Route patterns are matched, not the filled
 * routes, so a `$screen` never carries the date or the gallery id in the path.
 *
 * Onboarding is deliberately absent: it reports one screen view per step, with the step on it, from
 * inside `OnboardingScreen`. Reporting it here as well would double every first-run screen view.
 */
fun screenNameFor(route: String?): String? = when (route) {
    Routes.HOME -> Screens.HOME
    Routes.GALLERY -> Screens.GALLERY
    Routes.SETTINGS -> Screens.SETTINGS
    Routes.RECAP -> Screens.RECAP
    Routes.EXCEPTIONS -> Screens.EXCEPTIONS
    Routes.HABITS -> Screens.HABITS
    Routes.PERMISSIONS -> Screens.PERMISSIONS
    Routes.REVEAL -> Screens.REVEAL
    Routes.ROADMAP -> Screens.ROADMAP
    else -> null
}

/** Opens a tab [route] as the tab bar does: each tab keeps its own back stack. */
internal fun NavController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * "See my Sprigs" on the recap: the recap is popped first, so it is not saved into Home's stack
 * and brought back by the next tap on Today (QA on #129), then the Sprig tab opens as from the
 * tab bar.
 */
internal fun NavController.leaveRecapForSprigs() {
    popBackStack()
    openTab(Routes.GALLERY)
}

/** The three tabs and the route each one opens. */
private val tabRoutes = mapOf(
    KeptTab.Today to Routes.HOME,
    KeptTab.Sprig to Routes.GALLERY,
    KeptTab.Settings to Routes.SETTINGS,
)

@Composable
fun KeptApp(startDestination: String, pendingRoute: String?, consumeRoute: () -> Unit, analytics: Analytics) {
    val nav = rememberNavController()
    val c = KeptTheme.colors
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in tabRoutes.values

    // One listener for the whole graph, so a new destination cannot be added without a screen view.
    DisposableEffect(nav, analytics) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            screenNameFor(destination.route)?.let { analytics.screen(it) }
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }

    LaunchedEffect(pendingRoute) {
        val r = pendingRoute ?: return@LaunchedEffect
        when {
            r == "recap" -> nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
            r == "settings" -> nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
            else -> nav.navigate(Routes.HOME) { launchSingleTop = true }
        }
        consumeRoute()
    }

    Box(Modifier.fillMaxSize().background(c.surface1)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                NavHost(nav, startDestination = startDestination) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenRecap = { nav.navigate(Routes.recap(it)) },
                            onOpenReveal = { nav.navigate(Routes.reveal(it)) },
                            onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                            onOpenHabits = { nav.navigate(Routes.HABITS) },
                        )
                    }
                    composable(Routes.GALLERY) { GalleryScreen(onOpenRoadmap = { nav.navigate(Routes.ROADMAP) }) }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenExceptions = { nav.navigate(Routes.EXCEPTIONS) },
                            onOpenHabits = { nav.navigate(Routes.HABITS) },
                            onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                            onOpenRecap = { nav.navigate(Routes.recap(it)) },
                        )
                    }
                    composable(Routes.RECAP, arguments = listOf(navArgument("date") { type = NavType.StringType })) {
                        RecapScreen(
                            date = it.arguments!!.getString("date")!!,
                            onClose = { nav.popBackStack() },
                            onOpenSprigs = { nav.leaveRecapForSprigs() },
                        )
                    }
                    composable(Routes.EXCEPTIONS) { ExceptionsScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.HABITS) { HabitsEditScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.REVEAL, arguments = listOf(navArgument("galleryId") { type = NavType.LongType })) {
                        EvolutionRevealScreen(galleryId = it.arguments!!.getLong("galleryId"), onClose = { nav.popBackStack() })
                    }
                    composable(Routes.ROADMAP) { RoadmapScreen(onBack = { nav.popBackStack() }) }
                }
            }
            AnimatedVisibility(showBar, enter = fadeIn(), exit = fadeOut()) {
                KeptTabBar(
                    selected = tabRoutes.entries.firstOrNull { it.value == currentRoute }?.key,
                    onSelect = { tab -> nav.openTab(tabRoutes.getValue(tab)) },
                )
            }
        }
        // Above the nav graph and the bottom bar: the celebration is a full-screen moment, and it
        // must be able to cover whichever tab is open when the day is finished (issue #9). Kept out
        // of HomeScreen, which is long enough already.
        if (currentRoute != Routes.ONBOARDING) CelebrationHost()
    }
}
