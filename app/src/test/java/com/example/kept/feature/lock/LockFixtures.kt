package com.example.kept.feature.lock

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.data.LockState
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.SprigState
import com.example.kept.feature.home.HomeFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Lock states shaped like `A3-Lock`: Bloom (the sweater), a 12 day streak, the design's habits. */
internal object LockFixtures {
    /**
     * [total] habits, the first [done] ticked in time. With everything done the lock is no longer
     * active, as `LockPolicy` says, so the state is the open one.
     */
    fun state(total: Int, done: Int): LockUi {
        val t = HomeFixtures.habits(total, done)
        val locked = done < total
        return LockUi(
            loaded = true,
            lock = LockState(Settings(onboardingDone = true), t, breakActiveUntil = null, breaksUsedThisWeek = 0, userExceptions = emptySet(), unprotectedToday = false),
            sprig = SprigState(level = 4, streakDays = 12, bestStreak = 12),
            lockActive = locked,
            blockedStillLocked = locked,
        )
    }
}

/** Every state where Sprig stands highest or lowest, and where the text is longest. */
internal val LOCK_STATES = listOf(1 to 0, 1 to 1, 2 to 0, 2 to 1, 2 to 2, 10 to 0, 10 to 2, 10 to 9, 10 to 10)

private fun ComposeContentTestRule.rectDp(tag: String, unmerged: Boolean = false): Rect {
    val d = density.density
    val n = onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
    return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
}

/**
 * At font 1.0 and 2.0, in every [LOCK_STATES] state and with [labels] as the app name: Sprig's box
 * touches neither the headline nor the line; its bottom is below the hill at both lower corners
 * and its top above the crest (so it rises over the hill, never floats); the footer button(s)
 * end inside the screen; and "Phone" stays on one line.
 */
internal fun assertLockFits(
    rule: ComposeContentTestRule,
    current: MutableState<LockUi>,
    scale: MutableFloatState,
    label: MutableState<String>,
    labels: List<String> = listOf("Chrome", "Instagram", "Google Photos"),
) {
    for (f in listOf(1f, 2f)) for (name in labels) for ((total, done) in LOCK_STATES) {
        scale.floatValue = f
        label.value = name
        current.value = LockFixtures.state(total, done)
        rule.waitForIdle()
        val at = "$name $done/$total at $f"
        val root = rule.onRoot().fetchSemanticsNode().size.let { Size(it.width / rule.density.density, it.height / rule.density.density) }
        val sprig = rule.rectDp("lock_sprig")
        for (tag in listOf("lock_headline", "lock_line")) {
            val text = rule.rectDp(tag)
            assertTrue("$at: Sprig $sprig overlaps $tag $text", !sprig.overlaps(text))
            assertTrue("$at: $tag $text runs off the screen", text.left >= 0f && text.right <= root.width)
        }
        val crest = rule.rectDp("lock_crest", unmerged = true).top
        val a = root.width / 2 + 60f
        fun hillAt(x: Float): Float {
            val k = ((x - root.width / 2) / a).coerceIn(-1f, 1f)
            return crest + 48f - 48f * kotlin.math.sqrt(1f - k * k)
        }
        for (x in listOf(sprig.left, sprig.right)) {
            assertTrue("$at: Sprig's bottom ${sprig.bottom} is not below the hill (${hillAt(x)}) at x=$x", sprig.bottom >= hillAt(x) + 1f)
        }
        assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
        assertTrue("$at: the headline block ends below the crest", rule.rectDp("lock_line").bottom <= crest)
        if (done < total) {
            // "Phone" never breaks inside the word, and is never cut.
            val result = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("Phone", useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
            val r = result.single()
            assertTrue("$at: Phone takes ${r.lineCount} lines", r.lineCount == 1)
            assertEquals("$at: Phone breaks inside the word or is cut", "Phone".length, r.getLineEnd(0, visibleEnd = true))
        }
        val buttons = if (done < total) listOf("emergency_call", "break_lock") else listOf("lock_open_app")
        for (tag in buttons) {
            val b = rule.rectDp(tag)
            assertTrue("$at: $tag $b is cut off (screen ${root.height})", b.bottom <= root.height + 0.5f && b.height >= 47.9f)
        }
    }
}
