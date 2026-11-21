package com.example.kept.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyRampTest {

    @Test fun `home index for two habits`() {
        assertEquals(0, SkyRamp.homeIndex(0, 2))
        assertEquals(3, SkyRamp.homeIndex(1, 2))
        assertEquals(6, SkyRamp.homeIndex(2, 2))
    }

    @Test fun `home index for six habits steps through the whole ramp`() {
        assertEquals((0..6).toList(), (0..6).map { SkyRamp.homeIndex(it, 6) })
    }

    @Test fun `home index rounds half up like the design`() {
        // 1/4 × 6 = 1.5 → 2, 3/4 × 6 = 4.5 → 5
        assertEquals(listOf(0, 2, 3, 5, 6), (0..4).map { SkyRamp.homeIndex(it, 4) })
        // 1/3 × 6 = 2, 2/3 × 6 = 4
        assertEquals(listOf(0, 2, 4, 6), (0..3).map { SkyRamp.homeIndex(it, 3) })
    }

    @Test fun `home sky colours come from the ramp`() {
        assertEquals(Sky.night, SkyRamp.homeSky(0, 3))
        assertEquals(Sky.gold, SkyRamp.homeSky(3, 3))
        assertEquals(Sky.dawn, SkyRamp.homeSky(5, 6))
    }

    @Test fun `no habits or bad counts stay on night`() {
        assertEquals(0, SkyRamp.homeIndex(0, 0))
        assertEquals(0, SkyRamp.homeIndex(-1, 3))
        assertEquals(6, SkyRamp.homeIndex(9, 3))
    }

    @Test fun `lock index uses five steps`() {
        assertEquals(listOf(0, 1, 2, 3, 4), (0..4).map { SkyRamp.lockIndex(it, 4) })
        assertEquals(listOf(0, 2, 4), (0..2).map { SkyRamp.lockIndex(it, 2) })
    }

    @Test fun `lock sky is gold only when everything is done`() {
        assertEquals(Sky.lockRamp[2], SkyRamp.lockSky(1, 2))
        assertEquals(Sky.gold, SkyRamp.lockSky(2, 2))
        assertEquals(Sky.night, SkyRamp.lockSky(0, 0))
    }

    @Test fun `home text is light below the halfway point of the ramp`() {
        assertEquals(
            listOf(true, true, true, false, false, false, false),
            (0..6).map { SkyRamp.homeTextIsLight(it, 6) },
        )
        assertTrue(SkyRamp.homeTextIsLight(0, 2))
        assertFalse(SkyRamp.homeTextIsLight(1, 2))
    }

    @Test fun `lock text is light until the gold sky`() {
        assertTrue(SkyRamp.lockTextIsLight(0, 3))
        assertTrue(SkyRamp.lockTextIsLight(2, 3))
        assertFalse(SkyRamp.lockTextIsLight(3, 3))
    }

    @Test fun `text colour for light and dark content`() {
        assertEquals(DaybreakColors.skyTextLight, SkyRamp.textOn(true))
        assertEquals(DaybreakColors.ink, SkyRamp.textOn(false))
    }

    @Test fun `luminance rule agrees with the home ramp at every step`() {
        Sky.progressRamp.forEachIndexed { i, sky ->
            assertEquals("ramp index $i", i < 3, SkyRamp.prefersLightContent(sky))
        }
    }

    @Test fun `night, lock and evolve skies get light content, the rest dark`() {
        Sky.lockRamp.forEach { assertTrue(SkyRamp.prefersLightContent(it)) }
        assertTrue(SkyRamp.prefersLightContent(Sky.lockGround))
        listOf(Sky.gold, Sky.dawn, Sky.overcast, Sky.tide, Sky.lilac, Sky.recapLockOff, Sky.recapTooLate, DaybreakColors.cream)
            .forEach { assertFalse(it.toString(), SkyRamp.prefersLightContent(it)) }
    }
}
