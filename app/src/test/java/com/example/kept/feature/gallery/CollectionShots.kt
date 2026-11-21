package com.example.kept.feature.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.data.GalleryCard
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigState
import com.example.kept.core.ui.KeptTab
import com.example.kept.core.ui.KeptTabBar
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate

object CollectionFixtures {
    private val start = LocalDate.of(2026, 8, 1)

    /** Gallery rows for every form passed on the way to a [streak]-day streak. */
    fun cards(streak: Int): List<GalleryCard> = SprigForm.entries
        .filter { it.streakThreshold in 1..streak }
        .mapIndexed { i, f -> GalleryCard(i + 1L, f, start.plusDays(f.streakThreshold.toLong()), f.streakThreshold, seen = true) }

    fun day(streak: Int, cards: List<GalleryCard> = cards(streak)) = GalleryUi(cards, SprigState(streakDays = streak, bestStreak = streak))

    /** The issue's three states: day 0, day 12 (Bloom, as in the design) and day 100. */
    val DAY_0 = day(0)
    val DAY_12 = day(12)
    val DAY_100 = day(100)

    val ALL = listOf("day0" to DAY_0, "day12" to DAY_12, "day100" to DAY_100)
}

/** The Sprig tab as the app shows it: the page, then the fixed tab bar. */
@Composable
internal fun CollectionFrame(s: GalleryUi, onOpenForm: (SprigForm) -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { GalleryContent(s, onOpenForm) }
        KeptTabBar(KeptTab.Sprig, onSelect = {})
    }
}

/**
 * Screenshots and fit checks of the Sprig tab for one screen size ([prefix] names it in the
 * golden), shared by the 412 × 915 and 360 × 640 test classes.
 */
class CollectionShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun shot(name: String, s: GalleryUi, fontScale: Float) {
        rule.setContent { Shot(fontScale) { CollectionFrame(s) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/collection${prefix}_${name}_font$fontScale.png")
    }

    /** The page scrolled to its end: the last row of the grid sits above the tab bar. */
    fun scrolledShot(name: String, s: GalleryUi, fontScale: Float) {
        rule.setContent { Shot(fontScale) { CollectionFrame(s) } }
        rule.onNodeWithTag("collection_form_CELESTIAL").performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/collection${prefix}_${name}_end_font$fontScale.png")
    }

    private fun bounds(tag: String, unmerged: Boolean = false): Rect {
        val d = rule.density.density
        val n = rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
        return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
    }

    private fun layout(tag: String): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        return result.single()
    }

    /**
     * Every state at font 1.0, 1.3 and 2.0: the headline never splits inside a word, the pill
     * never wraps or overflows, Sprig's box touches none of the hero text, and Sprig's bottom is
     * under the hill (the crest is at least 1dp above it, and its lower corners are below the
     * ellipse).
     */
    fun everyStateFits() {
        val scale = mutableFloatStateOf(1f)
        val current = mutableStateOf(CollectionFixtures.DAY_0)
        rule.setContent { Shot(scale.floatValue) { CollectionFrame(current.value) } }
        val d = rule.density.density
        for (f in listOf(1f, 1.3f, 2f)) for ((name, s) in CollectionFixtures.ALL) {
            scale.floatValue = f
            current.value = s
            rule.waitForIdle()
            val at = "$name$prefix at font $f"
            assertNoWordSplit(rule, "collection_headline", at)
            val pill = layout("collection_pill")
            assertTrue("$at: the pill wraps", pill.lineCount == 1)
            assertTrue("$at: the pill is cut off (${pill.multiParagraph.intrinsics.maxIntrinsicWidth} > ${pill.size.width})", pill.multiParagraph.intrinsics.maxIntrinsicWidth <= pill.size.width + 0.5f)
            val sprig = bounds("collection_sprig")
            for (tag in listOf("collection_headline", "collection_day", "collection_pill")) {
                val text = bounds(tag)
                assertFalse("$at: Sprig $sprig overlaps $tag $text", sprig.overlaps(text))
            }
            val width = rule.onRoot().fetchSemanticsNode().size.width / d
            val crest = bounds("collection_crest", unmerged = true).top
            val a = width / 2 + 60f
            fun hillAt(x: Float): Float {
                val k = ((x - width / 2) / a).coerceIn(-1f, 1f)
                return crest + 48f - 48f * kotlin.math.sqrt(1f - k * k)
            }
            for (x in listOf(sprig.left, sprig.right)) {
                assertTrue("$at: Sprig's bottom ${sprig.bottom} is not behind the hill (${hillAt(x)}) at x=$x", sprig.bottom >= hillAt(x) + 1f)
            }
            assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
            // Hero text ends above the crest, so the hill never covers it.
            assertTrue("$at: the pill runs into the hill", bounds("collection_pill").bottom <= crest)
        }
    }
}
