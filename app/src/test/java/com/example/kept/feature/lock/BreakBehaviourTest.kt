package com.example.kept.feature.lock

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.kept.core.domain.BreakSprig
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigLook
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.sprig.SprigFrame
import com.example.kept.core.ui.sprig.SprigMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the break screen does: the rise and pop, the fade, petting, and its two buttons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class BreakBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private var introStarts = 0

    private fun show(reduceMotion: Boolean, playIntro: Boolean, motion: SprigMotion = SprigMotion()) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KeptTheme(reduceMotion = reduceMotion) {
                BreakLockScreen(
                    BreakFixtures.NORMAL, secondsLeft = 60, onCancel = {}, onConfirm = {},
                    playIntro = playIntro, onIntroStarted = { introStarts++ }, sprigMotion = motion,
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun sprigTop() = rule.breakRect("break_sprig").top
    /** The bubble as drawn: its bounds in the root include the pop's scale. */
    private fun bubbleWidth() = rule.onNodeWithTag("break_bubble").fetchSemanticsNode().boundsInRoot.width / rule.density.density

    @Test fun sprigRisesFromBehindTheHill() {
        show(reduceMotion = false, playIntro = true)
        val design = BreakSprig.DESIGN_TOP_DP.toFloat()
        assertEquals("starts 260 down", design + BreakSprig.RISE_FROM_DP, sprigTop(), 2f)
        rule.mainClock.advanceTimeBy(RISE_DELAY_MS - 50L)
        assertEquals("waits 0.3s", design + BreakSprig.RISE_FROM_DP, sprigTop(), 2f)
        rule.mainClock.advanceTimeBy(500)
        assertTrue("rising at 0.75s: ${sprigTop()}", sprigTop() < design + 200f)
        rule.mainClock.advanceTimeBy(RISE_MS.toLong())
        assertEquals("risen by 1.3s", design, sprigTop(), 1f)
        assertEquals("played once", 1, introStarts)
    }

    @Test fun theBubblePopsAt1200ms() {
        show(reduceMotion = false, playIntro = true)
        rule.mainClock.advanceTimeBy(POP_DELAY_MS - 100L)
        val before = bubbleWidth()
        rule.mainClock.advanceTimeBy(100L + POP_MS + 100L)
        val after = bubbleWidth()
        assertTrue("bubble $before wide before 1.2s, $after after", before < after * 0.5f)
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals("stays", after, bubbleWidth(), 0.5f)
    }

    @Test fun reduceMotionShowsSprigAndTheBubbleAtOnce() {
        show(reduceMotion = true, playIntro = true)
        assertEquals(BreakSprig.DESIGN_TOP_DP.toFloat(), sprigTop(), 1f)
        val w = bubbleWidth()
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals("no pop", w, bubbleWidth(), 0.5f)
        assertEquals("the rise counts as played", 1, introStarts)
    }

    @Test fun aRecreatedScreenDoesNotReplayTheRise() {
        show(reduceMotion = false, playIntro = false)
        assertEquals(BreakSprig.DESIGN_TOP_DP.toFloat(), sprigTop(), 1f)
        assertEquals(0, introStarts)
    }

    @Test fun pettingStopsTheTear() {
        val m = SprigMotion()
        show(reduceMotion = false, playIntro = false, motion = m)
        fun tear() = SprigFrame.of(SprigPose.DROOP, SprigLook.stage(SprigForm.BLOOM), SprigLook.outfit(SprigForm.BLOOM), true, m).tearAlpha
        // The tear falls on a 1.8s loop; somewhere in it, it shows.
        var seen = 0f
        repeat(18) { rule.mainClock.advanceTimeBy(100); seen = maxOf(seen, tear()) }
        assertTrue("a tear falls", seen > 0f)
        rule.onNodeWithTag("break_sprig").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(15f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(15f, 0f))
        }
        rule.mainClock.advanceTimeBy(600)
        var during = 0f
        repeat(18) { rule.mainClock.advanceTimeBy(100); during = maxOf(during, tear()) }
        assertEquals("no tear while petted", 0f, during, 0f)
        rule.onNodeWithTag("break_sprig").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        var after = 0f
        repeat(18) { rule.mainClock.advanceTimeBy(100); after = maxOf(after, tear()) }
        assertTrue("the tear comes back when let go", after > 0f)
    }

    @Test fun buttonsGoBackAndUnlockOnlyAtZero() {
        val left = mutableIntStateOf(60)
        var cancels = 0
        var confirms = 0
        rule.setContent {
            Shot { BreakLockScreen(BreakFixtures.state(breakMinutes = 15), secondsLeft = left.intValue, onCancel = { cancels++ }, onConfirm = { confirms++ }) }
        }
        rule.onNodeWithTag("break_clock_label").assertTextEquals("You can unlock in")
        rule.onNodeWithTag("break_countdown").assertTextEquals("1:00")
        rule.onNodeWithTag("unlock_anyway").assertTextEquals("Unlock anyway (60s)").assertIsNotEnabled().performClick()
        assertEquals(0, confirms)
        rule.onNodeWithTag("break_go_back").assertTextEquals("Never mind, go back").assertIsEnabled().performClick()
        assertEquals(1, cancels)

        left.intValue = 0
        rule.onNodeWithTag("break_clock_label").assertTextEquals("You can unlock now")
        rule.onNodeWithTag("break_countdown").assertTextEquals("0:00")
        rule.onNodeWithTag("unlock_anyway").assertTextEquals("Unlock for 15 min").assertIsEnabled().performClick()
        assertEquals(1, confirms)
        rule.onNodeWithTag("break_go_back").assertIsEnabled()
    }

    @Test fun costsUseRealValuesAndTheFourthBreakIsTheLastResort() {
        val state = androidx.compose.runtime.mutableStateOf(BreakFixtures.state(breaksUsed = 2, streak = 7))
        rule.setContent { Shot { BreakLockScreen(state.value, secondsLeft = 60, onCancel = {}, onConfirm = {}) } }
        rule.onNodeWithText("What it costs").assertExists()
        rule.onNodeWithText("Sprig drops a level and wilts until tomorrow.").assertExists()
        rule.onNodeWithText("Your 7 day streak stays.").assertExists()
        rule.onNodeWithText("0 of 3 breaks left this week.").assertExists()
        rule.onNodeWithTag("break_last_resort").assertDoesNotExist()

        state.value = BreakFixtures.state(breaksUsed = 3, streak = 7)
        rule.onNodeWithTag("break_cost_wilt").assertDoesNotExist()
        rule.onNodeWithTag("break_cost_breaks").assertDoesNotExist()
        rule.onNodeWithTag("break_last_resort").assertTextEquals("This is your fourth break this week, the last resort.")
        rule.onNodeWithTag("break_written_off").assertTextEquals("Today is written off. It won't count toward your 7 day streak, and your shield can't save it.")
    }
}
