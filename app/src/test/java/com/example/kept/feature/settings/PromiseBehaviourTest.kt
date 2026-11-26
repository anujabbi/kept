package com.example.kept.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseLimits
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The promise (#218): the sentence and its blanks, every list and its values, "Something else…",
 * the days list's limits, Done with Sprig's cheer, and what TalkBack hears. Which store a promise
 * goes to from Settings is in `PromiseSettingsTest`, from onboarding in `OnboardingBehaviourTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class PromiseBehaviourTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<PromiseDraft>()
    private var closed = 0

    private fun show(initial: PromiseDraft, limits: PromiseLimits = PromiseLimits(), remove: PromiseRemove? = null, fontScale: Float = 1f) {
        rule.setContent { Shot(fontScale) { PromisePage(initial, limits, onDone = { d, done -> saved += d; done() }, onClose = { closed++ }, remove = remove) } }
        rule.waitForIdle()
    }

    private fun sentence() = promiseSentence(rule)
    private fun openList(blank: String) = rule.onNodeWithTag("promise_blank_$blank").performClick()

    /** The labels of [blank]'s list, top to bottom. */
    private fun options(blank: String): List<String> {
        openList(blank)
        val rows = rule.onAllNodes(hasAnyAncestor(hasTestTag("promise_list_$blank")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes()
            .map { n -> n.config[SemanticsProperties.Text].first().text.replace(WeeklyGoalCopy.NBSP, ' ') }
        openList(blank)
        return rows
    }

    @Test fun exercise_reads_as_the_issue_and_every_blank_opens_its_list() {
        show(PromiseFixtures.EXERCISE)
        assertEquals("I will exercise for 30 minutes, 4 days a week.", sentence())
        rule.onNodeWithTag("promise_line").assertTextEquals("Week starts on Sunday.")
        assertEquals(listOf("Exercise", "Read", "Practice", "Study", "Tidy room", "Something else…"), options("activity"))
        assertEquals(listOf("5", "10", "15", "20", "30", "45", "60", "90"), options("amount"))
        assertEquals(listOf("minutes", "pages", "times", "glasses"), options("unit"))
        assertEquals(listOf("Every day", "6 days a week", "5 days a week", "4 days a week", "3 days a week", "2 days a week", "1 day a week"), options("days"))
        rule.onNodeWithTag("promise_label").assertTextEquals("Your promise")
        rule.onNodeWithTag("promise_cancel").assertTextEquals("Cancel")
    }

    @Test fun read_reads_every_day_with_no_line_and_its_lists_fit_pages() {
        show(PromiseFixtures.READ)
        assertEquals("I will read 10 pages, every day.", sentence())
        rule.onNodeWithTag("promise_line").assertDoesNotExist()
        assertEquals(listOf("5", "10", "20", "30", "50"), options("amount"))
    }

    @Test fun times_and_glasses_offer_1_to_10_and_the_joining_word_follows_the_unit() {
        show(PromiseFixtures.READ)
        openList("unit")
        rule.onNodeWithTag("promise_option_times").performClick()
        assertEquals("I will read 1 time, every day.", sentence())
        assertEquals((1..10).map { it.toString() }, options("amount"))
        openList("unit")
        rule.onNodeWithTag("promise_option_glasses").performClick()
        assertEquals("I will read 8 glasses, every day.", sentence())
        assertEquals((1..10).map { it.toString() }, options("amount"))
        openList("unit")
        rule.onNodeWithTag("promise_option_min").performClick()
        assertEquals("I will read for 20 minutes, every day.", sentence())
    }

    @Test fun picking_from_each_list_changes_the_sentence_and_done_saves_what_it_reads() {
        show(PromiseFixtures.EXERCISE)
        openList("activity")
        rule.onNodeWithTag("promise_option_Practice").performClick()
        openList("amount")
        rule.onNodeWithTag("promise_option_45").performClick()
        openList("days")
        rule.onNodeWithTag("promise_option_days_2").performClick()
        assertEquals("I will practice for 45 minutes, 2 days a week.", sentence())
        rule.onNodeWithTag("promise_done").performClick()
        rule.waitForIdle()
        assertEquals(listOf(PromiseDraft("Practice", 45, "min", days = 2)), saved)
        // The lists no longer open while Sprig cheers.
        openList("days")
        rule.onNodeWithTag("promise_list_days").assertDoesNotExist()
    }

    @Test fun something_else_turns_the_activity_into_a_field_and_saves_the_typed_name() {
        show(PromiseFixtures.EXERCISE)
        openList("activity")
        rule.onNodeWithTag("promise_option_something_else").assertTextEquals("Something else…", "type your own").performClick()
        rule.onNodeWithTag("promise_done").assertIsNotEnabled()
        rule.onNodeWithTag("promise_activity_field").performTextInput("swim")
        assertEquals("I will swim for 30 minutes, 4 days a week.", sentence())
        rule.onNodeWithTag("promise_done").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals("Swim", saved.single().name)
        // The chevron beside the field opens the list again, to go back to an activity.
        rule.onNodeWithTag("promise_sprig").assertExists()
    }

    @Test fun at_three_weekly_goals_the_weekly_choices_are_off_with_the_limit_line() {
        show(PromiseFixtures.MEDITATE, PromiseFixtures.AT_LIMIT)
        openList("days")
        rule.onNodeWithTag("promise_option_days_0").assertIsEnabled().assertIsSelected()
        (1..6).forEach { rule.onNodeWithTag("promise_option_days_$it").assertIsNotEnabled() }
        rule.onNodeWithTag("promise_limit").assertTextEquals(WeeklyGoalCopy.LIMIT_REACHED)
        rule.onNodeWithTag("promise_option_days_4").performClick()
        assertEquals("I will meditate for 20 minutes, every day.", sentence())
    }

    @Test fun a_new_promise_opening_on_a_choice_that_is_off_takes_the_other_kind() {
        show(PromiseFixtures.EXERCISE, PromiseFixtures.AT_LIMIT)
        assertEquals("I will exercise for 30 minutes, every day.", sentence())
    }

    @Test fun done_leaves_the_text_sprig_cheers_and_the_page_closes_after_about_a_second_and_a_half() {
        show(PromiseFixtures.EXERCISE)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("promise_done").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("promise_sprig").assertExists()
        rule.onNodeWithTag("promise_done").assertDoesNotExist()
        assertEquals("the text stays as it is", "I will exercise for 30 minutes, 4 days a week.", sentence())
        rule.mainClock.advanceTimeBy(PROMISE_CHEER_MS - 200)
        assertEquals(0, closed)
        rule.mainClock.advanceTimeBy(400)
        assertEquals(1, closed)
        assertEquals(1, saved.size)
    }

    @Test fun sprig_rises_from_the_bottom_and_with_reduce_motion_is_simply_there() {
        var reduce by mutableStateOf(false)
        rule.setContent {
            KeptTheme(reduceMotion = reduce) {
                androidx.compose.runtime.key(reduce) { PromisePage(PromiseFixtures.EXERCISE, PromiseLimits(), { _, done -> done() }, {}) }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("promise_done").performClick()
        rule.mainClock.advanceTimeBy(64)
        val rising = rule.onNodeWithTag("promise_sprig").fetchSemanticsNode().positionInRoot.y
        rule.mainClock.advanceTimeBy(1_000)
        val risen = rule.onNodeWithTag("promise_sprig").fetchSemanticsNode().positionInRoot.y
        assertTrue("Sprig moves up as it rises ($rising to $risen)", risen < rising)
        reduce = true
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("promise_done").performClick()
        rule.mainClock.advanceTimeBy(32)
        val snapped = rule.onNodeWithTag("promise_sprig").fetchSemanticsNode().positionInRoot.y
        assertEquals("with reduce motion Sprig is already where it ends", risen, snapped, 1f)
        assertNotEquals(rising, snapped)
    }

    @Test fun cancel_and_back_close_without_saving_and_back_closes_an_open_list_first() {
        show(PromiseFixtures.EXERCISE)
        openList("unit")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("promise_list_unit").assertDoesNotExist()
        assertEquals(0, closed)
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(1, closed)
        rule.onNodeWithTag("promise_cancel").performClick()
        assertEquals("closes once", 1, closed)
        assertTrue(saved.isEmpty())
    }

    @Test fun edit_offers_remove_and_the_last_habit_says_why_it_cannot_go() {
        var removed = 0
        var allowed by mutableStateOf(true)
        rule.setContent {
            Shot {
                androidx.compose.runtime.key(allowed) {
                    PromisePage(PromiseFixtures.READ, PromiseLimits.SAVED_HABIT, { _, _ -> }, {}, remove = PromiseRemove(HabitsCopy.REMOVE, allowed, HabitsCopy.KEEP_ONE) { removed++ })
                }
            }
        }
        rule.onNodeWithTag("promise_remove").assertTextEquals("Remove habit").performClick()
        assertEquals(1, removed)
        allowed = false
        rule.waitForIdle()
        rule.onNodeWithTag("promise_remove").assertDoesNotExist()
        rule.onNodeWithTag("promise_keep_one").assertTextEquals(HabitsCopy.KEEP_ONE)
        openList("days")
        rule.onNodeWithTag("promise_option_days_3").assertIsNotEnabled()
        rule.onNodeWithTag("promise_limit").assertTextEquals("A saved habit stays every day.")
    }

    @Test fun talkback_reads_each_blank_as_a_button_with_its_value_at_font_1_and_2() {
        val scale = mutableFloatStateOf(1f)
        rule.setContent { Shot(scale.floatValue) { PromisePage(PromiseFixtures.EXERCISE, PromiseLimits(), { _, _ -> }, {}) } }
        for (f in listOf(1f, 2f)) {
            scale.floatValue = f
            rule.waitForIdle()
            val d = rule.density.density
            for ((blank, value) in listOf("activity" to "exercise", "amount" to "30", "unit" to "minutes", "days" to "4 days a week")) {
                val node = rule.onNodeWithTag("promise_blank_$blank").fetchSemanticsNode()
                assertEquals(Role.Button, node.config[SemanticsProperties.Role])
                assertEquals(value, node.config[SemanticsProperties.Text].joinToString { it.text }.replace(WeeklyGoalCopy.NBSP, ' '))
                assertTrue("$blank at font $f is ${node.size.height / d}dp tall", node.size.height / d >= 47.9f)
                assertTrue("$blank at font $f is ${node.size.width / d}dp wide", node.size.width / d >= 47.9f)
                // The whole value shows: no line of it is cut off.
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                layouts.forEach { l -> assertTrue("$blank at font $f is cut off", !l.didOverflowHeight && l.multiParagraph.intrinsics.maxIntrinsicWidth <= l.size.width + 0.5f) }
            }
        }
    }
}
