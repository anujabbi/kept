package com.example.kept.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.HabitIconRules
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.domain.WeeklyGoalDraft
import com.example.kept.core.ui.shots.Shot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * What Weekly goals and its setup screen do when touched, what they store, and what TalkBack
 * hears (issue #165, story #173).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class WeeklyGoalsBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private fun text(tag: String): String {
        val c = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        val t = c.getOrNull(SemanticsProperties.EditableText)?.text ?: c[SemanticsProperties.Text].joinToString { it.text }
        return t.replace(WeeklyGoalCopy.NBSP, ' ')
    }

    private var saved: WeeklyGoalDraft? = null
    private var closed = 0
    private var deleted = 0

    private fun showSetup(initial: WeeklyGoalDraft = WeeklyGoalDraft(), existing: Boolean = false) {
        rule.setContent {
            Shot {
                WeeklyGoalEditPage(
                    initial, onClose = { closed++ }, onSave = { saved = it },
                    sprig = WeeklyGoalsFixtures.SPRIG, onDelete = if (existing) ({ deleted++ }) else null,
                )
            }
        }
    }

    // --- the list ------------------------------------------------------------------------------

    @Test fun with_no_goals_the_list_is_empty_and_offers_add() {
        var added = 0
        var back = 0
        rule.setContent { Shot { WeeklyGoalsContent(emptyList(), onBack = { back++ }, onAdd = { added++ }, onEdit = {}) } }
        rule.onNodeWithTag("page_title").assertTextEquals("Weekly goals")
        rule.onNodeWithText(WeeklyGoalCopy.LIST_LINE).assertExists()
        rule.onNodeWithTag("goals_add").assertTextEquals("Add a weekly goal").assertIsEnabled().performClick()
        assertEquals(1, added)
        assertEquals("0 of 3 weekly goals", text("goals_count"))
        rule.onNodeWithTag("page_back").performClick()
        assertEquals(1, back)
    }

    @Test fun the_list_shows_each_goal_and_a_row_opens_it() {
        val opened = mutableListOf<Long>()
        rule.setContent { Shot { WeeklyGoalsContent(WeeklyGoalsFixtures.TWO, {}, {}, onEdit = { opened += it }) } }
        assertEquals("Exercise · 30 min · 4 days a week", text("goal_text_1"))
        assertEquals("Read · 1 day a week", text("goal_text_2"))
        rule.onNodeWithTag("goal_row_2").performClick()
        rule.onNodeWithTag("goal_row_1").performClick()
        assertEquals(listOf(2L, 1L), opened)
        assertEquals("2 of 3 weekly goals", text("goals_count"))
        rule.onNodeWithTag("goals_add").assertIsEnabled()
    }

    @Test fun with_three_goals_a_fourth_cannot_be_added_and_the_screen_says_why() {
        var added = 0
        rule.setContent { Shot { WeeklyGoalsContent(WeeklyGoalsFixtures.THREE, {}, onAdd = { added++ }, onEdit = {}) } }
        rule.onNodeWithTag("goals_add").assertIsNotEnabled().performClick()
        assertEquals(0, added)
        assertEquals("That's the limit: 3 weekly goals. Remove one to add another.", text("goals_count"))
    }

    @Test fun a_row_is_made_from_the_stored_goal() {
        val g = WeeklyGoalEntity(id = 7, title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 1, effectiveFrom = "2026-11-21")
        val item = WeeklyGoalItem.of(g)
        assertEquals(7L, item.id)
        assertEquals("Exercise · 4 days a week", item.text.replace(WeeklyGoalCopy.NBSP, ' '))
        assertEquals("run", item.iconKey)
    }

    // --- setup ---------------------------------------------------------------------------------

    @Test fun setup_shows_everything_in_the_issue_with_three_selected() {
        showSetup()
        rule.onNodeWithTag("page_title").assertTextEquals("Weekly goal")
        rule.onNodeWithTag("goal_subtitle").assertTextEquals("For things you don't need to do every day.")
        rule.onNodeWithText("What will you do?").assertExists()
        assertEquals("", text("goal_name"))
        for (p in listOf("Exercise", "Read", "Practice", "Study", "Tidy room")) rule.onNodeWithTag("goal_pick_$p").assertTextEquals(p).assertIsNotSelected()
        rule.onNodeWithTag("goal_custom").assertTextEquals("+ Custom")
        rule.onNodeWithTag("goal_days_label").assertTextEquals("How many days a week?")
        for (n in 1..7) {
            val chip = rule.onNodeWithTag("goal_day_$n").assertTextEquals(n.toString())
            if (n == 3) chip.assertIsSelected() else chip.assertIsNotSelected()
        }
        assertEquals("3 of 7", text("goal_days_count"))
        assertEquals("3 days a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_summary_body")
            .assertTextEquals("Week runs Sunday to Saturday. If you fall behind, KEPT locks your phone on the day you can't skip anymore.")
        rule.onNodeWithTag("goal_seven_hint").assertDoesNotExist()
        rule.onNodeWithTag("goal_save").assertTextEquals("Save goal")
        rule.onNodeWithTag("goal_delete").assertDoesNotExist()
        rule.onNodeWithTag("goal_sprig").assertExists()
    }

    @Test fun save_is_disabled_with_no_activity_and_with_spaces_alone() {
        showSetup()
        rule.onNodeWithTag("goal_save").assertIsNotEnabled().performClick()
        assertNull(saved)
        rule.onNodeWithTag("goal_name").performTextInput("    ")
        rule.onNodeWithTag("goal_save").assertIsNotEnabled().performClick()
        assertNull(saved)
        rule.onNodeWithTag("goal_name").performTextReplacement("Climb")
        rule.onNodeWithTag("goal_save").assertIsEnabled()
        rule.onNodeWithTag("goal_name").performTextReplacement("")
        rule.onNodeWithTag("goal_save").assertIsNotEnabled()
    }

    @Test fun the_field_stops_at_28_characters() {
        showSetup()
        rule.onNodeWithTag("goal_name").performTextInput("a".repeat(40))
        assertEquals("a".repeat(28), text("goal_name"))
        rule.onNodeWithTag("goal_name").performTextReplacement("Practice guitar with my band tonight")
        assertEquals("Practice guitar with my band", text("goal_name"))
        rule.onNodeWithTag("goal_save").performClick()
        assertEquals(28, saved!!.name.length)
    }

    @Test fun each_quick_pick_fills_the_activity_and_lights_up() {
        showSetup()
        for (p in WeeklyGoalDraft.QUICK_PICKS) {
            rule.onNodeWithTag("goal_pick_$p").performClick().assertIsSelected()
            assertEquals(p, text("goal_name"))
            assertTrue(text("goal_summary_title").startsWith("$p, ") && text("goal_summary_title").endsWith(", 3 days a week"))
            WeeklyGoalDraft.QUICK_PICKS.filter { it != p }.forEach { rule.onNodeWithTag("goal_pick_$it").assertIsNotSelected() }
        }
        rule.onNodeWithTag("goal_save").assertIsEnabled()
        rule.onNodeWithTag("goal_name").performTextReplacement("Tidy rooms")
        rule.onNodeWithTag("goal_pick_Tidy room").assertIsNotSelected()
    }

    @Test fun custom_puts_the_cursor_in_the_field_and_keeps_what_is_there() {
        showSetup()
        rule.onNodeWithTag("goal_name").assertIsNotFocused()
        rule.onNodeWithTag("goal_custom").performClick()
        rule.onNodeWithTag("goal_name").assertIsFocused()
        assertEquals("", text("goal_name"))
        rule.onNodeWithTag("goal_save").assertIsNotEnabled()
        rule.onNodeWithTag("goal_name").performTextInput("Climb")
        rule.onNodeWithTag("goal_save").assertIsEnabled()
    }

    @Test fun picking_exercise_and_4_saves_that_draft() {
        showSetup()
        rule.onNodeWithTag("goal_pick_Exercise").performClick()
        rule.onNodeWithTag("goal_day_4").performClick().assertIsSelected()
        rule.onNodeWithTag("goal_day_3").assertIsNotSelected()
        assertEquals("4 of 7", text("goal_days_count"))
        assertEquals("Exercise, 30 min, 4 days a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_save").performClick()
        assertEquals(WeeklyGoalDraft("Exercise", 4, 30, "min"), saved)
        assertEquals(0, closed)
    }

    @Test fun seven_shows_the_hint_and_the_summary_follows_every_change() {
        showSetup(WeeklyGoalDraft("Exercise", 4))
        rule.onNodeWithTag("goal_seven_hint").assertDoesNotExist()
        rule.onNodeWithTag("goal_day_7").performClick()
        rule.onNodeWithTag("goal_seven_hint").assertTextEquals("7 days a week locks every day until it's done.")
        assertEquals("Exercise, 20 min, 7 days a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_day_1").performClick()
        rule.onNodeWithTag("goal_seven_hint").assertDoesNotExist()
        assertEquals("Exercise, 20 min, 1 day a week", text("goal_summary_title"))
        assertEquals("1 of 7", text("goal_days_count"))
        rule.onNodeWithTag("goal_pick_Read").performClick()
        assertEquals("Read, 10 pages, 1 day a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_name").performTextReplacement("Call grandma")
        rule.onNodeWithTag("goal_day_2").performClick()
        assertEquals("Call grandma, 10 pages, 2 days a week", text("goal_summary_title"))
    }

    // --- the target (story #205, task #206) ----------------------------------------------------

    @Test fun exercise_fills_30_min_the_stepper_changes_it_and_save_carries_it() {
        showSetup()
        rule.onNodeWithTag("goal_amount_row").assertDoesNotExist()
        rule.onNodeWithTag("goal_pick_Exercise").performClick()
        assertEquals("How much?", text("goal_how_much"))
        assertEquals("30 min", text("goal_amount"))
        rule.onNodeWithTag("goal_units").assertDoesNotExist()
        rule.onNodeWithTag("goal_day_4").performClick()
        assertEquals("Exercise, 30 min, 4 days a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_more").performClick()
        rule.onNodeWithTag("goal_more").performClick()
        rule.onNodeWithTag("goal_less").performClick()
        assertEquals("35 min", text("goal_amount"))
        assertEquals("Exercise, 35 min, 4 days a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_save").performClick()
        assertEquals(WeeklyGoalDraft("Exercise", 4, 35, "min"), saved)
    }

    @Test fun a_custom_name_shows_the_unit_chips_and_one_time_reads_1_time() {
        showSetup()
        rule.onNodeWithTag("goal_name").performTextInput("Call grandma")
        rule.onNodeWithTag("goal_units").performScrollTo()
        for (u in listOf("min", "pages", "times", "glasses")) rule.onNodeWithTag("goal_unit_$u").assertExists()
        rule.onNodeWithTag("goal_unit_min").assertIsSelected()
        rule.onNodeWithTag("goal_unit_times").performClick().assertIsSelected()
        assertEquals("1 time", text("goal_amount"))
        rule.onNodeWithTag("goal_less").assertIsNotEnabled()
        assertEquals("Call grandma, 1 time, 3 days a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_more").performClick()
        assertEquals("2 times", text("goal_amount"))
        rule.onNodeWithTag("goal_save").performClick()
        assertEquals(WeeklyGoalDraft("Call grandma", 3, 2, "times"), saved)
    }

    @Test fun a_goal_with_no_target_opens_with_one_and_saving_gives_it_that_target() {
        showSetup(WeeklyGoalDraft.of("Exercise", 4, null, null), existing = true)
        assertEquals("30 min", text("goal_amount"))
        assertEquals("Exercise, 30 min, 4 days a week", text("goal_summary_title"))
        rule.onNodeWithTag("goal_save").performClick()
        assertEquals(WeeklyGoalDraft("Exercise", 4, 30, "min"), saved)
    }

    @Test fun the_list_row_carries_the_target() {
        val g = WeeklyGoalEntity(id = 7, title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 1, effectiveFrom = "2026-11-21", targetValue = 30, unit = "min")
        assertEquals("Exercise · 30 min · 4 days a week", WeeklyGoalItem.of(g).text.replace(WeeklyGoalCopy.NBSP, ' '))
    }

    @Test fun close_cancels_without_saving() {
        showSetup()
        rule.onNodeWithTag("goal_name").performTextInput("Piano")
        rule.onNodeWithTag("page_back").performClick()
        assertEquals(1, closed)
        assertNull(saved)
    }

    @Test fun a_double_tap_on_save_saves_once_and_close_then_does_nothing() {
        var saves = 0
        var pops = 0
        rule.setContent { Shot { WeeklyGoalEditPage(WeeklyGoalDraft("Piano"), onClose = { pops++ }, onSave = { saves++; pops++ }) } }
        rule.onNodeWithTag("goal_save").performClick()
        rule.onNodeWithTag("goal_save").performClick()
        rule.onNodeWithTag("page_back").performClick()
        assertEquals("one save", 1, saves)
        assertEquals("one pop", 1, pops)
    }

    // --- an existing goal ----------------------------------------------------------------------

    @Test fun an_existing_goal_opens_filled_in_and_saves_its_changes() {
        showSetup(WeeklyGoalDraft("Exercise", 4), existing = true)
        rule.onNodeWithTag("page_title").assertTextEquals("Weekly goal")
        assertEquals("Exercise", text("goal_name"))
        rule.onNodeWithTag("goal_pick_Exercise").assertIsSelected()
        rule.onNodeWithTag("goal_day_4").assertIsSelected()
        rule.onNodeWithTag("goal_delete").assertTextEquals("Delete goal")
        rule.onNodeWithTag("goal_day_2").performClick()
        rule.onNodeWithTag("goal_name").performTextReplacement("Swim")
        rule.onNodeWithTag("goal_save").performClick()
        assertEquals(WeeklyGoalDraft("Swim", 2), saved)
        assertEquals(0, deleted)
    }

    @Test fun delete_asks_first_and_cancel_keeps_the_goal() {
        showSetup(WeeklyGoalDraft("Exercise", 4), existing = true)
        rule.onNodeWithTag("goal_delete_confirm").assertDoesNotExist()
        rule.onNodeWithTag("goal_delete").performClick()
        assertEquals("nothing is deleted before the answer", 0, deleted)
        rule.onNodeWithText("Delete this weekly goal?").assertExists()
        assertEquals("Exercise, 4 days a week will be removed from your weekly goals.", text("goal_delete_message"))
        rule.onNodeWithTag("goal_delete_cancel").performClick()
        rule.onNodeWithTag("goal_delete_confirm").assertDoesNotExist()
        assertEquals(0, deleted)
        assertEquals(0, closed)
        // Still on the page, and it still works.
        rule.onNodeWithTag("goal_save").assertIsEnabled()
    }

    @Test fun confirming_delete_deletes_once() {
        showSetup(WeeklyGoalDraft("Exercise", 4), existing = true)
        rule.onNodeWithTag("goal_delete").performClick()
        rule.onNodeWithTag("goal_delete_confirm").performClick()
        assertEquals(1, deleted)
        rule.onNodeWithTag("goal_delete_confirm").assertDoesNotExist()
        // The page is leaving: neither Delete nor Save answers again.
        rule.onNodeWithTag("goal_delete").performClick()
        rule.onNodeWithTag("goal_delete_confirm").assertDoesNotExist()
        rule.onNodeWithTag("goal_save").performClick()
        assertEquals(1, deleted)
        assertNull(saved)
    }

    @Test fun the_confirm_names_the_saved_goal_not_the_unsaved_edit() {
        showSetup(WeeklyGoalDraft("Exercise", 4), existing = true)
        rule.onNodeWithTag("goal_name").performTextReplacement("Swim")
        rule.onNodeWithTag("goal_day_6").performClick()
        rule.onNodeWithTag("goal_delete").performClick()
        assertEquals("Exercise, 4 days a week will be removed from your weekly goals.", text("goal_delete_message"))
    }

    // --- what is stored (the ViewModel over a real in-memory database) -------------------------

    private class TestClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }

    // Saturday 21 November 2026.
    private val clock = TestClock(Instant.parse("2026-11-21T12:00:00Z"))
    private val time = TimeSource(clock)
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val repo = WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time)
    private val vm by lazy {
        WeeklyGoalsViewModel(repo, SprigRepository(KeptPreferences(ctx), db.galleryDao(), db.dayRecordDao(), time), CoroutineScope(Dispatchers.Unconfined))
    }

    @After fun closeDb() = db.close()

    private suspend fun active() = repo.observeGoals().first()

    @Test fun saving_exercise_4_stores_one_active_goal_from_today() = runBlocking {
        vm.add(WeeklyGoalDraft().pick("Exercise").withDays(4)).join()
        val goal = active().single()
        assertEquals("Exercise", goal.title)
        assertEquals(4, goal.daysPerWeek)
        assertTrue(goal.isActive)
        assertEquals("2026-11-21", goal.effectiveFrom)
        assertEquals(HabitIconRules.forName("Exercise"), goal.iconKey)
        assertEquals("run", goal.iconKey)
        assertEquals("Exercise · 30 min · 4 days a week", WeeklyGoalItem.of(goal).text.replace(WeeklyGoalCopy.NBSP, ' '))
    }

    @Test fun adding_then_editing_the_target_updates_the_list_row() = runBlocking {
        vm.add(WeeklyGoalDraft().pick("Exercise").withDays(4)).join()
        val goal = active().single()
        assertEquals(30 to "min", goal.targetValue to goal.unit)
        assertEquals("Exercise · 30 min · 4 days a week", WeeklyGoalItem.of(goal).text.replace(WeeklyGoalCopy.NBSP, ' '))
        val opened = WeeklyGoalDraft.of(goal.title, goal.daysPerWeek, goal.targetValue, goal.unit)
        assertEquals("30 min", opened.amount.replace(WeeklyGoalCopy.NBSP, ' '))
        vm.save(goal.id, opened.more().more().more()).join()
        assertEquals("Exercise · 45 min · 4 days a week", WeeklyGoalItem.of(active().single()).text.replace(WeeklyGoalCopy.NBSP, ' '))
    }

    @Test fun a_custom_name_is_saved_trimmed_with_its_icon() = runBlocking {
        vm.add(WeeklyGoalDraft("  Call grandma ", 1)).join()
        val goal = active().single()
        assertEquals("Call grandma", goal.title)
        assertEquals(HabitIconRules.FALLBACK, goal.iconKey)
        assertEquals(1, goal.daysPerWeek)
    }

    @Test fun a_draft_with_no_activity_stores_nothing() = runBlocking {
        vm.add(WeeklyGoalDraft("   ", 4)).join()
        assertEquals(emptyList<WeeklyGoalEntity>(), active())
        vm.add(WeeklyGoalDraft("Read", 2)).join()
        val id = active().single().id
        vm.save(id, WeeklyGoalDraft("", 5)).join()
        assertEquals("Read", active().single().title)
        assertEquals(2, active().single().daysPerWeek)
    }

    @Test fun editing_changes_the_goal_and_the_list_and_keeps_this_weeks_sessions() = runBlocking {
        clock.now = Instant.parse("2026-11-09T12:00:00Z") // set the week before last
        vm.add(WeeklyGoalDraft("Exercise", 4)).join()
        val id = active().single().id
        clock.now = Instant.parse("2026-11-16T12:00:00Z") // Monday
        assertTrue(repo.logToday(id))
        clock.now = Instant.parse("2026-11-18T12:00:00Z") // Wednesday
        assertTrue(repo.logToday(id))

        vm.save(id, WeeklyGoalDraft("Swim", 2)).join()

        val goal = active().single()
        assertEquals(id, goal.id)
        assertEquals("Swim", goal.title)
        assertEquals(2, goal.daysPerWeek)
        assertEquals("the start date does not move", "2026-11-09", goal.effectiveFrom)
        assertEquals("Swim · 20 min · 2 days a week", WeeklyGoalItem.of(goal).text.replace(WeeklyGoalCopy.NBSP, ' '))
        val week = repo.thisWeek().single().week
        assertEquals("both sessions are still counted", 2, week.done)
        assertEquals(2, week.target)
        assertEquals(2, db.weeklyGoalEntryDao().between("2026-11-15", "2026-11-21").size)
    }

    @Test fun deleting_removes_the_goal_from_the_list_and_makes_room() = runBlocking {
        listOf("Exercise", "Read", "Study").forEach { vm.add(WeeklyGoalDraft(it, 3)).join() }
        vm.add(WeeklyGoalDraft("Practice", 3)).join()
        assertEquals("a fourth is not stored", listOf("Exercise", "Read", "Study"), active().map { it.title })
        vm.remove(active()[1].id).join()
        assertEquals(listOf("Exercise", "Study"), active().map { it.title })
        vm.add(WeeklyGoalDraft("Practice", 3)).join()
        assertEquals(listOf("Exercise", "Study", "Practice"), active().map { it.title })
    }

    // --- TalkBack and touch targets ------------------------------------------------------------

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

    @Test fun the_list_is_accessible() = assertAccessible { WeeklyGoalsContent(WeeklyGoalsFixtures.TWO, {}, {}, {}) }
    @Test fun the_empty_list_is_accessible() = assertAccessible { WeeklyGoalsContent(emptyList(), {}, {}, {}) }
    @Test fun setup_is_accessible() = assertAccessible { WeeklyGoalEditPage(WeeklyGoalDraft(), {}, {}, sprig = WeeklyGoalsFixtures.SPRIG) }
    @Test fun an_existing_goals_setup_is_accessible() =
        assertAccessible { WeeklyGoalEditPage(WeeklyGoalsFixtures.SEVEN, {}, {}, sprig = WeeklyGoalsFixtures.SPRIG, onDelete = {}) }

    @Test fun the_delete_confirm_is_accessible() = assertAccessible { DeleteGoalConfirm("Exercise, 4 days a week will be removed from your weekly goals.", {}, {}) }

    @Test fun talkback_hears_the_day_chips_as_days_a_week_and_the_field_as_its_question() {
        showSetup()
        fun described(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().replace(WeeklyGoalCopy.NBSP, ' ')
        assertEquals("1 day a week", described("goal_day_1"))
        assertEquals("4 days a week", described("goal_day_4"))
        assertEquals("What will you do?", described("goal_name"))
        assertEquals("Cancel", described("page_back"))
        rule.onNodeWithTag("goal_summary").performScrollTo().assertExists()
    }
}
