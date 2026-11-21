package com.example.kept.core.domain

import com.example.kept.core.domain.LockWindowRules.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Onboarding's pure rules (#132): the lock window clamps, the copy, and the two Sprig layouts. */
class OnboardingLayoutTest {

    private fun h(hour: Int, min: Int = 0) = hour * 60 + min

    // Lock window (A11) ------------------------------------------------------------------------

    @Test fun `every button moves by 30 minutes`() {
        val w = Window(h(7), h(21))
        assertEquals(Window(h(6, 30), h(21)), LockWindowRules.fromEarlier(w))
        assertEquals(Window(h(7, 30), h(21)), LockWindowRules.fromLater(w))
        assertEquals(Window(h(7), h(20, 30)), LockWindowRules.dueEarlier(w))
        assertEquals(Window(h(7), h(21, 30)), LockWindowRules.dueLater(w))
    }

    @Test fun `the start never goes before 6 am`() {
        val w = Window(h(6), h(21))
        assertEquals(w, LockWindowRules.fromEarlier(w))
        assertFalse(LockWindowRules.canFromEarlier(w))
        assertEquals(h(6), LockWindowRules.fromEarlier(Window(h(6, 15), h(21))).fromMinute)
    }

    @Test fun `give-up never goes after 11 30 pm`() {
        val w = Window(h(7), h(23, 30))
        assertEquals(w, LockWindowRules.dueLater(w))
        assertFalse(LockWindowRules.canDueLater(w))
    }

    @Test fun `give-up stays at least two hours after the start, from either side`() {
        val w = Window(h(10), h(12))
        assertEquals(w, LockWindowRules.fromLater(w))
        assertEquals(w, LockWindowRules.dueEarlier(w))
        assertFalse(LockWindowRules.canFromLater(w))
        assertFalse(LockWindowRules.canDueEarlier(w))
        // Half a step of room: the start moves only as far as the gap allows.
        assertEquals(h(10, 15), LockWindowRules.fromLater(Window(h(10), h(12, 15))).fromMinute)
        assertEquals(h(12), LockWindowRules.dueEarlier(Window(h(10), h(12, 15))).dueMinute)
    }

    @Test fun `walking every button to its end keeps the window inside the rules`() {
        var w = Window(h(7), h(21))
        repeat(60) { w = LockWindowRules.fromLater(w) }
        assertEquals(h(19), w.fromMinute)
        repeat(60) { w = LockWindowRules.dueLater(w) }
        assertEquals(h(23, 30), w.dueMinute)
        repeat(60) { w = LockWindowRules.fromLater(w) }
        assertEquals(Window(h(21, 30), h(23, 30)), w)
        repeat(60) { w = LockWindowRules.fromEarlier(w); w = LockWindowRules.dueEarlier(w) }
        assertEquals(Window(h(6), h(8)), w)
    }

    @Test fun `a stored window outside the bar is brought inside it`() {
        // A window set in Settings that runs past midnight, or starts before 6 am.
        assertEquals(Window(h(21, 30), h(23, 30)), LockWindowRules.normalize(h(22), h(1)))
        assertEquals(Window(h(6), h(9)), LockWindowRules.normalize(h(5), h(9)))
        assertEquals(Window(h(9), h(11)), LockWindowRules.normalize(h(9), h(10)))
        // Inside the rules: untouched, even off the 30 minute grid.
        assertEquals(Window(h(6), h(22, 15)), LockWindowRules.normalize(h(6), h(22, 15)))
        assertEquals(Window(h(7), h(21)), LockWindowRules.normalize(h(7), h(21)))
    }

    @Test fun `the bar runs from 6 am to midnight`() {
        assertEquals(0f, LockWindowRules.barFraction(h(6)), 0f)
        assertEquals(0f, LockWindowRules.barFraction(h(5)), 0f)
        assertEquals(1f / 3, LockWindowRules.barFraction(h(12)), 1e-6f)
        assertEquals(5f / 6, LockWindowRules.barFraction(h(21)), 1e-6f)
        assertEquals(1f, LockWindowRules.barFraction(h(24)), 0f)
    }

    // Copy ---------------------------------------------------------------------------------------

    @Test fun `times read 7 00 am with a no-break space`() {
        assertEquals("7:00 am", OnboardingCopy.time(h(7)))
        assertEquals("11:30 pm", OnboardingCopy.time(h(23, 30)))
        assertEquals("12:00 pm", OnboardingCopy.time(h(12)))
        assertFalse(OnboardingCopy.BAR_LABELS.any { ' ' in it })
    }

    @Test fun `the sentence names both times in bold`() {
        val parts = OnboardingCopy.ruleSentence(h(7), h(21))
        assertEquals(
            "From 7:00 am, everything but the essentials locks until your habits are done. If they are not done by 9:00 pm, apps open and the day is missed.",
            parts.joinToString("") { it.first },
        )
        assertEquals(listOf("7:00 am", "9:00 pm"), parts.filter { it.second }.map { it.first })
    }

