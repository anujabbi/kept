package com.example.kept.feature.lock

import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockCopy
import com.example.kept.core.domain.LockMode
import com.example.kept.core.domain.LockReason
import com.example.kept.core.domain.NightNotice
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.SprigState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The lock screen during night lockdown (issue #164, story #168): its copy, its mode, and
 * [lockUiFlow] with a pinned clock. Night lockdown holds whether or not the habits are done, so a
 * finished day never opens the screen then; one screen shows when both locks hold, the night one.
 */
class NightLockTest {

    private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
        fun advance(by: Duration) { instant = instant.plus(by) }
    }

    private val zone: ZoneId = ZoneId.of("UTC")
    private val clock = MutableClock(Instant.parse("2026-11-21T23:30:00Z"), zone)
    private val time = TimeSource(clock)

    /** Night lockdown on, 11:00 pm to 7:00 am; one habit, [done] in time or not at all. */
    private fun lockState(done: Boolean, settings: Settings = Settings(onboardingDone = true, nightLockEnabled = true)): LockState {
        val habit = HabitEntity(id = 1, title = "Read", iconKey = "book", proofType = ProofType.MANUAL, targetValue = 1, unit = "", createdAt = 0)
        val due = Instant.parse("2026-11-21T21:00:00Z").toEpochMilli()
        val entry = if (done) HabitEntryEntity(habitId = 1, date = "2026-11-21", progressValue = 1, completedAt = due - 60_000) else null
        return LockState(
            settings = settings,
            today = TodaySummary(listOf(HabitToday(habit, entry, dueMillis = due))),
            breakActiveUntil = null,
            breaksUsedThisWeek = 0,
            userExceptions = emptySet(),
            unprotectedToday = false,
        )
    }

    private fun TestScope.ui(lock: MutableStateFlow<LockState>) = lockUiFlow(
        lockState = lock,
        sprig = MutableStateFlow(SprigState()),
        ticker = time.ticker(1_000),
        blockedPackage = MutableStateFlow("com.android.chrome"),
        time = time,
    ).stateIn(backgroundScope, SharingStarted.Eagerly, LockUi())

    @Test fun `the night copy`() {
        assertEquals("It's shutdown time.", LockCopy.NIGHT_HEADLINE)
        assertEquals("Apps open at 7:00 am.", LockCopy.nightLine(7 * 60))
        assertEquals("Apps open at 6:30 am.", LockCopy.nightLine(6 * 60 + 30))
        assertEquals(SprigPose.SLEEP, LockCopy.NIGHT_POSE)
        assertEquals("Night lockdown", NightNotice.TITLE)
        assertEquals("Apps open at 7:00 am", NightNotice.text(7 * 60))
    }

    @Test fun `at night a finished day keeps the screen locked instead of opening it`() {
        assertEquals(LockMode.LOCKED, LockMode.of(loaded = true, lockActive = true, blockedStillLocked = true, allDoneOnTime = true, night = true))
        assertEquals(LockMode.LOCKED, LockMode.of(loaded = true, lockActive = true, blockedStillLocked = true, allDoneOnTime = false, night = true))
        // Its app was added as an exception: gone, with no "Open {App}" on the way.
        assertEquals(LockMode.DISMISS, LockMode.of(loaded = true, lockActive = true, blockedStillLocked = false, allDoneOnTime = true, night = true))
        assertEquals(LockMode.LOCKED, LockMode.of(loaded = false, lockActive = false, blockedStillLocked = false, allDoneOnTime = false, night = true))
    }

    @Test fun `with every habit done the night lock holds and shows the night variant`() = runTest {
        val state = ui(MutableStateFlow(lockState(done = true)))
        runCurrent()
        assertTrue(state.value.lockActive)
        assertEquals(LockReason.NIGHT, state.value.reason)
        assertTrue(state.value.night)
        assertEquals(LockMode.LOCKED, state.value.mode)
        assertFalse("no Open app at night", state.value.open)
        assertFalse(state.value.shouldDismiss)
        assertEquals(7 * 60, state.value.nightUntilMinute)
    }

    @Test fun `a wrapping daily window that overlaps the night window shows one screen, the night one`() = runTest {
        val both = Settings(onboardingDone = true, nightLockEnabled = true, lockFromMinute = 20 * 60, dueMinute = 8 * 60)
        val state = ui(MutableStateFlow(lockState(done = false, settings = both)))
        runCurrent()
        assertEquals(LockReason.NIGHT, state.value.reason)
        assertTrue(state.value.night)
        assertEquals(LockMode.LOCKED, state.value.mode)
    }

    @Test fun `the lock stays up through midnight and the rollover and lifts at 7 00`() = runTest {
        val state = ui(MutableStateFlow(lockState(done = true)))
        runCurrent()
        for (at in listOf("2026-11-22T00:00:00Z", "2026-11-22T00:05:00Z", "2026-11-22T06:59:59Z")) {
            clock.instant = Instant.parse(at)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(at, LockMode.LOCKED, state.value.mode)
            assertTrue(at, state.value.night)
        }
        clock.advance(Duration.ofSeconds(1))
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(state.value.lockActive)
        assertEquals(null, state.value.reason)
    }

    @Test fun `night ending with habits left hands over to the daily lock screen`() = runTest {
        val state = ui(MutableStateFlow(lockState(done = false)))
        runCurrent()
        assertTrue(state.value.night)
        clock.instant = Instant.parse("2026-11-22T07:00:00Z")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(LockReason.DAILY, state.value.reason)
        assertFalse(state.value.night)
        assertEquals(LockMode.LOCKED, state.value.mode)
    }

    @Test fun `turning night lockdown off lifts the lock on the next emission`() = runTest {
        val lock = MutableStateFlow(lockState(done = false))
        val state = ui(lock)
        runCurrent()
        assertEquals(LockMode.LOCKED, state.value.mode)
        lock.value = lockState(done = false, settings = Settings(onboardingDone = true, nightLockEnabled = false))
        runCurrent()
        assertEquals(LockMode.DISMISS, state.value.mode)
    }

    @Test fun `a break at night takes the screen away`() = runTest {
        val lock = MutableStateFlow(lockState(done = false))
        val state = ui(lock)
        runCurrent()
        lock.value = lock.value.copy(breakActiveUntil = clock.instant.plusSeconds(900))
        runCurrent()
        assertEquals(null, state.value.reason)
        assertEquals(LockMode.DISMISS, state.value.mode)
    }
}
