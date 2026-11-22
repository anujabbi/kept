package com.example.kept.feature.home

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.example.kept.core.data.WeeklyGoalWeek
import com.example.kept.core.domain.WeeklyCardCopy
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home's weekly cards and the "Add a weekly goal" row (issue #165, Home; story #174). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HomeWeeklyBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private class Calls {
        val done = mutableListOf<Long>()
        val undone = mutableListOf<Long>()
        val habitsDone = mutableListOf<Long>()
        var add = 0
        var habits = 0
        fun actions() = HomeActions(
            onComplete = { habitsDone += it },
            onOpenHabits = { habits++ },
            onWeeklyDone = { done += it },
            onWeeklyUndo = { undone += it },
            onAddWeeklyGoal = { add++ },
        )
    }

    private val current = mutableStateOf(HomeFixtures.state(2, 0))
    private val scale = mutableFloatStateOf(1f)

    private fun show(s: HomeUiState, calls: Calls = Calls(), fontScale: Float = 1f): Calls {
        current.value = s
        scale.floatValue = fontScale
        rule.setContent { Shot(scale.floatValue) { HomeFrame(current.value, calls.actions()) } }
        return calls
    }

    private fun with(vararg goals: WeeklyGoalWeek, habits: Int = 2, done: Int = 0) = HomeFixtures.state(habits, done, weekly = goals.toList())

    private fun text(tag: String): String =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
            .replace(WeeklyCardCopy.NBSP, ' ')

    /** What TalkBack hears for the seven cells of goal [id]'s strip, Sunday first. */
    private fun strip(id: Long): List<String> =
        rule.onNodeWithTag("home_weekly_strip_$id", useUnmergedTree = true).fetchSemanticsNode().children
            .map { it.config[SemanticsProperties.ContentDescription].single() }

    // Where a part is laid out, whether or not it is scrolled into view (boundsInRoot is clipped).
    private fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
    private fun bottom(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    // --- no goal ---------------------------------------------------------------------------------

    @Test fun with_no_weekly_goal_home_offers_add_and_it_opens_setup() {
        val calls = show(HomeFixtures.state(2, 0))
        rule.onNodeWithText("This week").assertExists()
        rule.onNodeWithText("Sun to Sat").assertExists()
        rule.onNodeWithText("Add a weekly goal").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("For things you don't need to do every day.").assertExists()
        rule.onNodeWithTag("home_weekly_add").performClick()
        assertEquals(1, calls.add)
        assertEquals("it is not the add-a-habit row", 0, calls.habits)
        rule.onNodeWithTag("home_weekly_card_1").assertDoesNotExist()
    }

    @Test fun the_add_row_is_offered_in_every_layout_and_on_the_empty_home() {
        show(HomeFixtures.state(2, 0))
        for (n in listOf(0, 1, 3, 4, 6, 10)) {
            current.value = HomeFixtures.state(n, 0)
            rule.waitForIdle()
            rule.onNodeWithTag("home_weekly_add").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun once_there_is_a_goal_the_add_row_gives_way_to_its_card() {
        show(with(HomeFixtures.exampleB()))
        rule.onNodeWithTag("home_weekly_add").assertDoesNotExist()
        rule.onNodeWithText("Add a weekly goal").assertDoesNotExist()
        rule.onNodeWithTag("home_weekly_card_1").assertExists()
    }

    // --- the card --------------------------------------------------------------------------------

    @Test fun example_B_reads_1_of_4_with_sunday_done_monday_today_and_no_lock() {
        show(with(HomeFixtures.exampleB()))
        assertEquals("Exercise · 1 of 4 this week", text("home_weekly_title_1"))
        assertEquals(
            listOf(
                "Sunday, done", "Monday, today, not done yet", "Tuesday, coming up", "Wednesday, coming up",
                "Thursday, coming up", "Friday, coming up", "Saturday, coming up",
            ),
            strip(1),
        )
        rule.onNodeWithText("On track, no lock").assertExists()
        rule.onNodeWithText("Today counts: apps lock until it's done").assertDoesNotExist()
    }

    @Test fun example_A_shows_three_skipped_days_and_says_today_counts() {
        show(with(HomeFixtures.todayCounts()))
        assertEquals("Exercise · 0 of 4 this week", text("home_weekly_title_1"))
        assertEquals(
            listOf(
                "Sunday, skipped", "Monday, skipped", "Tuesday, skipped", "Wednesday, today, not done yet",
                "Thursday, coming up", "Friday, coming up", "Saturday, coming up",
            ),
            strip(1),
        )
        rule.onNodeWithText("Today counts: apps lock until it's done").assertExists()
        rule.onNodeWithText("On track, no lock").assertDoesNotExist()
    }

    @Test fun days_before_the_goal_was_set_are_spoken_as_not_counted() {
        show(with(HomeFixtures.goal(1, "Practice", 2, from = HomeFixtures.sunday.plusDays(2))))
        assertEquals("Practice · 0 of 2 this week", text("home_weekly_title_1"))
        assertEquals(listOf("Sunday, not counted", "Monday, not counted", "Tuesday, not counted", "Wednesday, today, not done yet"), strip(1).take(4))
    }

    @Test fun the_next_sunday_reads_0_of_4_with_an_empty_strip() {
        show(with(HomeFixtures.goal(1, "Exercise", 4, on = HomeFixtures.sunday.plusDays(7))))
        assertEquals("Exercise · 0 of 4 this week", text("home_weekly_title_1"))
        assertEquals(listOf("Sunday, today, not done yet") + listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").map { "$it, coming up" }, strip(1))
        rule.onNodeWithText("On track, no lock").assertExists()
    }

    // --- mark done and undo ----------------------------------------------------------------------

    @Test fun tapping_today_logs_the_session_and_holding_it_once_done_undoes_it() {
        val calls = show(with(HomeFixtures.exampleB()))
        val row = rule.onNodeWithTag("home_weekly_today_1")
        assertEquals(ToggleableState.Off, row.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
        row.performClick()
        assertEquals(listOf(1L), calls.done)

        // Logged: the count, today's cell and the row follow.
        current.value = with(HomeFixtures.goal(1, "Exercise", 4, 0, 1, on = HomeFixtures.sunday.plusDays(1)))
        rule.waitForIdle()
        assertEquals("Exercise · 2 of 4 this week", text("home_weekly_title_1"))
        assertEquals("Monday, today, done", strip(1)[1])
        assertEquals(ToggleableState.On, row.fetchSemanticsNode().config[SemanticsProperties.ToggleableState])

        // Tapping a done row, once or twice, asks for nothing.
        row.performClick()
        row.performClick()
        assertEquals("a done session is not logged again", listOf(1L), calls.done)
        assertTrue(calls.undone.isEmpty())

        row.performTouchInput { longClick() }
        assertEquals(listOf(1L), calls.undone)
        assertTrue("logging a weekly session never ticks a habit", calls.habitsDone.isEmpty())
    }

    @Test fun each_card_logs_its_own_goal() {
        val calls = show(HomeFixtures.state(2, 0, weekly = HomeFixtures.threeGoals()))
        rule.onNodeWithTag("home_weekly_today_3").performScrollTo().performClick()
        rule.onNodeWithTag("home_weekly_today_2").performScrollTo().performClick()
        assertEquals(listOf(3L, 2L), calls.done)
    }

    // --- three goals, and the habits -------------------------------------------------------------

    @Test fun three_goals_are_three_cards_and_the_habits_and_their_count_do_not_change() {
        show(HomeFixtures.state(2, 1))
        val headline = text("home_headline")
        val line = text("home_line")
        current.value = HomeFixtures.state(2, 1, weekly = HomeFixtures.threeGoals())
        rule.waitForIdle()
        for (id in 1L..3L) rule.onNodeWithTag("home_weekly_card_$id").assertExists()
        assertEquals("Exercise · 2 of 4 this week", text("home_weekly_title_1"))
        assertEquals("Read · 1 of 5 this week", text("home_weekly_title_2"))
        assertEquals("Practice · 0 of 2 this week", text("home_weekly_title_3"))
        // "Today" still counts the two habits, and the headline and its line are the habits' alone.
        rule.onNodeWithText("1 of 2").assertExists()
        assertEquals(headline, text("home_headline"))
        assertEquals("One to go.", headline)
        assertEquals(line, text("home_line"))
        rule.onNodeWithTag("home_habit_1").assertExists()
        rule.onNodeWithTag("home_habit_2").assertExists()
        assertEquals(2, current.value.today.total)
    }

    @Test fun a_goal_does_not_make_home_compact_or_count_as_a_habit() {
        show(HomeFixtures.state(3, 0, weekly = HomeFixtures.threeGoals()))
        // Roomy rows say "Done. Hold to undo"; compact ones scroll in "home_habit_list".
        rule.onNodeWithTag("home_habit_list").assertDoesNotExist()
        rule.onNodeWithText("0 of 3").assertExists()
    }

    // --- where the section sits ------------------------------------------------------------------

    @Test fun cards_follow_the_habits_and_come_before_the_last_seven_days() {
        show(with(HomeFixtures.onTrack()))
        assertTrue(top("home_weekly") >= bottom("home_habit_2"))
        assertTrue(bottom("home_weekly_card_1") <= top("home_shield"))
    }

    @Test fun with_no_goal_the_add_row_comes_after_the_shield_so_the_week_stays_on_the_first_screen() {
        show(HomeFixtures.state(3, 0))
        assertTrue(top("home_weekly") >= bottom("home_shield"))
    }

    @Test fun in_the_compact_layout_the_section_ends_the_habit_list() {
        show(HomeFixtures.state(6, 0, weekly = listOf(HomeFixtures.onTrack())))
        rule.onNode(hasTestTag("home_weekly") and hasAnyAncestor(hasTestTag("home_habit_list"))).assertExists()
        rule.onNodeWithTag("home_weekly_today_1").performScrollTo().assertIsDisplayed()
        assertTrue(top("home_weekly") >= bottom("home_habit_6"))
        // The week and the shield keep their place under the list.
        rule.onNodeWithTag("home_shield").assertIsDisplayed()
    }

    @Test fun the_empty_home_keeps_add_a_habit_first() {
        show(HomeFixtures.state(0, 0, weekly = listOf(HomeFixtures.onTrack())))
        rule.onNodeWithText("Add a habit.").assertExists()
        assertTrue(top("home_weekly") >= bottom("home_add_first"))
        rule.onNodeWithTag("home_weekly_today_1").performScrollTo().assertIsDisplayed()
    }

    // --- accessibility ---------------------------------------------------------------------------

    /** Every tappable part of the section is laid out at least 48 × 48dp and has a TalkBack label. */
    private fun assertSectionAccessible(at: String) {
        val density = rule.density.density
        val nodes = rule.onAllNodes(hasClickAction() and (hasAnyAncestor(hasTestTag("home_weekly")))).fetchSemanticsNodes()
        assertTrue("$at: found tappable parts", nodes.isNotEmpty())
        nodes.forEach { node ->
            val c = node.config
            val label = listOfNotNull(
                c.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            ).joinToString(" ").trim()
            assertTrue("$at: node ${node.id} has a TalkBack label", label.isNotEmpty())
            assertTrue("$at: '$label' is ${node.size.width / density}dp wide", node.size.width / density >= 47.9f)
            assertTrue("$at: '$label' is ${node.size.height / density}dp tall", node.size.height / density >= 47.9f)
        }
    }

    @Test fun tappable_parts_are_48dp_and_labelled() {
        show(HomeFixtures.state(2, 0))
        for (f in listOf(1f, 2f)) {
            scale.floatValue = f
            current.value = HomeFixtures.state(2, 0)
            rule.waitForIdle()
            assertSectionAccessible("add row at $f")
            current.value = HomeFixtures.state(2, 0, weekly = HomeFixtures.threeGoals())
            rule.waitForIdle()
            assertSectionAccessible("cards at $f")
            current.value = HomeFixtures.state(6, 0, weekly = listOf(HomeFixtures.completeWeek()))
            rule.waitForIdle()
            assertSectionAccessible("compact at $f")
        }
    }

    @Test fun the_today_row_says_what_a_tap_and_a_hold_do() {
        show(with(HomeFixtures.todayCounts()))
        val todo = rule.onNodeWithTag("home_weekly_today_1").fetchSemanticsNode().config
        assertEquals("Mark done", todo[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
        assertEquals(listOf("Exercise", "Today"), todo[SemanticsProperties.Text].map { it.text })
        current.value = with(HomeFixtures.completeWeek())
        rule.waitForIdle()
        val done = rule.onNodeWithTag("home_weekly_today_1").fetchSemanticsNode().config
        assertEquals("Undo", done[androidx.compose.ui.semantics.SemanticsActions.OnLongClick].label)
    }

    @Test fun the_title_never_breaks_inside_a_word_and_the_strip_stays_inside_the_card() {
        show(HomeFixtures.state(2, 0))
        for (f in listOf(1f, 1.3f, 2f)) {
            scale.floatValue = f
            current.value = HomeFixtures.state(
                2, 0,
                weekly = listOf(HomeFixtures.goal(1, "Practice the double bass now", 7), HomeFixtures.goal(2, "Exercise", 4, 0, 2)),
            )
            rule.waitForIdle()
            for (id in 1L..2L) {
                assertNoWordSplit(rule, "home_weekly_title_$id", "title $id at $f", unmerged = true)
                // Every strip cell stays inside the card, all seven on one row.
                val card = rule.onNodeWithTag("home_weekly_card_$id", useUnmergedTree = true).fetchSemanticsNode()
                val left = card.positionInRoot.x
                val right = left + card.size.width
                val cells = rule.onNodeWithTag("home_weekly_strip_$id", useUnmergedTree = true).fetchSemanticsNode().children
                assertEquals(7, cells.size)
                cells.forEach {
                    val x = it.positionInRoot.x
                    assertTrue("cell $x..${x + it.size.width} in card $left..$right at $f", x >= left && x + it.size.width <= right)
                }
                assertTrue("cells are level at $f", cells.map { it.positionInRoot.y }.distinct().size == 1)
            }
        }
    }
}
