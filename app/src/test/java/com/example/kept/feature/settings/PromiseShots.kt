package com.example.kept.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.PromiseCopy
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseLimits
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.ui.shots.Shot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue

/**
 * The promise's sentence as it reads on screen, built from the sentence's children in order:
 * words, each blank's value (or the typed activity, or the placeholder), and the punctuation.
 */
fun promiseSentence(rule: ComposeContentTestRule): String {
    val parts = rule.onNodeWithTag("promise_sentence").onChildren().fetchSemanticsNodes().mapNotNull { n ->
        val c = n.config
        val edit = c.getOrNull(SemanticsProperties.EditableText)?.text
        when {
            edit != null -> edit.ifEmpty { PromiseCopy.PLACEHOLDER }
            c.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.endsWith(" list") == true -> null
            else -> c.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        }
    }
    return parts.joinToString(" ").replace(" ,", ",").replace(" .", ".").replace(PromiseCopy.NBSP, ' ')
}

internal object PromiseFixtures {
    /** The issue's example: "I will exercise for 30 minutes, 4 days a week." */
    val EXERCISE = PromiseRules.fresh(emptyList(), days = 4)

    /** "I will read 10 pages, every day." */
    val READ = PromiseDraft("Read").pick(PromiseRules.PRESETS[1])

    /** A saved goal of its own, "Swim", 2 days a week: a blank like the presets until "Something else…". */
    val SWIM = PromiseDraft("Swim", 30, "min", days = 2, custom = true)

    /** "Add your own": "Something else…" with nothing typed yet, so the activity is the text field. */
    val TYPING = PromiseDraft("", 30, "min", days = 2).somethingElse()

    /** A saved photo habit. */
    val TIDY = PromiseRules.ofHabit("Tidy room", ProofType.PHOTO, 1, "photo")

    /** Three weekly goals already: "Meditate, 20 minutes, every day" with the weekly choices off. */
    val MEDITATE = PromiseDraft("Meditate", 20, "min", custom = true)
    val AT_LIMIT = PromiseLimits.new(daily = 2, weekly = 3)
}

/** Every promise state with a golden, by name: the page, and the list to open first (if any). */
internal val promiseScreens: List<Triple<String, String?, @Composable () -> Unit>> = listOf(
    Triple("exercise", null) { PromisePage(PromiseFixtures.EXERCISE, PromiseLimits(), { _, _ -> }, {}) },
    Triple("read_daily", null) { PromisePage(PromiseFixtures.READ, PromiseLimits(), { _, _ -> }, {}) },
    Triple("activity_list", "activity") { PromisePage(PromiseFixtures.EXERCISE, PromiseLimits(), { _, _ -> }, {}) },
    Triple("days_list", "days") { PromisePage(PromiseFixtures.EXERCISE, PromiseLimits(), { _, _ -> }, {}) },
    Triple("unit_list", "unit") { PromisePage(PromiseFixtures.EXERCISE, PromiseLimits(), { _, _ -> }, {}) },
    Triple("limit", "days") { PromisePage(PromiseFixtures.MEDITATE, PromiseFixtures.AT_LIMIT, { _, _ -> }, {}) },
    Triple("custom", null) { PromisePage(PromiseFixtures.SWIM, PromiseLimits(), { _, _ -> }, {}) },
    Triple("typing", null) { PromisePage(PromiseFixtures.TYPING, PromiseLimits(), { _, _ -> }, {}) },
    Triple("photo", null) { PromisePage(PromiseFixtures.TIDY, PromiseLimits.savedHabit(daily = 2, weekly = 0), { _, _ -> }, {}, remove = PromiseRemove(HabitsCopy.REMOVE, true) {}) },
    Triple("edit_last", null) {
        PromisePage(PromiseFixtures.READ, PromiseLimits.savedHabit(daily = 1, weekly = 0), { _, _ -> }, {}, remove = PromiseRemove(HabitsCopy.REMOVE, false, HabitsCopy.KEEP_ONE) {})
    },
    Triple("cheer", "done") { PromisePage(PromiseFixtures.EXERCISE, PromiseLimits(), { _, saved -> saved() }, {}, form = SprigForm.BLOOM) },
)

/** Goldens and fit checks of the promise (#218) for one screen size; [prefix] names it. */
class PromiseShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    private fun open(list: String?) {
        when (list) {
            null -> Unit
            "done" -> rule.onNodeWithTag("promise_done").performClick()
            else -> rule.onNodeWithTag("promise_blank_$list").performClick()
        }
        rule.waitForIdle()
    }

    fun shot(name: String, fontScale: Float) {
        val (_, list, content) = promiseScreens.first { it.first == name }
        rule.setContent { Shot(fontScale) { content() } }
        rule.waitForIdle()
        open(list)
        rule.onRoot().captureRoboImage("src/test/screenshots/promise${prefix}_${name}_font$fontScale.png")
    }

    private fun bounds(tag: String): Rect {
        val d = rule.density.density
        val n = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
    }

    private fun rootDp(): Size {
        val d = rule.density.density
        val n = rule.onRoot().fetchSemanticsNode()
        return Size(n.size.width / d, n.size.height / d)
    }

    /**
     * At font 1.0, 1.3 and 2.0 in every state: every word and blank of the sentence lies inside
     * the screen's width and above Done, no two of them overlap, Cancel and Done are on screen,
     * and an open list lies inside the screen.
     */
    fun everyStateFits() {
        val scale = mutableFloatStateOf(1f)
        val current = mutableStateOf(promiseScreens.first())
        rule.setContent { Shot(scale.floatValue) { androidx.compose.runtime.key(current.value.first, scale.floatValue) { current.value.third() } } }
        for (f in listOf(1f, 1.3f, 2f)) for (screen in promiseScreens.filter { it.second != "done" }) {
            scale.floatValue = f
            current.value = screen
            rule.waitForIdle()
            open(screen.second)
            val at = "${screen.first}$prefix at font $f"
            val root = rootDp()
            val done = bounds("promise_done")
            assertTrue("$at: Done $done is off the screen", done.bottom <= root.height + 0.5f && done.top >= 0f)
            val cancel = bounds("promise_cancel")
            assertTrue("$at: Cancel is off the screen", cancel.right <= root.width + 0.5f && cancel.top >= 0f)
            val d = rule.density.density
            val pieces = rule.onNodeWithTag("promise_sentence").onChildren().fetchSemanticsNodes().map {
                Rect(it.positionInRoot / d, Size(it.size.width / d, it.size.height / d))
            }
            pieces.forEachIndexed { i, b ->
                assertTrue("$at: piece $i $b runs off the screen", b.left >= -0.5f && b.right <= root.width + 0.5f)
                assertTrue("$at: piece $i $b runs under Done", b.bottom <= done.top + 0.5f)
                pieces.drop(i + 1).forEach { o -> assertTrue("$at: pieces overlap $b $o", !b.deflate(0.5f).overlaps(o.deflate(0.5f))) }
            }
            if (screen.second != null) {
                val list = bounds("promise_list_${screen.second}")
                assertTrue("$at: the list $list is off the screen", list.left >= 0f && list.right <= root.width + 0.5f && list.bottom <= root.height + 0.5f)
            }
        }
    }
}
