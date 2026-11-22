package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weekly goal setup screen's rules and copy (issue #165, Setup; story #173). */
class WeeklyGoalSetupTest {

    private fun plain(s: String) = s.replace(WeeklyGoalCopy.NBSP, ' ')

    // --- the draft -----------------------------------------------------------------------------

    @Test fun a_new_draft_has_no_activity_and_three_days() {
        val d = WeeklyGoalDraft()
        assertEquals("", d.title)
        assertEquals(3, d.days)
        assertEquals(WeeklyGoalRules.DEFAULT_DAYS, d.days)
        assertFalse(d.canSave)
        assertFalse(d.showsSevenHint)
    }

    @Test fun save_needs_an_activity_and_spaces_alone_are_not_one() {
        assertFalse(WeeklyGoalDraft("").canSave)
        assertFalse(WeeklyGoalDraft("   ").canSave)
        assertFalse(WeeklyGoalDraft("\t \n").canSave)
        assertTrue(WeeklyGoalDraft("x").canSave)
        assertTrue(WeeklyGoalDraft("  Climb  ").canSave)
        assertEquals("Climb", WeeklyGoalDraft("  Climb  ").name)
    }

    @Test fun the_activity_stops_at_28_characters() {
        assertEquals(28, HabitDraft.MAX_TITLE)
        val typed = WeeklyGoalDraft().withTitle("x".repeat(40))
        assertEquals(28, typed.title.length)
        assertEquals("x".repeat(28), WeeklyGoalDraft().withTitle("x".repeat(28)).title)
        assertEquals("y" + "x".repeat(27), WeeklyGoalDraft().withTitle("y" + "x".repeat(60)).title)
    }

    @Test fun the_quick_picks_are_the_five_in_the_issue_and_each_fills_the_activity() {
        assertEquals(listOf("Exercise", "Read", "Practice", "Study", "Tidy room"), WeeklyGoalDraft.QUICK_PICKS)
        for (p in WeeklyGoalDraft.QUICK_PICKS) {
            val d = WeeklyGoalDraft("Something else", 5).pick(p)
            assertEquals(p, d.name)
            assertEquals("a pick keeps the days", 5, d.days)
            assertTrue(d.canSave)
            assertTrue(d.isPicked(p))
            assertEquals("only one pick is lit", 1, WeeklyGoalDraft.QUICK_PICKS.count { d.isPicked(it) })
        }
        assertEquals(0, WeeklyGoalDraft.QUICK_PICKS.count { WeeklyGoalDraft("Piano").isPicked(it) })
        assertEquals(0, WeeklyGoalDraft.QUICK_PICKS.count { WeeklyGoalDraft().isPicked(it) })
    }

