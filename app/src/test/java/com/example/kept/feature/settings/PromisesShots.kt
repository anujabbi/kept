package com.example.kept.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.example.kept.core.domain.HabitIconRules
import com.example.kept.core.domain.PromiseItem
import com.example.kept.core.domain.PromiseListCopy
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue

object PromisesFixtures {
    private fun habit(id: Long, title: String, amount: String, order: Int) = PromiseItem(id, null, title, HabitIconRules.forName(title), amount, order)
    private fun goal(id: Long, title: String, amount: String, days: Int, order: Int) = PromiseItem(id, days, title, HabitIconRules.forName(title), amount, order)

    /** The mockup's six (#236): 4 every day, 2 weekly, in the person's order. */
    val SIX: List<PromiseItem> = listOf(
        habit(1, "Read", PromiseListCopy.habitAmount(false, 10, "pages"), 0),
        goal(1, "Exercise", PromiseListCopy.goalAmount("Exercise", 30, "min"), 4, 1),
        habit(2, "Drink water", PromiseListCopy.habitAmount(false, 8, "glasses"), 2),
        habit(3, "Tidy room", PromiseListCopy.habitAmount(true, 1, "photo"), 3),
        goal(2, "Study", PromiseListCopy.goalAmount("Study", 45, "min"), 2, 4),
        habit(4, "Practice guitar", PromiseListCopy.habitAmount(false, 20, "min"), 5),
    )

    /** Both sides full: ten habits and three weekly goals, so Add gives way to the limit line. */
    val FULL: List<PromiseItem> = SIX + listOf(
        habit(5, "Journal", PromiseListCopy.habitAmount(false, 2, "pages"), 6),
        habit(6, "Meditate", PromiseListCopy.habitAmount(false, 10, "min"), 7),
        habit(7, "Call grandma", PromiseListCopy.habitAmount(false, 1, "times"), 8),
        habit(8, "Push-ups", PromiseListCopy.habitAmount(false, 3, "times"), 9),
        habit(9, "Stretch", PromiseListCopy.habitAmount(false, 10, "min"), 10),
        habit(10, "Walk the dog", PromiseListCopy.habitAmount(false, 1, "times"), 11),
        goal(3, "Swim", PromiseListCopy.goalAmount("Swim", 30, "min"), 1, 12),
    )
}

/** Every state, by golden name. */
internal val promisesScreens: List<Pair<String, @Composable () -> Unit>> = listOf(
    "list" to { PromisesContent(PromisesFixtures.SIX, {}, {}, {}) },
    "full" to { PromisesContent(PromisesFixtures.FULL, {}, {}, {}) },
)

/** Screenshots and fit checks of Your promises (#236) for one screen size; [prefix] names it. */
class PromisesShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun shot(name: String, fontScale: Float) {
        val content = promisesScreens.first { it.first == name }.second
        rule.setContent { Shot(fontScale) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/promises${prefix}_${name}_font$fontScale.png")
    }

    /** The list scrolled to its end: the last card, Add a habit and the count. */
    fun shotEnd(name: String, fontScale: Float) {
        val content = promisesScreens.first { it.first == name }.second
        rule.setContent { Shot(fontScale) { content() } }
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("promises_count"))
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/promises${prefix}_${name}_end_font$fontScale.png")
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

    private fun assertOnScreen(at: String, tag: String, unmerged: Boolean = false) {
        val b = bounds(tag, unmerged)
        val r = rootDp()
        assertTrue("$at: $tag $b is off the screen ($r)", b.left >= -0.5f && b.top >= -0.5f && b.right <= r.width + 0.5f && b.bottom <= r.height + 0.5f)
    }

    /**
     * At font 1.0, 1.3 and 2.0 in every state: no title splits inside a word, and every card's name,
     * amount and chip, the Add row (or the limit line) and the count are whole and on screen once
     * scrolled to.
     */
    fun everyStateFits() {
        val scale = mutableFloatStateOf(1f)
        val current = mutableStateOf(promisesScreens.first())
        rule.setContent { Shot(scale.floatValue) { current.value.second() } }
        for (f in listOf(1f, 1.3f, 2f)) for (screen in promisesScreens) {
            scale.floatValue = f
            current.value = screen
            rule.waitForIdle()
            val name = screen.first
            val at = "$name$prefix at font $f"
            // From font 1.5 the title scrolls with the list.
            if (f >= 1.5f) rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("page_title"))
            rule.waitForIdle()
            assertNoWordSplit(rule, "page_title", at)
            assertOnScreen(at, "page_back")
            val items = if (name == "list") PromisesFixtures.SIX else PromisesFixtures.FULL
            for (p in items) {
                val tag = "promise_${p.key}"
                rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag(tag))
                rule.waitForIdle()
                assertNoWordSplit(rule, "${tag}_title", at, unmerged = true)
                assertNoWordSplit(rule, "${tag}_detail", at, unmerged = true)
                assertOnScreen(at, tag)
                assertOnScreen(at, "${tag}_chip", unmerged = true)
            }
            val end = if (name == "list") "promises_add" else "promises_full"
            rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("promises_count"))
            rule.waitForIdle()
            assertOnScreen(at, end)
            assertOnScreen(at, "promises_count")
            assertNoWordSplit(rule, "promises_count", at)
            if (name == "full") assertNoWordSplit(rule, "promises_full", at)
        }
    }
}
