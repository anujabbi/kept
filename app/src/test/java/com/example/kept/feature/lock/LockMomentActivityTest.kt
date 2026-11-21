package com.example.kept.feature.lock

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.kept.MainActivity
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.feature.celebration.CelebrationViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * The lock-screen path of "Promise kept" (issue #130), on the real `LockActivity` and the real Hilt
 * graph. Ticking the last habit in time plays the full-screen moment inside the lock screen; a
 * rotation keeps it without playing it again; tapping carries on to the gold open state with
 * "Open {App}"; and KEPT, opened later the same day, does not show it a second time. A day
 * finished in KEPT still shows it there (the control that proves the KEPT check can see it).
 *
 * Goldens: `app/src/test/screenshots/lock_celebrate_*` (subclasses add the other sizes).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-420dpi")
open class LockMomentActivityTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var actions: HabitActions
    @Inject lateinit var time: TimeSource

    /** Names the golden for this screen size. */
    open val size: String = ""

    private var habitId = 0L

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            // A window open all day, with one habit undone, so the lock is on whatever the time.
            prefs.updateSettings {
                it.copy(onboardingDone = true, lockFromMinute = 0, dueMinute = 24 * 60 - 1, celebrationPendingDate = null, celebrationShownDate = null)
            }
            habitId = habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
        }
    }

    private fun lockIntent() = Intent(ApplicationProvider.getApplicationContext(), LockActivity::class.java)
        .putExtra(LockActivity.EXTRA_PKG, "com.android.chrome").putExtra(LockActivity.EXTRA_LABEL, "Chrome")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun finishOnTheLockScreen(): ActivityScenario<LockActivity> {
        val scenario = ActivityScenario.launch<LockActivity>(lockIntent())
        awaitTag("lock_habit_$habitId")
        compose.onNodeWithTag("lock_habit_$habitId").performClick()
        awaitTag("celebration")
        return scenario
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/lock_celebrate${size}_$name.png")

    @Test fun finishedOnTheLockScreenThePromiseKeptMomentPlaysThereThenTheOpenState() = playsOnTheLockScreen(1f)

    @Test fun atFont2() = playsOnTheLockScreen(2f)

    private fun playsOnTheLockScreen(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        val scenario = finishOnTheLockScreen()
        compose.onNodeWithTag("celebration_headline").assertTextEquals("Promise kept.")
        compose.onNodeWithTag("celebration_line").assertTextEquals("Day 1 of your streak. Apps are open.")
        shot("font$fontScale")
        val today = time.todayKey()
        runBlocking {
            assertEquals("the lock screen took today's moment", today, prefs.currentSettings().celebrationShownDate)
            assertNull("nothing left pending for KEPT", prefs.currentSettings().celebrationPendingDate)
        }

        // Rotation: the moment is still up, and its motion is not played again.
        scenario.recreate()
        awaitTag("celebration")
        scenario.onActivity { assertFalse("played once", ViewModelProvider(it)[LockViewModel::class.java].momentIntroPending) }

        // Tap to carry on: the gold open state.
        compose.onNodeWithTag("celebration").performClick()
        awaitTag("lock_open_app")
        compose.onNodeWithTag("lock_open_app").assertTextEquals("Open Chrome")
        compose.onNodeWithTag("lock_headline").assertTextEquals("Chrome is open.")
        compose.onAllNodes(hasTestTag("celebration")).fetchSemanticsNodes().let { assertEquals("gone", 0, it.size) }
        if (fontScale == 1f) shot("then_open_font1.0")
        RuntimeEnvironment.setFontScale(1f)
    }

    /**
     * Ticks the last habit with the frame clock in hand and checks every frame, until the open state
     * has shown and settled, that the "Promise kept" moment never appears, not even for a frame
     * while the tick is in flight (review of PR #144).
     */
    private fun tickAndNeverCelebrate(what: String) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("lock_habit_$habitId").performClick()
        var openFrames = 0
        var frames = 0
        while (openFrames < 30) {
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(5)
            assertEquals("$what: frame $frames shows the moment", 0, compose.onAllNodes(hasTestTag("celebration")).fetchSemanticsNodes().size)
            if (compose.onAllNodes(hasTestTag("lock_open_app")).fetchSemanticsNodes().isNotEmpty()) openFrames++
            frames++
            check(frames < 3_000) { "$what: the open state never showed" }
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("lock_open_app").assertTextEquals("Open Chrome")
    }

    @Test fun finishedAgainOnTheLockScreenAfterAnUndoDoesNotCelebrateAgain() {
        finishOnTheLockScreen()
        compose.onNodeWithTag("celebration").performClick()
        awaitTag("lock_open_app")
        // Hold to undo in the open state: back to locked.
        compose.onNodeWithTag("lock_habit_$habitId").performTouchInput { longClick() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("lock_open_app")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("lock_headline").assertTextEquals("Chrome can wait.")
        tickAndNeverCelebrate("re-finished on the lock screen")
    }

    @Test fun aDayCelebratedInKeptThenUndoneDoesNotCelebrateOnTheLockScreen() {
        runBlocking {
            actions.complete(habitId)
            assertTrue("KEPT showed it", actions.claimMoment())
            actions.undo(habitId)
        }
        ActivityScenario.launch<LockActivity>(lockIntent())
        awaitTag("lock_habit_$habitId")
        tickAndNeverCelebrate("celebrated in KEPT, finished on the lock screen")
    }

    @Test fun keptDoesNotShowItAgainTheSameDay() {
        finishOnTheLockScreen().close()
        val main = ActivityScenario.launch(MainActivity::class.java)
        awaitTag("home_streak")
        compose.waitForIdle()
        assertEquals("no second showing in KEPT", 0, compose.onAllNodes(hasTestTag("celebration")).fetchSemanticsNodes().size)
        main.close()
    }

    @Test fun aDayFinishedInKeptStillShowsItInKept() {
        runBlocking { actions.complete(habitId) }
        val main = ActivityScenario.launch(MainActivity::class.java)
        awaitTag("celebration")
        compose.onNodeWithTag("celebration_headline").assertTextEquals("Promise kept.")
        runBlocking { assertEquals(time.todayKey(), prefs.currentSettings().celebrationShownDate) }
        // Rotation keeps it up without playing it again; a tap anywhere carries on.
        main.recreate()
        awaitTag("celebration")
        main.onActivity { assertFalse("played once", ViewModelProvider(it)[CelebrationViewModel::class.java].introPending) }
        compose.onNodeWithTag("celebration").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("celebration")).fetchSemanticsNodes().isEmpty() }
        main.close()
        // And the lock screen, if it were raised now, plays nothing more today.
        runBlocking { assertFalse(actions.claimMoment()) }
    }
}

/** The same path at 360 × 640dp. Goldens: `lock_celebrate_360_*`. */
@HiltAndroidTest
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w360dp-h640dp-420dpi")
class LockMomentNarrowActivityTest : LockMomentActivityTest() {
    override val size = "_360"
}
