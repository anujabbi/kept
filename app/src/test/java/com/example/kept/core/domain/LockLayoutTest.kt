package com.example.kept.core.domain

import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyRamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

/** The lock screen's rules (spec, Lock; issue #127). */
class LockLayoutTest {

    @Test fun `lock ramp index is round(done over total times 4)`() {
        assertEquals(listOf(0, 2, 4), (0..2).map { SkyRamp.lockIndex(it, 2) })
        // 2 of 10 is 0.8, which rounds to 1; 5 of 10 is exactly 2; 9 of 10 is 3.6, so 4.
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 2, 3, 3, 4, 4), (0..10).map { SkyRamp.lockIndex(it, 10) })
        assertEquals(listOf(0, 1, 2, 3, 4), (0..4).map { SkyRamp.lockIndex(it, 4) })
        assertEquals(listOf(0, 1, 3, 4), (0..3).map { SkyRamp.lockIndex(it, 3) })
    }

    @Test fun `lock sky walks the five night colours then turns gold`() {
        assertEquals(Sky.lockRamp[0], SkyRamp.lockSky(0, 2))
        assertEquals(Sky.lockRamp[2], SkyRamp.lockSky(1, 2))
        assertEquals(Sky.gold, SkyRamp.lockSky(2, 2))
        assertEquals(Sky.lockRamp[1], SkyRamp.lockSky(2, 10))
        assertEquals(Sky.lockRamp[4], SkyRamp.lockSky(9, 10))
        assertEquals(Sky.gold, SkyRamp.lockSky(10, 10))
    }

    @Test fun `a day finished in time opens the lock screen instead of dismissing it`() {
        assertEquals(LockMode.OPEN, LockMode.of(loaded = true, lockActive = false, blockedStillLocked = false, allDoneOnTime = true))
    }

    @Test fun `habits left keep it locked`() {
        assertEquals(LockMode.LOCKED, LockMode.of(loaded = true, lockActive = true, blockedStillLocked = true, allDoneOnTime = false))
    }

    @Test fun `a lifted lock with nothing to celebrate dismisses`() {
        // A break, the window ending, or a day finished after the give-up time.
        assertEquals(LockMode.DISMISS, LockMode.of(loaded = true, lockActive = false, blockedStillLocked = false, allDoneOnTime = false))
        // The blocked app was added as an exception (issue #4).
        assertEquals(LockMode.DISMISS, LockMode.of(loaded = true, lockActive = true, blockedStillLocked = false, allDoneOnTime = false))
    }

    @Test fun `nothing happens before the first emission`() {
        assertEquals(LockMode.LOCKED, LockMode.of(loaded = false, lockActive = false, blockedStillLocked = false, allDoneOnTime = false))
    }

    @Test fun `copy uses the app name`() {
        assertEquals("Chrome can wait.", LockCopy.headline("Chrome", open = false))
        assertEquals("Chrome is open.", LockCopy.headline("Chrome", open = true))
        assertEquals("Finish your promised habits to unlock.", LockCopy.line(open = false))
        assertEquals("Promise kept. Enjoy it.", LockCopy.line(open = true))
        assertEquals("Open Chrome", LockCopy.openButton("Chrome"))
        assertEquals("2 of 10", LockCopy.count(2, 10))
    }

    @Test fun `an unknown label falls back to This app`() {
        assertEquals("This app", LockCopy.appName(""))
        assertEquals("This app", LockCopy.appName("  "))
        assertEquals("This app", LockCopy.appName(null))
        assertEquals("Instagram", LockCopy.appName(" Instagram "))
    }

    @Test fun `Sprig guards while locked and cheers when open`() {
        assertEquals(SprigPose.GUARD, LockCopy.pose(open = false))
        assertEquals(SprigPose.CHEER, LockCopy.pose(open = true))
    }

    @Test fun `Sprig stands where the design puts it and keeps its distance to the crest`() {
        assertEquals(232, LockSprig.crestDp(textBottomDp = 200))
        assertEquals(116, LockSprig.topDp(232, open = false))
        assertEquals(80, LockSprig.topDp(232, open = true))
        // A taller headline block moves the crest, and Sprig with it.
        assertEquals(262, LockSprig.crestDp(textBottomDp = 250))
        assertEquals(146, LockSprig.topDp(262, open = false))
        // A smaller Sprig keeps the design's bottom edge.
        assertEquals(116 + 180, LockSprig.topDp(232, open = false, sprigDp = 150) + 150)
    }

    @Test fun `once every habit is done the lock is inactive, so it does not re-trigger for the app`() {
        val s = LockPolicy.Snapshot(
            lockFromMinute = 9 * 60, dueMinute = 21 * 60, habitsIncomplete = false, breakActiveUntil = null,
            hardAllowlist = emptySet(), userExceptions = emptySet(), launchable = setOf("com.android.chrome"),
        )
        val noon = LocalTime.NOON
        assertFalse(LockPolicy.isLockActive(Instant.EPOCH, noon, s))
        assertFalse(LockPolicy.shouldLock("com.android.chrome", Instant.EPOCH, noon, s))
    }
}
