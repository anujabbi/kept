package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promise's rules that no screen test reaches cheaply (#218): saved habits and goals with
 * values the lists lack, singular units, the limits' lines, and what a promise saves. What the
 * screen shows and saves is in `PromiseBehaviourTest`.
 */
class PromiseRulesTest {

    private fun plain(s: String) = s.replace(PromiseCopy.NBSP, ' ')

    @Test fun `a saved amount the list lacks is offered in its place`() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 45, 60, 90), PromiseRules.amounts(HabitDraft.MIN, 25))
        assertEquals(listOf(5, 10, 12, 20, 30, 50), PromiseRules.amounts(HabitDraft.PAGES, 12))
        assertEquals((1..10).toList() + 12, PromiseRules.amounts(HabitDraft.GLASSES, 12))
        assertEquals(listOf(5, 10, 20, 30, 50), PromiseRules.amounts(HabitDraft.PAGES, 10))
    }

    @Test fun `one of a unit reads singular`() {
        assertEquals("I will drink 1 glass, every day.", plain(PromiseDraft("Drink", 1, HabitDraft.GLASSES).sentence))
        assertEquals("I will stretch 1 time, every day.", plain(PromiseDraft("Stretch", 1, HabitDraft.TIMES).sentence))
        assertEquals("I will read 1 page, 1 day a week.", plain(PromiseDraft("Read", 1, HabitDraft.PAGES, days = 1).sentence))
    }

    @Test fun `a photo promise has no amount or unit and no target as a goal`() {
        val d = PromiseDraft("", days = 2).pick(PromiseRules.PRESETS.last())
        assertEquals("I will tidy room, 2 days a week.", plain(d.sentence))
        assertNull(d.goalTarget)
        assertNull(d.goalUnit)
        assertEquals(ProofType.PHOTO, d.habit().proof)
        assertEquals("something else drops the photo and starts at 20 minutes", "I will something for 20 minutes, 2 days a week.", plain(d.somethingElse().sentence))
    }

    @Test fun `a saved habit opens filled in, and a one-tap habit reads as 1 time`() {
        assertEquals(PromiseDraft("Read", 12, HabitDraft.PAGES), PromiseRules.ofHabit("Read", ProofType.MANUAL, 12, HabitDraft.PAGES))
        assertEquals(PromiseDraft("Journal", 1, HabitDraft.TIMES, custom = true), PromiseRules.ofHabit("Journal", ProofType.MANUAL, 1, HabitDraft.CHECK_IN))
        val photo = PromiseRules.ofHabit("Tidy room", ProofType.PHOTO, 1, "photo")
        assertTrue(photo.photo)
        assertFalse(photo.custom)
    }

    @Test fun `a saved goal opens filled in, and one with no target gets its preset's or 20 minutes`() {
        assertEquals(PromiseDraft("Exercise", 30, HabitDraft.MIN, days = 4), PromiseRules.ofGoal("Exercise", 4, 30, HabitDraft.MIN))
        assertEquals(PromiseDraft("Exercise", 30, HabitDraft.MIN, days = 4), PromiseRules.ofGoal("Exercise", 4, null, null))
        assertEquals(PromiseDraft("Swim", 20, HabitDraft.MIN, days = 2, custom = true), PromiseRules.ofGoal("Swim", 2, null, null))
        assertTrue("Tidy room with no target is the photo goal", PromiseRules.ofGoal("Tidy room", 2, null, null).photo)
        assertEquals(HabitDraft.MAX_TARGET, PromiseRules.ofGoal("Read", 2, 5000, HabitDraft.PAGES).target)
    }

    @Test fun `a goal saved at 7 days a week keeps 7 on its list`() {
        assertEquals(listOf(null, 7, 6, 5, 4, 3, 2, 1), PromiseRules.dayChoices(7))
        assertEquals(listOf(null, 6, 5, 4, 3, 2, 1), PromiseRules.dayChoices(4))
    }

    @Test fun `a new promise starts on the first activity not kept yet`() {
        assertEquals("Exercise", PromiseRules.fresh(emptyList(), null).name)
        assertEquals("Practice", PromiseRules.fresh(listOf("exercise", "Read"), null).name)
        val none = PromiseRules.fresh(PromiseRules.PRESETS.map { it.title }, 3)
        assertTrue(none.custom)
        assertEquals("", none.activity)
        assertEquals(3, none.days)
    }

    @Test fun `the limits say what is off and why, and a new promise moves to what is on`() {
        assertEquals(PromiseLimits(true, true, null), PromiseLimits.new(daily = 2, weekly = 2))
        assertEquals(PromiseLimits(true, false, WeeklyGoalCopy.LIMIT_REACHED), PromiseLimits.new(daily = 2, weekly = 3))
        assertEquals(PromiseLimits(false, true, HabitLimits.LIMIT_REACHED), PromiseLimits.new(daily = 10, weekly = 0))
        assertEquals(3, PromiseLimits.new(10, 0).fit(PromiseDraft("Read", days = null)).days)
        assertNull(PromiseLimits.new(2, 3).fit(PromiseDraft("Read", days = 4)).days)
    }

    @Test fun `a saved promise may switch kind within the limits, and the last every-day habit may not (#236)`() {
        assertEquals(PromiseLimits(true, true, null), PromiseLimits.savedHabit(daily = 4, weekly = 2))
        assertEquals(PromiseLimits(true, false, WeeklyGoalCopy.LIMIT_REACHED), PromiseLimits.savedHabit(daily = 4, weekly = 3))
        assertEquals(PromiseLimits(true, false, PromiseCopy.KEEP_ONE_DAILY), PromiseLimits.savedHabit(daily = 1, weekly = 0))
        assertEquals(PromiseLimits(true, true, null), PromiseLimits.savedGoal(daily = 9))
        assertEquals(PromiseLimits(false, true, HabitLimits.LIMIT_REACHED), PromiseLimits.savedGoal(daily = 10))
    }

    @Test fun `the activity is trimmed and stops at 28 characters`() {
        assertEquals(28, PromiseDraft("").withActivity("x".repeat(40)).activity.length)
        assertEquals("Swim", PromiseDraft("  Swim ").name)
        assertFalse(PromiseDraft("   ").canSave)
    }

    @Test fun `a new unit starts at its own value`() {
        assertEquals(8, PromiseDraft("Drink", 30, HabitDraft.MIN).withUnit(HabitDraft.GLASSES).target)
        assertEquals(30, PromiseDraft("Drink", 30, HabitDraft.MIN).withUnit(HabitDraft.MIN).target)
    }
}
