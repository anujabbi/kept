package com.example.kept.feature.recap

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.ui.shots.assertNoWordSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate

/**
 * The six outcomes shaped like `A5-Recap`'s script: 2 habits; a 12 day streak (Bloom, 5 of the 7
 * days to Thicket) where it is kept, 0 (Sprig, toward Bud) where it reset. Plus the final form.
 */
internal object RecapFixtures {
    private val DATE = LocalDate.of(2026, 11, 20)

    private fun ui(
        done: Int, breaks: Int = 0, streak: Int,
        counted: Boolean = false, shield: Boolean = false, unprotected: Boolean = false, writtenOff: Boolean = false,
        reset: Boolean = false,
        weeklyDue: Int = 0, weeklyMissed: String = "",
    ) = RecapUi(
        DayRecordEntity(
            date = DATE.toString(), habitsDone = done, habitsTotal = 2, lockedMillis = 3_600_000, breaksUsed = breaks,
            unprotected = unprotected, broken = breaks > 0, writtenOff = writtenOff, countedForStreak = counted,
            shieldConsumed = shield, formId = SprigForm.forStreak(streak).id, streakEnd = streak, finalized = true,
            weeklyDue = weeklyDue, weeklyDone = 0, weeklyMissed = weeklyMissed,
        ),
        DATE,
        streakReset = reset,
    )

    val KEPT = ui(done = 2, streak = 12, counted = true)
    val SHIELD = ui(done = 1, streak = 12, shield = true)
    val LOCK_OFF = ui(done = 2, streak = 12, unprotected = true)
    val SLIPPED = ui(done = 1, breaks = 1, streak = 0, reset = true)
    val TOO_LATE = ui(done = 2, streak = 0, reset = true)
    val WRITTEN_OFF = ui(done = 1, breaks = 4, streak = 0, writtenOff = true, reset = true)
    val FINAL = ui(done = 2, streak = 120, counted = true)

    /** Both habits done; the day was missed only by a weekly goal's must-do session (story #176). */
    val WEEKLY = ui(done = 2, streak = 0, reset = true, weeklyDue = 1, weeklyMissed = "Exercise")

    /** The same day saved by the shield. */
    val WEEKLY_SHIELD = ui(done = 2, streak = 12, shield = true, weeklyDue = 1, weeklyMissed = "Exercise")

    /**
     * Two goals due and missed, with names as long as a goal's can be (28 characters): the line
     * runs to five lines, so on a short screen this one page scrolls at font 1.0.
     */
    val WEEKLY_LONG = ui(done = 2, streak = 12, shield = true, weeklyDue = 2, weeklyMissed = "Practice guitar with friends\nRead one chapter of my novel")

    /** Where the fill ends for [ui]: 5 of 7 at day 12, never less than the 16dp minimum. */
    fun fraction(ui: RecapUi): Float = if (ui.record!!.streakEnd == 12) 5f / 7 else 0f

    val ALL = listOf(
        "kept" to KEPT, "shield" to SHIELD, "lockoff" to LOCK_OFF,
        "slipped" to SLIPPED, "late" to TOO_LATE, "writtenoff" to WRITTEN_OFF, "final" to FINAL,
        "weekly" to WEEKLY, "weekly_shield" to WEEKLY_SHIELD,
    )

    /** Everything that must fit at every font scale: [ALL], and the longest weekly line. */
    val FITS = ALL + ("weekly_long" to WEEKLY_LONG)
}

internal fun ComposeContentTestRule.recapRect(tag: String, unmerged: Boolean = false): Rect {
    val d = density.density
    val n = onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
    return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
}

internal fun ComposeContentTestRule.recapText(tag: String): TextLayoutResult {
    val result = mutableListOf<TextLayoutResult>()
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
    return result.single()
}

/** How far the page scrolls, in dp. */
internal fun ComposeContentTestRule.recapScrollDp(): Float {
    val range = onNodeWithTag("recap_page").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    return range.maxValue() / density.density
}

/**
 * For every outcome and the final form, at font 1.0, 1.3 and 2.0: the headline and the line never
 * break inside a word; Sprig's box clears the text, rises over the crest, and its bottom is behind
 * the hill at both lower corners; the stats start on the cream, below the hill's edge, and their
 * values are one line; the card ends above the buttons; both buttons are whole and 48dp or taller.
 */
internal fun assertRecapFits(rule: ComposeContentTestRule, current: MutableState<RecapUi>, scale: MutableFloatState, widthDp: Float) {
    for (f in listOf(1f, 1.3f, 2f)) for ((name, ui) in RecapFixtures.FITS) {
        scale.floatValue = f
        current.value = ui
        rule.waitForIdle()
        val at = "$name at $f"
        assertNoWordSplit(rule, "recap_headline", at)
        assertNoWordSplit(rule, "recap_line", at)
        val line = rule.recapRect("recap_line", unmerged = true)
        val sprig = rule.recapRect("recap_sprig")
        assertTrue("$at: Sprig's box $sprig is on the line $line", sprig.top >= line.bottom)
        val crest = rule.recapRect("recap_crest", unmerged = true).top
        assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
        val a = widthDp / 2 + 60f
        fun hillAt(x: Float): Float {
            val k = ((x - widthDp / 2) / a).coerceIn(-1f, 1f)
            return crest + 48f - 48f * kotlin.math.sqrt(1f - k * k)
        }
        for (x in listOf(sprig.left, sprig.right)) {
            assertTrue("$at: Sprig's bottom ${sprig.bottom} is not behind the hill (${hillAt(x)}) at x=$x", sprig.bottom >= hillAt(x) + 1f)
        }
        val stats = rule.recapRect("recap_stats")
        assertTrue("$at: the stats ${stats.top} start on the hill's slope (crest $crest)", stats.top >= crest + 48f)
        for (tag in listOf("recap_done", "recap_broken")) {
            assertEquals("$at: $tag wraps", 1, rule.recapText(tag).lineCount)
            val t = rule.recapText(tag)
            assertEquals("$at: $tag is cut", t.layoutInput.text.length, t.getLineEnd(0, visibleEnd = true))
        }
        val card = rule.recapRect("recap_progress")
        for (tag in listOf("recap_second", "recap_done_button")) {
            val b = rule.recapRect(tag)
            assertTrue("$at: the card ${card.bottom} runs into $tag ${b.top}", card.bottom <= b.top)
            assertTrue("$at: $tag $b is too small or off the page", b.height >= 47.9f && b.left >= 0f && b.right <= widthDp + 0.5f)
        }
        val doneLabel = rule.recapRect("recap_done_label", unmerged = true)
        val brokenLabel = rule.recapRect("recap_broken_label", unmerged = true)
        assertEquals("$at: the stat labels start on different lines", doneLabel.top, brokenLabel.top, 0.5f)
        if (ui.record!!.streakEnd < 100) {
            val bar = rule.recapRect("recap_bar")
            assertTrue("$at: the bar is squeezed to ${bar.width}dp", bar.width >= 60f)
            // "Bud in 3 days" and "Day 0 of 3": 8dp apart on one line, or the second under the first.
            val next = rule.recapRect("recap_next", unmerged = true)
            val count = rule.recapRect("recap_count", unmerged = true)
            assertTrue(
                "$at: \"${rule.recapText("recap_next").layoutInput.text}\" $next touches \"${rule.recapText("recap_count").layoutInput.text}\" $count",
                count.left - next.right >= 8f || count.top >= next.bottom,
            )
            assertTrue("$at: the day count runs off the card", count.right <= widthDp - 36f + 0.5f)
        }
    }
}
