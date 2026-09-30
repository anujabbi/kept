package com.example.kept.feature.lock

import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The lock screen's clock never ticked because it was formatted inline in composition (issue
 * #15). It now rides [lockUiFlow] — the `combine` behind `LockViewModel.state` — which already
 * re-runs on `TimeSource.ticker(1_000)`, so a pinned clock that is nudged past the minute must
 * show up in [LockUi.clockLabel] on the next tick.
 */
class LockViewModelClockTest {

    /** A [Clock] the test can wind forward; [TimeSource] reads it on every tick. */
    private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
        fun advance(by: Duration) { instant = instant.plus(by) }
    }

    private val zone: ZoneId = ZoneId.of("UTC")
    private val clock = MutableClock(Instant.parse("2026-09-16T09:59:59Z"), zone)
    private val time = TimeSource(clock)

    private fun lockState(): LockState {
        val habit = HabitEntity(id = 1, title = "Read", iconKey = "book", proofType = ProofType.MANUAL, targetValue = 1, unit = "", createdAt = 0)
        return LockState(
            settings = Settings(onboardingDone = true),
            today = TodaySummary(listOf(HabitToday(habit, null))),
            breakActiveUntil = null,
            breaksUsedThisWeek = 0,
            userExceptions = emptySet(),
            unprotectedToday = false,
        )
    }

    @Test fun `clock label follows the time source across the minute boundary`() = runTest {
        val state = lockUiFlow(
            lockState = flowOf(lockState()),
            sprig = flowOf(SprigState()),
            ticker = time.ticker(1_000),
            blockedPackage = MutableStateFlow(null),
            time = time,
        ).stateIn(backgroundScope, SharingStarted.Eagerly, LockUi())

        runCurrent()
        assertEquals("9:59", state.value.clockLabel)

        clock.advance(Duration.ofSeconds(1))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("10:00", state.value.clockLabel)
    }

    @Test fun `clock label is empty before the first emission`() {
        assertEquals("", LockUi().clockLabel)
    }
}
