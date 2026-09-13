package com.example.kept

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.ProofType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Issue #4, end to end through the real Room database: an exception added while the lock is
 * running must reach the live [LockRepository.observeState] flow that the watcher service and the
 * lock screen both read, and must flip both decisions the moment it lands.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExceptionsHonouredTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @Inject lateinit var db: KeptDatabase
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var lockRepo: LockRepository
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var time: TimeSource

    private val blocked = "com.example.blockedapp"

    @Before fun setUp() {
        hilt.inject()
        resetAppState(db, prefs)
        runBlocking {
            // A window that is open right now, with one habit still undone, so the lock is on.
            prefs.updateSettings { it.copy(onboardingDone = true, lockFromMinute = 0, dueMinute = 24 * 60 - 1) }
            habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
        }
    }

    private suspend fun snapshot() =
        lockRepo.observeState().first().snapshot(hardAllowlist = emptySet(), launchable = null)

    /** Waits for the live flow to carry [pkg], rather than assuming an instant Room invalidation. */
    private suspend fun awaitExceptionVisible(pkg: String, present: Boolean) = withTimeout(10_000) {
        lockRepo.observeState().first { (pkg in it.userExceptions) == present }
    }

    @Test fun an_exception_added_while_the_lock_runs_is_honoured_immediately() = runBlocking {
        val before = snapshot()
        assertTrue(LockPolicy.shouldLock(blocked, time.now(), time.localTime(), before))

        lockRepo.addException(blocked, "Blocked App")
        awaitExceptionVisible(blocked, present = true)

        val after = snapshot()
        assertTrue(blocked in after.userExceptions)
        assertFalse(LockPolicy.shouldLock(blocked, time.now(), time.localTime(), after))
    }

    /** The lock screen already on top of that package must come down too, not just stop re-firing. */
    @Test fun the_lock_screen_stops_covering_a_package_the_moment_it_is_excepted() = runBlocking {
        assertTrue(LockPolicy.lockScreenShouldStay(blocked, time.now(), time.localTime(), snapshot()))

        lockRepo.addException(blocked, "Blocked App")
        awaitExceptionVisible(blocked, present = true)

        assertFalse(LockPolicy.lockScreenShouldStay(blocked, time.now(), time.localTime(), snapshot()))
    }

    @Test fun removing_an_exception_puts_the_package_straight_back_under_the_lock() = runBlocking {
        lockRepo.addException(blocked, "Blocked App")
        awaitExceptionVisible(blocked, present = true)
        assertFalse(LockPolicy.shouldLock(blocked, time.now(), time.localTime(), snapshot()))

        lockRepo.removeException(blocked)
        awaitExceptionVisible(blocked, present = false)
        assertTrue(LockPolicy.shouldLock(blocked, time.now(), time.localTime(), snapshot()))
    }

    /** Other packages are untouched: one exception is not a general amnesty. */
    @Test fun an_exception_exempts_only_its_own_package() = runBlocking {
        lockRepo.addException(blocked, "Blocked App")
        awaitExceptionVisible(blocked, present = true)

        val s = snapshot()
        assertFalse(LockPolicy.shouldLock(blocked, time.now(), time.localTime(), s))
        assertTrue(LockPolicy.shouldLock("com.example.otherapp", time.now(), time.localTime(), s))
    }
}
