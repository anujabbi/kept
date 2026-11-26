package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    @Test fun `roomy for one to three habits, compact from four to ten`() {
        (0..3).forEach { assertEquals("$it habits", HomeLayout.ROOMY, HomeLayout.forHabitCount(it)) }
        (4..10).forEach { assertEquals("$it habits", HomeLayout.COMPACT, HomeLayout.forHabitCount(it)) }
    }

    @Test fun `headline words one to ten`() {
        val words = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")
        words.forEachIndexed { i, w ->
            val left = i + 1
            assertEquals("$w to go.", HomeCopy.headline(total = 10, done = 10 - left, keptOnTime = false))
        }
        assertEquals("One to go.", HomeCopy.headline(total = 2, done = 1, keptOnTime = false))
        assertEquals("Two to go.", HomeCopy.headline(total = 2, done = 0, keptOnTime = false))
    }

    @Test fun `promise kept only when finished in time`() {
        assertEquals("Promise kept.", HomeCopy.headline(total = 2, done = 2, keptOnTime = true))
        assertEquals("Too late to count.", HomeCopy.headline(total = 2, done = 2, keptOnTime = false))
    }

    @Test fun `line under the headline`() {
        assertEquals("Apps unlock when you finish, or at 9:00\u00A0pm.", HomeCopy.line(2, 0, "9:00 pm", pastDue = false, breakActive = false))
        assertEquals("Apps are open. See you tomorrow.", HomeCopy.line(2, 2, "9:00 pm", pastDue = false, breakActive = false))
        assertEquals("Apps are open. See you tomorrow.", HomeCopy.line(2, 2, "9:00 pm", pastDue = true, breakActive = false))
        assertEquals("Apps are open. Today won't count.", HomeCopy.line(2, 1, "9:00 pm", pastDue = true, breakActive = false))
        assertEquals("Lock paused for a break. Apps lock again soon.", HomeCopy.line(2, 1, "9:00 pm", pastDue = false, breakActive = true))
    }

    @Test fun `habit detail`() {
        assertEquals("30 min", HomeCopy.habitDetail(30, "min"))
        assertEquals("10 pages", HomeCopy.habitDetail(10, "pages"))
        assertEquals("Once a day", HomeCopy.habitDetail(1, ""))
        assertEquals("Once a day", HomeCopy.habitDetail(1, "times"))
        assertEquals("1 glass", HomeCopy.habitDetail(1, "glass"))
    }

    @Test fun `home's crest sits at 156 for a short headline and moves down for tall text`() {
        assertEquals(156, HomeSprig.homeCrestDp(textBottomDp = 140))
        assertEquals(312, HomeSprig.homeCrestDp(textBottomDp = 300))
    }

    @Test fun `home's Sprig shows its face with nothing done and rises 34dp, its bottom always under the crest`() {
        assertEquals(56, HomeSprig.homeTopDp(156, 0, 2))
        assertEquals(39, HomeSprig.homeTopDp(156, 1, 2))
        assertEquals(22, HomeSprig.homeTopDp(156, 2, 2))
        for (total in 1..10) for (done in 0..total) {
            assertTrue(HomeSprig.homeTopDp(156, done, total) + HomeSprig.HOME_SPRIG_DP >= 156 + 16)
        }
    }

    @Test fun `home's Sprig is 150dp and the headline column takes the rest`() {
        assertEquals(HomeSprig.CompactWidths(220, 150), HomeSprig.homeWidths(412))
        assertEquals(HomeSprig.CompactWidths(168, 150), HomeSprig.homeWidths(360))
        for (w in 320..480) {
            val (text, sprig) = HomeSprig.homeWidths(w)
            assertTrue("$w: text ends ${HomeSprig.TEXT_SIDE_DP + text}", HomeSprig.TEXT_SIDE_DP + text + HomeSprig.COMPACT_GAP_DP <= w - HomeSprig.COMPACT_SPRIG_END_DP - sprig)
        }
    }

    @Test fun `compact widths never let the headline column reach Sprig`() {
        for (w in 320..480) {
            val (text, sprig) = HomeSprig.compactWidths(w)
            val textEnd = HomeSprig.TEXT_SIDE_DP + text
            val sprigStart = w - HomeSprig.COMPACT_SPRIG_END_DP - sprig
            assertTrue("$w: text ends $textEnd, Sprig starts $sprigStart", textEnd + HomeSprig.COMPACT_GAP_DP <= sprigStart)
            assertTrue("$w: text $text", text in 1..180)
            assertTrue("$w: sprig $sprig", sprig in HomeSprig.COMPACT_SPRIG_MIN_DP..180)
        }
    }

    @Test fun `compact widths keep the design where there is room and shrink Sprig first`() {
        assertEquals(HomeSprig.CompactWidths(180, 180), HomeSprig.compactWidths(412))
        assertEquals(HomeSprig.CompactWidths(180, 168), HomeSprig.compactWidths(390))
        assertEquals(HomeSprig.CompactWidths(168, 150), HomeSprig.compactWidths(360))
    }
}
