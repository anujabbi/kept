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
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseRules
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
 * What a weekly promise stores (issue #165, story #173), and what TalkBack hears on the delete
 * confirm. The goal is set on the promise (#218): `PromiseBehaviourTest`; it is listed in Your
 * promises (#236): `PromisesBehaviourTest`.
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

    // --- setup ---------------------------------------------------------------------------------

    // --- the target (story #205, task #206) ----------------------------------------------------

    // --- an existing goal ----------------------------------------------------------------------

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

    /** The goal as its card in Your promises (#236) reads: name, amount, how often. */
    private fun card(g: WeeklyGoalEntity) = g.promise().let { "${it.title} · ${it.amount} · ${it.chip}" }.replace(WeeklyGoalCopy.NBSP, ' ')

    @Test fun saving_exercise_4_stores_one_active_goal_from_today() = runBlocking {
        vm.add(PromiseRules.fresh(emptyList(), days = 4)).join()
        val goal = active().single()
        assertEquals("Exercise", goal.title)
        assertEquals(4, goal.daysPerWeek)
        assertTrue(goal.isActive)
        assertEquals("2026-11-21", goal.effectiveFrom)
        assertEquals(HabitIconRules.forName("Exercise"), goal.iconKey)
        assertEquals("run", goal.iconKey)
        assertEquals("Exercise · 30 min · 4 days a week", card(goal))
    }

    @Test fun adding_then_editing_the_target_updates_the_list_row() = runBlocking {
        vm.add(PromiseRules.fresh(emptyList(), days = 4)).join()
        val goal = active().single()
        assertEquals(30 to "min", goal.targetValue to goal.unit)
        assertEquals("Exercise · 30 min · 4 days a week", card(goal))
        val opened = PromiseRules.ofGoal(goal.title, goal.daysPerWeek, goal.targetValue, goal.unit)
        assertEquals("I will exercise for 30 minutes, 4 days a week.", opened.sentence.replace(WeeklyGoalCopy.NBSP, ' '))
        vm.save(goal.id, opened.withTarget(45)).join()
        assertEquals("Exercise · 45 min · 4 days a week", card(active().single()))
    }

    @Test fun a_custom_name_is_saved_trimmed_with_its_icon() = runBlocking {
        vm.add(PromiseDraft("  Call grandma ", days = 1)).join()
        val goal = active().single()
        assertEquals("Call grandma", goal.title)
        assertEquals(HabitIconRules.FALLBACK, goal.iconKey)
        assertEquals(1, goal.daysPerWeek)
    }

    @Test fun a_draft_with_no_activity_stores_nothing() = runBlocking {
        vm.add(PromiseDraft("   ", days = 4)).join()
        assertEquals(emptyList<WeeklyGoalEntity>(), active())
        vm.add(PromiseDraft("Read", days = 2)).join()
        val id = active().single().id
        vm.save(id, PromiseDraft("", days = 5)).join()
        assertEquals("Read", active().single().title)
        assertEquals(2, active().single().daysPerWeek)
    }

    @Test fun editing_changes_the_goal_and_the_list_and_keeps_this_weeks_sessions() = runBlocking {
        clock.now = Instant.parse("2026-11-09T12:00:00Z") // set the week before last
        vm.add(PromiseDraft("Exercise", days = 4)).join()
        val id = active().single().id
        clock.now = Instant.parse("2026-11-16T12:00:00Z") // Monday
        assertTrue(repo.logToday(id))
        clock.now = Instant.parse("2026-11-18T12:00:00Z") // Wednesday
        assertTrue(repo.logToday(id))

        vm.save(id, PromiseDraft("Swim", days = 2)).join()

        val goal = active().single()
        assertEquals(id, goal.id)
        assertEquals("Swim", goal.title)
        assertEquals(2, goal.daysPerWeek)
        assertEquals("the start date does not move", "2026-11-09", goal.effectiveFrom)
        assertEquals("Swim · 20 min · 2 days a week", card(goal))
        val week = repo.thisWeek().single().week
        assertEquals("both sessions are still counted", 2, week.done)
        assertEquals(2, week.target)
        assertEquals(2, db.weeklyGoalEntryDao().between("2026-11-15", "2026-11-21").size)
    }

    @Test fun deleting_removes_the_goal_from_the_list_and_makes_room() = runBlocking {
        listOf("Exercise", "Read", "Study").forEach { vm.add(PromiseDraft(it, days = 3)).join() }
        vm.add(PromiseDraft("Practice", days = 3)).join()
        assertEquals("a fourth is not stored", listOf("Exercise", "Read", "Study"), active().map { it.title })
        vm.remove(active()[1].id).join()
        assertEquals(listOf("Exercise", "Study"), active().map { it.title })
        vm.add(PromiseDraft("Practice", days = 3)).join()
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

    @Test fun the_delete_confirm_is_accessible() = assertAccessible { DeleteGoalConfirm("Exercise, 4 days a week will be removed from your weekly goals.", {}, {}) }

}
