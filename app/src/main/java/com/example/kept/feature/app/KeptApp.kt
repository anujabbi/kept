package com.example.kept.feature.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kept.core.FeatureFlags
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.analytics.Screens
import com.example.kept.core.ui.KeptTheme
import com.example.kept.feature.buddy.BuddyScreen
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
    const val BUDDY = "buddy"
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
    Routes.BUDDY -> Screens.BUDDY
    Routes.SETTINGS -> Screens.SETTINGS
    Routes.RECAP -> Screens.RECAP
    Routes.EXCEPTIONS -> Screens.EXCEPTIONS
    Routes.HABITS -> Screens.HABITS
    Routes.PERMISSIONS -> Screens.PERMISSIONS
    Routes.REVEAL -> Screens.REVEAL
    Routes.ROADMAP -> Screens.ROADMAP
    else -> null
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOfNotNull(
    Tab(Routes.HOME, "Today", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.GALLERY, "Sprig", Icons.Outlined.Spa, Icons.Rounded.Spa),
    if (FeatureFlags.showBuddy) Tab(Routes.BUDDY, "Buddy", Icons.Outlined.People, Icons.Rounded.People) else null,
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun KeptApp(startDestination: String, pendingRoute: String?, consumeRoute: () -> Unit, analytics: Analytics) {
    val nav = rememberNavController()
    val c = KeptTheme.colors
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }

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
            r == "buddy" && FeatureFlags.showBuddy -> nav.navigate(Routes.BUDDY) { launchSingleTop = true }
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
                            onOpenRoadmap = { nav.navigate(Routes.ROADMAP) },
                        )
                    }
                    composable(Routes.GALLERY) { GalleryScreen(onOpenRoadmap = { nav.navigate(Routes.ROADMAP) }) }
                    if (FeatureFlags.showBuddy) {
                        composable(Routes.BUDDY) { BuddyScreen() }
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenExceptions = { nav.navigate(Routes.EXCEPTIONS) },
                            onOpenHabits = { nav.navigate(Routes.HABITS) },
                            onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                            onOpenRecap = { nav.navigate(Routes.recap(it)) },
                        )
                    }
                    composable(Routes.RECAP, arguments = listOf(navArgument("date") { type = NavType.StringType })) {
                        RecapScreen(date = it.arguments!!.getString("date")!!, onClose = { nav.popBackStack() })
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
                BottomBar(nav, currentRoute)
            }
        }
        // Above the nav graph and the bottom bar: the celebration is a full-screen moment, and it
        // must be able to cover whichever tab is open when the day is finished (issue #9). Kept out
        // of HomeScreen, which is long enough already.
        if (currentRoute != Routes.ONBOARDING) CelebrationHost()
    }
}

@Composable
private fun BottomBar(nav: NavHostController, currentRoute: String?) {
    val c = KeptTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface2)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            tabs.forEach { t ->
                val selected = t.route == currentRoute
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!selected) nav.navigate(t.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (selected) t.selectedIcon else t.icon, t.label, Modifier.size(24.dp), tint = if (selected) c.purple600 else c.textMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (selected) c.purple600 else c.textMuted)
                }
            }
        }
    }
}
