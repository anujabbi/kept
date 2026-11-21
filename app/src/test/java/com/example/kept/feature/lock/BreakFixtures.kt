package com.example.kept.feature.lock

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.data.LockState
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.SprigState
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.example.kept.feature.home.HomeFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Break states shaped like `A4-Break`: Bloom (the sweater), a 12 day streak, level 4. */
internal object BreakFixtures {
    /** [breaksUsed] this week before this one; 3 makes this the fourth, the last resort. */
    fun state(breaksUsed: Int = 1, streak: Int = 12, level: Int = 4, breakMinutes: Int = 30): LockUi = LockUi(
        loaded = true,
        lock = LockState(
            Settings(onboardingDone = true, breakDurationMin = breakMinutes), HomeFixtures.habits(2, 0, 0),
            breakActiveUntil = null, breaksUsedThisWeek = breaksUsed, userExceptions = emptySet(), unprotectedToday = false,
        ),
        sprig = SprigState(level = level, streakDays = streak, bestStreak = streak),
        lockActive = true,
        blockedStillLocked = true,
    )

    val NORMAL = state()
    val LAST_RESORT = state(breaksUsed = 3)
}

internal fun ComposeContentTestRule.breakRect(tag: String, unmerged: Boolean = false): Rect {
    val d = density.density
    val n = onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
    return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
}

/**
 * Sprig's drawn body inside its box, in 120ths of the box, as three nested rectangles under the
 * rounded top (`M16 160 V84 C16 52 34 34 60 34 …`): text may sit inside the box's empty corners
 * but never on the body itself.
 */
private val BODY = listOf(Rect(16f, 60f, 104f, 120f), Rect(28f, 47f, 92f, 120f), Rect(40f, 37f, 80f, 120f))

private fun bodyRects(box: Rect): List<Rect> = BODY.map {
    val k = box.width / 120f
    Rect(box.left + it.left * k, box.top + it.top * k, box.left + it.right * k, box.top + it.bottom * k)
}

/**
 * At font 1.0, 1.3 and 2.0, normal and last resort, at 60 and 0 seconds: the headline and the
 * plea never break inside a word; neither touches Sprig's body; the bubble clears the headline;
 * Sprig rises over the hill (top above the crest) and its box bottom is behind the hill at both
 * lower corners; the clock is one line and never cut; both buttons are whole and on screen.
 */
internal fun assertBreakFits(rule: ComposeContentTestRule, current: MutableState<LockUi>, scale: MutableFloatState, left: MutableIntState) {
    for (f in listOf(1f, 1.3f, 2f)) for (s in listOf(BreakFixtures.NORMAL, BreakFixtures.LAST_RESORT)) for (secs in listOf(60, 0)) {
        scale.floatValue = f
        current.value = s
        left.intValue = secs
        rule.waitForIdle()
        val at = "${if (s.overCap) "last resort" else "normal"} ${secs}s at $f"
        val root = rule.onRoot().fetchSemanticsNode().size.let { Size(it.width / rule.density.density, it.height / rule.density.density) }
        assertNoWordSplit(rule, "break_headline", at)
        assertNoWordSplit(rule, "break_plea", at)
        val headline = rule.breakRect("break_headline")
        val bubble = rule.breakRect("break_bubble")
        val sprig = rule.breakRect("break_sprig")
        for (b in bodyRects(sprig)) {
            assertTrue("$at: headline $headline is on Sprig's body $b", !headline.overlaps(b))
            assertTrue("$at: bubble $bubble is on Sprig's body $b", !bubble.overlaps(b))
        }
        assertTrue("$at: bubble $bubble overlaps the headline $headline", !bubble.overlaps(headline))
        assertTrue("$at: bubble runs off the screen", bubble.left >= 0f && bubble.right <= root.width)
        val crest = rule.breakRect("break_crest", unmerged = true).top
        assertTrue("$at: bubble ends below the crest", bubble.bottom <= crest)
        val a = root.width / 2 + 60f
        fun hillAt(x: Float): Float {
            val k = ((x - root.width / 2) / a).coerceIn(-1f, 1f)
            return crest + 48f - 48f * kotlin.math.sqrt(1f - k * k)
        }
        for (x in listOf(sprig.left, sprig.right.coerceAtMost(root.width))) {
            assertTrue("$at: Sprig's bottom ${sprig.bottom} is not below the hill (${hillAt(x)}) at x=$x", sprig.bottom >= hillAt(x) + 1f)
        }
        assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)

        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("break_countdown").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        val clock = result.single()
        assertEquals("$at: the clock wraps", 1, clock.lineCount)
        assertEquals("$at: the clock is cut", clock.layoutInput.text.length, clock.getLineEnd(0, visibleEnd = true))
        assertTrue("$at: the clock is wider than the screen", clock.size.width / rule.density.density <= root.width - 48f + 0.5f)

        for (tag in listOf("break_go_back", "unlock_anyway")) {
            val b = rule.breakRect(tag)
            assertTrue("$at: $tag $b is cut off (screen $root)", b.top >= 0f && b.bottom <= root.height + 0.5f && b.height >= 47.9f)
        }
    }
}

private fun scrollRangeDp(rule: ComposeContentTestRule): Float {
    val range = rule.onNodeWithTag("break_body").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
    return range.maxValue() / rule.density.density
}

private fun setFont1(rule: ComposeContentTestRule) {
    rule.setContent {
        com.example.kept.core.ui.shots.Shot(1f) { BreakLockScreen(BreakFixtures.NORMAL, secondsLeft = 60, onCancel = {}, onConfirm = {}) }
    }
    rule.waitForIdle()
}

/** At font 1.0 nothing scrolls and the clock ends above the buttons. */
internal fun assertFitsWithoutScroll(rule: ComposeContentTestRule) {
    setFont1(rule)
    assertEquals("the body scrolls", 0f, scrollRangeDp(rule), 0.5f)
    assertTrue("the clock runs into the buttons", rule.breakRect("break_countdown").bottom <= rule.breakRect("break_go_back").top)
}

/**
 * At font 1.0 the body scrolls by at most [maxScrollDp]; "What it costs" and its rows show without
 * scrolling, and the clock can be scrolled into view above the buttons.
 */
internal fun assertScrollsAtMost(rule: ComposeContentTestRule, maxScrollDp: Float) {
    setFont1(rule)
    val range = scrollRangeDp(rule)
    assertTrue("the body scrolls ${range}dp", range <= maxScrollDp)
    val buttons = rule.breakRect("break_go_back").top
    for (k in listOf("wilt", "streak", "breaks")) {
        assertTrue("$k is under the buttons", rule.breakRect("break_cost_$k").bottom <= buttons)
    }
    rule.onNodeWithTag("break_countdown").performScrollTo()
    rule.waitForIdle()
    assertTrue("the clock cannot be scrolled above the buttons", rule.breakRect("break_countdown").bottom <= buttons + 0.5f)
}
