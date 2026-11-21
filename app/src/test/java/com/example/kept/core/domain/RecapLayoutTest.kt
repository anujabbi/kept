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

    @Test fun `a counted day is kept`() = assertEquals(RecapOutcome.KEPT, outcome(counted = true, done = 2))

    @Test fun `a shielded day is shield`() = assertEquals(RecapOutcome.SHIELD, outcome(shield = true))

    @Test fun `an unprotected day is lock off, even with everything ticked`() {
        assertEquals(RecapOutcome.LOCK_OFF, outcome(unprotected = true))
        assertEquals(RecapOutcome.LOCK_OFF, outcome(unprotected = true, done = 2))
    }

    @Test fun `an unprotected day that was also written off keeps its penalty`() =
        assertEquals(RecapOutcome.WRITTEN_OFF, outcome(unprotected = true, writtenOff = true))

    @Test fun `a written off day is written off`() = assertEquals(RecapOutcome.WRITTEN_OFF, outcome(writtenOff = true, done = 2))

    @Test fun `everything ticked but not counted is too late`() = assertEquals(RecapOutcome.TOO_LATE, outcome(done = 2))

    @Test fun `anything else slipped`() {
        assertEquals(RecapOutcome.SLIPPED, outcome(done = 1))
        assertEquals(RecapOutcome.SLIPPED, outcome(done = 0, total = 0))
    }

    @Test fun `a shield beats written off and too late, as the recap always ordered them`() {
        assertEquals(RecapOutcome.SHIELD, outcome(shield = true, writtenOff = true))
        assertEquals(RecapOutcome.SHIELD, outcome(shield = true, done = 2))
    }

    @Test fun `kept, shield and lock off keep the streak and the others reset it`() {
        val kept = RecapOutcome.entries.filter { it.streakKept }
        assertEquals(listOf(RecapOutcome.KEPT, RecapOutcome.SHIELD, RecapOutcome.LOCK_OFF), kept)
    }

    // Copy, word for word from the table, with real numbers.

    @Test fun `headlines`() {
        assertEquals(
            listOf("Promise kept.", "Shield saved it.", "The lock was off.", "The promise slipped.", "Too late to count.", "Day written off."),
            RecapOutcome.entries.map(RecapCopy::headline),
        )
    }

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

    @Test fun `stats`() {
        assertEquals("2 of 2", RecapCopy.done(2, 2))
        assertEquals("lock broken", RecapCopy.brokenLabel(1))
        assertEquals("locks broken", RecapCopy.brokenLabel(0))
        assertEquals("locks broken", RecapCopy.brokenLabel(4))
    }

    // Progress card.

    @Test fun `day 12 is 5 of the 7 days from Bloom to Thicket, as in the design`() {
        val p = RecapProgress.of(12, grows = true) as RecapProgress.Toward
        assertEquals(SprigForm.BLOOM, p.from)
        assertEquals(SprigForm.THICKET, p.to)
        assertEquals(7, p.span)
        assertEquals(5, p.into)
        assertEquals(2, p.daysLeft)
        assertEquals("Thicket in 2 days", RecapCopy.nextLine(p.to, p.daysLeft))
        assertEquals("Day 12 of 14", RecapCopy.dayCount(p.day, p.target))
        assertEquals(5f / 7, p.toFraction, 1e-6f)
        assertEquals(4f / 7, p.fromFraction, 1e-6f)
    }

    @Test fun `one dot per day inside the span, white once behind`() {
        val p = RecapProgress.of(12, grows = true) as RecapProgress.Toward
        assertEquals(6, p.dots.size)
        assertEquals((1..6).map { it / 7f }, p.dots.map { it.fraction })
        assertEquals(listOf(true, true, true, true, true, false), p.dots.map { it.reached })
    }

    @Test fun `only a kept day grows, otherwise the fill starts at today`() {
        val p = RecapProgress.of(12, grows = false) as RecapProgress.Toward
        assertEquals(p.toFraction, p.fromFraction, 0f)
    }

    @Test fun `a reset streak is day 0 of 3 toward Bud`() {
        val p = RecapProgress.of(0, grows = false) as RecapProgress.Toward
        assertEquals(SprigForm.SPRIG, p.from)
        assertEquals(SprigForm.BUD, p.to)
        assertEquals(0f, p.toFraction, 0f)
        assertEquals(2, p.dots.size)
        assertTrue(p.dots.none { it.reached })
        assertEquals("Bud in 3 days", RecapCopy.nextLine(p.to, p.daysLeft))
        assertEquals("Day 0 of 3", RecapCopy.dayCount(p.day, p.target))
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

    @Test fun `a tall headline pushes Sprig down, never onto the text`() {
        for (kept in listOf(true, false)) for (text in listOf(145, 250, 400, 600)) for (h in listOf(640, 844, 915)) {
            val s = RecapSprig.spot(390, h, text, 339, kept)
            assertTrue("$kept $text $h: Sprig's box ${s.sprigTopDp} is on the text", s.sprigTopDp >= text + RecapSprig.CLEARANCE_DP)
            assertTrue(s.sprigDp in RecapSprig.SIZE_MIN_DP..RecapSprig.SIZE_DP)
        }
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

    @Test fun `a short screen brings the crest up and shrinks Sprig before it scrolls`() {
        val s = RecapSprig.spot(360, 640, 145, 339, kept = true)
        assertTrue(s.crestDp < RecapSprig.DESIGN_CREST_DP)
        assertTrue(s.sprigDp < RecapSprig.SIZE_DP)
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

    @Test fun `the bar grows over 1·2s after 0·9s`() {
        assertEquals(0f, RecapMotion.growProgress(900), 0f)
        assertEquals(0.5f, RecapMotion.growProgress(1500), 1e-6f)
        assertEquals(1f, RecapMotion.growProgress(2100), 0f)
    }

    @Test fun `the knob pops to 1·35 and back after 2s`() {
        val linear = { t: Float -> t }
        assertEquals(1f, RecapMotion.knobScale(RecapMotion.popProgress(1999), linear), 0f)
        assertEquals(1.35f, RecapMotion.knobScale(RecapMotion.popProgress(2250), linear), 1e-5f)
        assertEquals(1f, RecapMotion.knobScale(RecapMotion.popProgress(2500), linear), 0f)
    }
}
