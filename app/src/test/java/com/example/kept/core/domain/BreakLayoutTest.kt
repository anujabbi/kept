package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Break the lock (`A4-Break`, spec Break the lock): the fade, the countdown, the copy and the layout. */
class BreakLayoutTest {

    @Test fun saturationFadesFromOneToAQuarter() {
        assertEquals(1f, BreakFade.saturation(60), 1e-6f)
        assertEquals(0.625f, BreakFade.saturation(30), 1e-6f)
        assertEquals(0.25f, BreakFade.saturation(0), 1e-6f)
    }

    @Test fun bubbleTextAlphaFadesFromOneTo035() {
        assertEquals(1f, BreakFade.voiceAlpha(60), 1e-6f)
        assertEquals(0.675f, BreakFade.voiceAlpha(30), 1e-6f)
        assertEquals(0.35f, BreakFade.voiceAlpha(0), 1e-6f)
    }

    @Test fun fadeStaysInRangeOutsideTheMinute() {
        assertEquals(1f, BreakFade.saturation(90), 1e-6f)
        assertEquals(0.25f, BreakFade.saturation(-5), 1e-6f)
        assertEquals(0.35f, BreakFade.voiceAlpha(-1), 1e-6f)
    }

    @Test fun countdownIsSixtySecondsOfWholeSteps() {
        assertEquals(60, BREAK_COUNTDOWN_SECONDS)
        val t0 = 1_000_000L
        assertEquals(60, BreakCountdown.secondsLeft(t0, t0))
        assertEquals(60, BreakCountdown.secondsLeft(t0, t0 + 999))
        assertEquals(59, BreakCountdown.secondsLeft(t0, t0 + 1_000))
        assertEquals(30, BreakCountdown.secondsLeft(t0, t0 + 30_500))
        assertEquals(1, BreakCountdown.secondsLeft(t0, t0 + 59_999))
        assertEquals(0, BreakCountdown.secondsLeft(t0, t0 + 60_000))
        assertEquals(0, BreakCountdown.secondsLeft(t0, t0 + 3_600_000))
        // A clock that went backwards never adds time.
        assertEquals(60, BreakCountdown.secondsLeft(t0, t0 - 5_000))
    }

    @Test fun nextTickLandsOnTheSecond() {
        assertEquals(1_000L, BreakCountdown.untilNextTickMs(0, 0))
        assertEquals(250L, BreakCountdown.untilNextTickMs(0, 10_750))
        assertEquals(12, BreakCountdown.secondsWaited(0, 12_999))
    }

    @Test fun clockReadsMinutesAndSeconds() {
        assertEquals("1:00", BreakCountdown.clock(60))
        assertEquals("0:30", BreakCountdown.clock(30))
        assertEquals("0:09", BreakCountdown.clock(9))
        assertEquals("0:00", BreakCountdown.clock(0))
    }

    @Test fun labelsChangeAtZero() {
        assertEquals("You can unlock in", BreakCopy.clockLabel(1))
        assertEquals("You can unlock now", BreakCopy.clockLabel(0))
        assertEquals("Unlock anyway (60s)", BreakCopy.unlockLabel(60, 30))
        assertEquals("Unlock anyway (1s)", BreakCopy.unlockLabel(1, 30))
        assertEquals("Unlock for 30 min", BreakCopy.unlockLabel(0, 30))
        assertEquals("Unlock for 15 min", BreakCopy.unlockLabel(0, 15))
    }

    @Test fun normalBreakListsTheThreeCostsWithRealValues() {
        val c = BreakCosts.of(level = 4, streakDays = 12, breaksLeft = 2, overCap = false) as BreakCosts.Normal
        assertEquals(
            listOf(
                BreakCosts.Row(BreakCosts.Kind.WILT, "Sprig drops a level and wilts until tomorrow."),
                BreakCosts.Row(BreakCosts.Kind.STREAK, "Your 12 day streak stays."),
                BreakCosts.Row(BreakCosts.Kind.BREAKS, "2 of 3 breaks left this week."),
            ),
            c.rows,
        )
        val one = BreakCosts.of(level = 2, streakDays = 1, breaksLeft = 1, overCap = false) as BreakCosts.Normal
        assertEquals(listOf("Sprig drops a level and wilts until tomorrow.", "Your 1 day streak stays.", "1 of 3 breaks left this week."), one.rows.map { it.text })
    }

