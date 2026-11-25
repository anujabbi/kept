package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weekly goal setup screen's rules and copy (issue #165, Setup; story #173). */
class WeeklyGoalSetupTest {

    private fun plain(s: String) = s.replace(WeeklyGoalCopy.NBSP, ' ')

    // --- the draft -----------------------------------------------------------------------------

    @Test fun the_day_chips_are_1_to_7_and_a_pick_outside_is_pulled_in() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), WeeklyGoalDraft.DAY_CHOICES)
        for (n in 1..7) assertEquals(n, WeeklyGoalDraft().withDays(n).days)
        assertEquals(1, WeeklyGoalDraft().withDays(0).days)
        assertEquals(7, WeeklyGoalDraft().withDays(8).days)
        assertEquals("days keep the activity", "Read", WeeklyGoalDraft("Read").withDays(2).title)
    }

    // --- copy ----------------------------------------------------------------------------------

    @Test fun the_summary_follows_the_activity_and_the_number() {
        assertEquals("Exercise, 20 min, 4 days a week", plain(WeeklyGoalDraft("Exercise", 4).summary))
        assertEquals("Read, 10 pages, 4 days a week", plain(WeeklyGoalDraft("Exercise", 4).pick("Read").summary))
        assertEquals("Read, 20 min, 5 days a week", plain(WeeklyGoalDraft("Read", 4).withDays(5).summary))
        assertEquals("the saved name, not the spaces around it", "Climb, 20 min, 2 days a week", plain(WeeklyGoalDraft("  Climb ", 2).summary))
    }

    @Test fun the_days_never_wrap() {
        assertFalse(WeeklyGoalCopy.days(4).contains(' '))
        assertEquals("Exercise · 4 days a week", plain(WeeklyGoalCopy.listRow("Exercise", null, 4)))
        assertEquals("4 of 7", plain(WeeklyGoalDraft("Exercise", 4).daysCount))
        assertEquals("3 of 7", plain(WeeklyGoalDraft().daysCount))
    }

    // --- the target (story #205, task #206) ----------------------------------------------------

    @Test fun a_quick_pick_that_is_a_habit_preset_fills_its_target() {
        val ex = WeeklyGoalDraft().pick("Exercise")
        assertEquals(30 to "min", ex.target to ex.unit)
        assertEquals("30 min", plain(ex.amount))
        val read = ex.pick("Read")
        assertEquals(10 to "pages", read.target to read.unit)
        assertEquals("a pick that is no habit preset keeps the target", 10 to "pages", read.pick("Tidy room").let { it.target to it.unit })
        assertEquals("a new draft starts at 20 min", 20 to "min", WeeklyGoalDraft().let { it.target to it.unit })
    }

    @Test fun the_stepper_steps_like_a_habits_and_stops_at_its_limits() {
        val ex = WeeklyGoalDraft().pick("Exercise")
        assertEquals(35, ex.more().target)
        assertEquals(25, ex.less().target)
        val five = WeeklyGoalDraft("Exercise", target = 5, unit = HabitDraft.MIN)
        assertFalse(five.canLess)
        assertEquals("never below one step", 5, five.less().target)
        val pages = WeeklyGoalDraft("Read", target = 1, unit = HabitDraft.PAGES)
        assertEquals("1 for everything but minutes", 2, pages.more().target)
        assertEquals(1, pages.less().target)
        val max = WeeklyGoalDraft("Read", target = HabitDraft.MAX_TARGET, unit = HabitDraft.PAGES)
        assertFalse(max.canMore)
        assertEquals(HabitDraft.MAX_TARGET, max.more().target)
    }

    @Test fun a_unit_chip_sets_the_unit_and_its_start_value_and_shows_for_a_custom_name_only() {
        val d = WeeklyGoalDraft("Call grandma").withUnit(HabitDraft.TIMES)
        assertEquals(1 to "times", d.target to d.unit)
        assertEquals("one time reads 1 time, not Once a day", "1 time", plain(d.amount))
        assertEquals("3 times", plain(d.more().more().amount))
        assertEquals(8, d.withUnit(HabitDraft.GLASSES).target)
        assertTrue(d.showsUnits)
        assertTrue("Tidy room is a weekly pick but no habit preset", WeeklyGoalDraft("Tidy room").showsUnits)
        assertFalse(WeeklyGoalDraft("Exercise").showsUnits)
        assertFalse(WeeklyGoalDraft("").showsUnits)
    }

    @Test fun an_existing_goal_opens_with_its_target_or_the_default_for_its_name() {
        assertEquals(WeeklyGoalDraft("Exercise", 4, 45, "min"), WeeklyGoalDraft.of("Exercise", 4, 45, "min"))
        assertEquals("no target: Exercise's preset", WeeklyGoalDraft("Exercise", 4, 30, "min"), WeeklyGoalDraft.of("Exercise", 4, null, null))
        assertEquals("no target, no preset: 20 min", WeeklyGoalDraft("Tidy room", 2, 20, "min"), WeeklyGoalDraft.of("Tidy room", 2, null, null))
        assertEquals("a stored target out of range is pulled in", HabitDraft.MAX_TARGET, WeeklyGoalDraft.of("Read", 2, 5000, "pages").target)
    }

    @Test fun the_summary_and_list_row_carry_the_target_and_read_as_before_without_one() {
        assertEquals("Exercise, 30 min, 4 days a week", plain(WeeklyGoalDraft("Exercise", 4).pick("Exercise").summary))
        assertEquals("with no activity, the days alone", "3 days a week", plain(WeeklyGoalDraft().summary))
        assertEquals("Exercise, 30 min, 4 days a week", plain(WeeklyGoalCopy.summary("Exercise", "30 min", 4)))
        assertEquals("Exercise, 4 days a week", plain(WeeklyGoalCopy.summary("Exercise", null, 4)))
        assertEquals("Exercise · 30 min · 4 days a week", plain(WeeklyGoalCopy.listRow("Exercise", "30 min", 4)))
        assertEquals("Exercise · 4 days a week", plain(WeeklyGoalCopy.listRow("Exercise", null, 4)))
        assertEquals("10 pages", plain(WeeklyGoalCopy.target(10, "pages")!!))
        assertEquals("1 page", plain(WeeklyGoalCopy.target(1, "pages")!!))
        assertEquals("8 glasses", plain(WeeklyGoalCopy.target(8, "glasses")!!))
        assertEquals("1 time", plain(WeeklyGoalCopy.target(1, "times")!!))
        assertEquals(null, WeeklyGoalCopy.target(null, null))
        assertFalse("the target never wraps", WeeklyGoalCopy.target(30, "min")!!.contains(' '))
    }

    // --- layout --------------------------------------------------------------------------------

    @Test fun seven_day_chips_are_each_at_least_48dp_wide_from_336dp_up() {
        for (w in listOf(336, 360, 390, 412, 600)) {
            val side = WeeklyGoalSetupLayout.dayRowSideDp(w)
            assertTrue("side at $w", side in 0..21)
            assertTrue("cell at $w is ${(w - 2 * side) / 7f}", (w - 2 * side) / 7f >= 48f)
        }
        assertEquals("the drawn chips start at the page's 24dp", 21, WeeklyGoalSetupLayout.dayRowSideDp(412))
        assertEquals(21, WeeklyGoalSetupLayout.dayRowSideDp(390))
        assertEquals(11, WeeklyGoalSetupLayout.dayRowSideDp(360))
        assertEquals(0, WeeklyGoalSetupLayout.dayRowSideDp(320))
    }

    @Test fun sprig_leaves_the_title_alone_at_a_large_font() {
        assertTrue(WeeklyGoalSetupLayout.showsSprig(1f))
        assertTrue(WeeklyGoalSetupLayout.showsSprig(1.3f))
        assertFalse(WeeklyGoalSetupLayout.showsSprig(1.5f))
        assertFalse(WeeklyGoalSetupLayout.showsSprig(2f))
    }
}
