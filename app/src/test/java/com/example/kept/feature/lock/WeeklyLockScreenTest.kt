package com.example.kept.feature.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.data.WeeklyGoalWeek
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.domain.LockMode
import com.example.kept.core.domain.LockReason
import com.example.kept.core.domain.LockScreenKind
import com.example.kept.core.domain.LockSprig
import com.example.kept.core.domain.WeeklyGoalRules
import com.example.kept.core.domain.WeeklyLockCopy
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Lock states with weekly goals (issue #165, story #175), on the week of Sunday 22 November 2026
 * with the goal "Exercise, 4 days a week" set long before, as in the issue's worked examples.
 */
internal object WeeklyLockFixtures {
    val sunday: LocalDate = LocalDate.of(2026, 11, 22)
    val wednesday: LocalDate = sunday.plusDays(3)
    private val longAgo = LocalDate.of(2026, 11, 1)

    /** A goal's week as of [today], with a session on each of [sessions] (0 = Sunday … 6 = Saturday). */
    fun goal(id: Long, title: String, daysPerWeek: Int, today: LocalDate, vararg sessions: Int, from: LocalDate = longAgo): WeeklyGoalWeek =
        WeeklyGoalWeek(
            WeeklyGoalEntity(id = id, title = title, iconKey = "run", daysPerWeek = daysPerWeek, createdAt = 0, effectiveFrom = from.toString()),
            WeeklyGoalRules.week(WeeklyGoalRules.Goal(daysPerWeek, from), sessions.map { sunday.plusDays(it.toLong()) }.toSet(), today),
        )

    /**
     * [habitsTotal] habits with [habitsDone] ticked, and [goals]. The daily lock holds while a
     * habit or a must-do session is left, as `LockPolicy` says inside the window.
     */
    fun state(habitsTotal: Int, habitsDone: Int, vararg goals: WeeklyGoalWeek): LockUi {
        val base = LockFixtures.state(habitsTotal, habitsDone)
        val lock = base.lock!!.copy(weekly = goals.toList())
        val locked = lock.habitsIncomplete || lock.weeklyMustDoIncomplete
        return base.copy(lock = lock, lockActive = locked, blockedStillLocked = locked, reason = if (locked) LockReason.DAILY else null)
    }

    /** Example A: Wednesday, nothing logged, both habits done. */
    fun nothingDone(): LockUi = state(2, 2, goal(1, "Exercise", 4, wednesday))

    /** Example C: Thursday after Wednesday's session. */
    fun someDone(): LockUi = state(2, 2, goal(1, "Exercise", 4, sunday.plusDays(4), 3))

    /** Example C: Friday, Thursday skipped, so 4 is out of reach. */
    fun cantHit(): LockUi = state(2, 2, goal(1, "Exercise", 4, sunday.plusDays(5), 3))

    /** A Saturday with one to go. */
    fun saturday(): LockUi = state(2, 2, goal(1, "Exercise", 4, sunday.plusDays(6), 0, 1, 2))

    /** A daily habit ("Read") and a must-do session both undone. The goal is "Swim", so its row is not taken for the habit "Exercise". */
    fun combined(): LockUi = state(2, 1, goal(1, "Swim", 4, wednesday))

    /** The longest activity a goal can have (28 characters). */
    fun longName(): LockUi = state(2, 2, goal(1, "Practice the piano every day", 4, wednesday))
}

private fun ComposeContentTestRule.rect(tag: String, unmerged: Boolean = false): Rect {
    val d = density.density
    val n = onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
    return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
}

private fun ComposeContentTestRule.text(tag: String): String =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }.replace(WeeklyLockCopy.NBSP, ' ')

/**
 * At font 1.0, 1.3 and 2.0, for each of [states]: no word of the headline or the line is split,
 * Sprig touches neither, the block ends above the hill's crest, "Phone" stays on one line, and
 * the three footer buttons are whole, on screen and at least 48dp tall.
 */
