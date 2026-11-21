package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two moments' rules, copy and motion (`A16-Celebrate`, `A17-Evolve`, issue #130). */
class MomentsTest {

    private val today = "2026-11-25"
    private val yesterday = "2026-11-24"

    @Test fun `the moment is due in KEPT only for today, once, and only for a day that counts`() {
        assertTrue(MomentRules.dueInKept(today, null, today, allDoneOnTime = true))
        assertTrue(MomentRules.dueInKept(today, yesterday, today, allDoneOnTime = true))
        assertFalse("already played today (KEPT or the lock screen)", MomentRules.dueInKept(today, today, today, true))
        assertFalse("yesterday's leftover is stale", MomentRules.dueInKept(yesterday, null, today, true))
        assertFalse("nothing pending", MomentRules.dueInKept(null, null, today, true))
        assertFalse("undone since, or finished late", MomentRules.dueInKept(today, null, today, allDoneOnTime = false))
    }

    @Test fun `it plays once per day on either surface`() {
        assertTrue(MomentRules.mayPlay(null, today))
        assertTrue(MomentRules.mayPlay(yesterday, today))
        assertFalse(MomentRules.mayPlay(today, today))
    }

    @Test fun `a finished day leaves the moment pending for KEPT only when nothing else played it`() {
        assertEquals(today, MomentRules.pendingAfterFinish(shownHere = false, shownDate = null, today = today))
        assertEquals(today, MomentRules.pendingAfterFinish(false, yesterday, today))
        assertNull("the lock screen plays it itself", MomentRules.pendingAfterFinish(true, null, today))
        assertNull("already played today", MomentRules.pendingAfterFinish(false, today, today))
    }

    @Test fun `only a day finished in time counts`() {
        assertTrue(MomentRules.finishedInTime(allDoneNow = true, beforeDue = true))
        assertFalse("finished after the give-up time", MomentRules.finishedInTime(true, false))
        assertFalse("habits left", MomentRules.finishedInTime(false, true))
    }

    @Test fun `promise kept copy`() {
        assertEquals("Promise kept.", CelebrationCopy.HEADLINE)
        assertEquals("Tap to carry on", CelebrationCopy.CARRY_ON)
        assertEquals(13, CelebrationCopy.streakDay(12))
        assertEquals(1, CelebrationCopy.streakDay(0))
        assertEquals("Day 13 of your streak. Apps are open.", CelebrationCopy.line(13))
        assertEquals("Promise kept. Day 13 of your streak. Apps are open. Tap to carry on.", CelebrationCopy.spoken(13))
    }

    @Test fun `evolve copy for every form`() {
        assertEquals("14 days kept", EvolveCopy.kept(14))
        assertEquals("Sprig is now Thicket", EvolveCopy.headline(SprigForm.THICKET))
        val lines = mapOf(
            SprigForm.BUD to "New outfit: a red scarf. Next up is Bloom, at day 7.",
            SprigForm.BLOOM to "New outfit: a yellow cardigan. Next up is Thicket, at day 14.",
            SprigForm.THICKET to "New outfit: a scarf and a cardigan. Next up is Grove, at day 30.",
            SprigForm.GROVE to "New outfit: a leaf crown. Next up is Ancient, at day 60.",
            SprigForm.ANCIENT to "New outfit: round glasses and a beard. Next up is Celestial, at day 100.",
            SprigForm.CELESTIAL to "New outfit: a starry cape and a halo.",
        )
        for ((form, line) in lines) assertEquals(form.name, line, EvolveCopy.line(form))
        assertFalse("the final form has no Next up", EvolveCopy.line(SprigForm.CELESTIAL).contains("Next up"))
        assertEquals("Share", EvolveCopy.SHARE)
        assertEquals("Carry on", EvolveCopy.CARRY_ON)
    }

    @Test fun `eighteen petals from the moments' script`() {
        val p = MomentMotion.SHOWER.petals
        assertEquals(18, p.size)
        assertEquals(8f, p[0].xDp, 0f)
        assertEquals(55f, p[1].xDp, 0f)
        assertEquals((8 + (17 * 47) % 370).toFloat(), p[17].xDp, 0f)
        assertEquals(0, p[0].delayMs)
        assertEquals(((5 * 29) % 13) * 100, p[5].delayMs)
        assertEquals(0xFFFF8FB0, p[0].colour)
        assertEquals(0xFFFFFFFF, p[6].colour)
        assertEquals(1200, p.maxOf { it.delayMs })
        assertEquals(4000, MomentMotion.SHOWER.endMs)
        assertEquals(4000, MomentMotion.END_MS)
        // `petalFall`: 0 → 1 by 10%, 1 to 85%, then 0.
        assertEquals(0.5f, MomentMotion.SHOWER.alpha(0.05f), 1e-5f)
        assertEquals(1f, MomentMotion.SHOWER.alpha(0.85f), 1e-5f)
        assertEquals(0.5f, MomentMotion.SHOWER.alpha(0.925f), 1e-4f)
    }

