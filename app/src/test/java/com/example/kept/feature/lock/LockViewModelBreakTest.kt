package com.example.kept.feature.lock

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.analytics.Screens
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.di.ApplicationScope
import com.example.kept.core.domain.ProofType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import javax.inject.Inject

/**
 * `LockViewModel`'s break: what it reports and when (once per break, not per recreation), and that
 * a new showing of the lock screen never opens on a break left over from an earlier one (review of
 * PR #141). Real repositories on the Hilt graph; a recording [Analytics]; Robolectric's clock
 * stands in for `SystemClock.elapsedRealtime`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class LockViewModelBreakTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var lockRepo: LockRepository
    @Inject lateinit var sprigRepo: SprigRepository
    @Inject lateinit var actions: HabitActions
    @Inject lateinit var time: TimeSource
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private class Recorder : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override val enabled = true
        override fun capture(event: String, properties: Map<String, Any>) { events += event to properties }
        override fun screen(name: String, properties: Map<String, Any>) { events += "screen:$name" to properties }
        override fun register(key: String, value: Any) = Unit
        override fun restore(enabled: Boolean) = Unit
        override fun setEnabled(enabled: Boolean) = Unit
        fun count(event: String) = events.count { it.first == event }
    }

    private val analytics = Recorder()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            prefs.updateSettings { it.copy(onboardingDone = true, lockFromMinute = 0, dueMinute = 24 * 60 - 1) }
            habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
        }
    }

    @After fun clear() = stores.forEach { it.clear() }

    /** A ViewModel the way the activity gets one, so `viewModelScope` is cleared after the test. */
    private fun viewModel(saved: SavedStateHandle): LockViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LockViewModel(saved, lockRepo, sprigRepo, actions, time, appScope, analytics) as T
        }
        return ViewModelProvider(store, factory)[LockViewModel::class.java]
    }

    private fun waitSeconds(s: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(s))

    @Test fun breakStartedIsReportedOncePerBreakNotPerRecreation() {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.startBreak()
        vm.startBreak()
        assertEquals(1, analytics.count("lock_break_started"))
        assertEquals(1, analytics.count("screen:${Screens.BREAK}"))

        // Recreation after process death: a new ViewModel on the same saved state.
        val rebuilt = viewModel(saved)
        assertTrue(rebuilt.breakTimer.breaking)
        rebuilt.startBreak()
        assertEquals(1, analytics.count("lock_break_started"))
        assertEquals(1, analytics.count("screen:${Screens.BREAK}"))

        rebuilt.cancelBreak()
        assertEquals(1, analytics.count("lock_break_cancelled"))
        assertFalse(rebuilt.breakTimer.breaking)
    }

    @Test fun confirmingReportsHowLongTheScreenWasOpen() {
        val vm = viewModel(SavedStateHandle())
        vm.startBreak()
        waitSeconds(65)
        assertEquals(0, vm.breakTimer.secondsLeft)
        var done = false
        vm.breakLock { done = true }
        val deadline = System.currentTimeMillis() + 10_000
        while (!done && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("breakLock finished", done)
        val broken = analytics.events.single { it.first == "lock_broken" }
        val waited = broken.second["seconds_waited"] as Int
        assertTrue("seconds_waited $waited", waited >= 60)
    }

    @Test fun aNewShowingNeverOpensOnAnOldBreak() {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.startBreak()
        vm.breakTimer.markIntroPlayed()
        waitSeconds(70)
        assertEquals(0, vm.breakTimer.secondsLeft)

        // Home, then later a blocked app: the watcher raises the screen again.
        vm.onShown()
        assertFalse("the new showing opens on the break", vm.breakTimer.breaking)
        assertEquals("closing a stale break is not the user's cancel", 0, analytics.count("lock_break_cancelled"))
        assertFalse("the reset is saved too", viewModel(saved).breakTimer.breaking)

        vm.startBreak()
        assertEquals(60, vm.breakTimer.secondsLeft)
        assertFalse("the rise plays for the new break", vm.breakTimer.introPlayed)
        assertEquals(2, analytics.count("lock_break_started"))
    }
}
