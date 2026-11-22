package com.example.kept.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.kept.core.domain.HabitIconRules
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.domain.WeeklyGoalDraft
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

object WeeklyGoalsFixtures {
    private fun item(id: Long, title: String, days: Int) = WeeklyGoalItem(id, WeeklyGoalCopy.listRow(title, days), HabitIconRules.forName(title))

    /** The issue's example and one more. */
    val TWO: List<WeeklyGoalItem> = listOf(item(1, "Exercise", 4), item(2, "Read", 1))

    /** At the limit, with a name as long as the field allows. */
    val THREE: List<WeeklyGoalItem> = TWO + item(3, "Practice guitar with my band", 7)

    /** Mockup 1: Exercise, 4 days a week. */
    val FILLED = WeeklyGoalDraft("Exercise", 4)
    val SEVEN = WeeklyGoalDraft("Tidy room", 7)
    val SPRIG = SetupSprig(SprigForm.BLOOM)
}

/** Every state the issue lists, by golden name. */
internal val weeklyGoalScreens: List<Pair<String, @Composable () -> Unit>> = listOf(
    "list" to { WeeklyGoalsContent(WeeklyGoalsFixtures.TWO, {}, {}, {}) },
    "list_empty" to { WeeklyGoalsContent(emptyList(), {}, {}, {}) },
    "list_limit" to { WeeklyGoalsContent(WeeklyGoalsFixtures.THREE, {}, {}, {}) },
    "setup" to { WeeklyGoalEditPage(WeeklyGoalDraft(), {}, {}, sprig = WeeklyGoalsFixtures.SPRIG) },
    "setup_filled" to { WeeklyGoalEditPage(WeeklyGoalsFixtures.FILLED, {}, {}, sprig = WeeklyGoalsFixtures.SPRIG) },
    "setup_seven" to { WeeklyGoalEditPage(WeeklyGoalsFixtures.SEVEN, {}, {}, sprig = WeeklyGoalsFixtures.SPRIG) },
    "setup_edit" to { WeeklyGoalEditPage(WeeklyGoalsFixtures.FILLED, {}, {}, sprig = WeeklyGoalsFixtures.SPRIG, onDelete = {}) },
)