    @Test fun `footer counts habits picked`() {
        assertEquals("Pick at least one", OnboardingCopy.habitsPicked(0))
        assertEquals("1 habit picked", OnboardingCopy.habitsPicked(1))
        assertEquals("2 habits picked", OnboardingCopy.habitsPicked(2))
        assertEquals("10 habits picked", OnboardingCopy.habitsPicked(10))
    }

    @Test fun `habit rows read as the design`() {
        assertEquals("30 min", OnboardingCopy.habitDetail(ProofType.MANUAL, 30, "min"))
        assertEquals("10 pages", OnboardingCopy.habitDetail(ProofType.MANUAL, 10, "pages"))
        assertEquals("Photo check-in", OnboardingCopy.habitDetail(ProofType.PHOTO, 1, "photo"))
        assertEquals("Tap when done", OnboardingCopy.habitDetail(ProofType.MANUAL, 1, "check-in"))
    }

    @Test fun `exceptions count line`() {
        assertEquals("Everything locks", OnboardingCopy.appsOpen(0))
        assertEquals("1 app stays open", OnboardingCopy.appsOpen(1))
        assertEquals("2 apps stay open", OnboardingCopy.appsOpen(2))
    }

    // Welcome (A1) -------------------------------------------------------------------------------

    /** The design frame: text ends about 271, the button block is about 110 tall. */
    @Test fun `on the design frame Sprig is 350 and sits as the design has it`() {
        val s = WelcomeLayout.spec(390f, 844f, 271f, 110f)
        assertEquals(844f, s.pageDp)
        assertEquals(844f - 36 - 110 - 58, s.crestDp)
        assertEquals(350f, s.sprigDp)
        assertEquals(20f, s.sprigLeftDp)
        assertTrue("Sprig clears the text", s.sprigTopDp >= 271f + WelcomeLayout.TEXT_GAP_DP)
    }

    @Test fun `Sprig's bottom is under the hill at both lower corners at every width`() {
        for (w in listOf(320f, 360f, 390f, 412f, 480f)) for (hgt in listOf(640f, 844f, 915f)) {
            val s = WelcomeLayout.spec(w, hgt, 271f, 110f)
            val bottom = s.sprigTopDp + s.sprigDp
            assertTrue("$w×$hgt", bottom >= s.crestDp + WelcomeLayout.SPRIG_BELOW_CREST_DP)
            for (x in listOf(s.sprigLeftDp, s.sprigLeftDp + s.sprigDp)) {
                assertTrue("$w×$hgt at $x", bottom >= HillEdge.topDp(s.crestDp, w, x) + 1.9f)
            }
            assertTrue("$w×$hgt: Sprig is centred", kotlin.math.abs(s.sprigLeftDp + s.sprigDp / 2 - w / 2) < 0.01f)
            assertTrue("$w×$hgt: 20dp from each side", s.sprigLeftDp >= 19.99f)
        }
    }

    @Test fun `a short screen shrinks Sprig to fit between the text and the hill`() {
        val s = WelcomeLayout.spec(360f, 640f, 271f, 110f)
        assertEquals(640f, s.pageDp)
        assertTrue(s.sprigDp in WelcomeLayout.SPRIG_MIN_DP.toFloat()..349f)
        assertTrue(s.sprigTopDp >= 271f + WelcomeLayout.TEXT_GAP_DP - 0.01f)
    }

    @Test fun `when even the smallest Sprig does not fit, the page grows instead`() {
        val s = WelcomeLayout.spec(360f, 592f, 460f, 160f)
        assertTrue(s.pageDp > 592f)
        assertTrue(s.sprigDp >= WelcomeLayout.SPRIG_MIN_DP)
        assertTrue(s.sprigTopDp >= 460f + WelcomeLayout.TEXT_GAP_DP - 0.01f)
    }

    // How Sprig works (A14) ---------------------------------------------------------------------

    @Test fun `ready widths come from the screen and never touch`() {
        for (w in listOf(320, 360, 390, 412)) {
            val x = ReadySprig.widths(w)
            assertTrue("$w", ReadySprig.TEXT_SIDE_DP + x.textDp + ReadySprig.GAP_DP + x.sprigDp + ReadySprig.SPRIG_END_DP <= w)
        }
        assertEquals(ReadySprig.Widths(170, 190), ReadySprig.widths(412))
        assertEquals(ReadySprig.SPRIG_MIN_DP, ReadySprig.widths(320).sprigDp)
    }

    @Test fun `ready Sprig keeps the design's 30dp under the crest and sits behind the hill`() {
        assertEquals(ReadySprig.DESIGN_CREST_DP, ReadySprig.crestDp(96 + 70))
        assertEquals(96 + 250 + 12, ReadySprig.crestDp(96 + 250))
        for (w in listOf(360, 390, 412)) {
            val x = ReadySprig.widths(w)
            val top = ReadySprig.sprigTopDp(252, x.sprigDp, w)
            val bottom = top + x.sprigDp
            assertTrue(bottom >= 252 + 30)
            val right = (w - ReadySprig.SPRIG_END_DP).toFloat()
            for (edge in listOf(right - x.sprigDp, right)) assertTrue("$w at $edge", bottom >= HillEdge.topDp(252f, w.toFloat(), edge) + 1.9f)
        }
    }
}
