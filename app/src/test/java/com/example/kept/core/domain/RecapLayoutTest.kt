package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recap's pure rules (spec, Recap; `A5-Recap.dc.html`'s script). */
class RecapLayoutTest {

    private fun outcome(
        counted: Boolean = false,
        unprotected: Boolean = false,
        writtenOff: Boolean = false,
        shield: Boolean = false,
        done: Int = 1,
        total: Int = 2,
    ) = RecapOutcome.of(counted, unprotected, writtenOff, shield, done, total)

    // Outcome mapping, every row of the outcome table.

    @Test fun `an unprotected day that was also written off keeps its penalty`() =
        assertEquals(RecapOutcome.WRITTEN_OFF, outcome(unprotected = true, writtenOff = true))

    @Test fun `a written off day is written off`() = assertEquals(RecapOutcome.WRITTEN_OFF, outcome(writtenOff = true, done = 2))

    @Test fun `anything else slipped`() {
        assertEquals(RecapOutcome.SLIPPED, outcome(done = 1))
        assertEquals(RecapOutcome.SLIPPED, outcome(done = 0, total = 0))
    }

    @Test fun `a shield beats too late`() = assertEquals(RecapOutcome.SHIELD, outcome(shield = true, done = 2))

    @Test fun `a written off day is never shield, even on a record from before issue 143`() {
        // Older builds could spend the shield on a written-off day; the recap still says so.
        assertEquals(RecapOutcome.WRITTEN_OFF, outcome(shield = true, writtenOff = true))
        assertEquals(RecapOutcome.WRITTEN_OFF, outcome(shield = true, writtenOff = true, done = 2))
    }

    // Copy, word for word from the table, with real numbers.

    @Test fun `lines with the streak`() {
        assertEquals("12 days running.", RecapCopy.line(RecapOutcome.KEPT, 12, false))
        assertEquals("1 day running.", RecapCopy.line(RecapOutcome.KEPT, 1, false))
        assertEquals("Your weekly shield kept the 9-day streak alive.", RecapCopy.line(RecapOutcome.SHIELD, 9, false))
        assertEquals("That one is on us. Today does not count, but your 12-day streak is safe.", RecapCopy.line(RecapOutcome.LOCK_OFF, 12, false))
        assertEquals("Streak reset. Tomorrow is fresh.", RecapCopy.line(RecapOutcome.SLIPPED, 0, true))
        assertEquals("Everything got ticked after the give-up time. Streak reset.", RecapCopy.line(RecapOutcome.TOO_LATE, 0, true))
        assertEquals("A fourth break this week. Streak reset, and the shield could not save it.", RecapCopy.line(RecapOutcome.WRITTEN_OFF, 0, true))
    }

    @Test fun `no reset is claimed when the streak was already 0 (issue 16)`() {
        for (o in listOf(RecapOutcome.SLIPPED, RecapOutcome.TOO_LATE, RecapOutcome.WRITTEN_OFF)) {
            assertFalse(o.name, RecapCopy.line(o, 0, reset = false).contains("Streak reset"))
        }
        assertEquals("Tomorrow is fresh.", RecapCopy.line(RecapOutcome.SLIPPED, 0, false))
    }

    @Test fun `lock off with no streak promises no 0-day streak`() =
        assertEquals("That one is on us. Today does not count.", RecapCopy.line(RecapOutcome.LOCK_OFF, 0, false))

    @Test fun `poses`() {
        assertEquals(
            listOf(SprigPose.CHEER, SprigPose.IDLE, SprigPose.IDLE, SprigPose.DROOP, SprigPose.DROOP, SprigPose.DROOP),
            RecapOutcome.entries.map(RecapCopy::pose),
        )
    }

    // Progress card.

    @Test fun `one dot per day inside the span, white once behind`() {
        val p = RecapProgress.of(12, grows = true) as RecapProgress.Toward
        assertEquals(6, p.dots.size)
        assertEquals((1..6).map { it / 7f }, p.dots.map { it.fraction })
        assertEquals(listOf(true, true, true, true, true, false), p.dots.map { it.reached })
    }

    @Test fun `a day that reaches a form starts the new span at 0`() {
        val p = RecapProgress.of(7, grows = true) as RecapProgress.Toward
        assertEquals(SprigForm.BLOOM, p.from)
        assertEquals(0f, p.fromFraction, 0f)
        assertEquals(0f, p.toFraction, 0f)
    }

    @Test fun `one day left reads in 1 day`() {
        val p = RecapProgress.of(13, grows = true) as RecapProgress.Toward
        assertEquals("Thicket in 1 day", RecapCopy.nextLine(p.to, p.daysLeft))
    }

