package com.example.kept.feature.lock

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.domain.LockReason
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Lock states for night lockdown (issue #164): the design's Sprig, night holding the phone. */
internal object NightLockFixtures {
    /** [total] habits with [done] ticked; night lockdown holds either way, until [untilMinute]. */
    fun state(total: Int = 2, done: Int = 2, untilMinute: Int = 7 * 60): LockUi {
        val base = LockFixtures.state(total, done)
        val lock = base.lock!!
        return base.copy(
            lock = lock.copy(settings = lock.settings.copy(nightLockEnabled = true, nightUntilMinute = untilMinute)),
            lockActive = true,
            blockedStillLocked = true,
            reason = LockReason.NIGHT,
        )
    }
}

private fun ComposeContentTestRule.rect(tag: String): Rect {
    val d = density.density
    val n = onNodeWithTag(tag).fetchSemanticsNode()
    return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
}

/**
 * At font 1.0, 1.3 and 2.0: no word of the night headline or line is split, Sprig touches neither,
 * the headline block ends above the hill's crest, "Phone" stays on one line, and both footer
 * buttons are whole, on screen and at least 48dp tall.
 */
internal fun assertNightLockFits(rule: ComposeContentTestRule) {
    val scale = mutableFloatStateOf(1f)
    rule.setContent { Shot(scale.floatValue) { LockScreen(NightLockFixtures.state(), "Chrome", LockActions()) } }
    for (f in listOf(1f, 1.3f, 2f)) {
        scale.floatValue = f
        rule.waitForIdle()
        val at = "night lock at font $f"
        val d = rule.density.density
        val root = rule.onRoot().fetchSemanticsNode().size.let { Size(it.width / d, it.height / d) }
        val sprig = rule.rect("lock_sprig")
        for (tag in listOf("lock_headline", "lock_line")) {
            assertNoWordSplit(rule, tag, at)
            val text = rule.rect(tag)
            assertTrue("$at: Sprig $sprig overlaps $tag $text", !sprig.overlaps(text))
            assertTrue("$at: $tag $text runs off the screen", text.left >= 0f && text.right <= root.width)
        }
        val crest = rule.onNodeWithTag("lock_crest", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y / d
        assertTrue("$at: the headline block ends below the crest", rule.rect("lock_line").bottom <= crest)
        assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Phone", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        assertEquals("$at: Phone wraps", 1, result.single().lineCount)
        for (tag in listOf("emergency_call", "break_lock")) {
            val b = rule.rect(tag)
            assertTrue("$at: $tag $b is cut off (screen ${root.height})", b.bottom <= root.height + 0.5f && b.height >= 47.9f)
            assertTrue("$at: $tag $b runs off the side", b.left >= 0f && b.right <= root.width + 0.5f)
        }
    }
}

/**
 * The night lock screen (issue #164, story #168) at 412 x 915dp, font scale 1.0 and 2.0. Goldens:
 * `app/src/test/screenshots/lock_night_*`. Also what it shows and does not show, what its two
 * buttons do, and that it fits and is accessible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class NightLockScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(fontScale: Float) {
        rule.setContent { Shot(fontScale) { LockScreen(NightLockFixtures.state(), "Chrome", LockActions()) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/lock_night_font$fontScale.png")
    }

    @Test fun night() = shot(1f)
    @Test fun night_font2() = shot(2f)

    @Test fun it_says_shutdown_time_and_when_apps_open() {
        rule.setContent { Shot { LockScreen(NightLockFixtures.state(), "Chrome", LockActions()) } }
        rule.onNodeWithTag("lock_headline").assertTextEquals("It's shutdown time.")
        rule.onNodeWithTag("lock_line").assertTextEquals("Apps open at 7:00 am.")
        rule.onNodeWithText("Chrome can wait.").assertDoesNotExist()
        rule.onNodeWithText("Chrome is open.").assertDoesNotExist()
    }

    @Test fun the_line_follows_the_end_time() {
        rule.setContent { Shot { LockScreen(NightLockFixtures.state(untilMinute = 6 * 60 + 30), "Chrome", LockActions()) } }
        rule.onNodeWithTag("lock_line").assertTextEquals("Apps open at 6:30 am.")
    }

    @Test fun there_is_no_habit_list_and_no_open_app_with_habits_done_or_not() {
        for ((total, done) in listOf(2 to 2, 2 to 0, 10 to 3)) {
            val s = NightLockFixtures.state(total, done)
            assertTrue(s.night)
            assertTrue("$done/$total is not the open state", !s.open && !s.shouldDismiss)
        }
        rule.setContent { Shot { LockScreen(NightLockFixtures.state(2, 0), "Chrome", LockActions()) } }
        rule.onNodeWithTag("lock_habit_list").assertDoesNotExist()
        rule.onNodeWithTag("lock_count").assertDoesNotExist()
        rule.onNodeWithText("Today").assertDoesNotExist()
        rule.onNodeWithTag("lock_open_app").assertDoesNotExist()
        rule.onNodeWithTag("emergency_call").assertExists()
        rule.onNodeWithTag("break_lock").assertExists()
    }

    @Test fun phone_and_break_the_lock_work() {
        var phone = 0
        var breaks = 0
        rule.setContent { Shot { LockScreen(NightLockFixtures.state(), "Chrome", LockActions(onPhone = { phone++ }, onBreak = { breaks++ })) } }
        rule.onNodeWithTag("emergency_call").performClick()
        rule.onNodeWithTag("break_lock").performClick()
        assertEquals(1, phone)
        assertEquals(1, breaks)
    }

    @Test fun sprig_is_asleep() {
        rule.setContent { Shot { LockScreen(NightLockFixtures.state(), "Chrome", LockActions()) } }
        val said = rule.onNodeWithTag("lock_sprig").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        assertTrue("Sprig is described as '$said'", said.contains("asleep"))
    }

    @Test fun every_tappable_part_is_48dp_and_labelled() {
        rule.setContent { Shot { LockScreen(NightLockFixtures.state(), "Chrome", LockActions()) } }
        val d = rule.density.density
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("Phone and Break the lock are tappable", nodes.size >= 2)
        nodes.forEach { n ->
            val label = listOfNotNull(
                n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            ).joinToString(" ").trim()
            assertTrue("a tappable part has no label", label.isNotBlank())
            assertTrue("'$label' is ${n.size.height / d}dp tall", n.size.height / d >= 47.9f)
            assertTrue("'$label' is ${n.size.width / d}dp wide", n.size.width / d >= 47.9f)
        }
        for (tag in listOf("emergency_call", "break_lock")) rule.onNodeWithTag(tag).assert(hasClickAction())
    }

    @Test fun itFits() = assertNightLockFits(rule)
}

/** The night lock screen at 360 x 640dp. Goldens: `app/src/test/screenshots/lock_360_night_*`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class NightLockNarrowScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(fontScale: Float) {
        rule.setContent { Shot(fontScale) { LockScreen(NightLockFixtures.state(), "Chrome", LockActions()) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/lock_360_night_font$fontScale.png")
    }

    @Test fun night() = shot(1f)
    @Test fun night_font2() = shot(2f)

    @Test fun itFits() = assertNightLockFits(rule)
}
