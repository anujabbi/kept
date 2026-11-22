package com.example.kept.feature.onboarding

import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.HabitTemplate
import com.example.kept.core.ui.HabitTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state behind every onboarding tap (#132), as [OnboardingViewModel] applies it. */
class OnboardingFlowRulesTest {

    private fun custom(i: Int) = HabitTemplate("Custom $i", "star", ProofType.MANUAL, 5, "min")

    @Test fun `onboarding opens on the welcome screen and Pick my habits goes to step 1`() {
        val s = OnboardingState()
        assertEquals(OnboardingStep.WELCOME, s.step)
        assertEquals(OnboardingStep.HABITS, OnboardingFlow.next(s).step)
    }

    @Test fun `next stops at step 6 and back stops at the welcome screen`() {
        var s = OnboardingState()
        repeat(9) { s = OnboardingFlow.next(s) }
        assertEquals(OnboardingStep.READY, s.step)
        assertEquals(6, s.step)
        repeat(9) { s = OnboardingFlow.back(s) }
        assertEquals(OnboardingStep.WELCOME, s.step)
    }

    @Test fun `back from any step keeps every choice`() {
        var s = OnboardingFlow.next(OnboardingState())
        s = OnboardingFlow.toggle(s, HabitTemplates.list[0]) // Exercise off
        s = OnboardingFlow.toggle(s, HabitTemplates.list[4]) // Tidy room on
        s = OnboardingFlow.next(s)
        s = OnboardingFlow.pickShutdown(s, 23 * 60)
        s = OnboardingFlow.next(s)
        s = OnboardingFlow.setWindow(s, LockWindowRules.Window(8 * 60, 20 * 60))
        val chosen = s.chosen
        val window = s.window
        repeat(3) { s = OnboardingFlow.next(s) }
        assertEquals(OnboardingStep.READY, s.step)
        repeat(6) {
            s = OnboardingFlow.back(s)
            assertEquals(chosen, s.chosen)
            assertEquals(23 * 60, s.shutdownMinute)
            assertEquals(window, s.window)
            assertTrue(s.windowTouched)
        }
        assertEquals(listOf("Read", "Tidy room"), s.chosen.map { it.title })
    }

    @Test fun `the step after habits is the shutdown step, with nothing picked`() {
        val s = OnboardingFlow.next(OnboardingFlow.next(OnboardingState()))
        assertEquals(OnboardingStep.SHUTDOWN, s.step)
        assertEquals(2, s.step)
        assertEquals(null, s.shutdownMinute)
        assertEquals(OnboardingStep.LOCK_WINDOW, OnboardingFlow.next(s).step)
    }

    @Test fun `picking a shutdown time holds it and stays on the step, one at a time`() {
        var s = OnboardingState(step = OnboardingStep.SHUTDOWN)
        s = OnboardingFlow.pickShutdown(s, 22 * 60)
        assertEquals(22 * 60, s.shutdownMinute)
        assertEquals(OnboardingStep.SHUTDOWN, s.step)
        s = OnboardingFlow.pickShutdown(s, 0)
        assertEquals(0, s.shutdownMinute)
    }

    @Test fun `back from the lock window returns to the shutdown step with the choice kept`() {
        var s = OnboardingFlow.pickShutdown(OnboardingState(step = OnboardingStep.SHUTDOWN), 23 * 60)
        s = OnboardingFlow.back(OnboardingFlow.next(s))
        assertEquals(OnboardingStep.SHUTDOWN, s.step)
        assertEquals(23 * 60, s.shutdownMinute)
    }

    @Test fun `Skip goes to the lock window with no shutdown time, even after a pick`() {
        val fresh = OnboardingFlow.skipShutdown(OnboardingState(step = OnboardingStep.SHUTDOWN))
        assertEquals(OnboardingStep.LOCK_WINDOW, fresh.step)
        assertEquals(null, fresh.shutdownMinute)
        val picked = OnboardingFlow.pickShutdown(OnboardingState(step = OnboardingStep.SHUTDOWN), 22 * 60)
        val skipped = OnboardingFlow.skipShutdown(picked)
        assertEquals(OnboardingStep.LOCK_WINDOW, skipped.step)
        assertEquals(null, skipped.shutdownMinute)
    }

    @Test fun `back on Add a habit closes it and stays on step 1`() {
        var s = OnboardingFlow.openAddHabit(OnboardingFlow.next(OnboardingState()))
        assertTrue(s.addingHabit)
        s = OnboardingFlow.back(s)
        assertFalse(s.addingHabit)
        assertEquals(OnboardingStep.HABITS, s.step)
    }

    @Test fun `a saved habit is added picked and the page closes`() {
        var s = OnboardingFlow.openAddHabit(OnboardingFlow.next(OnboardingState()))
        s = OnboardingFlow.addCustom(s, custom(1))
        assertFalse(s.addingHabit)
        assertEquals(listOf("Exercise", "Read", "Custom 1"), s.chosen.map { it.title })
        // Toggling it off removes it, like a preset.
        assertEquals(listOf("Exercise", "Read"), OnboardingFlow.toggle(s, custom(1)).chosen.map { it.title })
    }

    @Test fun `the ten habit limit holds for presets and added habits`() {
        var s = OnboardingState()
        HabitTemplates.list.drop(2).forEach { s = OnboardingFlow.toggle(s, it) }
        (1..5).forEach { s = OnboardingFlow.addCustom(s, custom(it)) }
        assertEquals(HabitLimits.MAX_HABITS, s.chosen.size)
        assertTrue(s.atLimit)
        assertEquals(s, OnboardingFlow.addCustom(s, custom(6)))
        assertEquals(s, OnboardingFlow.openAddHabit(s))
        // Unpicking one makes room again.
        val fewer = OnboardingFlow.toggle(s, HabitTemplates.list[0])
        assertFalse(fewer.atLimit)
        assertTrue(OnboardingFlow.openAddHabit(fewer).addingHabit)
    }

    @Test fun `the window counts as touched only once it is moved`() {
        val s = OnboardingState()
        assertFalse(s.windowTouched)
        assertEquals(LockWindowRules.Window(7 * 60, 21 * 60), s.window)
        assertTrue(OnboardingFlow.setWindow(s, LockWindowRules.fromLater(s.window)).windowTouched)
    }
}
