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
import com.example.kept.feature.celebration.SprigEvolvesScreen
import com.example.kept.feature.gallery.GalleryScreen
import com.example.kept.feature.home.HomeScreen
import com.example.kept.feature.onboarding.OnboardingScreen
import com.example.kept.feature.recap.RecapScreen
import com.example.kept.feature.settings.ExceptionsScreen
import com.example.kept.feature.settings.HabitEditScreen
import com.example.kept.feature.settings.WeeklyGoalEditScreen
import com.example.kept.feature.settings.PromisesScreen
import com.example.kept.feature.settings.HistoryScreen
import com.example.kept.feature.settings.PermissionsScreen
import com.example.kept.feature.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val RECAP = "recap/{date}"
    const val EXCEPTIONS = "exceptions"
    /** Your promises (#236): every daily habit and weekly goal. */
    const val PROMISES = "promises"
    const val HABIT_ADD = "habits/add"
    const val HABIT_EDIT = "habits/edit/{habitId}"
    const val WEEKLY_GOAL_ADD = "weekly-goals/add"
    const val WEEKLY_GOAL_EDIT = "weekly-goals/edit/{goalId}"
    const val PERMISSIONS = "permissions"
    const val HISTORY = "history"
    const val REVEAL = "reveal/{galleryId}"

    fun recap(date: String) = "recap/$date"
    fun reveal(id: Long) = "reveal/$id"
    fun habitEdit(id: Long) = "habits/edit/$id"
    fun weeklyGoalEdit(id: Long) = "weekly-goals/edit/$id"
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
    Routes.PROMISES -> Screens.PROMISES
    Routes.HABIT_ADD -> Screens.ADD_HABIT
    Routes.HABIT_EDIT -> Screens.EDIT_HABIT
    Routes.WEEKLY_GOAL_ADD -> Screens.ADD_WEEKLY_GOAL
    Routes.WEEKLY_GOAL_EDIT -> Screens.EDIT_WEEKLY_GOAL
    Routes.PERMISSIONS -> Screens.PERMISSIONS
    Routes.HISTORY -> Screens.HISTORY
    Routes.REVEAL -> Screens.REVEAL
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
/**
 * Navigates to [to] only while [from] is on top, so a double tap opens one page, not two
 * (review on #149: two Add pages at 9 habits could save an 11th).
 */
internal fun NavController.navigateFrom(from: String, to: String) {
    if (currentBackStackEntry?.destination?.route == from) navigate(to)
}

/** Pops only while [route] is on top, so a second close cannot pop the page under it. */
internal fun NavController.popFrom(route: String) {
    if (currentBackStackEntry?.destination?.route == route) popBackStack()
}

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
                            onOpenHabits = { nav.navigateFrom(Routes.HOME, Routes.PROMISES) },
                            onAddWeeklyGoal = { nav.navigateFrom(Routes.HOME, Routes.WEEKLY_GOAL_ADD) },
                        )
                    }
                    composable(Routes.GALLERY) { GalleryScreen() }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenExceptions = { nav.navigate(Routes.EXCEPTIONS) },
                            onOpenPromises = { nav.navigateFrom(Routes.SETTINGS, Routes.PROMISES) },
                            onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                            onOpenHistory = { nav.navigate(Routes.HISTORY) },
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
                    composable(Routes.PROMISES) {
                        PromisesScreen(
                            onBack = { nav.popFrom(Routes.PROMISES) },
                            onAdd = { nav.navigateFrom(Routes.PROMISES, Routes.HABIT_ADD) },
                            onEditHabit = { nav.navigateFrom(Routes.PROMISES, Routes.habitEdit(it)) },
                            onEditGoal = { nav.navigateFrom(Routes.PROMISES, Routes.weeklyGoalEdit(it)) },
                        )
                    }
                    composable(Routes.HABIT_ADD) { HabitEditScreen(habitId = null, onClose = { nav.popFrom(Routes.HABIT_ADD) }) }
                    composable(Routes.HABIT_EDIT, arguments = listOf(navArgument("habitId") { type = NavType.LongType })) {
                        HabitEditScreen(habitId = it.arguments!!.getLong("habitId"), onClose = { nav.popFrom(Routes.HABIT_EDIT) })
                    }
                    composable(Routes.WEEKLY_GOAL_ADD) { WeeklyGoalEditScreen(goalId = null, onClose = { nav.popFrom(Routes.WEEKLY_GOAL_ADD) }) }
                    composable(Routes.WEEKLY_GOAL_EDIT, arguments = listOf(navArgument("goalId") { type = NavType.LongType })) {
                        WeeklyGoalEditScreen(goalId = it.arguments!!.getLong("goalId"), onClose = { nav.popFrom(Routes.WEEKLY_GOAL_EDIT) })
                    }
                    composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.HISTORY) {
                        HistoryScreen(onBack = { nav.popBackStack() }, onOpenRecap = { nav.navigate(Routes.recap(it)) })
                    }
                    composable(Routes.REVEAL, arguments = listOf(navArgument("galleryId") { type = NavType.LongType })) {
                        // "Sprig is now {Form}" (issue #130) replaces the old reveal, at the same moment.
                        SprigEvolvesScreen(onClose = { nav.popBackStack() })
                    }
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
