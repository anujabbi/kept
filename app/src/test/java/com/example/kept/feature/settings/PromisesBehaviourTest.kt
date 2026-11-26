package com.example.kept.feature.settings

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.PromiseItem
import com.example.kept.core.domain.PromiseListCopy
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalCopy
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
 * What Your promises (#236) shows, what a tap opens, and what TalkBack hears. Switching a promise
 * is in `PromiseSwitchTest`; the promise page itself in `PromiseBehaviourTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class PromisesBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private fun text(tag: String): String =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text]
            .joinToString { it.text }.replace(WeeklyGoalCopy.NBSP, ' ')

    @Test fun the_page_shows_every_promise_in_order_with_its_amount_and_how_often() {
        rule.setContent { Shot { PromisesContent(PromisesFixtures.SIX, {}, {}, {}) } }
        rule.onNodeWithTag("page_title").assertTextEquals("Your promises")
        rule.onNodeWithText("Tap one to change it, even how often.").assertExists()
        val shown = PromisesFixtures.SIX.map { p ->
            rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("promise_${p.key}"))
            // The card's name, amount and chip, in reading order.
            rule.onNodeWithTag("promise_${p.key}").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text.replace(WeeklyGoalCopy.NBSP, ' ') }
        }
        assertEquals(
            listOf(
                listOf("Read", "10 pages", "Every day"),
                listOf("Exercise", "30 min", "4 days a week"),
                listOf("Drink water", "8 glasses", "Every day"),
                listOf("Tidy room", "Photo check-in", "Every day"),
                listOf("Study", "45 min", "2 days a week"),
                listOf("Practice guitar", "20 min", "Every day"),
            ),
            shown,
        )
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("promises_count"))
        rule.onNodeWithTag("promises_add").assertExists()
        assertEquals("4 daily habits · 2 weekly goals", text("promises_count"))
    }

    @Test fun a_card_or_its_chip_opens_that_promise_add_opens_a_new_one_and_back_goes_back() {
        val opened = mutableListOf<String>()
        var added = 0
        var back = 0
        rule.setContent { Shot { PromisesContent(PromisesFixtures.SIX, onBack = { back++ }, onAdd = { added++ }, onOpen = { opened += it.key }) } }
        rule.onNodeWithTag("promise_goal_1").performClick()
        rule.onNodeWithTag("promise_habit_2_chip", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("promises_add"))
        rule.onNodeWithTag("promises_add").performClick()
        rule.onNodeWithTag("page_back").performClick()
        assertEquals(listOf("goal_1", "habit_2"), opened)
        assertEquals(1, added)
        assertEquals(1, back)
    }

    @Test fun with_ten_habits_and_three_goals_add_gives_way_to_the_limit() {
        var added = 0
        rule.setContent { Shot { PromisesContent(PromisesFixtures.FULL, {}, onAdd = { added++ }, onOpen = {}) } }
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("promises_count"))
        rule.onNodeWithTag("promises_add").assertDoesNotExist()
        assertEquals(PromiseListCopy.FULL, text("promises_full"))
        assertEquals("10 daily habits · 3 weekly goals", text("promises_count"))
        assertEquals(0, added)
    }

    /** TalkBack reads a card as one button, "Read, 10 pages, Every day", that changes it; at font 1 and 2 every target is 48dp. */
    @Test fun talkback_reads_each_card_as_its_name_amount_and_how_often() {
        val scale = mutableFloatStateOf(1f)
        rule.setContent { Shot(scale.floatValue) { PromisesContent(PromisesFixtures.SIX, {}, {}, {}) } }
        for (f in listOf(1f, 2f)) {
            scale.floatValue = f
            rule.waitForIdle()
            for (p in PromisesFixtures.SIX) {
                rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("promise_${p.key}"))
                val c = rule.onNodeWithTag("promise_${p.key}").fetchSemanticsNode().config
                assertEquals(listOf(p.title, p.amount, p.chip), c[SemanticsProperties.Text].map { it.text })
                assertEquals(PromiseListCopy.CHANGE, c[SemanticsActions.OnClick].label)
            }
            val density = rule.density.density
            rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
                val label = listOfNotNull(
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                ).joinToString(" ").trim()
                assertTrue("node ${node.id} has a TalkBack label", label.isNotEmpty())
                assertTrue("'$label' is ${node.size.height / density}dp tall", node.size.height / density >= 47.9f)
                assertTrue("'$label' is ${node.size.width / density}dp wide", node.size.width / density >= 47.9f)
            }
        }
    }

    @Test fun a_card_is_made_from_the_stored_habit_or_goal() {
        val h = HabitEntity(id = 4, title = "Tidy room", iconKey = "tidy", proofType = ProofType.PHOTO, targetValue = 1, unit = "photo", sortOrder = 3, createdAt = 1)
        assertEquals(PromiseItem(4, null, "Tidy room", "tidy", "Photo check-in", 3), h.promise())
        val g = com.example.kept.core.data.db.WeeklyGoalEntity(
            id = 2, title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 1, effectiveFrom = "2026-11-01", sortOrder = 1, targetValue = 30, unit = "min",
        )
        val card = g.promise()
        assertEquals("goal_2", card.key)
        assertEquals("30 min", card.amount.replace(WeeklyGoalCopy.NBSP, ' '))
        assertEquals("4 days a week", card.chip.replace(WeeklyGoalCopy.NBSP, ' '))
    }

    // Edit habit keeps the habit (moved from the Your habits tests, issue #134).

    @Test fun an_edit_keeps_the_habit_and_its_history_and_re_picks_the_icon_only_on_rename() {
        val h = HabitEntity(id = 4, title = "Read", iconKey = "star", proofType = ProofType.MANUAL, targetValue = 10, unit = "pages", sortOrder = 2, createdAt = 99)
        val same = editedHabit(h, HabitDraft("Read", 15, "pages"))
        assertEquals(h.copy(targetValue = 15), same)
        val renamed = editedHabit(h, HabitDraft("Drink water", 8, "glasses"))
        assertEquals(h.copy(title = "Drink water", iconKey = "water", targetValue = 8, unit = "glasses"), renamed)
        val photo = editedHabit(h, HabitDraft("Read", 15, "pages", photo = true))
        assertEquals(h.copy(proofType = ProofType.PHOTO, targetValue = 1, unit = "photo"), photo)
        val sleep = HabitEntity(id = 6, title = "Lights out", iconKey = "sleep", proofType = ProofType.MANUAL, targetValue = 1, unit = "check-in", createdAt = 1)
        assertEquals("an untouched one-tap habit saves as once: 1 time", sleep.copy(unit = "times"), editedHabit(sleep, PromiseRules.ofHabit(sleep.title, sleep.proofType, sleep.targetValue, sleep.unit).habit()))
        val tidy = HabitEntity(id = 7, title = "Tidy room", iconKey = "tidy", proofType = ProofType.PHOTO, targetValue = 1, unit = "photo", createdAt = 1)
        assertEquals("an untouched photo habit saves unchanged", tidy, editedHabit(tidy, PromiseRules.ofHabit(tidy.title, tidy.proofType, tidy.targetValue, tidy.unit).habit()))
    }
}
