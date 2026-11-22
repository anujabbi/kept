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

/** What Your habits and Add a habit do when touched, and what TalkBack hears (issue #134). */
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

    private fun assertAmount(expected: String) = assertEquals(expected, text("habit_amount"))

    private var saved: HabitDraft? = null
    private var closed = 0

    private fun showEditor(initial: HabitDraft = HabitDraft(), editing: Boolean = false, remove: RemoveHabit? = null) {
        rule.setContent { Shot { HabitEditPage(initial, onClose = { closed++ }, onSave = { saved = it }, editing = editing, remove = remove) } }
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

    @Test fun save_is_disabled_until_the_name_has_text_and_saving_returns_the_draft() {
        showEditor()
        rule.onNodeWithTag("page_title").assertTextEquals("Add a habit")
        rule.onNodeWithTag("habit_save").assertIsNotEnabled()
        rule.onNodeWithTag("habit_name").performTextInput("   ")
        rule.onNodeWithTag("habit_save").assertIsNotEnabled()
        rule.onNodeWithTag("habit_name").performTextReplacement("Walk the dog")
        rule.onNodeWithTag("habit_save").assertIsEnabled().performClick()
        val d = saved!!
        assertEquals("Walk the dog", d.name)
        assertEquals(ProofType.MANUAL, d.proof)
        assertEquals(20, d.savedTarget)
        assertEquals("min", d.savedUnit)
    }

    @Test fun each_quick_pick_fills_name_amount_and_unit_and_hides_the_units() {
        showEditor()
        val expected = mapOf("Exercise" to "30 min", "Read" to "10 pages", "Practice" to "20 min", "Study" to "45 min")
        for ((name, amount) in expected) {
            rule.onNodeWithTag("habit_preset_$name").performClick()
            assertEquals(name, text("habit_name"))
            assertAmount(amount)
            rule.onNodeWithTag("habit_units").assertDoesNotExist()
        }
        rule.onNodeWithTag("habit_save").performClick()
        assertEquals(HabitDraft("Study", 45, "min"), saved)
    }

    @Test fun a_custom_name_shows_the_unit_chips_and_each_starts_at_its_value() {
        showEditor()
        rule.onNodeWithTag("habit_units").assertDoesNotExist()
        rule.onNodeWithTag("habit_name").performTextInput("Piano")
        rule.onNodeWithTag("habit_units").assertExists()
        rule.onNodeWithTag("habit_unit_min").assertIsSelected()
        rule.onNodeWithTag("habit_unit_pages").performClick().assertIsSelected()
        assertAmount("10 pages")
        rule.onNodeWithTag("habit_unit_glasses").performClick()
        assertAmount("8 glasses")
        rule.onNodeWithTag("habit_unit_times").performClick()
        assertAmount("Once a day")
        rule.onNodeWithTag("habit_unit_min").performClick()
        assertAmount("20 min")
    }

    @Test fun minutes_step_by_five_and_never_below_one_step() {
        showEditor()
        rule.onNodeWithTag("habit_more").performClick()
        assertAmount("25 min")
        repeat(6) { rule.onNodeWithTag("habit_less").performClick() }
        assertAmount("5 min")
        rule.onNodeWithTag("habit_less").assertIsNotEnabled()
    }

    @Test fun times_step_by_one_and_one_time_reads_once_a_day() {
        showEditor(HabitDraft(title = "Stretch"))
        rule.onNodeWithTag("habit_unit_times").performClick()
        assertAmount("Once a day")
        rule.onNodeWithTag("habit_less").assertIsNotEnabled()
        rule.onNodeWithTag("habit_more").performClick()
        assertAmount("2 times")
        rule.onNodeWithTag("habit_save").performClick()
        assertEquals(2, saved!!.savedTarget)
        assertEquals("times", saved!!.savedUnit)
    }

    @Test fun the_photo_switch_hides_how_much_and_saves_a_photo_habit() {
        showEditor(HabitDraft(title = "Tidy room"))
        rule.onNodeWithTag("habit_photo").assertIsOff()
        assertEquals("Off", rule.onNodeWithTag("habit_photo").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        rule.onNodeWithTag("habit_amount_row").assertExists()
        rule.onNodeWithTag("habit_photo").performClick().assertIsOn()
        assertEquals("On", rule.onNodeWithTag("habit_photo").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        rule.onNodeWithTag("habit_amount_row").assertDoesNotExist()
        rule.onNodeWithTag("habit_save").performClick()
        assertEquals(ProofType.PHOTO, saved!!.proof)
        assertEquals(1, saved!!.savedTarget)
        assertEquals("photo", saved!!.savedUnit)
    }

    @Test fun the_icon_is_chosen_from_the_name() {
        showEditor()
        rule.onNodeWithTag("habit_name").performTextInput("Drink water")
        rule.onNodeWithTag("habit_name").performTextReplacement("Call grandma")
        rule.onNodeWithTag("habit_save").performClick()
        assertEquals("the pencil", "write", saved!!.iconKey)
    }

    @Test fun a_known_name_gets_its_icon() {
        showEditor()
        rule.onNodeWithTag("habit_name").performTextInput("Drink water")
        rule.onNodeWithTag("habit_save").performClick()
        assertEquals("water", saved!!.iconKey)
    }

    @Test fun close_cancels_without_saving() {
        showEditor()
        rule.onNodeWithTag("habit_name").performTextInput("Piano")
        rule.onNodeWithTag("page_back").performClick()
        assertEquals(1, closed)
        assertNull(saved)
    }

    @Test fun a_double_tap_on_save_saves_once_and_closes_once() {
        var saves = 0
        var pops = 0
        rule.setContent { Shot { HabitEditPage(HabitDraft(title = "Piano"), onClose = { pops++ }, onSave = { saves++; pops++ }) } }
        rule.onNodeWithTag("habit_save").performClick()
        rule.onNodeWithTag("habit_save").performClick()
        rule.onNodeWithTag("page_back").performClick()
        assertEquals("one save", 1, saves)
        assertEquals("one pop", 1, pops)
    }

    @Test fun a_double_tap_on_close_closes_once_and_then_nothing_saves() {
        showEditor(HabitDraft(title = "Piano"))
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("habit_save").performClick()
        assertEquals(1, closed)
        assertNull(saved)
    }

    @Test fun remove_can_be_asked_again_but_not_after_save() {
        var removes = 0
        showEditor(HabitsFixtures.EDIT, editing = true, remove = RemoveHabit(true) { removes++ })
        // A cancelled lock-window confirm leaves the page open, so Remove still answers.
        rule.onNodeWithTag("habit_remove").performClick()
        rule.onNodeWithTag("habit_remove").performClick()
        assertEquals(2, removes)
        rule.onNodeWithTag("habit_save").performClick()
        rule.onNodeWithTag("habit_remove").performClick()
        assertEquals("nothing after the page has saved", 2, removes)
    }

    // Edit habit

    @Test fun edit_mode_fills_the_fields_and_offers_remove() {
        var removed = 0
        showEditor(HabitDraft.of("Read", ProofType.MANUAL, 12, "pages"), editing = true, remove = RemoveHabit(true) { removed++ })
        rule.onNodeWithTag("page_title").assertTextEquals("Edit habit")
        assertEquals("Read", text("habit_name"))
        assertAmount("12 pages")
        rule.onNodeWithTag("habit_photo").assertIsOff()
        rule.onNodeWithTag("habit_keep_one").assertDoesNotExist()
        rule.onNodeWithTag("habit_remove").assertTextEquals("Remove habit").performClick()
        assertEquals(1, removed)
        rule.onNodeWithTag("habit_more").performClick()
        rule.onNodeWithTag("habit_save").performClick()
        assertEquals(HabitDraft("Read", 13, "pages"), saved)
    }

    @Test fun a_photo_habit_opens_with_the_photo_on() {
        showEditor(HabitDraft.of("Tidy room", ProofType.PHOTO, 1, "photo"), editing = true, remove = RemoveHabit(true) {})
        rule.onNodeWithTag("habit_photo").assertIsOn()
        rule.onNodeWithTag("habit_amount_row").assertDoesNotExist()
    }

    @Test fun the_last_habit_cannot_be_removed_and_says_why() {
        showEditor(HabitDraft.of("Read", ProofType.MANUAL, 10, "pages"), editing = true, remove = RemoveHabit(false) {})
        rule.onNodeWithTag("habit_remove").assertDoesNotExist()
        rule.onNodeWithTag("habit_keep_one").assertTextEquals(HabitsCopy.KEEP_ONE)
        assertEquals("Keep at least one habit. Remove this one after adding another.", HabitsCopy.KEEP_ONE)
    }

    @Test fun an_edit_keeps_the_habit_and_its_history_and_re_picks_the_icon_only_on_rename() {
        val h = HabitEntity(id = 4, title = "Read", iconKey = "star", proofType = ProofType.MANUAL, targetValue = 10, unit = "pages", sortOrder = 2, createdAt = 99)
        val same = editedHabit(h, HabitDraft("Read", 15, "pages"))
        assertEquals(h.copy(targetValue = 15), same)
        val renamed = editedHabit(h, HabitDraft("Drink water", 8, "glasses"))
        assertEquals(h.copy(title = "Drink water", iconKey = "water", targetValue = 8, unit = "glasses"), renamed)
        val photo = editedHabit(h, HabitDraft("Read", 15, "pages", photo = true))
        assertEquals(h.copy(proofType = ProofType.PHOTO, targetValue = 1, unit = "photo"), photo)
        val sleep = HabitEntity(id = 6, title = "Lights out", iconKey = "sleep", proofType = ProofType.MANUAL, targetValue = 1, unit = "check-in", createdAt = 1)
        assertEquals("an untouched habit saves unchanged", sleep, editedHabit(sleep, HabitDraft.of(sleep.title, sleep.proofType, sleep.targetValue, sleep.unit)))
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
    @Test fun add_a_habit_is_accessible() = assertAccessible { HabitEditPage(HabitsFixtures.CUSTOM, {}, {}) }
    @Test fun edit_habit_is_accessible() = assertAccessible { HabitEditPage(HabitsFixtures.EDIT, {}, {}, editing = true, remove = RemoveHabit(true) {}) }
}
