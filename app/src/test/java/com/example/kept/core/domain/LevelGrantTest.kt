package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The same-day level is granted once and taken back only if it was granted (issue #7). The
 * give-up time is a mutable setting, so eligibility must be a stored fact rather than something
 * recomputed at undo time.
 */
class LevelGrantTest {
    private val today = "2026-09-05"

    @Test fun `finishing on time grants a level and records the day`() {
        val after = LevelRules.grantForDay(SprigState(level = 3), today, allDoneOnTime = true)
        assertEquals(4, after.level)
        assertEquals(today, after.levelGrantedDate)
    }

    @Test fun `finishing after the give-up time grants nothing`() {
        val after = LevelRules.grantForDay(SprigState(level = 3), today, allDoneOnTime = false)
        assertEquals(3, after.level)
        assertNull(after.levelGrantedDate)
    }

    @Test fun `the level is granted once per day`() {
        var s = LevelRules.grantForDay(SprigState(level = 3), today, allDoneOnTime = true)
        s = LevelRules.grantForDay(s, today, allDoneOnTime = true)
        assertEquals(4, s.level)
    }

    @Test fun `moving the give-up time earlier then undoing still takes back the granted level`() {
        // Granted at 20:00 against a 21:00 give-up time...
        val granted = LevelRules.grantForDay(SprigState(level = 3), today, allDoneOnTime = true)
        // ...the user then moves the give-up time to 19:00, so recomputing would now say "late"
        // and refund nothing. The stored grant is what counts.
        val undone = LevelRules.revokeForDay(granted, today)
        assertEquals(3, undone.level)
        assertNull(undone.levelGrantedDate)
    }

    @Test fun `moving the give-up time later then undoing does not take back a level never granted`() {
        // Ticked at 22:00 against a 21:00 give-up time: late, so no level.
        val notGranted = LevelRules.grantForDay(SprigState(level = 3), today, allDoneOnTime = false)
        // The user then moves the give-up time to 23:00, so recomputing would now say "on time"
        // and take a level away. The stored grant says there is nothing to take.
        val undone = LevelRules.revokeForDay(notGranted, today)
        assertEquals(3, undone.level)
        assertNull(undone.levelGrantedDate)
    }

    @Test fun `an undo on a later day does not touch an earlier day's grant`() {
        val granted = LevelRules.grantForDay(SprigState(level = 3), today, allDoneOnTime = true)
        val undone = LevelRules.revokeForDay(granted, "2026-09-06")
        assertEquals(4, undone.level)
        assertEquals(today, undone.levelGrantedDate)
    }

    @Test fun `the level never drops below the floor`() {
        val s = SprigState(level = LevelRules.FLOOR, levelGrantedDate = today)
        assertEquals(LevelRules.FLOOR, LevelRules.revokeForDay(s, today).level)
    }
}