internal fun assertWeeklyLockFits(rule: ComposeContentTestRule, states: List<Pair<String, LockUi>>) {
    val scale = mutableFloatStateOf(1f)
    var current by mutableStateOf(states.first().second)
    rule.setContent { Shot(scale.floatValue) { LockScreen(current, "Chrome", LockActions()) } }
    for (f in listOf(1f, 1.3f, 2f)) for ((name, state) in states) {
        scale.floatValue = f
        current = state
        rule.waitForIdle()
        val at = "weekly lock ($name) at font $f"
        val d = rule.density.density
        val root = rule.onRoot().fetchSemanticsNode().size.let { Size(it.width / d, it.height / d) }
        // Sprig stands beside the words up to font 1.5, and steps aside above it.
        val sprig = if (LockSprig.weeklyShowsSprig(f)) rule.rect("lock_sprig") else null
        if (sprig == null) rule.onNodeWithTag("lock_sprig").assertDoesNotExist()
        for (tag in listOf("lock_headline", "lock_line")) {
            assertNoWordSplit(rule, tag, at)
            val t = rule.rect(tag)
            if (sprig != null) assertTrue("$at: Sprig $sprig overlaps $tag $t", !sprig.overlaps(t))
            assertTrue("$at: $tag $t runs off the screen", t.left >= 0f && t.right <= root.width)
        }
        val headline = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("lock_headline").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(headline)
        assertTrue("$at: the headline takes more than its two clauses' lines", headline.single().lineCount <= 2)
        val chip = rule.rect("lock_weekly_chip", unmerged = true)
        assertTrue("$at: the chip $chip is under the status bar or off the side", chip.top >= 0f && chip.right <= root.width)
        assertTrue("$at: the chip overlaps the headline", chip.bottom <= rule.rect("lock_headline").top)
        val crest = rule.onNodeWithTag("lock_crest", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y / d
        assertTrue("$at: the headline block ends below the crest", rule.rect("lock_line").bottom <= crest)
        if (sprig != null) assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Phone", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        assertEquals("$at: Phone wraps", 1, result.single().lineCount)
        // "Phone" and "Break the lock" never scroll: whole, on screen, 48dp or taller.
        for (tag in listOf("emergency_call", "break_lock")) {
            val b = rule.rect(tag)
            assertTrue("$at: $tag $b is cut off (screen ${root.height})", b.bottom <= root.height + 0.5f && b.height >= 47.9f)
            assertTrue("$at: $tag $b runs off the side", b.left >= 0f && b.right <= root.width + 0.5f)
        }
        // The week keeps room for its header, however little the sky leaves.
        val page = rule.rect("lock_weekly_page")
        assertTrue("$at: the week's page is only ${page.height}dp tall", page.height >= 55.9f)
        // The gold button is the way out: whole and over "Phone" wherever the screen has room for
        // it under the week's header, and never with less than a 48dp strip of it to tap.
        val phoneTop = rule.rect("emergency_call").top
        val before = rule.rect("lock_weekly_done")
        val roomy = phoneTop - page.top >= 56f + before.height + 25f
        if (roomy) assertTrue("$at: the gold button $before is not whole over Phone ($phoneTop) without a scroll", before.bottom <= phoneTop + 0.5f)
        rule.onNodeWithTag("lock_weekly_done").performScrollTo()
        val gold = rule.rect("lock_weekly_done")
        assertTrue("$at: the gold button $gold is under 48dp", gold.height >= 47.9f)
        assertTrue("$at: under 48dp of the gold button $gold shows above Phone ($phoneTop)", minOf(gold.bottom, phoneTop) - gold.top >= 47.9f)
        assertTrue("$at: the gold button $gold runs off the side", gold.left >= 0f && gold.right <= root.width + 0.5f)
    }
}

internal val WEEKLY_STATES get() = listOf(
    "nothing done" to WeeklyLockFixtures.nothingDone(),
    "some done" to WeeklyLockFixtures.someDone(),
    "can't hit" to WeeklyLockFixtures.cantHit(),
    "saturday" to WeeklyLockFixtures.saturday(),
    "long name" to WeeklyLockFixtures.longName(),
)

/**
 * The weekly variant of the lock screen and the habits list's "This week" row (issue #165, story
 * #175; weekly goals mockup 3) at 412 × 915dp. Goldens: `app/src/test/screenshots/lock_weekly_*`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class WeeklyLockScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun show(s: LockUi, actions: LockActions = LockActions(), fontScale: Float = 1f) {
        rule.setContent { Shot(fontScale) { LockScreen(s, "Chrome", actions) } }
        rule.waitForIdle()
    }

    private fun shot(name: String, fontScale: Float, s: LockUi) {
        show(s, fontScale = fontScale)
        rule.onRoot().captureRoboImage("src/test/screenshots/lock_weekly_${name}_font$fontScale.png")
    }

    @Test fun nothingDone() = shot("nothing_done", 1f, WeeklyLockFixtures.nothingDone())
    @Test fun nothingDone_font2() = shot("nothing_done", 2f, WeeklyLockFixtures.nothingDone())
    @Test fun someDone() = shot("some_done", 1f, WeeklyLockFixtures.someDone())
    @Test fun someDone_font2() = shot("some_done", 2f, WeeklyLockFixtures.someDone())
    @Test fun cantHit() = shot("cant_hit", 1f, WeeklyLockFixtures.cantHit())
    @Test fun cantHit_font2() = shot("cant_hit", 2f, WeeklyLockFixtures.cantHit())
    @Test fun combined() = shot("combined", 1f, WeeklyLockFixtures.combined())
    @Test fun combined_font2() = shot("combined", 2f, WeeklyLockFixtures.combined())

    @Test fun example_A_reads_as_the_issue_says() {
        val s = WeeklyLockFixtures.nothingDone()
        assertEquals(LockScreenKind.WEEKLY, s.kind)
        assertEquals(LockMode.LOCKED, s.mode)
        show(s)
        rule.onNodeWithText("Weekly goal").assertExists()
        assertEquals("4 days left, 4 to go.", rule.text("lock_headline"))
        assertEquals("No exercise yet this week. Do today's session to unlock.", rule.text("lock_line"))
        rule.onNodeWithText("This week").assertExists()
        assertEquals("Exercise · 0 of 4", rule.text("lock_weekly_summary"))
        assertEquals("Skip today and tomorrow locks too.", rule.text("lock_weekly_hint"))
        rule.onNodeWithText("Mark today's exercise done").assertExists()
        assertEquals(listOf("Exercise", "Today"), rule.onNodeWithTag("lock_weekly_today").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
        rule.onNodeWithTag("emergency_call").assertExists()
        rule.onNodeWithTag("break_lock").assertExists()
    }

    @Test fun at_font_1_each_clause_of_the_headline_has_its_own_line() {
        show(WeeklyLockFixtures.nothingDone())
        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("lock_headline").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        assertEquals("\"4 days left,\" and \"4 to go.\"", 2, result.single().lineCount)
    }

    @Test fun the_headline_keeps_each_clause_whole() {
        assertEquals("4 days left, 4 to go.", wholeClauses("4 days left, 4 to go."))
        assertEquals("1 day left, 1 to go.", wholeClauses("1 day left, 1 to go."))
    }

    @Test fun it_shows_neither_the_habit_list_nor_the_app_name() {
        show(WeeklyLockFixtures.nothingDone())
        rule.onNodeWithTag("lock_habit_list").assertDoesNotExist()
        rule.onNodeWithTag("lock_count").assertDoesNotExist()
        rule.onNodeWithText("Chrome can wait.").assertDoesNotExist()
        rule.onNodeWithTag("lock_open_app").assertDoesNotExist()
    }

    @Test fun the_strip_says_each_day() {
        show(WeeklyLockFixtures.someDone())
        val strip = rule.onNodeWithTag("lock_weekly_strip", useUnmergedTree = true).fetchSemanticsNode()
        val spoken = strip.children.map { it.config[SemanticsProperties.ContentDescription].single() }
        assertEquals(
            listOf("Sunday, skipped", "Monday, skipped", "Tuesday, skipped", "Wednesday, done", "Thursday, today, not done yet", "Friday, coming up", "Saturday, coming up"),
            spoken,
        )
        assertEquals("1 of 4 done. Do today's session to unlock.", rule.text("lock_line"))
        assertEquals("3 days left, 3 to go.", rule.text("lock_headline"))
    }

    @Test fun the_cant_hit_line_and_the_singular_headline() {
        var s by mutableStateOf(WeeklyLockFixtures.cantHit())
        rule.setContent { Shot { LockScreen(s, "Chrome", LockActions()) } }
        assertEquals("2 days left, 3 to go.", rule.text("lock_headline"))
        assertEquals("You can't hit 4 this week, but today still counts.", rule.text("lock_line"))
        s = WeeklyLockFixtures.saturday()
        rule.waitForIdle()
        assertEquals("1 day left, 1 to go.", rule.text("lock_headline"))
        assertEquals("3 of 4 done. Do today's session to unlock.", rule.text("lock_line"))
        // Tomorrow is a new week with 7 days for 4: the hint would be untrue, so it is left out.
        rule.onNodeWithTag("lock_weekly_hint").assertDoesNotExist()
    }

    @Test fun the_button_and_the_today_row_both_mark_this_goal_done() {
        val marked = mutableListOf<Long>()
        show(WeeklyLockFixtures.nothingDone(), LockActions(onWeeklyDone = { marked += it }))
        rule.onNodeWithTag("lock_weekly_done").performClick()
        rule.onNodeWithTag("lock_weekly_today").performScrollTo().performClick()
        assertEquals(listOf(1L, 1L), marked)
    }

    @Test fun phone_and_break_the_lock_work() {
        var phone = 0
        var breaks = 0
        show(WeeklyLockFixtures.nothingDone(), LockActions(onPhone = { phone++ }, onBreak = { breaks++ }))
        rule.onNodeWithTag("emergency_call").performClick()
        rule.onNodeWithTag("break_lock").performClick()
        assertEquals(1 to 1, phone to breaks)
    }

    @Test fun with_two_must_do_goals_it_is_about_the_first_and_then_the_second() {
        val exercise = WeeklyLockFixtures.goal(1, "Exercise", 4, WeeklyLockFixtures.wednesday)
        val practice = WeeklyLockFixtures.goal(2, "Practice", 7, WeeklyLockFixtures.wednesday, 0, 1, 2)
        var s by mutableStateOf(WeeklyLockFixtures.state(2, 2, exercise, practice))
        val marked = mutableListOf<Long>()
        rule.setContent { Shot { LockScreen(s, "Chrome", LockActions(onWeeklyDone = { marked += it })) } }
        rule.onNodeWithText("Mark today's exercise done").performClick()
        assertEquals(listOf(1L), marked)
        // Exercise logged: the lock still holds for Practice, and the screen is now about it.
        s = WeeklyLockFixtures.state(2, 2, WeeklyLockFixtures.goal(1, "Exercise", 4, WeeklyLockFixtures.wednesday, 3), practice)
        rule.waitForIdle()
        assertEquals(LockMode.LOCKED, s.mode)
        assertEquals("4 days left, 4 to go.", rule.text("lock_headline"))
        assertEquals("3 of 7 done. Do today's session to unlock.", rule.text("lock_line"))
        assertEquals("Practice · 3 of 7", rule.text("lock_weekly_summary"))
        rule.onNodeWithText("Mark today's practice done").performClick()
        assertEquals(listOf(1L, 2L), marked)
    }

    @Test fun a_habit_and_a_must_do_session_share_the_habits_list() {
        val s = WeeklyLockFixtures.combined()
        assertEquals(LockScreenKind.HABITS, s.kind)
        assertNull(s.weekly)
        val logged = mutableListOf<Long>()
        show(s, LockActions(onWeeklyLog = { logged += it }))
        assertEquals("Chrome can wait.", rule.text("lock_headline"))
        rule.onNodeWithTag("lock_habit_list").assertExists()
        rule.onNodeWithTag("lock_weekly_chip").assertDoesNotExist()
        rule.onNodeWithTag("lock_weekly_done").assertDoesNotExist()
        val row = rule.onNodeWithTag("lock_weekly_row_1").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Swim", "This week"), row)
        rule.onNodeWithTag("lock_weekly_row_1").performScrollTo().performClick()
        assertEquals(listOf(1L), logged)
    }

    @Test fun ticking_only_the_habit_keeps_the_lock_and_turns_to_the_weekly_variant() {
        val s = WeeklyLockFixtures.state(2, 2, WeeklyLockFixtures.goal(1, "Exercise", 4, WeeklyLockFixtures.wednesday))
        assertEquals("habits done, the session not", LockMode.LOCKED, s.mode)
        assertFalse(s.open)
        assertEquals(LockScreenKind.WEEKLY, s.kind)
    }

    @Test fun logging_only_the_session_keeps_the_lock_and_its_row_can_be_undone() {
        val s = WeeklyLockFixtures.state(2, 1, WeeklyLockFixtures.goal(1, "Exercise", 4, WeeklyLockFixtures.wednesday, 3))
        assertEquals(LockMode.LOCKED, s.mode)
        assertEquals(LockScreenKind.HABITS, s.kind)
        val undone = mutableListOf<Long>()
        show(s, LockActions(onWeeklyUndo = { undone += it }))
        val row = rule.onNodeWithTag("lock_weekly_row_1").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals("a logged session stays as a done row", listOf("Exercise", "Done"), row)
        rule.onNodeWithTag("lock_weekly_row_1").performScrollTo().performTouchInput { longClick() }
        assertEquals(listOf(1L), undone)
    }

    @Test fun both_done_opens_the_screen() {
        val s = WeeklyLockFixtures.state(2, 2, WeeklyLockFixtures.goal(1, "Exercise", 4, WeeklyLockFixtures.wednesday, 3))
        assertEquals(LockMode.OPEN, s.mode)
        show(s)
        assertEquals("Chrome is open.", rule.text("lock_headline"))
        rule.onNodeWithTag("lock_open_app").assertExists()
    }

    @Test fun a_goal_that_is_not_must_do_adds_nothing_to_the_habits_list() {
        val monday = WeeklyLockFixtures.sunday.plusDays(1)
        val s = WeeklyLockFixtures.state(2, 1, WeeklyLockFixtures.goal(1, "Exercise", 4, monday, 0))
        assertTrue(s.weeklyRows.isEmpty())
        show(s)
        rule.onNodeWithTag("lock_weekly_row_1").assertDoesNotExist()
    }

    @Test fun night_lockdown_keeps_its_own_screen_over_a_must_do_session() {
        val weekly = WeeklyLockFixtures.nothingDone()
        val s = weekly.copy(reason = LockReason.NIGHT, lock = weekly.lock!!.copy(settings = weekly.lock!!.settings.copy(nightLockEnabled = true)))
        assertEquals(LockScreenKind.NIGHT, s.kind)
        assertNull(s.weekly)
        show(s)
        assertEquals("It's shutdown time.", rule.text("lock_headline"))
        rule.onNodeWithTag("lock_weekly_done").assertDoesNotExist()
        rule.onNodeWithTag("lock_weekly_chip").assertDoesNotExist()
    }

    @Test fun after_the_give_up_time_the_screen_gets_out_of_the_way() {
        val s = WeeklyLockFixtures.nothingDone().copy(lockActive = false, blockedStillLocked = false, reason = null)
        assertEquals(LockMode.DISMISS, s.mode)
    }

    @Test fun every_tappable_part_is_48dp_and_labelled() {
        for (f in listOf(1f, 2f)) for (s in listOf(WeeklyLockFixtures.nothingDone(), WeeklyLockFixtures.combined())) {
            ruleContent(f, s)
            val d = rule.density.density
            val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            assertTrue("the rows and the footer are tappable", nodes.size >= 3)
            nodes.forEach { n ->
                val label = listOfNotNull(
                    n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                    n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                ).joinToString(" ").trim()
                assertTrue("a tappable part has no label", label.isNotBlank())
                assertTrue("'$label' is ${n.size.height / d}dp tall at font $f", n.size.height / d >= 47.9f)
                assertTrue("'$label' is ${n.size.width / d}dp wide at font $f", n.size.width / d >= 47.9f)
            }
        }
    }

    private val shownScale = mutableFloatStateOf(1f)
    private val shownState = mutableStateOf<LockUi?>(null)
    private var contentSet = false

    /** Sets the content once, then swaps the state and the font scale under it. */
    private fun ruleContent(scale: Float, s: LockUi) {
        shownScale.floatValue = scale
        shownState.value = s
        if (!contentSet) {
            contentSet = true
            rule.setContent { Shot(shownScale.floatValue) { shownState.value?.let { LockScreen(it, "Chrome", LockActions()) } } }
        }
        rule.waitForIdle()
    }

    @Test fun the_today_row_says_what_a_tap_does() {
        show(WeeklyLockFixtures.nothingDone())
        val c = rule.onNodeWithTag("lock_weekly_today").fetchSemanticsNode().config
        assertEquals("Mark done", c[SemanticsActions.OnClick].label)
    }

    @Test fun itFits() = assertWeeklyLockFits(rule, WEEKLY_STATES)
}

/** The weekly variant at 360 × 640dp. Goldens: `app/src/test/screenshots/lock_360_weekly_*`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class WeeklyLockNarrowScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, fontScale: Float, s: LockUi) {
        rule.setContent { Shot(fontScale) { LockScreen(s, "Chrome", LockActions()) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/lock_360_weekly_${name}_font$fontScale.png")
    }

    @Test fun nothingDone() = shot("nothing_done", 1f, WeeklyLockFixtures.nothingDone())
    @Test fun nothingDone_font2() = shot("nothing_done", 2f, WeeklyLockFixtures.nothingDone())

    @Test fun itFits() = assertWeeklyLockFits(rule, WEEKLY_STATES)
}
