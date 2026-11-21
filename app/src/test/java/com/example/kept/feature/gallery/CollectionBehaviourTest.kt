package com.example.kept.feature.gallery

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.kept.core.data.GalleryCard
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigState
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** What the Sprig tab does when touched (issue #131). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class CollectionBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private fun show(s: GalleryUi, onOpen: (SprigForm) -> Unit = {}) {
        rule.setContent { Shot(1f) { CollectionFrame(s, onOpen) } }
        rule.waitForIdle()
    }

    @Test fun tapping_a_reached_form_opens_its_share_flow() {
        val opened = mutableListOf<SprigForm>()
        show(CollectionFixtures.DAY_12) { opened += it }
        for (f in listOf(SprigForm.SPRIG, SprigForm.BUD, SprigForm.BLOOM)) {
            rule.onNodeWithTag("collection_form_${f.name}").performScrollTo().assertHasClickAction().performClick()
        }
        assertEquals(listOf(SprigForm.SPRIG, SprigForm.BUD, SprigForm.BLOOM), opened)
    }

    @Test fun locked_forms_have_no_click_and_do_nothing_when_tapped() {
        val opened = mutableListOf<SprigForm>()
        show(CollectionFixtures.DAY_12) { opened += it }
        for (f in listOf(SprigForm.THICKET, SprigForm.GROVE, SprigForm.ANCIENT, SprigForm.CELESTIAL)) {
            rule.onNodeWithTag("collection_form_${f.name}").performScrollTo().assertHasNoClickAction().performClick()
        }
        assertEquals(emptyList<SprigForm>(), opened)
    }

    @Test fun cards_read_their_state_to_talkback() {
        show(CollectionFixtures.DAY_12)
        fun desc(f: SprigForm) = rule.onNodeWithTag("collection_form_${f.name}").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        assertEquals("Bud, reached", desc(SprigForm.BUD))
        assertEquals("Bloom, you are here", desc(SprigForm.BLOOM))
        assertEquals("Thicket, locked, unlocks at day 14", desc(SprigForm.THICKET))
    }

    /** QA on #125: reaching a form twice (two gallery rows) must not count it twice. */
    @Test fun the_count_is_of_distinct_forms_not_gallery_rows() {
        val d = LocalDate.of(2026, 9, 1)
        val cards = listOf(
            GalleryCard(1, SprigForm.BUD, d, 3, true),
            GalleryCard(2, SprigForm.BLOOM, d.plusDays(4), 7, true),
            GalleryCard(3, SprigForm.BUD, d.plusDays(20), 3, true),
            GalleryCard(4, SprigForm.BLOOM, d.plusDays(24), 7, true),
        )
        show(GalleryUi(cards, SprigState(streakDays = 8)))
        rule.onNodeWithTag("collection_count").assertTextEquals("3 of 7 reached")
    }

    @Test fun the_hero_shows_the_current_form_the_streak_and_the_next_form() {
        show(CollectionFixtures.DAY_12)
        rule.onNodeWithTag("collection_headline").assertTextEquals("Bloom")
        rule.onNodeWithTag("collection_day").assertTextEquals("Day 12 of your streak")
        rule.onNodeWithTag("collection_pill").assertTextEquals("Thicket in 2 days")
    }

    @Test fun the_final_form_says_so() {
        show(CollectionFixtures.DAY_100)
        rule.onNodeWithTag("collection_headline").assertTextEquals("Celestial")
        rule.onNodeWithTag("collection_pill").assertTextEquals("Final form")
        rule.onNodeWithTag("collection_count").assertTextEquals("7 of 7 reached")
    }

    @Test fun sprig_on_the_sky_is_interactive() {
        show(CollectionFixtures.DAY_12)
        rule.onNodeWithTag("collection_sprig").assertHasClickAction()
    }

    /** The whole page scrolls, Sprig and the sky with it; the tab bar stays where it is. */
    @Test fun the_page_scrolls_and_the_tab_bar_stays_fixed() {
        show(CollectionFixtures.DAY_12)
        rule.onNodeWithTag("collection_page").assert(hasScrollAction())
        val bar = rule.onNodeWithText("Today").fetchSemanticsNode().boundsInRoot
        val sprig = rule.onNodeWithTag("collection_sprig").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("collection_page").performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertEquals(bar, rule.onNodeWithText("Today").fetchSemanticsNode().boundsInRoot)
        assertNotEquals(sprig.top, rule.onNodeWithTag("collection_sprig").fetchSemanticsNode().boundsInRoot.top)
        // The last card scrolls clear of the tab bar.
        val last = rule.onNodeWithTag("collection_form_CELESTIAL").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertTrue("Celestial $last is under the tab bar $bar", last.bottom <= bar.top)
    }

    /** The share flow takes the form's first gallery entry; Sprig itself has none. */
    @Test fun sharing_a_form_uses_when_it_was_first_reached() {
        val d = LocalDate.of(2026, 9, 1)
        val cards = listOf(
            GalleryCard(1, SprigForm.BUD, d.plusDays(20), 3, true),
            GalleryCard(2, SprigForm.BUD, d, 3, true),
        )
        val state = SprigState(streakDays = 2)
        val bud = FormShare.of(SprigForm.BUD, cards, state)
        assertEquals(FormShare(SprigForm.BUD, 3, d), bud)
        assertEquals("Sprig became Bud", bud.content().headline)
        assertEquals("Reached 1 September 2026", bud.content().subline)
        assertEquals("Reached at a 3-day streak", bud.sheetLine)
        val sprig = FormShare.of(SprigForm.SPRIG, cards, state)
        assertEquals(FormShare(SprigForm.SPRIG, 2, null), sprig)
        assertEquals("Day 2 with Sprig", sprig.content().headline)
        assertEquals("Where everyone starts", sprig.content().subline)
        // A form below the current one with no entry (an old install) falls back to its threshold.
        assertEquals(FormShare(SprigForm.BLOOM, 7, null), FormShare.of(SprigForm.BLOOM, emptyList(), SprigState(streakDays = 20)))
    }

    @Test fun no_route_or_screen_name_for_the_roadmap_is_left() {
        val routes = com.example.kept.feature.app.Routes::class.java.declaredFields.map { it.name }
        assertTrue(routes.none { it.contains("ROADMAP") })
        val screens = com.example.kept.core.analytics.Screens::class.java.declaredFields.map { it.name }
        assertTrue(screens.none { it.contains("ROADMAP") })
        assertNull(com.example.kept.feature.app.screenNameFor("roadmap"))
    }
}
