package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Night lockdown setting's rules (issue #164, story #168). */
class NightWindowTest {
    private val off = NightSetting(enabled = false, fromMinute = 23 * 60, untilMinute = 7 * 60)
    private val on = off.copy(enabled = true)

    private fun plain(s: String) = s.replace(SettingsCopy.NBSP, ' ')

    @Test fun `the defaults are off, 11 pm to 7 am`() {
        assertEquals(NightSetting(false, 1380, 420), NightSetting())
        assertEquals(1380, NightWindow.DEFAULT_FROM)
        assertEquals(420, NightWindow.DEFAULT_UNTIL)
    }

    @Test fun `the presets are 10 pm, 11 pm and midnight`() {
        assertEquals(listOf(1320, 1380, 0), NightWindow.PRESETS)
        assertEquals(listOf("10:00 pm", "11:00 pm", "Midnight"), NightWindow.PRESETS.map { plain(SettingsCopy.nightTime(it)) })
        assertEquals("7:00 am", plain(SettingsCopy.nightTime(420)))
        assertEquals("10:30 pm", plain(SettingsCopy.nightTime(22 * 60 + 30)))
    }

    @Test fun `a window is as long as the minutes from start to end, past midnight`() {
        assertEquals(8 * 60, NightWindow.lengthMinutes(23 * 60, 7 * 60))
        assertEquals(7 * 60, NightWindow.lengthMinutes(0, 7 * 60))
        assertEquals(4 * 60, NightWindow.lengthMinutes(60, 5 * 60))
        assertEquals(0, NightWindow.lengthMinutes(23 * 60, 23 * 60))
    }

    @Test fun `a window must be 1 to 12 hours long`() {
        assertTrue(NightWindow.isValid(23 * 60, 7 * 60))
        assertTrue("exactly 1 hour", NightWindow.isValid(23 * 60, 0))
        assertTrue("exactly 12 hours", NightWindow.isValid(22 * 60, 10 * 60))
        assertFalse("the end equals the start", NightWindow.isValid(23 * 60, 23 * 60))
        assertFalse("59 minutes", NightWindow.isValid(23 * 60, 23 * 60 + 59))
        assertFalse("30 minutes", NightWindow.isValid(23 * 60, 23 * 60 + 30))
        assertFalse("12 hours and a minute", NightWindow.isValid(22 * 60, 10 * 60 + 1))
        assertFalse("23 hours", NightWindow.isValid(23 * 60, 22 * 60))
    }

    @Test fun `an invalid pick has the one error line and a valid one has none`() {
        assertEquals("Pick an end time 1 to 12 hours after the start.", SettingsCopy.NIGHT_WINDOW_ERROR)
        assertEquals(SettingsCopy.NIGHT_WINDOW_ERROR, NightWindow.error(23 * 60, 23 * 60))
        assertEquals(SettingsCopy.NIGHT_WINDOW_ERROR, NightWindow.error(23 * 60, 12 * 60))
        assertNull(NightWindow.error(23 * 60, 7 * 60))
    }

    @Test fun `turning the switch on with nothing else changed stores 11 pm to 7 am`() {
        val edit = NightEdit.switch(NightSetting(), on = true)!!
        assertEquals(NightSetting(true, 1380, 420), edit.result)
        assertNull("turning it on never opens a lock", edit.confirm)
    }

    @Test fun `turning the switch off asks, in its own words, and keeps the times`() {
        val edit = NightEdit.switch(on.copy(fromMinute = 22 * 60), on = false)!!
        assertEquals(NightSetting(false, 22 * 60, 7 * 60), edit.result)
        assertEquals("Your apps are locked right now. Turning off night lockdown opens them. Still turn it off?", edit.confirm)
    }

    @Test fun `a switch that changes nothing does nothing`() {
        assertNull(NightEdit.switch(on, on = true))
        assertNull(NightEdit.switch(off, on = false))
    }

    @Test fun `picking a preset start stores it`() {
        assertEquals(1320, NightEdit.start(on, 1320)!!.result.fromMinute)
        assertEquals(0, NightEdit.start(on, 0)!!.result.fromMinute)
        assertEquals(1380, NightEdit.start(on.copy(fromMinute = 0), 1380)!!.result.fromMinute)
        assertNull("11 pm is already saved", NightEdit.start(on, 1380))
    }

    @Test fun `a custom start and a custom end save`() {
        assertEquals(NightSetting(true, 21 * 60 + 30, 7 * 60), NightEdit.start(on, 21 * 60 + 30)!!.result)
        assertEquals(NightSetting(true, 23 * 60, 6 * 60 + 30), NightEdit.end(on, 6 * 60 + 30)!!.result)
    }

    @Test fun `a start or an end that breaks the 1 to 12 hour rule is not an edit`() {
        assertNull("end equals start", NightEdit.end(on, 23 * 60))
        assertNull("30 minutes long", NightEdit.end(on, 23 * 60 + 30))
        assertNull("13 hours long", NightEdit.end(on, 12 * 60))
        assertNull("start 13 hours before the end", NightEdit.start(on, 18 * 60))
        assertNull("start equals the end", NightEdit.start(on, 7 * 60))
    }

    @Test fun `starting later or ending earlier asks, starting earlier or ending later does not`() {
        assertEquals(SettingsCopy.NIGHT_START_WHILE_LOCKED, NightEdit.start(on, 0)!!.confirm)
        assertEquals(SettingsCopy.NIGHT_END_WHILE_LOCKED, NightEdit.end(on, 6 * 60)!!.confirm)
        assertNull(NightEdit.start(on, 22 * 60)!!.confirm)
        assertNull(NightEdit.end(on, 8 * 60)!!.confirm)
    }

    @Test fun `a new window covers the old one only when every locked minute stays locked`() {
        // 23:00 to 07:00 inside 22:00 to 08:00.
        assertTrue(NightWindow.covers(22 * 60, 8 * 60, 23 * 60, 7 * 60))
        assertTrue(NightWindow.covers(23 * 60, 7 * 60, 23 * 60, 7 * 60))
        assertFalse(NightWindow.covers(0, 7 * 60, 23 * 60, 7 * 60))
        assertFalse(NightWindow.covers(23 * 60, 6 * 60, 23 * 60, 7 * 60))
        // The same length, moved: some locked minutes open.
        assertFalse(NightWindow.covers(1 * 60, 9 * 60, 23 * 60, 7 * 60))
    }

    @Test fun `every confirm starts the way the other lock confirms do`() {
        for (c in listOf(SettingsCopy.NIGHT_OFF_WHILE_LOCKED, SettingsCopy.NIGHT_START_WHILE_LOCKED, SettingsCopy.NIGHT_END_WHILE_LOCKED)) {
            assertTrue(c, c.startsWith("Your apps are locked right now. "))
        }
    }
}
