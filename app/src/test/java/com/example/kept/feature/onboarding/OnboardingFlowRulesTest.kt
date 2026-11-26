package com.example.kept.feature.onboarding

import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalRules
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

    @Test fun `the ten habit limit counts every-day habits only`() {
        var s = OnboardingState()
        HabitTemplates.list.drop(2).forEach { s = OnboardingFlow.toggle(s, it) }
        (1..6).forEach { s = OnboardingFlow.addCustom(s, custom(it)) }
        // Read, Practice, Study, Tidy room and six of their own every day; Exercise is weekly.
        assertEquals(HabitLimits.MAX_HABITS, s.daily.size)
        assertEquals(listOf("Exercise"), s.weekly.map { it.title })
        assertTrue(s.atLimit)
        assertEquals(s, OnboardingFlow.addCustom(s, custom(7)))
        assertEquals(s, OnboardingFlow.openAddHabit(s))
        // At the daily limit a weekly habit cannot switch to every day, but it can tick on as weekly.
        s = OnboardingFlow.openCadence(s, "Exercise")
        assertFalse(s.sheetCanEveryDay)
        assertEquals(4, OnboardingFlow.sheetEveryDay(s).sheet!!.days)
        s = OnboardingFlow.closeCadence(s)
        val offOn = OnboardingFlow.toggle(OnboardingFlow.toggle(s, HabitTemplates.list[0]), HabitTemplates.list[0])
        assertEquals(4, offOn.daysOf("Exercise"))
        // Unpicking one makes room again.
        val fewer = OnboardingFlow.toggle(s, HabitTemplates.list[1])
        assertFalse(fewer.atLimit)
        assertTrue(OnboardingFlow.openAddHabit(fewer).addingHabit)
    }

    // How often (#205) ---------------------------------------------------------------------------

    @Test fun `step 1 opens with Exercise at 4 days a week and Read every day`() {
        val s = OnboardingState()
        assertEquals(4, s.daysOf("Exercise"))
        assertEquals(null, s.daysOf("Read"))
        assertEquals(listOf("Read"), s.daily.map { it.title })
        assertTrue(s.canContinue)
    }

    @Test fun `unticking drops the cadence and ticking again applies the default`() {
        var s = OnboardingFlow.next(OnboardingState())
        s = OnboardingFlow.sheetDone(OnboardingFlow.sheetDays(OnboardingFlow.sheetSomeDays(OnboardingFlow.openCadence(s, "Read")), 2))
        assertEquals(2, s.daysOf("Read"))
        s = OnboardingFlow.toggle(s, HabitTemplates.list[1])
        assertEquals(null, s.daysOf("Read"))
        assertFalse("Read" in s.weeklyDays)
        s = OnboardingFlow.toggle(s, HabitTemplates.list[1])
        assertEquals(null, s.daysOf("Read"))
        s = OnboardingFlow.toggle(OnboardingFlow.toggle(s, HabitTemplates.list[0]), HabitTemplates.list[0])
        assertEquals(4, s.daysOf("Exercise"))
    }

    @Test fun `the sheet offers 1 to 6 and ignores anything else`() {
        assertEquals((1..6).toList(), OnboardingCadence.DAY_CHOICES)
        var s = OnboardingFlow.sheetSomeDays(OnboardingFlow.openCadence(OnboardingFlow.next(OnboardingState()), "Read"))
        assertEquals(WeeklyGoalRules.DEFAULT_DAYS, s.sheet!!.days)
        assertEquals(s, OnboardingFlow.sheetDays(s, 7))
        assertEquals(s, OnboardingFlow.sheetDays(s, 0))
        s = OnboardingFlow.sheetDays(s, 6)
        assertEquals(6, OnboardingFlow.sheetDone(s).daysOf("Read"))
    }

    @Test fun `dismissing the sheet keeps the cadence it opened with`() {
        var s = OnboardingFlow.openCadence(OnboardingFlow.next(OnboardingState()), "Exercise")
        s = OnboardingFlow.closeCadence(OnboardingFlow.sheetEveryDay(s))
        assertEquals(4, s.daysOf("Exercise"))
        assertEquals(null, s.sheet)
        // Back closes the sheet before it moves a step.
        val open = OnboardingFlow.openCadence(s, "Exercise")
        assertEquals(OnboardingStep.HABITS, OnboardingFlow.back(open).step)
        assertEquals(null, OnboardingFlow.back(open).sheet)
    }

    @Test fun `with only weekly habits Continue does not advance`() {
        var s = OnboardingFlow.next(OnboardingState())
        s = OnboardingFlow.toggle(s, HabitTemplates.list[1]) // Read off; Exercise is weekly
        assertFalse(s.canContinue)
        assertEquals(s, OnboardingFlow.next(s))
    }

    @Test fun `a fourth habit cannot be weekly and Exercise ticked fourth is every day`() {
        var s = OnboardingFlow.next(OnboardingState())
        s = OnboardingFlow.toggle(s, HabitTemplates.list[0]) // Exercise off
        listOf("Practice", "Study").forEach { t -> s = OnboardingFlow.toggle(s, HabitTemplates.list.first { it.title == t }) }
        listOf("Read", "Practice", "Study").forEach { s = OnboardingFlow.sheetDone(OnboardingFlow.sheetSomeDays(OnboardingFlow.openCadence(s, it))) }
        assertEquals(3, s.weekly.size)
        s = OnboardingFlow.toggle(s, HabitTemplates.list[0])
        assertEquals(null, s.daysOf("Exercise"))
        s = OnboardingFlow.openCadence(s, "Exercise")
        assertFalse(s.sheetCanSomeDays)
        assertEquals(null, OnboardingFlow.sheetSomeDays(s).sheet!!.days)
        // A weekly habit's own sheet can still change its days.
        assertTrue(OnboardingFlow.openCadence(s, "Read").sheetCanSomeDays)
    }

    @Test fun `back from any step keeps every cadence`() {
        var s = OnboardingFlow.next(OnboardingState())
        s = OnboardingFlow.sheetDone(OnboardingFlow.sheetDays(OnboardingFlow.sheetSomeDays(OnboardingFlow.openCadence(s, "Read")), 3))
        s = OnboardingFlow.toggle(s, HabitTemplates.list[2])
        val weekly = s.weeklyDays
        repeat(5) { s = OnboardingFlow.next(s) }
        assertEquals(OnboardingStep.READY, s.step)
        repeat(5) { s = OnboardingFlow.back(s); assertEquals(weekly, s.weeklyDays) }
        assertEquals(OnboardingStep.HABITS, s.step)
        assertEquals(3, s.daysOf("Read"))
        assertEquals(4, s.daysOf("Exercise"))
    }

    @Test fun `the window counts as touched only once it is moved`() {
        val s = OnboardingState()
        assertFalse(s.windowTouched)
        assertEquals(LockWindowRules.Window(7 * 60, 21 * 60), s.window)
        assertTrue(OnboardingFlow.setWindow(s, LockWindowRules.fromLater(s.window)).windowTouched)
    }
}
