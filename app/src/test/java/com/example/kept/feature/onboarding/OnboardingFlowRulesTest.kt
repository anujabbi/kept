package com.example.kept.feature.onboarding

import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.domain.NightSetting
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

    private fun custom(i: Int) = HabitTemplate("Custom $i", "write", ProofType.MANUAL, 5, "min")

    /** "Add your own", typed [name], 5 minutes, Done, then the cheer ends. */
    private fun addOwn(s: OnboardingState, name: String): OnboardingState {
        val open = OnboardingFlow.openAddHabit(s)
        return OnboardingFlow.closePromise(OnboardingFlow.promiseDone(open, PromiseDraft(name, 5, "min", custom = true)))
    }

    /** [title]'s promise set to [days] (null for every day), Done, then the cheer ends. */
    private fun setDays(s: OnboardingState, title: String, days: Int?): OnboardingState {
        val open = OnboardingFlow.openPromise(s, title)
        return OnboardingFlow.closePromise(OnboardingFlow.promiseDone(open, open.promise!!.draft.withDays(days)))
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
        s = OnboardingFlow.setWindow(s, LockWindowRules.Window(8 * 60, 20 * 60))
        s = OnboardingFlow.next(s)
        s = OnboardingFlow.pickShutdownFrom(s, 22 * 60)
        s = OnboardingFlow.pickShutdownUntil(s, 6 * 60 + 45)
        val chosen = s.chosen
        val window = s.window
        s = OnboardingFlow.continueShutdown(s)
        repeat(2) { s = OnboardingFlow.next(s) }
        assertEquals(OnboardingStep.READY, s.step)
        repeat(6) {
            s = OnboardingFlow.back(s)
            assertEquals(chosen, s.chosen)
            assertEquals(NightSetting(true, 22 * 60, 6 * 60 + 45), s.shutdown)
            assertEquals(window, s.window)
            assertTrue(s.windowTouched)
        }
        assertEquals(listOf("Read", "Tidy room"), s.chosen.map { it.title })
    }

    @Test fun `a saved habit is added picked and the page closes`() {
        var s = addOwn(OnboardingFlow.next(OnboardingState()), "Custom 1")
        assertEquals(null, s.promise)
        assertEquals(listOf("Exercise", "Read", "Custom 1"), s.chosen.map { it.title })
        // Toggling it off removes it, like a preset.
        assertEquals(listOf("Exercise", "Read"), OnboardingFlow.toggle(s, custom(1)).chosen.map { it.title })
    }

    @Test fun `the ten habit limit counts every-day habits only`() {
        var s = OnboardingState()
        HabitTemplates.list.drop(2).forEach { s = OnboardingFlow.toggle(s, it) }
        (1..6).forEach { s = addOwn(s, "Custom $it") }
        // Read, Practice, Study, Tidy room and six of their own every day; Exercise is weekly.
        assertEquals(HabitLimits.MAX_HABITS, s.daily.size)
        assertEquals(listOf("Exercise"), s.weekly.map { it.title })
        assertTrue(s.atLimit)
        assertEquals(s, OnboardingFlow.openAddHabit(s))
        // At the daily limit a weekly habit cannot switch to every day, but it can tick on as weekly.
        val open = OnboardingFlow.openPromise(s, "Exercise")
        assertFalse(open.promiseLimits.everyDay)
        assertEquals(HabitLimits.LIMIT_REACHED, open.promiseLimits.line)
        assertEquals(4, OnboardingFlow.promiseDone(open, open.promise!!.draft.withDays(null)).daysOf("Exercise"))
        val offOn = OnboardingFlow.toggle(OnboardingFlow.toggle(s, HabitTemplates.list[0]), HabitTemplates.list[0])
        assertEquals(4, offOn.daysOf("Exercise"))
        // Unpicking one makes room again.
        val fewer = OnboardingFlow.toggle(s, HabitTemplates.list[1])
        assertFalse(fewer.atLimit)
        assertTrue(OnboardingFlow.openAddHabit(fewer).promise != null)
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
        s = setDays(s, "Read", 2)
        assertEquals(2, s.daysOf("Read"))
        s = OnboardingFlow.toggle(s, HabitTemplates.list[1])
        assertEquals(null, s.daysOf("Read"))
        assertFalse("Read" in s.weeklyDays)
        s = OnboardingFlow.toggle(s, HabitTemplates.list[1])
        assertEquals(null, s.daysOf("Read"))
        s = OnboardingFlow.toggle(OnboardingFlow.toggle(s, HabitTemplates.list[0]), HabitTemplates.list[0])
        assertEquals(4, s.daysOf("Exercise"))
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
        listOf("Read", "Practice", "Study").forEach { s = setDays(s, it, 3) }
        assertEquals(3, s.weekly.size)
        s = OnboardingFlow.toggle(s, HabitTemplates.list[0])
        assertEquals(null, s.daysOf("Exercise"))
        val open = OnboardingFlow.openPromise(s, "Exercise")
        assertFalse(open.promiseLimits.weekly)
        assertEquals("a weekly choice is refused", null, OnboardingFlow.promiseDone(open, open.promise!!.draft.withDays(2)).daysOf("Exercise"))
        // A weekly habit's own promise can still change its days.
        assertTrue(OnboardingFlow.openPromise(s, "Read").promiseLimits.weekly)
    }

    @Test fun `a promise renames its habit, keeps a preset's icon, and refuses a name already ticked`() {
        val open = OnboardingFlow.openPromise(OnboardingFlow.next(OnboardingState()), "Read")
        val taken = OnboardingFlow.promiseDone(open, open.promise!!.draft.pick(PromiseRules.PRESETS[0]))
        assertEquals(open, taken)
        val s = OnboardingFlow.closePromise(OnboardingFlow.promiseDone(open, PromiseDraft("Swim", 20, "min", custom = true)))
        assertEquals(listOf("Exercise", "Swim"), s.chosen.map { it.title })
        assertEquals("run", s.chosen[1].iconKey)
        assertEquals("book", OnboardingFlow.promiseDone(open, open.promise!!.draft.withTarget(20)).chosen[1].iconKey)
    }

    @Test fun `back closes the promise before it moves a step`() {
        val open = OnboardingFlow.openPromise(OnboardingFlow.next(OnboardingState()), "Exercise")
        assertEquals(OnboardingStep.HABITS, OnboardingFlow.back(open).step)
        assertEquals(null, OnboardingFlow.back(open).promise)
        assertEquals(4, OnboardingFlow.back(open).daysOf("Exercise"))
    }

    @Test fun `back from any step keeps every cadence`() {
        var s = OnboardingFlow.next(OnboardingState())
        s = setDays(s, "Read", 3)
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
