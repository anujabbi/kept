package com.example.kept.core.domain

import com.example.kept.core.domain.HabitDraft.Companion.GLASSES
import com.example.kept.core.domain.HabitDraft.Companion.MIN
import com.example.kept.core.domain.HabitDraft.Companion.PAGES
import com.example.kept.core.domain.HabitDraft.Companion.TIMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a daily habit saves and how its amount reads (spec "Habits"; issues #134, #218). */
class HabitDraftTest {

    private val nb = HabitsCopy.NBSP
    private fun plain(s: String) = s.replace(nb, ' ')

    @Test fun once_a_day_for_one_time() {
        assertEquals("Once a day", HabitDraft.amount(1, TIMES))
        assertEquals("2 times", plain(HabitDraft.amount(2, TIMES)))
        assertEquals("Once a day", HabitDraft.amount(1, HabitDraft.CHECK_IN))
        assertEquals("1 page", plain(HabitDraft.amount(1, PAGES)))
        assertEquals("1 glass", plain(HabitDraft.amount(1, GLASSES)))
        assertEquals("8 glasses", plain(HabitDraft.amount(8, GLASSES)))
        assertEquals("amounts never wrap", "30${nb}min", HabitDraft.amount(30, MIN))
    }

    @Test fun a_photo_habit_is_saved_as_one_photo() {
        val d = HabitDraft(title = "Tidy room", target = 30, unit = MIN, photo = true)
        assertEquals(ProofType.PHOTO, d.proof)
        assertEquals(1, d.savedTarget)
        assertEquals("photo", d.savedUnit)
        assertEquals(ProofType.MANUAL, d.copy(photo = false).proof)
    }

    @Test fun list_rows_read_amount_and_tap_when_done() {
        assertEquals("30 min, tap when done", plain(HabitsCopy.detail(ProofType.MANUAL, 30, MIN)))
        assertEquals("10 pages, tap when done", plain(HabitsCopy.detail(ProofType.MANUAL, 10, PAGES)))
        assertEquals("Once a day, tap when done", HabitsCopy.detail(ProofType.MANUAL, 1, TIMES))
        assertEquals("Photo check-in", HabitsCopy.detail(ProofType.PHOTO, 1, "photo"))
        assertEquals("Tap when done", HabitsCopy.detail(ProofType.MANUAL, 1, HabitDraft.CHECK_IN))
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
