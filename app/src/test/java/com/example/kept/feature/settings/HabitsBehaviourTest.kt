package com.example.kept.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What Your habits does when touched, and what TalkBack hears (issue #134). Add and edit are the promise (#218): `PromiseBehaviourTest`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HabitsBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private fun text(tag: String): String {
        val c = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        val t = c.getOrNull(SemanticsProperties.EditableText)?.text ?: c[SemanticsProperties.Text].joinToString { it.text }
        return t.replace(HabitsCopy.NBSP, ' ')
    }

    // Your habits

    @Test fun the_list_shows_every_habit_with_its_detail_and_a_row_opens_it() {
        val opened = mutableListOf<Long>()
        var added = 0
        var back = 0
        rule.setContent { Shot { HabitsContent(HabitsFixtures.SIX, onBack = { back++ }, onAdd = { added++ }, onEdit = { opened += it }) } }
        rule.onNodeWithTag("page_title").assertTextEquals("Your habits")
        rule.onNodeWithText(HabitsCopy.LINE).assertExists()
        assertEquals("30 min, tap when done", text("habit_detail_1"))
        assertEquals("10 pages, tap when done", text("habit_detail_2"))
        assertEquals("Photo check-in", text("habit_detail_5"))
        assertEquals("8 glasses, tap when done", text("habit_detail_6"))
        rule.onNodeWithTag("habit_row_3").performClick()
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("habit_row_6"))
        rule.onNodeWithTag("habit_row_6").performClick()
        assertEquals(listOf(3L, 6L), opened)
        assertEquals("6 of 10 habits", text("habits_count"))
        rule.onNodeWithTag("habits_add").assertIsEnabled().performClick()
        assertEquals(1, added)
        rule.onNodeWithTag("page_back").performClick()
        assertEquals(1, back)
    }

    @Test fun at_ten_habits_add_is_disabled_and_the_limit_shows() {
        var added = 0
        rule.setContent { Shot { HabitsContent(HabitsFixtures.TEN, {}, onAdd = { added++ }, onEdit = {}) } }
        rule.onNodeWithTag("habits_add").assertIsNotEnabled().performClick()
        assertEquals(0, added)
        assertEquals(HabitLimits.LIMIT_REACHED, text("habits_count"))
        assertEquals("Once a day, tap when done", text("habit_detail_9"))
    }

    @Test fun only_the_list_scrolls() {
        rule.setContent { Shot { HabitsContent(HabitsFixtures.TEN, {}, {}, {}) } }
        val titleTop = rule.onNodeWithTag("page_title").fetchSemanticsNode().positionInRoot.y
        val addTop = rule.onNodeWithTag("habits_add").fetchSemanticsNode().positionInRoot.y
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("habit_row_10"))
        rule.onNodeWithTag("habit_row_10").assertIsDisplayed()
        rule.waitForIdle()
        assertEquals("the title stays put", titleTop, rule.onNodeWithTag("page_title").fetchSemanticsNode().positionInRoot.y, 0.5f)
        assertEquals("the footer stays put", addTop, rule.onNodeWithTag("habits_add").fetchSemanticsNode().positionInRoot.y, 0.5f)
    }

    // Add a habit

    // Edit habit

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

    // TalkBack and touch targets

    private fun assertAccessible(content: @Composable () -> Unit) {
        rule.setContent { Shot(1f, content) }
        val density = rule.density.density
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("found tappable parts", nodes.isNotEmpty())
        nodes.forEach { node ->
            val c = node.config
            val label = listOfNotNull(
                c.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            ).joinToString(" ").trim()
            assertTrue("node ${node.id} has a TalkBack label", label.isNotEmpty())
            assertTrue("'$label' is ${node.size.width / density}dp wide", node.size.width / density >= 47.9f)
            assertTrue("'$label' is ${node.size.height / density}dp tall", node.size.height / density >= 47.9f)
        }
    }

    @Test fun the_list_is_accessible() = assertAccessible { HabitsContent(HabitsFixtures.SIX, {}, {}, {}) }
}