    @Test fun theFourthBreakIsTheLastResort() {
        val c = BreakCosts.of(level = 4, streakDays = 12, breaksLeft = 0, overCap = true)
        assertEquals(
            BreakCosts.LastResort(
                "This is your fourth break this week, the last resort.",
                "Today is written off. It won't count toward your 12 day streak, and your shield can't save it.",
            ),
            c,
        )
    }

    @Test fun gapsTheDesignDoesNotDraw() {
        // Sprig at the floor level cannot drop one; no streak, no streak row (DECISIONS.md, #128).
        val c = BreakCosts.of(level = LevelRules.FLOOR, streakDays = 0, breaksLeft = 3, overCap = false) as BreakCosts.Normal
        assertEquals(listOf("Sprig wilts until tomorrow.", "3 of 3 breaks left this week."), c.rows.map { it.text })
        assertEquals(
            "Today is written off. It won't count toward your streak, and your shield can't save it.",
            (BreakCosts.of(1, 0, 0, overCap = true) as BreakCosts.LastResort).line,
        )
    }

    @Test fun designArtboardGivesTheDesignValues() {
        // 390 × 844, the design's headline (2 lines of 38 → ends at 136) and bubble (41 tall).
        val s = BreakSprig.spot(390, 844, 136, 41)
        assertEquals(312, s.crestDp)
        assertEquals(230, s.sprigDp)
        assertEquals(116, s.sprigTopDp)
        assertEquals(166, s.sprigLeftDp)
        assertEquals(178, s.bubbleTopDp)
    }

    @Test fun narrowScreenShrinksSprigAndKeepsTheHeadlineColumn() {
        val s = BreakSprig.spot(360, 915, 136, 41)
        assertEquals(312, s.crestDp)
        assertEquals(200, s.sprigDp)
        assertEquals(166, s.sprigLeftDp)
        assertEquals("bottom stays 34 below the crest", 312 + 34, s.sprigTopDp + s.sprigDp)
        val wide = BreakSprig.spot(412, 915, 136, 41)
        assertEquals(230, wide.sprigDp)
        assertEquals(412 + 6 - 230, wide.sprigLeftDp)
    }

    @Test fun shortScreenBringsTheCrestUpAndSprigShrinksWithIt() {
        val s = BreakSprig.spot(360, 640, 136, 41)
        assertEquals(BreakSprig.CREST_MIN_DP, s.crestDp)
        assertEquals(BreakSprig.SIZE_MIN_DP, s.sprigDp)
        assertEquals(s.crestDp + 34, s.sprigTopDp + s.sprigDp)
        assertTrue("bubble clears the headline", s.bubbleTopDp >= 136 + 12)
        assertTrue("bubble ends above the crest", s.bubbleTopDp + 41 <= s.crestDp)
    }

    @Test fun tallTextMovesTheCrestDown() {
        // Font 2.0: a three-line headline and a two-line bubble.
        val s = BreakSprig.spot(412, 915, 270, 70)
        assertEquals(270 + 12 + 70 + 24, s.crestDp)
        assertEquals(282, s.bubbleTopDp)
        assertEquals(230, s.sprigDp)
        assertEquals(s.crestDp + 34 - 230, s.sprigTopDp)
    }

    @Test fun bubbleNeverReachesSprigsBody() {
        for (w in listOf(360, 390, 412)) for (h in listOf(640, 844, 915)) {
            val s = BreakSprig.spot(w, h, 136, 41)
            val body = s.sprigLeftDp + s.sprigDp * 16f / 120f
            assertTrue("$w × $h: bubble ends at ${20 + s.bubbleMaxWidthDp}, body at $body", 20 + s.bubbleMaxWidthDp < body)
            // The design's plea (about 165dp wide at font 1.0) still fits on one line.
            assertTrue("$w × $h: bubble only ${s.bubbleMaxWidthDp} wide", s.bubbleMaxWidthDp >= 168)
        }
    }
}
