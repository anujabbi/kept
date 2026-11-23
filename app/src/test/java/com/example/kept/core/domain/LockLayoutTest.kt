package com.example.kept.core.domain

import com.example.kept.core.ui.SkyRamp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The lock screen's rules (spec, Lock; issue #127). */
class LockLayoutTest {

    @Test fun `lock ramp index is round(done over total times 4)`() {
        assertEquals(listOf(0, 2, 4), (0..2).map { SkyRamp.lockIndex(it, 2) })
        // 2 of 10 is 0.8, which rounds to 1; 5 of 10 is exactly 2; 9 of 10 is 3.6, so 4.
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 2, 3, 3, 4, 4), (0..10).map { SkyRamp.lockIndex(it, 10) })
        assertEquals(listOf(0, 1, 2, 3, 4), (0..4).map { SkyRamp.lockIndex(it, 4) })
        assertEquals(listOf(0, 1, 3, 4), (0..3).map { SkyRamp.lockIndex(it, 3) })
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
}
