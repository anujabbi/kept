package com.example.kept.core.domain

import com.example.kept.core.domain.SprigForm.ANCIENT
import com.example.kept.core.domain.SprigForm.BLOOM
import com.example.kept.core.domain.SprigForm.BUD
import com.example.kept.core.domain.SprigForm.CELESTIAL
import com.example.kept.core.domain.SprigForm.GROVE
import com.example.kept.core.domain.SprigForm.SPRIG
import com.example.kept.core.domain.SprigForm.THICKET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Sprig tab's rules (spec, Collection; `A6-Collection`). */
class CollectionLayoutTest {

    private fun statuses(gallery: List<SprigForm>, current: SprigForm) =
        CollectionForms.tiles(gallery, current).map { it.status }

    @Test fun `the design's Bloom state - three reached, Bloom current, four locked`() {
        val s = statuses(listOf(BUD, BLOOM), BLOOM)
        assertEquals(
            listOf(FormStatus.REACHED, FormStatus.REACHED, FormStatus.CURRENT) + List(4) { FormStatus.LOCKED },
            s,
        )
        assertEquals(3, CollectionForms.reachedCount(listOf(BUD, BLOOM), BLOOM))
    }

    @Test fun `all seven forms always show, in order`() {
        assertEquals(SprigForm.entries, CollectionForms.tiles(emptyList(), SPRIG).map { it.form })
    }

    @Test fun `day 0 - Sprig is current and the only form reached`() {
        assertEquals(listOf(FormStatus.CURRENT) + List(6) { FormStatus.LOCKED }, statuses(emptyList(), SPRIG))
        assertEquals(1, CollectionForms.reachedCount(emptyList(), SPRIG))
    }

    @Test fun `day 100 - all seven reached, Celestial current`() {
        val gallery = listOf(BUD, BLOOM, THICKET, GROVE, ANCIENT, CELESTIAL)
        assertEquals(List(6) { FormStatus.REACHED } + FormStatus.CURRENT, statuses(gallery, CELESTIAL))
        assertEquals(7, CollectionForms.reachedCount(gallery, CELESTIAL))
    }

    /** QA on #125: the old subtitle counted gallery rows, so a form reached twice counted twice. */
    @Test fun `a form reached twice counts once`() {
        val gallery = listOf(BUD, BLOOM, BUD, BLOOM, THICKET, BUD)
        assertEquals(4, CollectionForms.reachedCount(gallery, THICKET))
        assertEquals("4 of 7 reached", CollectionCopy.reached(CollectionForms.reachedCount(gallery, THICKET)))
    }

    @Test fun `a form reached before a streak reset stays reached`() {
        val gallery = listOf(BUD, BLOOM, THICKET, GROVE)
        val s = statuses(gallery, SPRIG)
        assertEquals(FormStatus.CURRENT, s[0])
        assertEquals(List(4) { FormStatus.REACHED }, s.subList(1, 5))
        assertEquals(listOf(FormStatus.LOCKED, FormStatus.LOCKED), s.subList(5, 7))
        assertEquals(5, CollectionForms.reachedCount(gallery, SPRIG))
    }

    @Test fun `forms below the current one count as reached even with no gallery entry`() {
        assertEquals(3, CollectionForms.reachedCount(emptyList(), BLOOM))
    }

    @Test fun `only locked forms cannot be tapped`() {
        val tiles = CollectionForms.tiles(listOf(BUD), BUD)
        assertTrue(tiles[0].tappable)
        assertTrue(tiles[1].tappable)
        tiles.drop(2).forEach { assertFalse(it.form.name, it.tappable) }
    }

    @Test fun `notes - you are here, reached, day n`() {
        val tiles = CollectionForms.tiles(listOf(BUD, BLOOM), BLOOM)
        assertEquals(
            listOf("Reached", "Reached", "You are here", "Day 14", "Day 30", "Day 60", "Day 100"),
            tiles.map(CollectionCopy::note),
        )
    }

    @Test fun `hero copy`() {
        assertEquals("Day 12 of your streak", CollectionCopy.day(12))
        assertEquals("Day 0 of your streak", CollectionCopy.day(0))
        assertEquals("3 of 7 reached", CollectionCopy.reached(3))
    }

    @Test fun `pill - next form and days left, final form at Celestial`() {
        fun pill(n: Int) = CollectionCopy.nextPill(n).replace(' ', ' ')
        assertEquals("Thicket in 2 days", pill(12))
        assertEquals("Bud in 3 days", pill(0))
        assertEquals("Bud in 1 day", pill(2))
        assertEquals("Bloom in 4 days", pill(3))
        assertEquals("Celestial in 1 day", pill(99))
        assertEquals("Final form", pill(100))
        assertEquals("Final form", pill(365))
    }

    @Test fun `the pill never has a breakable space`() {
        listOf(0, 2, 12, 99).forEach { assertFalse(CollectionCopy.nextPill(it).contains(' ')) }
    }

    @Test fun `widths - the design's 200dp Sprig from 412dp, Sprig gives way first on narrow screens`() {
        assertEquals(CollectionSprig.Widths(180, 200), CollectionSprig.widths(412))
        assertEquals(CollectionSprig.Widths(180, 178), CollectionSprig.widths(390))
        assertEquals(CollectionSprig.Widths(178, 150), CollectionSprig.widths(360))
        assertEquals(CollectionSprig.Widths(138, 150), CollectionSprig.widths(320))
    }

    @Test fun `the headline column and Sprig never touch`() {
        for (w in 320..600) {
            val x = CollectionSprig.widths(w)
            val textEnd = CollectionSprig.TEXT_SIDE_DP + x.textDp
            val sprigStart = w - CollectionSprig.SPRIG_END_DP - x.sprigDp
            assertTrue("at $w: text ends $textEnd, Sprig starts $sprigStart", textEnd + CollectionSprig.GAP_DP <= sprigStart)
        }
    }

    @Test fun `crest - the design's 252, lower when the headline block grows`() {
        assertEquals(252, CollectionSprig.crestDp(150))
        assertEquals(252, CollectionSprig.crestDp(240))
        assertEquals(312, CollectionSprig.crestDp(300))
    }

    @Test fun `Sprig's bottom goes under the hill at both lower corners, at every width`() {
        for (w in 320..600) for (crest in listOf(252, 312)) {
            val x = CollectionSprig.widths(w)
            val top = CollectionSprig.sprigTopDp(crest, x.sprigDp, w)
            val bottom = top + x.sprigDp
            val right = (w - CollectionSprig.SPRIG_END_DP).toFloat()
            for (edge in listOf(right - x.sprigDp, right)) {
                val hill = CollectionSprig.hillDp(crest, w, edge)
                assertTrue("at $w, crest $crest: bottom $bottom vs hill $hill at $edge", bottom >= hill + 1f)
            }
            assertTrue("at $w: Sprig's bottom is not under the crest", bottom >= crest + CollectionSprig.SPRIG_BELOW_CREST_DP)
            assertTrue("at $w: Sprig's top is under the crest", top < crest)
        }
    }

    @Test fun `the hill is the foundation's ellipse`() {
        assertEquals(252f, CollectionSprig.hillDp(252, 412, 206f), 0.001f)
        assertEquals(300f, CollectionSprig.hillDp(252, 412, -60f), 0.001f)
        assertEquals(300f, CollectionSprig.hillDp(252, 412, 472f), 0.001f)
    }
}
