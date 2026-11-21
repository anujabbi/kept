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

    @Test fun `streak pill copy`() {
        assertEquals("12 day streak", HomeCopy.streak(12))
        assertEquals("1 day streak", HomeCopy.streak(1))
    }

    @Test fun `habit detail`() {
        assertEquals("30 min", HomeCopy.habitDetail(30, "min"))
        assertEquals("10 pages", HomeCopy.habitDetail(10, "pages"))
        assertEquals("Once a day", HomeCopy.habitDetail(1, ""))
        assertEquals("Once a day", HomeCopy.habitDetail(1, "times"))
        assertEquals("1 glass", HomeCopy.habitDetail(1, "glass"))
    }

    @Test fun `sprig tops match the design scripts`() {
        // Main.dc.html, two habits: 224, 200, 176.
        assertEquals(listOf(224, 200, 176), (0..2).map { HomeSprig.designTopDp(HomeLayout.ROOMY, it, 2) })
        // A2-Home-6.dc.html: round(100 − 44 × frac).
        assertEquals(100, HomeSprig.designTopDp(HomeLayout.COMPACT, 0, 6))
        assertEquals(78, HomeSprig.designTopDp(HomeLayout.COMPACT, 3, 6))
        assertEquals(56, HomeSprig.designTopDp(HomeLayout.COMPACT, 6, 6))
        assertEquals(100, HomeSprig.designTopDp(HomeLayout.COMPACT, 0, 0))
    }

    @Test fun `sprig rises as habits are done`() {
        for (layout in HomeLayout.entries) for (total in 1..10) {
            val tops = (0..total).map { HomeSprig.designTopDp(layout, it, total) }
            assertEquals("$layout $total never sinks", tops, tops.sortedDescending())
        }
    }

    @Test fun `crest stays at the design for normal text and moves down for tall text`() {
        assertEquals(396, HomeSprig.crestDp(HomeLayout.ROOMY, textBottomDp = 150))
        assertEquals(212, HomeSprig.crestDp(HomeLayout.COMPACT, textBottomDp = 170))
        // Roomy: even the highest Sprig (everything done, 220 above the crest) clears the text.
        val crest = HomeSprig.crestDp(HomeLayout.ROOMY, textBottomDp = 300)
        assertTrue(HomeSprig.topDp(HomeLayout.ROOMY, crest, 2, 2) >= 300 + HomeSprig.CLEARANCE_DP)
        assertEquals(312, HomeSprig.crestDp(HomeLayout.COMPACT, textBottomDp = 300))
    }

    @Test fun `sprig never overlaps the headline and always reaches the crest in the listed states`() {
        val states = listOf(0 to 1, 0 to 3, 3 to 3, 0 to 6, 6 to 6, 0 to 10)
        for (textBottom in listOf(150, 250, 420)) for ((done, total) in states) {
            val layout = HomeLayout.forHabitCount(total)
            val crest = HomeSprig.crestDp(layout, textBottom)
            val top = HomeSprig.topDp(layout, crest, done, total)
            val size = HomeSprig.sizeDp(layout)
            if (layout == HomeLayout.ROOMY) assertTrue("$done/$total below text", top >= textBottom + HomeSprig.CLEARANCE_DP)
            // Its head is above the hill and its body reaches behind it: never floating, never buried.
            assertTrue("$done/$total head above crest", top < crest)
            assertTrue("$done/$total body behind hill", top + size > crest)
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
        assertEquals(312, HomeSprig.roomyTextDp(360))
        assertEquals(364, HomeSprig.roomyTextDp(412))
    }

    @Test fun `a smaller compact Sprig keeps the design's bottom`() {
        for (w in listOf(360, 390, 412)) {
            val sprig = HomeSprig.compactWidths(w).sprigDp
            for (total in 4..10) for (done in 0..total) {
                val top = HomeSprig.topDp(HomeLayout.COMPACT, 212, done, total, sprig)
                val designBottom = HomeSprig.topDp(HomeLayout.COMPACT, 212, done, total) + 180
                assertEquals("$w $done/$total", designBottom, top + sprig)
                // The design's highest bottom (everything done) is 56 + 180 = 236, 24dp below the crest.
                assertTrue("$w $done/$total bottom ${top + sprig} below crest", top + sprig >= 212 + 24)
            }
        }
    }

    @Test fun `the give-up time never splits`() {
        val line = HomeCopy.line(2, 0, "9:00 pm", pastDue = false, breakActive = false)
        assertTrue(line.endsWith("9:00\u00A0pm."))
    }
}
