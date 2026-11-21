package com.example.kept.feature.lock

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.di.ApplicationScope
import com.example.kept.core.di.drainAndCancel
import com.example.kept.core.domain.ProofType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineScope
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
import javax.inject.Inject

/**
 * The real `LockActivity` on the real Hilt graph (Room, preferences). The lock screen is raised,
 * raised again over another app (`onNewIntent`, a new showing), then the activity is recreated by
 * a configuration change while its ViewModel lives on. The screen must still show today's habits
 * for the app it was raised for last, not a blank stale-guarded state, and must still get out of
 * the way when the lock lifts for that app (review of PR #140, round 2).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-420dpi")
class LockActivityRecreateTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var lockRepo: LockRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    /** No tick's write may land in the next test: the preferences outlive it (issue #146). */
    @After fun drainAppScope() = appScope.drainAndCancel()

    private val chrome = "com.android.chrome"
    private val instagram = "com.instagram.android"

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            // A window open all day, with one habit undone, so the lock is on whatever the time.
            prefs.updateSettings { it.copy(onboardingDone = true, lockFromMinute = 0, dueMinute = 24 * 60 - 1) }
            habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
        }
    }

    private fun intent(pkg: String = chrome, label: String = "Chrome") = Intent(ApplicationProvider.getApplicationContext(), LockActivity::class.java)
        .putExtra(LockActivity.EXTRA_PKG, pkg).putExtra(LockActivity.EXTRA_LABEL, label)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun aRecreatedLockScreenStillShowsTodayAndStillDismisses() {
        val scenario = ActivityScenario.launch<LockActivity>(intent())
        awaitTag("lock_habit_1")

        // Raised again, over another app: a new showing.
        scenario.onActivity { newIntent(it, intent(instagram, "Instagram")) }
        awaitTag("lock_habit_1")
        compose.onNodeWithTag("lock_headline").assertTextEquals("Instagram can wait.")

        // Rotation, font change or split screen: the activity is rebuilt, the ViewModel is kept.
        scenario.recreate()
        awaitTag("lock_habit_1")
        compose.onNodeWithTag("lock_habit_1").assertIsDisplayed()
        compose.onNodeWithTag("lock_headline").assertTextEquals("Instagram can wait.")
        compose.onNodeWithTag("lock_count").assertTextEquals("0 of 1")

        // The lock lifts for the app it now covers (made an exception): the screen goes to the launcher.
        runBlocking { lockRepo.addException(instagram, "Instagram") }
        compose.waitUntil(10_000) { scenario.state == Lifecycle.State.DESTROYED || finishing(scenario) }
        val home = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.CATEGORY_HOME, home?.categories?.single())
    }

    /** What the system does when the watcher raises the single-task lock screen again. */
    private fun newIntent(activity: LockActivity, i: Intent) {
        val m = android.app.Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        m.isAccessible = true
        m.invoke(activity, i)
    }

    private fun finishing(scenario: ActivityScenario<LockActivity>): Boolean {
        var f = false
        runCatching { scenario.onActivity { f = it.isFinishing } }
        return f
    }
}
