package com.example.kept.core.ui.sprig

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.KeptTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SprigView's touch and frame-clock behaviour, driven through the Compose test clock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class SprigBehaviourTest {

    @get:Rule val rule = createComposeRule()

    @Before fun manualClock() {
        // With auto-advance on, the test framework cancels infinite animations, so drive frames by hand.
        rule.mainClock.autoAdvance = false
    }

    private fun show(
        pose: SprigPose = SprigPose.IDLE,
        sizeDp: Int = 200,
        reduceMotion: Boolean = false,
        motion: SprigMotion = SprigMotion(),
    ): SprigMotion {
        rule.setContent {
            KeptTheme(reduceMotion = reduceMotion) {
                Box { SprigView(SprigForm.BLOOM, pose, Modifier.size(sizeDp.dp).testTag("sprig"), motion = motion) }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        return motion
    }

    private fun frames(ms: Long) {
        var left = ms
        while (left > 0) { rule.mainClock.advanceTimeBy(minOf(500, left)); left -= 500 }
    }

    @Test fun tapHops() {
        val m = show()
        rule.onNodeWithTag("sprig").performTouchInput { down(center); up() }
        assertEquals(1f, m.hop, 0.05f)
        frames(200)
        assertTrue("decaying", m.hop in 0.3f..0.9f)
        frames(600)
        assertEquals(0f, m.hop, 0f)
    }

    @Test fun tripleTapTickles() {
        val m = show()
        repeat(3) {
            rule.onNodeWithTag("sprig").performTouchInput { down(center); up() }
            rule.mainClock.advanceTimeBy(150)
        }
        assertTrue("tickle wobble started", m.tick > 0.8f)
    }

    @Test fun dragPets() {
        val m = show()
        rule.onNodeWithTag("sprig").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(15f, 0f))
            moveBy(androidx.compose.ui.geometry.Offset(15f, 0f))
        }
        assertTrue("petting after 30px", m.petting)
        frames(500)
        assertTrue("pet eased in", m.pet > 0.8f)
        rule.onNodeWithTag("sprig").performTouchInput { up() }
        assertFalse(m.petting)
    }

    @Test fun twelveSecondsIdleSleepsAndTalkBackSaysSo() {
        val m = show()
        frames(11_500)
        assertFalse(m.asleepFromIdle)
        frames(1_000)
        assertTrue(m.asleepFromIdle)
        val label = rule.onNodeWithTag("sprig").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Sprig, Bloom, asleep"), label)
        rule.onNodeWithTag("sprig").performTouchInput { down(center); up() }
        assertFalse("a touch wakes it", m.asleepFromIdle)
    }

    @Test fun talkBackReadsSprigAndDoubleTapHops() {
        val m = show(SprigPose.CHEER)
        val node = rule.onNodeWithTag("sprig").fetchSemanticsNode()
        assertEquals(listOf("Sprig, Bloom, cheering"), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(androidx.compose.ui.semantics.Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        rule.onNodeWithTag("sprig").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1f, m.hop, 0.05f)
    }

    @Test fun reduceMotionFreezesSway() {
        val m = show(reduceMotion = true)
        frames(3_000)
        assertEquals("animation time frozen", 0f, m.t, 0f)
        rule.onNodeWithTag("sprig").performTouchInput { down(center); up() }
        assertEquals("no hop", 0f, m.hop, 0f)
        assertTrue("the clock still runs blinks", m.nextBlink < 2.5f)
    }

    @Test fun smallSprigsDoNotAnimate() {
        val m = show(sizeDp = 84)
        frames(3_000)
        assertEquals(0f, m.t, 0f)
        assertEquals("no frame clock at all", 0f, m.idle, 0f)
    }

    @Test fun bigSprigsAnimate() {
        val m = show()
        frames(1_000)
        assertTrue(m.t > 0.9f)
    }

    @Test fun pausesInTheBackground() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        val m = SprigMotion()
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                KeptTheme(reduceMotion = false) { SprigView(SprigForm.BLOOM, SprigPose.IDLE, Modifier.size(200.dp), motion = m) }
            }
        }
        frames(1_000)
        val running = m.t
        assertTrue(running > 0.5f)
        rule.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        frames(2_000)
        assertEquals("paused in the background", running, m.t, 0.1f)
        rule.runOnIdle { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        frames(1_000)
        assertTrue("resumed", m.t > running + 0.5f)
    }
}