/** Screenshots and fit checks of Weekly goals and its setup screen for one screen size; [prefix] names it. */
class WeeklyGoalsShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun shot(name: String, fontScale: Float) {
        val content = weeklyGoalScreens.first { it.first == name }.second
        rule.setContent { Shot(fontScale) { content() } }
        // "7 selected" is about the chips and the hint under them: bring them on screen at a large font.
        if (name == "setup_seven") rule.onNodeWithTag("goal_summary").performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/weekly_goals${prefix}_${name}_font$fontScale.png")
    }

    private fun bounds(tag: String, unmerged: Boolean = false): Rect {
        val d = rule.density.density
        val n = rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
        return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
    }

    private fun rootDp(): Size {
        val d = rule.density.density
        val n = rule.onRoot().fetchSemanticsNode()
        return Size(n.size.width / d, n.size.height / d)
    }

    private fun assertOnScreen(at: String, tag: String) {
        val b = bounds(tag)
        val r = rootDp()
        assertTrue("$at: $tag $b is off the screen ($r)", b.left >= -0.5f && b.top >= -0.5f && b.right <= r.width + 0.5f && b.bottom <= r.height + 0.5f)
    }

    private fun assertInWidth(at: String, tag: String, unmerged: Boolean = false) {
        val b = bounds(tag, unmerged)
        assertTrue("$at: $tag $b runs off the screen (${rootDp().width})", b.left >= -0.5f && b.right <= rootDp().width + 0.5f)
    }

    /**
     * At font 1.0, 1.3 and 2.0 in every state: no title splits inside a word and the close or back
     * button is on screen. On the list, every row's text is whole and above the fixed footer, and
     * the button and its count are on screen. On setup, "Save goal" (and "Delete goal") are on
     * screen; scrolled to, the field, every quick pick, "+ Custom", all seven day chips (each at
     * least 48dp wide and tall, side by side on one row), the "n of 7" count, the 7-days hint
     * when it shows and the summary card lie inside the screen's width with no word split; and
     * Sprig, when drawn, does not overlap the title or the line under it.
     */
    fun everyStateFits() {
        val scale = mutableFloatStateOf(1f)
        val current = mutableStateOf(weeklyGoalScreens.first())
        rule.setContent { Shot(scale.floatValue) { current.value.second() } }
        for (f in listOf(1f, 1.3f, 2f)) for (screen in weeklyGoalScreens) {
            scale.floatValue = f
            current.value = screen
            rule.waitForIdle()
            val name = screen.first
            val at = "$name$prefix at font $f"
            assertNoWordSplit(rule, "page_title", at)
            assertOnScreen(at, "page_back")
            if (name.startsWith("list")) {
                assertOnScreen(at, "goals_add")
                assertOnScreen(at, "goals_count")
                assertNoWordSplit(rule, "goals_count", at)
                val items = when (name) { "list" -> WeeklyGoalsFixtures.TWO; "list_limit" -> WeeklyGoalsFixtures.THREE; else -> emptyList() }
                for (g in items) {
                    rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("goal_row_${g.id}"))
                    rule.waitForIdle()
                    assertNoWordSplit(rule, "goal_text_${g.id}", at, unmerged = true)
                    val row = bounds("goal_row_${g.id}")
                    assertTrue("$at: row ${g.text} $row runs under the footer", row.bottom <= bounds("goals_add").top)
                    assertInWidth(at, "goal_row_${g.id}")
                }
            } else {
                assertOnScreen(at, "goal_save")
                if (name == "setup_edit") assertOnScreen(at, "goal_delete")
                rule.onNodeWithTag("page_title").performScrollTo()
                rule.waitForIdle()
                assertNoWordSplit(rule, "goal_subtitle", at)
                if (rule.onAllNodesWithTag("goal_sprig").fetchSemanticsNodes().isNotEmpty()) {
                    val sprig = bounds("goal_sprig")
                    assertFalse("$at: Sprig overlaps the title", sprig.overlaps(bounds("page_title")))
                    assertFalse("$at: Sprig overlaps the subtitle", sprig.overlaps(bounds("goal_subtitle")))
                    assertInWidth(at, "goal_sprig")
                }
                rule.onNodeWithTag("goal_name").performScrollTo()
                assertInWidth(at, "goal_name")
                for (p in WeeklyGoalDraft.QUICK_PICKS) {
                    rule.onNodeWithTag("goal_pick_$p").performScrollTo()
                    rule.waitForIdle()
                    assertInWidth(at, "goal_pick_$p")
                }
                rule.onNodeWithTag("goal_custom").performScrollTo()
                assertInWidth(at, "goal_custom")
                rule.onNodeWithTag("goal_days").performScrollTo()
                rule.waitForIdle()
                assertNoWordSplit(rule, "goal_days_label", at)
                assertInWidth(at, "goal_days_count")
                val chips = WeeklyGoalDraft.DAY_CHOICES.map { bounds("goal_day_$it") }
                chips.forEachIndexed { i, b ->
                    assertTrue("$at: day ${i + 1} $b is off the screen", b.left >= -0.5f && b.right <= rootDp().width + 0.5f)
                    assertTrue("$at: day ${i + 1} is ${b.width} x ${b.height}dp", b.width >= 47.9f && b.height >= 47.9f)
                    assertTrue("$at: day ${i + 1} is not on the first chip's row", b.top == chips[0].top)
                    if (i > 0) assertTrue("$at: day ${i + 1} overlaps day $i", b.left >= chips[i - 1].right - 0.5f)
                }
                if (name == "setup_seven") {
                    rule.onNodeWithTag("goal_seven_hint").performScrollTo()
                    rule.waitForIdle()
                    assertNoWordSplit(rule, "goal_seven_hint", at)
                    assertInWidth(at, "goal_seven_hint")
                } else {
                    rule.onNodeWithTag("goal_seven_hint").assertDoesNotExist()
                }
                rule.onNodeWithTag("goal_summary").performScrollTo()
                rule.waitForIdle()
                assertInWidth(at, "goal_summary")
                assertNoWordSplit(rule, "goal_summary_title", at, unmerged = true)
                assertNoWordSplit(rule, "goal_summary_body", at, unmerged = true)
                assertTrue("$at: the summary runs under the footer", bounds("goal_summary").bottom <= bounds("goal_save").top + 0.5f)
            }
        }
    }
}