    @Test fun `the recap keeps its own petals`() {
        val r = RecapMotion.SHOWER
        assertEquals(14, r.petals.size)
        assertEquals(2600, r.durationMs)
        assertEquals(380f, r.fallDp, 0f)
        assertEquals(1f, r.alpha(0.8f), 0f)
    }

    @Test fun `pop and ring keyframes`() {
        assertEquals(0f, MomentMotion.popProgress(150), 0f)
        assertEquals(0.5f, MomentMotion.popProgress(500), 1e-6f)
        assertEquals(1f, MomentMotion.popProgress(850), 0f)
        val start = MomentMotion.pop(0f)
        assertEquals(120f, start.yDp, 0f)
        assertEquals(0.6f, start.scale, 0f)
        assertEquals(0f, start.alpha, 0f)
        val end = MomentMotion.pop(1f)
        assertEquals(0f, end.yDp, 0f)
        assertEquals(1f, end.scale, 0f)
        assertEquals(1f, end.alpha, 0f)
        assertEquals("overshoot never makes it more than opaque", 1f, MomentMotion.pop(1.2f).alpha, 0f)

        assertEquals(0f, MomentMotion.ringProgress(500), 0f)
        assertEquals(1f, MomentMotion.ringProgress(1900), 0f)
        assertEquals(0.4f, MomentMotion.ringScale(0f), 1e-6f)
        assertEquals(1.6f, MomentMotion.ringScale(1f), 1e-6f)
        assertEquals(0.9f, MomentMotion.ringAlpha(0f), 1e-6f)
        assertEquals("the ring ends invisible", 0f, MomentMotion.ringAlpha(1f), 0f)
    }

    @Test fun `Sprig keeps the design size when it fits and shrinks on a short screen`() {
        assertEquals(260, MomentFit.sprigDp(260, 915, 300, 356))
        assertEquals(200, MomentFit.sprigDp(260, 640, 440, 304))
        assertEquals("never under the interactive size", 110, MomentFit.sprigDp(260, 640, 600, 304))
        assertEquals("never wider than the column", 252, MomentFit.sprigDp(280, 915, 300, 252))
    }

    @Test fun `the lock screen stands the moment in once, only while it can still play`() {
        assertTrue(MomentRules.lockStandsIn(remaining = 1, shownDate = null, standInDate = null, today = today))
        assertFalse("not the last habit", MomentRules.lockStandsIn(2, null, null, today))
        assertFalse("already played today", MomentRules.lockStandsIn(1, today, null, today))
        assertFalse("already stood in today", MomentRules.lockStandsIn(1, null, today, today))
        assertTrue("yesterday's stand-in does not count", MomentRules.lockStandsIn(1, yesterday, yesterday, today))
    }

    /**
     * QA on PR #144: tick, carry on, undo and tick again while the first claim is still being
     * written, then the two claims land in either order. Nothing may stand in or go up again.
     */
    @Test fun `an undo and re-tick before the first claim is stored never shows the moment again`() {
        var standIn: String? = null
        var carriedOn: String? = null
        val stored: String? = null // the first claim has not been written yet

        // Tick 1: the moment stands in.
        assertTrue(MomentRules.lockStandsIn(1, stored, standIn, today)); standIn = today
        // Carry on, undo, tick 2: the stored date still says "not played", but no second stand-in.
        carriedOn = today
        assertFalse(MomentRules.lockStandsIn(1, stored, standIn, today))
        // The claims land, in either order: one wins, and neither puts the moment back up.
        assertFalse(MomentRules.lockRaisesAfterClaim(claimed = true, carriedOnDate = carriedOn, today = today))
        assertFalse(MomentRules.lockRaisesAfterClaim(claimed = false, carriedOnDate = carriedOn, today = today))
    }

    @Test fun `the claimed moment goes up on the lock screen unless carried on already`() {
        assertTrue(MomentRules.lockRaisesAfterClaim(true, null, today))
        assertTrue(MomentRules.lockRaisesAfterClaim(true, yesterday, today))
        assertFalse(MomentRules.lockRaisesAfterClaim(false, null, today))
        assertFalse(MomentRules.lockRaisesAfterClaim(true, today, today))
    }
}
