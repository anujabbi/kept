package com.example.kept.core.domain

import com.example.kept.core.domain.HabitDraft.Companion.GLASSES
import com.example.kept.core.domain.HabitDraft.Companion.MIN
import com.example.kept.core.domain.HabitDraft.Companion.PAGES
import com.example.kept.core.domain.HabitDraft.Companion.TIMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Add a habit's rules (`A9-Habit-edit` script, spec "Habits"; issue #134). */
class HabitDraftTest {

    private val nb = HabitsCopy.NBSP
    private fun plain(s: String) = s.replace(nb, ' ')

    @Test fun a_new_draft_is_20_min_with_no_name_and_no_photo() {
        val d = HabitDraft()
        assertEquals("", d.title)
        assertEquals(20, d.target)
        assertEquals(MIN, d.unit)
        assertFalse(d.photo)
        assertEquals("20 min", plain(d.amount))
    }

    @Test fun unit_chips_start_at_20_10_1_and_8() {
        val d = HabitDraft(title = "Piano")
        assertEquals(20, d.withUnit(MIN).target)
        assertEquals(10, d.withUnit(PAGES).target)
        assertEquals(1, d.withUnit(TIMES).target)
        assertEquals(8, d.withUnit(GLASSES).target)
        assertEquals(listOf(MIN, PAGES, TIMES, GLASSES), HabitDraft.UNITS)
    }

    @Test fun minutes_step_by_5_and_every_other_unit_by_1() {
        val m = HabitDraft(target = 20, unit = MIN)
        assertEquals(25, m.more().target)
        assertEquals(15, m.less().target)
        for (u in listOf(PAGES, TIMES, GLASSES)) {
            val d = HabitDraft(target = 4, unit = u)
            assertEquals("$u up", 5, d.more().target)
            assertEquals("$u down", 3, d.less().target)
        }
    }

    @Test fun the_stepper_never_goes_below_one_step() {
        val m = HabitDraft(target = 5, unit = MIN)
        assertFalse(m.canLess)
        assertEquals(5, m.less().target)
        assertEquals(5, HabitDraft(target = 7, unit = MIN).less().target)
        val t = HabitDraft(target = 1, unit = TIMES)
        assertFalse(t.canLess)
        assertEquals(1, t.less().target)
        assertTrue(HabitDraft(target = 2, unit = TIMES).canLess)
    }

    @Test fun the_stepper_stops_at_three_digits() {
        val d = HabitDraft(target = 995, unit = MIN)
        assertFalse(d.canMore)
        assertEquals(995, d.more().target)
        assertEquals(999, HabitDraft(target = 998, unit = PAGES).more().target)
    }

    @Test fun once_a_day_for_one_time() {
        assertEquals("Once a day", HabitDraft(target = 1, unit = TIMES).amount)
        assertEquals("2 times", plain(HabitDraft(target = 2, unit = TIMES).amount))
        assertEquals("Once a day", HabitDraft.amount(1, HabitDraft.CHECK_IN))
        assertEquals("1 page", plain(HabitDraft.amount(1, PAGES)))
        assertEquals("1 glass", plain(HabitDraft.amount(1, GLASSES)))
        assertEquals("8 glasses", plain(HabitDraft.amount(8, GLASSES)))
        assertEquals("amounts never wrap", "30${nb}min", HabitDraft.amount(30, MIN))
    }

    @Test fun save_is_enabled_only_once_the_name_has_text() {
        assertFalse(HabitDraft().canSave)
        assertFalse(HabitDraft(title = "   ").canSave)
        assertTrue(HabitDraft(title = "Walk").canSave)
        assertEquals("Walk", HabitDraft(title = "  Walk ").name)
    }

    @Test fun names_are_capped_at_28_characters() {
        assertEquals(28, HabitDraft().withTitle("x".repeat(40)).title.length)
    }

    @Test fun each_quick_pick_fills_name_amount_and_unit() {
        val expected = listOf(Triple("Exercise", 30, MIN), Triple("Read", 10, PAGES), Triple("Practice", 20, MIN), Triple("Study", 45, MIN))
        assertEquals(expected, HabitDraft.PRESETS.map { Triple(it.title, it.target, it.unit) })
        for (p in HabitDraft.PRESETS) {
            val d = HabitDraft(title = "x", target = 3, unit = GLASSES).pick(p)
            assertEquals(p.title, d.title)
            assertEquals(p.target, d.target)
            assertEquals(p.unit, d.unit)
        }
    }

    @Test fun unit_chips_show_only_for_a_name_that_is_not_a_quick_pick() {
        assertFalse(HabitDraft().showsUnits)
        assertFalse(HabitDraft(title = " ").showsUnits)
        for (p in HabitDraft.PRESETS) assertFalse(p.title, HabitDraft(title = p.title).showsUnits)
        assertFalse(HabitDraft(title = "Read ").showsUnits)
        assertTrue(HabitDraft(title = "Piano").showsUnits)
        assertTrue(HabitDraft(title = "Reading").showsUnits)
    }

