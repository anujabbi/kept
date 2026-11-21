package com.example.kept.feature.celebration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.example.kept.core.domain.MomentMotion
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.PromiseKeptMoment
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the two moments do when touched, heard and animated (issue #130). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class MomentsBehaviourTest {

    @get:Rule val rule = createComposeRule()

    @Composable
    private fun Themed(reduce: Boolean, content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, 1f)) { KeptTheme(reduceMotion = reduce, content = content) }
    }

    @Test fun promiseKeptShowsTheCopyAndATapAnywhereCarriesOn() {
        var carriedOn = 0
        rule.setContent { Themed(true) { PromiseKeptMoment(SprigForm.BLOOM, streakDays = 12, onCarryOn = { carriedOn++ }) } }
        rule.onNodeWithTag("celebration_headline").assertTextEquals("Promise kept.")
        rule.onNodeWithTag("celebration_line").assertTextEquals("Day 13 of your streak. Apps are open.")
        rule.onNodeWithTag("celebration_carry_on").assertTextEquals("Tap to carry on")

        rule.onNodeWithTag("celebration_headline", useUnmergedTree = true).performTouchInput { click() }
        assertEquals("a tap on the headline", 1, carriedOn)
        rule.onNodeWithTag("moment_sprig", useUnmergedTree = true).performTouchInput { click() }
        assertEquals("a tap on Sprig carries on too (Sprig still hops)", 2, carriedOn)
        rule.onNodeWithTag("celebration").performTouchInput { click(topLeft) }
        assertEquals("a tap on the gold", 3, carriedOn)
    }

    @Test fun talkBackHearsPromiseKept() {
        var carriedOn = 0
        rule.setContent { Themed(true) { PromiseKeptMoment(SprigForm.BLOOM, streakDays = 12, onCarryOn = { carriedOn++ }) } }
        rule.onNodeWithTag("celebration").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Promise kept."))
        rule.onNodeWithTag("celebration_headline").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithTag("celebration_carry_on")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performClick()
        assertEquals(1, carriedOn)
        rule.onNodeWithTag("celebration").performClick()
        assertEquals("the pane itself is actionable", 2, carriedOn)
    }

    @Test fun promiseKeptPlaysItsMotionOnceAndNotWithReduceMotion() {
        var started = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { Themed(false) { PromiseKeptMoment(SprigForm.BLOOM, 12, onCarryOn = {}, playIntro = true, onIntroStarted = { started++ }) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("moment_petals").assertExists()
        assertEquals("the ViewModel is told once", 1, started)
        rule.mainClock.advanceTimeBy(MomentMotion.END_MS.toLong())
        rule.onNodeWithTag("moment_petals").assertDoesNotExist()
    }

    @Test fun anIntroThatHasPlayedDoesNotPlayAgain() {
        var started = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { Themed(false) { PromiseKeptMoment(SprigForm.BLOOM, 12, onCarryOn = {}, playIntro = false, onIntroStarted = { started++ }) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("moment_petals").assertDoesNotExist()
        rule.onNodeWithTag("celebration_headline").assertTextEquals("Promise kept.")
        assertEquals(0, started)
    }

    @Test fun reduceMotionShowsNoPetalsPopOrRing() {
        var started = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { Themed(true) { PromiseKeptMoment(SprigForm.BLOOM, 12, onCarryOn = {}, playIntro = true, onIntroStarted = { started++ }) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("moment_petals").assertDoesNotExist()
        assertEquals("nothing plays", 0, started)
    }

    @Test fun evolveShowsTheCopyAndItsButtons() {
        var shared = 0
        var carriedOn = 0
        rule.setContent { Themed(true) { SprigEvolvesContent(SprigForm.THICKET, 14, onShare = { shared++ }, onCarryOn = { carriedOn++ }) } }
        rule.onNodeWithTag("evolve_kept").assertTextEquals("14 days kept")
        rule.onNodeWithTag("evolve_headline").assertTextEquals("Sprig is now Thicket")
        rule.onNodeWithTag("evolve_line").assertTextEquals("New outfit: a scarf and a cardigan. Next up is Grove, at day 30.")
        rule.onNodeWithTag("evolve").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Sprig is now Thicket"))
        rule.onNodeWithTag("evolve_headline").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithTag("evolve_share").assertTextEquals("Share").performClick()
        rule.onNodeWithTag("evolve_carry_on").assertTextEquals("Carry on").performClick()
        assertEquals(1, shared)
        assertEquals(1, carriedOn)
    }

    @Test fun theFinalFormHasNoNextUp() {
        rule.setContent { Themed(true) { SprigEvolvesContent(SprigForm.CELESTIAL, 100, onShare = {}, onCarryOn = {}) } }
        rule.onNodeWithTag("evolve_line").assertTextEquals("New outfit: a starry cape and a halo.")
    }

    @Test fun evolvePlaysPetalsWithMotionAndNoneWithReduceMotion() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Themed(false) { SprigEvolvesContent(SprigForm.BUD, 3, onShare = {}, onCarryOn = {}) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("moment_petals").assertExists()
    }

    @Test fun evolveWithReduceMotionHasNoPetals() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Themed(true) { SprigEvolvesContent(SprigForm.BUD, 3, onShare = {}, onCarryOn = {}) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("moment_petals").assertDoesNotExist()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExists() = assertExists()