    @Test fun `the final form has no bar`() {
        assertEquals(RecapProgress.Final(SprigForm.CELESTIAL), RecapProgress.of(100, grows = true))
        assertEquals(RecapProgress.Final(SprigForm.CELESTIAL), RecapProgress.of(240, grows = false))
        assertEquals("Sprig is Celestial, the final form.", RecapCopy.finalForm(SprigForm.CELESTIAL))
        assertTrue(RecapProgress.of(99, grows = true) is RecapProgress.Toward)
    }

    // Sprig's place.

    @Test fun `on the 390 by 844 artboard Sprig is 250dp at the design's place`() {
        // Below the crest: 52 hill + 51 stats + 136 card + 16 gap + 84 buttons.
        val kept = RecapSprig.spot(390, 844, 140, 339, kept = true)
        assertEquals(RecapSprig.Spot(352, 250, 150, 70), kept)
        val other = RecapSprig.spot(390, 844, 140, 339, kept = false)
        assertEquals(RecapSprig.Spot(352, 250, 172, 70), other)
    }

    @Test fun `Sprig keeps the design's share above the crest at every size`() {
        for (kept in listOf(true, false)) for (h in 500..915 step 15) {
            val s = RecapSprig.spot(360, h, 145, 339, kept)
            val above = s.crestDp - s.sprigTopDp
            val expected = RecapSprig.aboveCrestDp(kept) * s.sprigDp / RecapSprig.SIZE_DP.toFloat()
            assertEquals("$kept $h", expected, above.toFloat(), 1f)
            assertTrue("$kept $h: Sprig's bottom is above the crest", s.sprigTopDp + s.sprigDp > s.crestDp + 20)
        }
    }

    @Test fun `Sprig is centred`() {
        val s = RecapSprig.spot(412, 915, 145, 339, kept = true)
        assertEquals((412 - s.sprigDp) / 2, s.sprigLeftDp)
    }

    @Test fun `headline sizes`() {
        assertEquals(52, RecapSprig.headlineSp(true))
        assertEquals(40, RecapSprig.headlineSp(false))
    }

    // The kept-day moment.

    @Test fun `14 petals at the design script's places, colours and delays`() {
        val p = RecapMotion.PETALS
        assertEquals(14, p.size)
        assertEquals(listOf(12f, 65f, 118f, 171f, 224f, 277f, 330f, 23f, 76f, 129f, 182f, 235f, 288f, 341f), p.map { it.xDp })
        assertEquals(0xFFFF8FB0, p[0].colour)
        assertEquals(0xFFFFFFFF, p[1].colour)
        assertEquals(0xFFFFF4C2, p[4].colour)
        assertEquals(0xFFFF8FB0, p[5].colour)
        assertEquals(listOf(0, 400, 800, 100, 500, 900, 200, 600, 1000, 300, 700, 0, 400, 800), p.map { it.delayMs })
    }

    @Test fun `petals fall once`() {
        assertEquals(3600, RecapMotion.PETALS_END_MS)
        val first = RecapMotion.PETALS[0]
        assertEquals(0f, RecapMotion.petalProgress(first, 0)!!, 0f)
        assertEquals(null, RecapMotion.petalProgress(first, 2601))
        assertEquals(null, RecapMotion.petalProgress(RecapMotion.PETALS[8], 999))
        assertTrue(RecapMotion.PETALS.all { RecapMotion.petalProgress(it, RecapMotion.PETALS_END_MS + 1L) == null })
    }

    @Test fun `petal opacity follows the keyframes`() {
        assertEquals(0f, RecapMotion.petalAlpha(0f), 0f)
        assertEquals(0.5f, RecapMotion.petalAlpha(0.05f), 1e-5f)
        assertEquals(1f, RecapMotion.petalAlpha(0.5f), 0f)
        assertEquals(0.5f, RecapMotion.petalAlpha(0.9f), 1e-5f)
        assertEquals(0f, RecapMotion.petalAlpha(1f), 0f)
    }

    @Test fun `the knob pops to 1·35 and back after 2s`() {
        val linear = { t: Float -> t }
        assertEquals(1f, RecapMotion.knobScale(RecapMotion.popProgress(1999), linear), 0f)
        assertEquals(1.35f, RecapMotion.knobScale(RecapMotion.popProgress(2250), linear), 1e-5f)
        assertEquals(1f, RecapMotion.knobScale(RecapMotion.popProgress(2500), linear), 0f)
    }
}