    @Test fun the_photo_switch_hides_how_much_and_saves_a_photo_habit() {
        val d = HabitDraft(title = "Tidy room", target = 30, unit = MIN)
        assertTrue(d.showsAmount)
        assertEquals(ProofType.MANUAL, d.proof)
        val p = d.togglePhoto()
        assertFalse(p.showsAmount)
        assertEquals(ProofType.PHOTO, p.proof)
        assertEquals(1, p.savedTarget)
        assertEquals("photo", p.savedUnit)
        val back = p.togglePhoto()
        assertEquals("turning it off keeps the amount", 30, back.savedTarget)
        assertEquals(MIN, back.savedUnit)
    }

    @Test fun an_existing_habit_fills_the_draft() {
        val m = HabitDraft.of("Read", ProofType.MANUAL, 12, PAGES)
        assertEquals(HabitDraft("Read", 12, PAGES, false), m)
        val p = HabitDraft.of("Tidy room", ProofType.PHOTO, 1, "photo")
        assertTrue(p.photo)
        assertEquals("a photo habit's amount starts at 20 min", "20 min", plain(p.togglePhoto().amount))
    }

    @Test fun list_rows_read_amount_and_tap_when_done() {
        assertEquals("30 min, tap when done", plain(HabitsCopy.detail(ProofType.MANUAL, 30, MIN)))
        assertEquals("10 pages, tap when done", plain(HabitsCopy.detail(ProofType.MANUAL, 10, PAGES)))
        assertEquals("Once a day, tap when done", HabitsCopy.detail(ProofType.MANUAL, 1, TIMES))
        assertEquals("Photo check-in", HabitsCopy.detail(ProofType.PHOTO, 1, "photo"))
        assertEquals("Tap when done", HabitsCopy.detail(ProofType.MANUAL, 1, HabitDraft.CHECK_IN))
    }

    @Test fun the_footer_counts_habits_and_shows_the_limit_at_ten() {
        assertEquals("6 of 10 habits", plain(HabitsCopy.count(6)))
        assertEquals("9 of 10 habits", plain(HabitsCopy.count(9)))
        assertEquals(HabitLimits.LIMIT_REACHED, HabitsCopy.count(10))
    }
}

/** The habit icon is chosen from its name (spec, Habits; issue #134). */
class HabitIconRulesTest {

    @Test fun the_design_habits_get_the_design_icons() {
        // A8-Habits: run, book, music, cap, broom.
        assertEquals("run", HabitIconRules.forName("Exercise"))
        assertEquals("book", HabitIconRules.forName("Read"))
        assertEquals("music", HabitIconRules.forName("Practice guitar"))
        assertEquals("music", HabitIconRules.forName("Practice"))
        assertEquals("study", HabitIconRules.forName("Study"))
        assertEquals("tidy", HabitIconRules.forName("Tidy room"))
    }

    @Test fun a_sleep_like_name_still_gets_the_moon() {
        assertEquals("sleep", HabitIconRules.forName("Bedtime routine"))
        assertEquals("sleep", HabitIconRules.forName("Nap"))
    }

    @Test fun keywords_match_the_start_of_any_word_ignoring_case() {
        assertEquals("book", HabitIconRules.forName("Reading"))
        assertEquals("run", HabitIconRules.forName("Go for a RUN"))
        assertEquals("run", HabitIconRules.forName("Walk the dog"))
        assertEquals("water", HabitIconRules.forName("Drink water"))
        assertEquals("gym", HabitIconRules.forName("20 push-ups"))
        assertEquals("calm", HabitIconRules.forName("Meditate"))
        assertEquals("art", HabitIconRules.forName("Draw something"))
        assertEquals("study", HabitIconRules.forName("Homework"))
        assertEquals("music", HabitIconRules.forName("Piano"))
        assertEquals("write", HabitIconRules.forName("Journal"))
    }

    @Test fun activities_beat_the_room_they_happen_in() {
        assertEquals("book", HabitIconRules.forName("Read in my room"))
        assertEquals("tidy", HabitIconRules.forName("Make my bed"))
        assertEquals("sleep", HabitIconRules.forName("Bedtime by 10"))
    }

    @Test fun an_unknown_name_gets_the_pencil() {
        assertEquals("write", HabitIconRules.FALLBACK)
        assertEquals(HabitIconRules.FALLBACK, HabitIconRules.forName("Call grandma"))
        assertEquals(HabitIconRules.FALLBACK, HabitIconRules.forName(""))
    }

    @Test fun every_key_has_an_icon() {
        val keys = setOf("run", "book", "sleep", "music", "study", "tidy", "water", "gym", "calm", "art", "write", "star")
        HabitIconRules.RULES.forEach { (k, _) -> assertTrue(k, k in keys) }
    }
}
