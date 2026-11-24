package com.example.kept.feature.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.TestViewModels
import com.example.kept.core.analytics.NoOpAnalytics
import com.example.kept.core.data.DayCompletionReactor
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.lock.Permissions
import com.example.kept.core.ui.shots.Shot
import com.example.kept.feature.celebration.RevealViewModel
import com.example.kept.feature.celebration.SprigEvolvesScreen
import com.example.kept.feature.home.HomeScreen
import com.example.kept.feature.home.HomeViewModel
import com.example.kept.feature.recap.RecapScreen
import com.example.kept.feature.recap.RecapViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The recap after "Sprig is now …" closes (issue #188). The real Home, evolution and recap
 * screens and their ViewModels, on an in-memory database, in a nav graph wired as `KeptApp` wires
 * it: a rollover has left an unseen unlock and a pending recap, Home opens the evolution screen,
 * and "Carry on" leads to the recap.
 *
 * What it guards: Home's state is shared `WhileSubscribed(5_000)`, so after more than five
 * seconds on the evolution screen Home comes back holding the last value it saw, and it leaves
 * for the recap before it collects a newer one. If that remembered value alone decides whether a
 * recap is pending, every return to Home opens the recap again, and Done and Back seem to do
 * nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class RecapAfterEvolutionTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class FixedClock(val now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    // Thursday 26 November 2026, just after the rollover that closed the 25th.
    private val time = TimeSource(FixedClock(Instant.parse("2026-11-26T00:05:00Z")))
    private val yesterday = "2026-11-25"
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val prefs = KeptPreferences(ctx)
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
    private val sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time)
    private val weekly = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)
    private val days = DayRepository(db.dayRecordDao(), time)
    private val vms = TestViewModels()

    private lateinit var nav: NavHostController
    private lateinit var homeVm: HomeViewModel

    /**
     * Home's lifecycle, moved by the test. On the phone a Home that comes back and leaves for the
     * recap in the same frame is stopped again before `collectAsStateWithLifecycle` has begun to
     * collect (logged on `kept_api35`: composed, the effect, ON_STOP within 5 ms, no collection).
     * Under Robolectric the collection begins at once, which hides the fault, so Home is held at
     * CREATED here until the test says it stays on screen.
     */
    private val homeOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun homeIs(state: Lifecycle.State) {
        rule.runOnUiThread { homeOwner.registry.currentState = state }
        rule.waitForIdle()
    }

    @Before fun reset() = runBlocking { prefs.clearAll() }

    @After fun close() {
        vms.clear()
        db.close()
    }

    /** What a rollover that evolves Sprig leaves behind: the closed day, an unseen unlock, a pending recap. */
    private fun rolloverWithEvolution(): Long = runBlocking {
        habits.addHabit("Read", "book", ProofType.MANUAL, 10, "pages")
        db.dayRecordDao().upsert(
            DayRecordEntity(
                date = yesterday, habitsDone = 1, habitsTotal = 1, countedForStreak = true,
                levelEnd = 15, formId = SprigForm.THICKET.id, streakEnd = 14, finalized = true,
            ),
        )
        sprig.insertUnlock(SprigForm.THICKET, LocalDate.parse(yesterday), 14)
        prefs.updateSettings { it.copy(onboardingDone = true, pendingRecapDate = yesterday) }
        sprig.unseenUnlocks.first().single().id
    }

    private fun showApp(unlockId: Long) {
        val home = vms.create {
            HomeViewModel(
                habits = habits,
                actions = HabitActions(habits, sprig, prefs, time, NoOpAnalytics(), object : DayCompletionReactor { override suspend fun onDayCompleted() = Unit }, weekly),
                sprigRepo = sprig,
                lockRepo = LockRepository(db.allowedAppDao(), db.lockBreakDao(), db.protectionGapDao(), db.dayRecordDao(), prefs, habits, sprig, time, weekly),
                dayRepo = days,
                prefs = prefs,
                permissions = Permissions(ctx),
                time = time,
                weeklyGoals = weekly,
            )
        }
        homeVm = home
        val reveal = vms.create { RevealViewModel(sprig, SavedStateHandle(mapOf("galleryId" to unlockId))) }
        val recap = vms.create { RecapViewModel(days, SavedStateHandle(mapOf("date" to yesterday))) }
        rule.setContent {
            Shot {
                nav = rememberNavController()
                // The three destinations as KeptApp wires them.
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        CompositionLocalProvider(LocalLifecycleOwner provides homeOwner) {
                            HomeScreen(
                                onOpenRecap = { nav.navigate(Routes.recap(it)) },
                                onOpenReveal = { nav.navigate(Routes.reveal(it)) },
                                onOpenPermissions = {}, onOpenHabits = {}, onAddWeeklyGoal = {},
                                vm = home,
                            )
                        }
                    }
                    composable(Routes.RECAP, arguments = listOf(navArgument("date") { type = NavType.StringType })) {
                        RecapScreen(date = it.arguments!!.getString("date")!!, onClose = { nav.popBackStack() }, onOpenSprigs = {}, vm = recap)
                    }
                    composable(Routes.REVEAL, arguments = listOf(navArgument("galleryId") { type = NavType.LongType })) {
                        SprigEvolvesScreen(onClose = { nav.popBackStack() }, vm = reveal)
                    }
                }
            }
        }
    }

    private fun stack(): List<String?> =
        nav.currentBackStack.value.filter { it.destination !is NavGraph }.map { it.destination.route }

    private fun waitForStack(vararg routes: String) {
        runCatching { rule.waitUntil(10_000) { rule.waitForIdle(); stack() == routes.toList() } }
        assertEquals(routes.toList(), stack())
    }

    private fun pendingRecap(): String? = runBlocking { prefs.currentSettings().pendingRecapDate }

    /** Time passes with the app left alone: long enough for Home's shared state to stop collecting. */
    private fun leaveFor(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
        rule.waitForIdle()
    }

    /** Evolution screen up for [onEvolution] seconds, then "Carry on": the recap is on top of Home. */
    private fun carryOnToTheRecap(onEvolution: Long) {
        val unlock = rolloverWithEvolution()
        homeIs(Lifecycle.State.RESUMED)
        showApp(unlock)
        waitForStack(Routes.HOME, Routes.REVEAL)
        homeIs(Lifecycle.State.CREATED)
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("evolve_carry_on")).fetchSemanticsNodes().isNotEmpty() }
        // The unlock is marked seen as the evolution screen opens, and Home's state hears of it
        // while its upstream is still running: a pending recap, nothing unseen.
        rule.waitUntil(10_000) { rule.waitForIdle(); homeVm.state.value.let { it.unseenUnlock == null && it.pendingRecapDate == yesterday } }
        leaveFor(onEvolution)
        rule.onNodeWithTag("evolve_carry_on").performClick()
        waitForStack(Routes.HOME, Routes.RECAP)
        rule.waitUntil(10_000) { rule.waitForIdle(); pendingRecap() == null }
    }

    /** The recap is gone and nothing opens over Home again, at once or once Home is collecting. */
    private fun assertHomeStays() {
        waitForStack(Routes.HOME)
        homeIs(Lifecycle.State.RESUMED)
        // Home is collecting again and hears that nothing is pending.
        rule.waitUntil(10_000) { rule.waitForIdle(); homeVm.state.value.pendingRecapDate == null }
        leaveFor(6)
        assertEquals("the recap must not come back", listOf(Routes.HOME), stack())
        rule.onNodeWithTag("recap_screen").assertDoesNotExist()
    }

    @Test fun doneClosesTheRecapAfterTenSecondsOnTheEvolutionScreen() {
        carryOnToTheRecap(onEvolution = 10)
        rule.onNodeWithTag("recap_done_button").performClick()
        assertHomeStays()
    }

    @Test fun backClosesTheRecapAfterTenSecondsOnTheEvolutionScreen() {
        carryOnToTheRecap(onEvolution = 10)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertHomeStays()
    }

    @Test fun doneClosesTheRecapWhenCarryOnIsTappedAtOnce() {
        carryOnToTheRecap(onEvolution = 0)
        rule.onNodeWithTag("recap_done_button").performClick()
        assertHomeStays()
    }
}
