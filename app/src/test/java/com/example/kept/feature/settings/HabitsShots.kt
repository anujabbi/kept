package com.example.kept.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.HabitIconRules
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue

object HabitsFixtures {
    private fun item(id: Long, title: String, proof: ProofType, target: Int, unit: String) =
        HabitItem(id, title, HabitsCopy.detail(proof, target, unit), HabitIconRules.forName(title))

    /** The design's six (`A8-Habits` script). */
    val SIX: List<HabitItem> = listOf(
        item(1, "Exercise", ProofType.MANUAL, 30, "min"),
        item(2, "Read", ProofType.MANUAL, 10, "pages"),
        item(3, "Practice guitar", ProofType.MANUAL, 20, "min"),
        item(4, "Study", ProofType.MANUAL, 45, "min"),
        item(5, "Tidy room", ProofType.PHOTO, 1, "photo"),
        item(6, "Drink water", ProofType.MANUAL, 8, "glasses"),
    )

    /** At the limit: four more. */
    val TEN: List<HabitItem> = SIX + listOf(
        item(7, "Journal", ProofType.MANUAL, 2, "pages"),
        item(8, "Meditate", ProofType.MANUAL, 10, "min"),
        item(9, "Call grandma", ProofType.MANUAL, 1, "times"),
        item(10, "Push-ups", ProofType.MANUAL, 3, "times"),
    )
}

/** Every state the issue lists, by golden name. */
internal val habitsScreens: List<Pair<String, @Composable () -> Unit>> = listOf(
    "list" to { HabitsContent(HabitsFixtures.SIX, {}, {}, {}) },
    "list_limit" to { HabitsContent(HabitsFixtures.TEN, {}, {}, {}) },
)

/** Screenshots and fit checks of Your habits for one screen size; [prefix] names it. */
class HabitsShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun shot(name: String, fontScale: Float) {
        val content = habitsScreens.first { it.first == name }.second
        rule.setContent { Shot(fontScale) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/habits${prefix}_${name}_font$fontScale.png")
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

    private fun layout(tag: String, unmerged: Boolean = false): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        return result.single()
    }

    /** [tag] lies wholly on screen. */
    private fun assertOnScreen(at: String, tag: String, unmerged: Boolean = false) {
        val b = bounds(tag, unmerged)
        val r = rootDp()
        assertTrue("$at: $tag $b is off the screen ($r)", b.left >= -0.5f && b.top >= -0.5f && b.right <= r.width + 0.5f && b.bottom <= r.height + 0.5f)
    }

    /** [tag]'s text is on one line and not cut off. */
    private fun assertOneLine(at: String, tag: String, unmerged: Boolean = false) {
        val l = layout(tag, unmerged)
        assertTrue("$at: $tag wraps", l.lineCount == 1)
        assertTrue("$at: $tag is cut off", l.multiParagraph.intrinsics.maxIntrinsicWidth <= l.size.width + 0.5f)
    }

    /**
     * At font 1.0, 1.3 and 2.0 in every state: no title splits inside a word, every row's title and
     * detail are whole and on screen once scrolled to, and the fixed footer (the button and its
     * count) is on screen. Add and edit are the promise (#218): `PromiseShots`.
     */
    fun everyStateFits() {
        val scale = mutableFloatStateOf(1f)
        val current = mutableStateOf(habitsScreens.first())
        rule.setContent { Shot(scale.floatValue) { current.value.second() } }
        for (f in listOf(1f, 1.3f, 2f)) for (screen in habitsScreens) {
            scale.floatValue = f
            current.value = screen
            rule.waitForIdle()
            val name = screen.first
            val at = "$name$prefix at font $f"
            assertNoWordSplit(rule, "page_title", at)
            assertOnScreen(at, "page_back")
            assertOnScreen(at, "habits_add")
            assertOnScreen(at, "habits_count")
            assertNoWordSplit(rule, "habits_count", at)
            val items = if (name == "list") HabitsFixtures.SIX else HabitsFixtures.TEN
            for (h in items) {
                rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("habit_row_${h.id}"))
                rule.waitForIdle()
                assertNoWordSplit(rule, "habit_title_${h.id}", at, unmerged = true)
                assertNoWordSplit(rule, "habit_detail_${h.id}", at, unmerged = true)
                val row = bounds("habit_row_${h.id}")
                assertTrue("$at: row ${h.title} $row runs under the footer", row.bottom <= bounds("habits_add").top)
                assertTrue("$at: row ${h.title} is off the screen", row.left >= 0f && row.right <= rootDp().width + 0.5f)
            }
        }
    }
}
