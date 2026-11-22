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
