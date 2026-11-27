package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shutdown and phone-back sliders (issue #218): ranges, 15 minute steps, and the 12 hour rule. */
class NightSlidersTest {

    @Test fun `shutdown runs from 8 pm to 2 am in 15 minute steps, past midnight`() {
        assertEquals(20 * 60, NightSliders.fromAt(0))
        assertEquals(20 * 60 + 15, NightSliders.fromAt(1))
        assertEquals(0, NightSliders.fromAt(16))
        assertEquals(2 * 60, NightSliders.fromAt(NightSliders.FROM_STEPS))
        assertEquals(12, NightSliders.fromIndex(23 * 60))
    }

    @Test fun `phone back runs from 5 am to 10 am in 15 minute steps`() {
        assertEquals(5 * 60, NightSliders.untilAt(0))
        assertEquals(10 * 60, NightSliders.untilAt(NightSliders.UNTIL_STEPS))
        assertEquals(8, NightSliders.untilIndex(7 * 60))
    }

    @Test fun `a time outside a slider sits at its nearer end`() {
        assertEquals(0, NightSliders.fromIndex(19 * 60))
        assertEquals(NightSliders.FROM_STEPS, NightSliders.fromIndex(3 * 60))
        assertEquals(0, NightSliders.untilIndex(4 * 60))
        assertEquals(NightSliders.UNTIL_STEPS, NightSliders.untilIndex(11 * 60))
        assertEquals(1, NightSliders.fromIndex(20 * 60 + 10))
    }

    @Test fun `picking one end never leaves a night longer than 12 hours`() {
        val w = NightSetting(enabled = true, fromMinute = 23 * 60, untilMinute = 10 * 60)
        assertEquals(NightSetting(true, 20 * 60, 8 * 60), NightSliders.pickFrom(w, 20 * 60))
        assertEquals(NightSetting(true, 23 * 60, 10 * 60), NightSliders.pickFrom(w, 23 * 60))
        val early = NightSetting(enabled = true, fromMinute = 20 * 60, untilMinute = 7 * 60)
        assertEquals(NightSetting(true, 22 * 60, 10 * 60), NightSliders.pickUntil(early, 10 * 60))
        for (f in 0..NightSliders.FROM_STEPS) for (u in 0..NightSliders.UNTIL_STEPS) {
            val a = NightSliders.pickUntil(NightSliders.pickFrom(NightSetting(), NightSliders.fromAt(f)), NightSliders.untilAt(u))
            assertTrue("$a", NightWindow.isValid(a.fromMinute, a.untilMinute))
        }
    }

    @Test fun `the sentences follow the times`() {
        assertEquals(
            "From 11:00 pm to 7:00 am, apps stay locked. Phone, messages and maps still work.",
            NightCopy.shutdownSentence(23 * 60, 7 * 60).joinToString("") { it.first }.replace(' ', ' '),
        )
        assertEquals("Apps lock from midnight until 7:30 am.", NightCopy.startSentence(0, 7 * 60 + 30).joinToString("") { it.first }.replace(' ', ' '))
        assertEquals("Midnight", NightCopy.big(0))
    }
}