    @Test fun the_day_chips_are_1_to_7_and_a_pick_outside_is_pulled_in() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), WeeklyGoalDraft.DAY_CHOICES)
        for (n in 1..7) assertEquals(n, WeeklyGoalDraft().withDays(n).days)
        assertEquals(1, WeeklyGoalDraft().withDays(0).days)
        assertEquals(7, WeeklyGoalDraft().withDays(8).days)
        assertEquals("days keep the activity", "Read", WeeklyGoalDraft("Read").withDays(2).title)
    }

    @Test fun the_seven_days_hint_shows_only_at_seven() {
        assertEquals("7 days a week locks every day until it's done.", WeeklyGoalCopy.SEVEN_HINT)
        for (n in 1..6) assertFalse("at $n", WeeklyGoalDraft("Read", n).showsSevenHint)
        assertTrue(WeeklyGoalDraft("Read", 7).showsSevenHint)
        assertTrue("with no activity too", WeeklyGoalDraft("", 7).showsSevenHint)
    }

    @Test fun the_icon_comes_from_the_name() {
        assertEquals("run", WeeklyGoalDraft("Exercise").iconKey)
        assertEquals("book", WeeklyGoalDraft("Read").iconKey)
        assertEquals("tidy", WeeklyGoalDraft("Tidy room").iconKey)
        assertEquals(HabitIconRules.FALLBACK, WeeklyGoalDraft("Call grandma").iconKey)
        assertEquals(HabitIconRules.forName("Practice"), WeeklyGoalDraft(" Practice ").iconKey)
    }

    // --- copy ----------------------------------------------------------------------------------

    @Test fun the_summary_follows_the_activity_and_the_number() {
        assertEquals("Exercise, 4 days a week", plain(WeeklyGoalDraft("Exercise", 4).summary))
        assertEquals("Read, 4 days a week", plain(WeeklyGoalDraft("Exercise", 4).pick("Read").summary))
        assertEquals("Read, 5 days a week", plain(WeeklyGoalDraft("Read", 4).withDays(5).summary))
        assertEquals("Read, 7 days a week", plain(WeeklyGoalDraft("Read", 7).summary))
        assertEquals("the saved name, not the spaces around it", "Climb, 2 days a week", plain(WeeklyGoalDraft("  Climb ", 2).summary))
    }

    @Test fun one_day_a_week_is_singular() {
        assertEquals("1 day a week", plain(WeeklyGoalCopy.days(1)))
        assertEquals("2 days a week", plain(WeeklyGoalCopy.days(2)))
        assertEquals("Exercise, 1 day a week", plain(WeeklyGoalDraft("Exercise", 1).summary))
        assertEquals("Exercise · 1 day a week", plain(WeeklyGoalCopy.listRow("Exercise", 1)))
    }

    @Test fun with_no_activity_the_summary_is_the_days_alone() {
        assertEquals("3 days a week", plain(WeeklyGoalDraft().summary))
        assertEquals("1 day a week", plain(WeeklyGoalDraft("  ", 1).summary))
    }

    @Test fun the_days_never_wrap() {
        assertFalse(WeeklyGoalCopy.days(4).contains(' '))
        assertEquals("Exercise · 4 days a week", plain(WeeklyGoalCopy.listRow("Exercise", 4)))
        assertEquals("4 of 7", plain(WeeklyGoalDraft("Exercise", 4).daysCount))
        assertEquals("3 of 7", plain(WeeklyGoalDraft().daysCount))
    }

    @Test fun the_copy_is_the_issues() {
        assertEquals("Weekly goals", WeeklyGoalCopy.ROW)
        assertEquals("Weekly goal", WeeklyGoalCopy.TITLE)
        assertEquals("For things you don't need to do every day.", WeeklyGoalCopy.SUBTITLE)
        assertEquals("What will you do?", WeeklyGoalCopy.NAME_LABEL)
        assertEquals("+ Custom", WeeklyGoalCopy.CUSTOM)
        assertEquals("How many days a week?", WeeklyGoalCopy.DAYS_LABEL)
        assertEquals(
            "Week runs Sunday to Saturday. If you fall behind, KEPT locks your phone on the day you can't skip anymore.",
            WeeklyGoalCopy.SUMMARY_BODY,
        )
        assertEquals("Save goal", WeeklyGoalCopy.SAVE)
        assertEquals("Add a weekly goal", WeeklyGoalCopy.ADD)
    }

    @Test fun the_list_counts_up_to_three_and_then_says_why_no_more() {
        assertEquals(3, WeeklyGoalRules.MAX_GOALS)
        assertEquals("0 of 3 weekly goals", plain(WeeklyGoalCopy.count(0)))
        assertEquals("2 of 3 weekly goals", plain(WeeklyGoalCopy.count(2)))
        assertEquals("That's the limit: 3 weekly goals. Remove one to add another.", WeeklyGoalCopy.count(3))
        assertEquals("2", WeeklyGoalCopy.rowValue(2))
    }

    @Test fun the_delete_confirm_names_the_goal() {
        assertEquals("Exercise, 4 days a week will be removed from your weekly goals.", plain(WeeklyGoalCopy.deleteBody("Exercise", 4)))
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
