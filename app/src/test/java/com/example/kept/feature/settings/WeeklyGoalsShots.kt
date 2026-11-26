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
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

object WeeklyGoalsFixtures {
    private fun item(id: Long, title: String, days: Int, target: String? = null) =
        WeeklyGoalItem(id, WeeklyGoalCopy.listRow(title, target, days), HabitIconRules.forName(title))

    /** The issue's example with its target, and one set before targets (story #205). */
    val TWO: List<WeeklyGoalItem> = listOf(item(1, "Exercise", 4, WeeklyGoalCopy.target(30, "min")), item(2, "Read", 1))

    /** At the limit, with a name as long as the field allows. */
    val THREE: List<WeeklyGoalItem> = TWO + item(3, "Practice guitar with my band", 7)
}

/** Every state the issue lists, by golden name. */
internal val weeklyGoalScreens: List<Pair<String, @Composable () -> Unit>> = listOf(
    "list" to { WeeklyGoalsContent(WeeklyGoalsFixtures.TWO, {}, {}, {}) },
    "list_empty" to { WeeklyGoalsContent(emptyList(), {}, {}, {}) },
    "list_limit" to { WeeklyGoalsContent(WeeklyGoalsFixtures.THREE, {}, {}, {}) },
)

/** Screenshots and fit checks of Weekly goals for one screen size; [prefix] names it. */
class WeeklyGoalsShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun shot(name: String, fontScale: Float) {
        val content = weeklyGoalScreens.first { it.first == name }.second
        rule.setContent { Shot(fontScale) { content() } }
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
     * At font 1.0, 1.3 and 2.0 in every state: no title splits inside a word and the back button
     * is on screen; every row's text is whole and above the fixed footer, and the button and its
     * count are on screen. A goal is set on the promise (#218): `PromiseShots`.
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
        }
    }
}
