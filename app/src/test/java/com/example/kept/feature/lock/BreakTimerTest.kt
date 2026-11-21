package com.example.kept.feature.lock

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The break countdown lives in the ViewModel (`BreakTimer`), not the composable, so a rotation or
 * process death keeps the break screen and its minute (issue #128). Time here is the test
 * scheduler's, standing in for `SystemClock.elapsedRealtime`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BreakTimerTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val clock = { scope.testScheduler.currentTime + 5_000_000L }

    @Test fun countsDownSixtySecondsThenStops() {
        val t = BreakTimer(SavedStateHandle(), scope, clock)
        assertFalse(t.breaking)
        assertEquals(60, t.secondsLeft)
        assertTrue(t.start())
        scope.runCurrent()
        assertTrue(t.breaking)
        assertEquals(60, t.secondsLeft)
        scope.advanceTimeBy(1_001); scope.runCurrent()
        assertEquals(59, t.secondsLeft)
        scope.advanceTimeBy(29_000); scope.runCurrent()
        assertEquals(30, t.secondsLeft)
        scope.advanceTimeBy(30_000); scope.runCurrent()
        assertEquals(0, t.secondsLeft)
        scope.advanceTimeBy(10_000); scope.runCurrent()
        assertEquals(0, t.secondsLeft)
        assertEquals(70, t.secondsWaited())
    }

    @Test fun aRebuiltTimerCarriesOnFromTheSavedStart() {
        val saved = SavedStateHandle()
        val first = BreakTimer(saved, scope, clock)
        first.start()
        first.markIntroPlayed()
        scope.advanceTimeBy(12_500); scope.runCurrent()
        assertEquals(48, first.secondsLeft)

        // Process death: a new ViewModel restores the same saved state, a little later.
        scope.advanceTimeBy(3_000)
        val second = BreakTimer(saved, scope, clock)
        assertTrue(second.breaking)
        assertEquals(45, second.secondsLeft)
        assertTrue("the rise is not replayed", second.introPlayed)
        scope.advanceTimeBy(45_000); scope.runCurrent()
        assertEquals(0, second.secondsLeft)
    }

    @Test fun startingAgainWhileOpenKeepsTheMinute() {
        val t = BreakTimer(SavedStateHandle(), scope, clock)
        t.start()
        scope.advanceTimeBy(20_500); scope.runCurrent()
        assertFalse(t.start())
        assertEquals(40, t.secondsLeft)
    }

    @Test fun cancelEndsTheBreakAndTheNextOneStartsFull() {
        val saved = SavedStateHandle()
        val t = BreakTimer(saved, scope, clock)
        t.start()
        t.markIntroPlayed()
        scope.advanceTimeBy(30_500); scope.runCurrent()
        t.cancel()
        assertFalse(t.breaking)
        assertEquals(60, t.secondsLeft)
        assertFalse(BreakTimer(saved, scope, clock).breaking)
        scope.advanceTimeBy(5_000); scope.runCurrent()
        assertEquals("a cancelled countdown stops", 60, t.secondsLeft)

        t.start()
        scope.runCurrent()
        assertEquals(60, t.secondsLeft)
        assertFalse("a new break plays the rise again", t.introPlayed)
    }
}
