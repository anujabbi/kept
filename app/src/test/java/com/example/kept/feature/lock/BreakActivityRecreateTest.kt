package com.example.kept.feature.lock

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import javax.inject.Inject

/**
 * The real `LockActivity`: "Break the lock" opens the break screen, then a rotation rebuilds the
 * activity. The break screen must still be there and its countdown must carry on where it was,
 * not start over or drop back to the lock screen (issue #128). Robolectric's clock stands in for
 * `SystemClock.elapsedRealtime`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-420dpi")
class BreakActivityRecreateTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var habits: HabitRepository

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            prefs.updateSettings { it.copy(onboardingDone = true, lockFromMinute = 0, dueMinute = 24 * 60 - 1) }
            habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
        }
    }

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitSeconds(s: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(s))
        compose.waitForIdle()
    }

    @Test fun theCountdownCarriesOnAcrossARotation() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_PKG, "com.android.chrome").putExtra(LockActivity.EXTRA_LABEL, "Chrome")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val scenario = ActivityScenario.launch<LockActivity>(intent)
        awaitTag("break_lock")
        compose.onNodeWithTag("break_lock").performClick()
        awaitTag("break_screen")
        waitSeconds(20)
        compose.onNodeWithTag("break_countdown").assertTextEquals("0:40")

        scenario.recreate()
        awaitTag("break_screen")
        compose.onNodeWithTag("break_countdown").assertTextEquals("0:40")
        compose.onNodeWithTag("unlock_anyway").assertIsNotEnabled()

        waitSeconds(40)
        compose.onNodeWithTag("break_countdown").assertTextEquals("0:00")
        compose.onNodeWithTag("break_clock_label").assertTextEquals("You can unlock now")
        compose.onNodeWithTag("unlock_anyway").assertIsEnabled()

        // "Never mind, go back" returns to the lock screen; a new break starts from a full minute.
        compose.onNodeWithTag("break_go_back").performClick()
        awaitTag("lock_screen")
        compose.onNodeWithTag("break_lock").performClick()
        awaitTag("break_screen")
        compose.onNodeWithTag("break_countdown").assertTextEquals("1:00")
    }

    /**
     * Break, then Home, then a blocked app a while later: the watcher raises the screen again
     * (`onNewIntent`, a new showing). It opens on the lock screen, not on the old break at 0:00, and
     * a break taken now waits its own minute (review of PR #141).
     */
    @Test fun aNewShowingOpensOnTheLockScreenNotAnOldBreak() {
        val scenario = ActivityScenario.launch<LockActivity>(intent())
        awaitTag("break_lock")
        compose.onNodeWithTag("break_lock").performClick()
        awaitTag("break_screen")
        waitSeconds(70)
        compose.onNodeWithTag("unlock_anyway").assertIsEnabled()

        scenario.onActivity { newIntent(it, intent("com.instagram.android", "Instagram")) }
        awaitTag("lock_screen")
        compose.onNodeWithTag("break_screen").assertDoesNotExist()
        compose.onNodeWithTag("lock_headline").assertTextEquals("Instagram can wait.")

        compose.onNodeWithTag("break_lock").performClick()
        awaitTag("break_screen")
        compose.onNodeWithTag("break_countdown").assertTextEquals("1:00")
        compose.onNodeWithTag("unlock_anyway").assertIsNotEnabled()
    }

    private fun intent(pkg: String = "com.android.chrome", label: String = "Chrome") =
        Intent(ApplicationProvider.getApplicationContext(), LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_PKG, pkg).putExtra(LockActivity.EXTRA_LABEL, label)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** What the system does when the watcher raises the single-task lock screen again. */
    private fun newIntent(activity: LockActivity, i: Intent) {
        val m = android.app.Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
        m.isAccessible = true
        m.invoke(activity, i)
    }
}
