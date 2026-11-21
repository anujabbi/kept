package com.example.kept.feature.lock

import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockMode
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
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
 * [lockUiFlow], the `combine` behind `LockViewModel.state`. It re-reads the injected [TimeSource]
 * on every tick of `TimeSource.ticker(1_000)` (issue #15), so the window ending lifts the lock
 * on time; and a day finished in time turns the lock screen into its open state instead of
 * dismissing it (issue #127).
 */
class LockUiFlowTest {

    /** A [Clock] the test can wind forward; [TimeSource] reads it on every tick. */
    private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
        fun advance(by: Duration) { instant = instant.plus(by) }
    }

    private val zone: ZoneId = ZoneId.of("UTC")
    // The default window locks from 7:00 am to 9:00 pm.
    private val clock = MutableClock(Instant.parse("2026-09-16T20:59:59Z"), zone)
    private val time = TimeSource(clock)

    private fun lockState(done: Boolean): LockState {
        val habit = HabitEntity(id = 1, title = "Read", iconKey = "book", proofType = ProofType.MANUAL, targetValue = 1, unit = "", createdAt = 0)
        val due = Instant.parse("2026-09-16T21:00:00Z").toEpochMilli()
        val entry = if (done) HabitEntryEntity(habitId = 1, date = "2026-09-16", progressValue = 1, completedAt = due - 60_000) else null
        return LockState(
            settings = Settings(onboardingDone = true),
            today = TodaySummary(listOf(HabitToday(habit, entry, dueMillis = due))),
            breakActiveUntil = null,
            breaksUsedThisWeek = 0,
            userExceptions = emptySet(),
            unprotectedToday = false,
        )
    }

    private fun TestScope.ui(lock: LockState) = lockUiFlow(
        lockState = flowOf(lock),
        sprig = flowOf(SprigState()),
        ticker = time.ticker(1_000),
        blockedPackage = MutableStateFlow("com.android.chrome"),
        time = time,
    ).stateIn(backgroundScope, SharingStarted.Eagerly, LockUi())

    @Test fun `the window ending lifts the lock on the next tick`() = runTest {
        val state = ui(lockState(done = false))
        runCurrent()
        assertEquals(LockMode.LOCKED, state.value.mode)

        clock.advance(Duration.ofSeconds(1))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(LockMode.DISMISS, state.value.mode)
        assertTrue(state.value.shouldDismiss)
    }

    @Test fun `a day finished in time opens the lock screen instead of dismissing it`() = runTest {
        val state = ui(lockState(done = true))
        runCurrent()
        assertFalse(state.value.lockActive)
        assertEquals(LockMode.OPEN, state.value.mode)
        assertTrue(state.value.open)
        assertFalse(state.value.shouldDismiss)
    }

    @Test fun `nothing is decided before the first emission`() {
        assertEquals(LockMode.LOCKED, LockUi().mode)
        assertFalse(LockUi().shouldDismiss)
    }
}
