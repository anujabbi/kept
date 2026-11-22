package com.example.kept.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kept.core.domain.NightEdit
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.NightWindow
import com.example.kept.core.domain.SettingsCopy
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
 * The Night lockdown group on the Settings tab and its editors (issue #164, story #168): where it
 * sits, what its switch and rows show and do, the three presets and "Custom…", and that "Set"
 * stays disabled, with the reason, for a window that is not 1 to 12 hours long.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class NightSettingsBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private fun text(tag: String): String =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }.replace(SettingsCopy.NBSP, ' ')

    private fun top(tag: String): Float = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    @Test fun the_group_sits_between_lock_and_reminders_and_is_off_by_default() {
        rule.setContent { Shot(1f) { SettingsFrame(SettingsPage()) } }
        rule.onNodeWithTag("night_toggle").performScrollTo()
        val c = rule.onNodeWithTag("night_toggle").fetchSemanticsNode().config
        assertEquals(Role.Switch, c.getOrNull(SemanticsProperties.Role))
        assertEquals("Off", c.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(listOf("Night lockdown", "Apps lock at night so you stop scrolling"), c[SemanticsProperties.Text].map { it.text })
        assertTrue("under the Lock group", top("night_toggle") > top("settings_permissions_row"))
        assertTrue("above Reminders and privacy", top("night_toggle") < top("reminders_toggle"))
    }

    @Test fun start_and_end_rows_show_only_while_the_switch_is_on() {
        var page by mutableStateOf(SettingsFixtures.ALL_SET)
        val asked = mutableListOf<Boolean>()
        rule.setContent { Shot(1f) { SettingsFrame(page, SettingsActions(onNightLock = { asked += it })) } }
        for (row in nightRows) rule.onNodeWithTag(row).assertDoesNotExist()

        rule.onNodeWithTag("night_toggle").performScrollTo().performClick()
        assertEquals(listOf(true), asked)
        // What the switch stores when nothing else was changed: 11:00 pm to 7:00 am.
        page = page.copy(night = NightEdit.switch(page.night, on = true)!!.result)
        rule.waitForIdle()
        assertEquals(NightSetting(true, 1380, 420), page.night)
        assertEquals("On", rule.onNodeWithTag("night_toggle").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals("Starts at", text("settings_night_start_title"))
        assertEquals("11:00 pm", text("settings_night_start_value"))
        assertEquals("Ends at", text("settings_night_end_title"))
        assertEquals("7:00 am", text("settings_night_end_value"))

        rule.onNodeWithTag("night_toggle").performClick()
        assertEquals(listOf(true, false), asked)
        page = page.copy(night = page.night.copy(enabled = false))
        rule.waitForIdle()
        for (row in nightRows) rule.onNodeWithTag(row).assertDoesNotExist()
    }

    @Test fun the_start_row_reads_each_preset_and_a_custom_time() {
        var page by mutableStateOf(SettingsFixtures.NIGHT_ON)
        rule.setContent { Shot(1f) { SettingsFrame(page) } }
        for ((minute, label) in listOf(1320 to "10:00 pm", 1380 to "11:00 pm", 0 to "Midnight", 21 * 60 + 30 to "9:30 pm")) {
            page = page.copy(night = page.night.copy(fromMinute = minute))
            rule.waitForIdle()
            assertEquals(label, text("settings_night_start_value"))
        }
        page = page.copy(night = page.night.copy(untilMinute = 6 * 60 + 30))
        rule.waitForIdle()
        assertEquals("6:30 am", text("settings_night_end_value"))
    }

    @Test fun the_rows_open_their_editors() {
        val opened = mutableListOf<String>()
        val actions = SettingsActions(onEditNightStart = { opened += "start" }, onEditNightEnd = { opened += "end" })
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.NIGHT_ON, actions) } }
        for (row in nightRows) rule.onNodeWithTag(row).performScrollTo().assertHasClickAction().performClick()
        assertEquals(listOf("start", "end"), opened)
    }

    @Test fun picking_a_preset_start_saves_it_and_closes() {
        val picked = mutableListOf<Int>()
        var open by mutableStateOf(true)
        rule.setContent { Shot(1f) { if (open) NightStartEditor(NightSetting(true), onDismiss = { open = false }, onCustom = {}) { picked += it } } }
        val chips = NightWindow.PRESETS.map { m ->
            rule.onNodeWithTag("night_start_$m").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }.replace(SettingsCopy.NBSP, ' ')
        }
        assertEquals(listOf("10:00 pm", "11:00 pm", "Midnight"), chips)
        rule.onNodeWithText("Custom…").assertExists()
        assertEquals(true, rule.onNodeWithTag("night_start_1380").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, rule.onNodeWithTag("night_start_custom").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag("night_start_error").assertDoesNotExist()
        rule.onNodeWithTag("night_start_0").performClick()
        rule.waitForIdle()
        assertEquals(listOf(0), picked)
        assertTrue(!open)
    }

    @Test fun custom_opens_the_time_picker_and_is_selected_for_a_custom_start() {
        var custom = 0
        rule.setContent { Shot(1f) { NightStartEditor(NightSetting(true, 21 * 60 + 30, 7 * 60), onDismiss = {}, onCustom = { custom++ }) {} } }
        assertEquals(true, rule.onNodeWithTag("night_start_custom").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        for (m in NightWindow.PRESETS) assertEquals(false, rule.onNodeWithTag("night_start_$m").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
        rule.onNodeWithTag("night_start_custom").performClick()
        assertEquals(1, custom)
    }

    @Test fun a_preset_more_than_12_hours_before_the_end_cannot_be_picked() {
        val picked = mutableListOf<Int>()
        // Ends at 11:00 am: 10:00 pm would be 13 hours, 11:00 pm 12 and Midnight 11.
        rule.setContent { Shot(1f) { NightStartEditor(NightSetting(true, 23 * 60, 11 * 60), onDismiss = {}, onCustom = {}) { picked += it } } }
        rule.onNodeWithTag("night_start_1320").assertIsNotEnabled()
        rule.onNodeWithTag("night_start_0").assertIsEnabled()
        rule.onNodeWithTag("night_start_error").assertExists()
        rule.onNodeWithText("Pick an end time 1 to 12 hours after the start.").assertExists()
        rule.onNodeWithTag("night_start_1320").performClick()
        assertEquals(emptyList<Int>(), picked)
    }

    private fun endEditor(end: Int, onSet: (Int) -> Unit = {}): @Composable () -> Unit = {
        TimeEditor(SettingsCopy.NIGHT_END, end, onDismiss = {}, error = { NightWindow.error(23 * 60, it) }, onSet = onSet)
    }

    @Test fun set_is_disabled_with_the_reason_for_an_end_equal_to_the_start() {
        rule.setContent { Shot(1f) { endEditor(23 * 60)() } }
        rule.onNodeWithTag("time_set").assertIsNotEnabled()
        rule.onNodeWithTag("time_error").assertExists()
        rule.onNodeWithText("Pick an end time 1 to 12 hours after the start.").assertExists()
    }

    @Test fun set_is_disabled_for_a_window_shorter_than_1_hour_or_longer_than_12() {
        var end by mutableStateOf(23 * 60 + 30)
        rule.setContent { Shot(1f) { androidx.compose.runtime.key(end) { endEditor(end)() } } }
        rule.onNodeWithTag("time_set").assertIsNotEnabled()
        rule.onNodeWithTag("time_error").assertExists()
        end = 12 * 60
        rule.waitForIdle()
        rule.onNodeWithTag("time_set").assertIsNotEnabled()
        rule.onNodeWithTag("time_error").assertExists()
        // Exactly 12 hours and exactly 1 hour are both allowed.
        end = 11 * 60
        rule.waitForIdle()
        rule.onNodeWithTag("time_set").assertIsEnabled()
        rule.onNodeWithTag("time_error").assertDoesNotExist()
        end = 0
        rule.waitForIdle()
        rule.onNodeWithTag("time_set").assertIsEnabled()
    }

    @Test fun a_valid_custom_end_sets() {
        val set = mutableListOf<Int>()
        rule.setContent { Shot(1f) { endEditor(6 * 60 + 30) { set += it }() } }
        rule.onNodeWithTag("time_error").assertDoesNotExist()
        rule.onNodeWithTag("time_set").assertIsEnabled().performClick()
        assertEquals(listOf(6 * 60 + 30), set)
    }

    @Test fun a_custom_start_is_checked_against_the_saved_end() {
        rule.setContent {
            Shot(1f) { TimeEditor(SettingsCopy.NIGHT_START, 18 * 60, onDismiss = {}, error = { NightWindow.error(it, 7 * 60) }) {} }
        }
        rule.onNodeWithTag("time_set").assertIsNotEnabled()
        rule.onNodeWithTag("time_error").assertExists()
    }

    @Test fun the_lock_window_editors_still_set_any_time() {
        val set = mutableListOf<Int>()
        rule.setContent { Shot(1f) { TimeEditor(SettingsCopy.GIVE_UP, 21 * 60, onDismiss = {}) { set += it } } }
        rule.onNodeWithTag("time_error").assertDoesNotExist()
        rule.onNodeWithTag("time_set").assertIsEnabled().performClick()
        assertEquals(listOf(21 * 60), set)
    }

    @Test fun turning_it_off_while_night_locked_asks_in_its_own_words() {
        var confirmed = 0
        var cancelled = 0
        rule.setContent { Shot(1f) { LockChangeConfirm(SettingsCopy.NIGHT_OFF_WHILE_LOCKED, { confirmed++ }, { cancelled++ }) } }
        rule.onNodeWithText("Apps are locked right now").assertExists()
        rule.onNodeWithText("Your apps are locked right now. Turning off night lockdown opens them. Still turn it off?").assertExists()
        rule.onNodeWithTag("change_cancel").performClick()
        rule.onNodeWithTag("change_anyway").performClick()
        assertEquals(1, confirmed)
        assertEquals(1, cancelled)
    }

    /** The existing accessibility check (`PartsBehaviourTest`): 48 x 48dp and something TalkBack reads. */
    private fun assertAccessible(tags: List<String>) {
        val d = rule.density.density
        for (tag in tags) {
            val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
            assertTrue("$tag is tappable", hasClickAction().matches(node) || node.config.contains(SemanticsProperties.ToggleableState) || node.config.contains(SemanticsProperties.Selected))
            val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ").orEmpty()
            assertTrue("$tag has a TalkBack label", label.isNotBlank())
            assertTrue("$tag '$label' is ${node.size.width / d}dp wide", node.size.width / d >= 47.9f)
            assertTrue("$tag '$label' is ${node.size.height / d}dp tall", node.size.height / d >= 47.9f)
        }
    }

    @Test fun the_group_is_accessible() {
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.NIGHT_ON) } }
        rule.onNodeWithTag("settings_night_end").performScrollTo()
        assertAccessible(listOf("night_toggle") + nightRows)
    }

    @Test fun the_start_editor_is_accessible_at_font_1_and_2() {
        var scale by mutableStateOf(1f)
        rule.setContent { Shot(scale) { NightStartEditor(NightSetting(true), onDismiss = {}, onCustom = {}) {} } }
        for (f in listOf(1f, 2f)) {
            scale = f
            rule.waitForIdle()
            assertAccessible(NightWindow.PRESETS.map { "night_start_$it" } + "night_start_custom")
            val buttons = rule.onAllNodes(hasClickAction() and hasAnyAncestor(isDialog())).fetchSemanticsNodes()
            assertTrue("the chips and Cancel are all there at font $f", buttons.size >= 5)
        }
    }
}
