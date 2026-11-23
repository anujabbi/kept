package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SettingsLayoutTest {

    private fun plain(s: String) = s.replace(SettingsCopy.NBSP, ' ')

    @Test fun row_values_read_as_in_the_design() {
        assertEquals("6", SettingsCopy.habits(6))
        assertEquals("7:00 am", plain(SettingsCopy.time(7 * 60)))
        assertEquals("9:00 pm", plain(SettingsCopy.time(21 * 60)))
        assertEquals("2 apps", plain(SettingsCopy.exceptions(2)))
        assertEquals("1 app", plain(SettingsCopy.exceptions(1)))
        assertEquals("0 apps", plain(SettingsCopy.exceptions(0)))
        assertEquals("30 min", plain(SettingsCopy.minutes(30)))
        assertEquals("Best streak 12", plain(SettingsCopy.bestStreak(12)))
    }

    /** Values sit on one line beside a title; their spaces never break. */
    @Test fun values_and_history_cells_use_no_break_spaces() {
        for (v in listOf(SettingsCopy.time(7 * 60), SettingsCopy.exceptions(2), SettingsCopy.minutes(15), SettingsCopy.permissions(2),
            SettingsCopy.bestStreak(3), SettingsCopy.habitsDone(1, 2), SettingsCopy.date(LocalDate.of(2026, 9, 29))) +
            RecapOutcome.entries.map(SettingsCopy::outcome)) {
            assertFalse("\"$v\" has a breaking space", v.contains(' '))
        }
    }

    @Test fun history_line_and_cells() {
        assertEquals("Your last 14 days. Best streak: 12 days.", SettingsCopy.historyLine(12))
        assertEquals("Your last 14 days. Best streak: 1 day.", SettingsCopy.historyLine(1))
        assertEquals("Your last 14 days. Best streak: 0 days.", SettingsCopy.historyLine(0))
        assertEquals("2 of 2", plain(SettingsCopy.habitsDone(2, 2)))
        assertEquals("Tue 29 Sep", plain(SettingsCopy.date(LocalDate.of(2026, 9, 29))))
        assertEquals("Wed 16 Sep", plain(SettingsCopy.date(LocalDate.of(2026, 9, 16))))
    }

    @Test fun the_title_gets_the_room_beside_sprig() {
        // 390 − 24 − 14 − 130 − 8.
        assertEquals(214, SettingsSky.titleWidthDp(390))
        assertEquals(184, SettingsSky.titleWidthDp(360))
        assertEquals(236, SettingsSky.titleWidthDp(412))
    }

    @Test fun a_taller_title_moves_the_crest_down() {
        assertEquals(142, SettingsSky.crestDp(104))
        assertEquals(142, SettingsSky.crestDp(130))
        assertEquals(160, SettingsSky.crestDp(148))
    }

    /** At the design's 390dp Sprig keeps its place: top 34, bottom 22 under the crest. */
    @Test fun sprig_sits_where_the_design_puts_it() {
        assertEquals(34, SettingsSky.sprigTopDp(142, 390))
    }

    @Test fun a_value_goes_under_the_title_only_when_they_do_not_fit_side_by_side() {
        assertTrue(SettingsRowFit.sideBySide(titleDp = 60f, hintWordDp = 0f, trailingDp = 80f, innerDp = 320f))
        assertTrue(SettingsRowFit.sideBySide(titleDp = 228f, hintWordDp = 0f, trailingDp = 80f, innerDp = 320f))
        assertFalse(SettingsRowFit.sideBySide(titleDp = 229f, hintWordDp = 0f, trailingDp = 80f, innerDp = 320f))
        assertFalse(SettingsRowFit.sideBySide(titleDp = 60f, hintWordDp = 240f, trailingDp = 80f, innerDp = 320f))
    }
}
