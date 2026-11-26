package com.example.kept.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home has room for today's habits and the weekly card (#218): one compact sky for every habit
 * count, and "Last 7 days" and the shield scroll with the page under the weekly card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HomeRoomTest {

    @get:Rule val rule = createComposeRule()

    private val d get() = rule.density.density
    private fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y / d
    private fun bottom(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { (it.positionInRoot.y + it.size.height) / d }

    /** Home just after onboarding: Read and Meditate every day, Exercise 4 days a week, set today (a Wednesday). */
    private fun afterOnboarding(habits: Int = 2): HomeUiState {
        val rows = listOf("Read" to (10 to "pages"), "Meditate" to (20 to "min")) +
            listOf("Practice", "Study", "Stretch").map { it to (20 to "min") }
        val today = TodaySummary(
            rows.take(habits).mapIndexed { i, (title, t) ->
                HabitToday(HabitEntity(id = i + 1L, title = title, iconKey = "book", proofType = ProofType.MANUAL, targetValue = t.first, unit = t.second, createdAt = 0), null, dueMillis = HomeFixtures.DUE)
            },
        )
        val exercise = HomeFixtures.goal(1, "Exercise", 4, from = HomeFixtures.today, target = 30 to "min")
        return HomeFixtures.state(0, 0, firstLanding = true, weekly = listOf(exercise)).copy(today = today)
    }

    private fun show(s: HomeUiState) {
        val current = mutableStateOf(s)
        rule.setContent { Shot(1f) { HomeFrame(current.value) } }
        rule.waitForIdle()
    }

    @Test fun after_onboarding_the_banner_both_habits_and_the_whole_weekly_card_show_without_scrolling() {
        show(afterOnboarding())
        // Robolectric draws no system bars; the kept_api35 emulator (412 x 915dp) has a 49dp status
        // bar, which pushes the page down, and a 24dp navigation bar under the tab bar.
        val tabBar = rule.onRoot().fetchSemanticsNode().size.height / d - TAB_BAR_DP - NAV_BAR_DP
        for (tag in listOf("home_first_landing", "home_habit_1", "home_habit_2", "home_weekly_card_1")) {
            val end = bottom(tag) + STATUS_BAR_DP
            assertTrue("$tag ends at ${end}dp, under the tab bar at ${tabBar}dp", end <= tabBar)
        }
        // The sky is compact: the crest sits about 156dp down, not 396.
        assertTrue("crest at ${top("home_crest")}", top("home_crest") in 150f..170f)
        val sprig = rule.onNodeWithTag("home_sprig").fetchSemanticsNode().size.width / d
        assertEquals(150f, sprig, 0.5f)
    }

    @Test fun with_2_habits_the_last_seven_days_and_the_shield_scroll_under_the_weekly_card() {
        show(afterOnboarding())
        rule.onNodeWithTag("home_week", useUnmergedTree = true).assertExistsUnder("home_page")
        assertTrue("the week comes after the card", top("home_week") >= bottom("home_weekly_card_1"))
        val before = top("home_shield")
        rule.onNodeWithTag("home_page").performScrollToNode(hasTestTag("home_shield"))
        rule.waitForIdle()
        assertTrue("the shield moved with the page ($before to ${top("home_shield")})", top("home_shield") < before)
    }

    @Test fun with_5_habits_the_week_still_scrolls_with_the_page() {
        show(afterOnboarding(5))
        rule.onNodeWithTag("home_week", useUnmergedTree = true).assertExistsUnder("home_page")
        val before = top("home_shield")
        rule.onNodeWithTag("home_page").performScrollToNode(hasTestTag("home_shield"))
        rule.waitForIdle()
        assertTrue("the shield moved with the page ($before to ${top("home_shield")})", top("home_shield") < before)
        rule.onNodeWithTag("home_habit_list").assertDoesNotExist()
    }

    @Test fun at_font_2_the_weekly_today_row_keeps_its_title_whole() {
        val current = mutableStateOf(afterOnboarding())
        rule.setContent { Shot(2f) { HomeFrame(current.value) } }
        rule.onNodeWithTag("home_weekly_today_1").performScrollTo()
        val title = rule.onNode(hasText("Exercise") and hasAnyAncestor(hasTestTag("home_weekly_today_1")), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        title.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals("\"Exercise\" is split across lines beside \"Today · 30 min\"", 1, layouts.single().lineCount)
    }

    @Test fun a_goal_set_mid_week_says_why_this_week_asks_for_fewer() {
        show(afterOnboarding())
        val line = rule.onNodeWithTag("home_weekly_first_week_1").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertEquals("First week: 3 days left, so 3 this week", line.replace(' ', ' '))
    }

    @Test fun a_goal_past_its_first_week_has_no_such_line() {
        show(HomeFixtures.state(2, 0, weekly = listOf(HomeFixtures.onTrack())))
        rule.onNodeWithTag("home_weekly_first_week_1").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsUnder(parent: String) {
        val node = fetchSemanticsNode()
        assertTrue("${node.config} sits in $parent", hasAnyAncestor(hasTestTag(parent)).matches(node))
    }

    private companion object {
        /** `KeptTabBar`'s height. */
        const val TAB_BAR_DP = 74f

        /** The emulator's system bars, which Robolectric does not draw. */
        const val STATUS_BAR_DP = 49f
        const val NAV_BAR_DP = 24f
    }
}
