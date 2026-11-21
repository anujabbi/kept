package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What Set in the two lock window editors saves, and what it asks during a lock (issue #154). */
class LockWindowEditTest {

    private val from = 7 * 60
    private val due = 21 * 60

    @Test fun lock_apps_from_asks_about_when_the_lock_starts() {
        val edit = LockWindowEdit.of(LockWindowField.LOCK_FROM, 6 * 60 + 30, from, due)!!
        assertEquals(
            "Your apps are locked right now. Changing when the lock starts opens them early. Still change it?",
            edit.confirm,
        )
        assertEquals(SettingsCopy.LOCK_FROM_WHILE_LOCKED, edit.confirm)
        assertEquals(6 * 60 + 30, edit.from)
        assertEquals(due, edit.due)
    }

    @Test fun give_up_time_keeps_its_copy() {
        val edit = LockWindowEdit.of(LockWindowField.GIVE_UP, 22 * 60, from, due)!!
        assertEquals(
            "Your apps are locked right now. Changing the give-up time opens them early. Still change it?",
            edit.confirm,
        )
        assertEquals(SettingsCopy.GIVE_UP_WHILE_LOCKED, edit.confirm)
        assertEquals(from, edit.from)
        assertEquals(22 * 60, edit.due)
    }

    @Test fun set_with_an_unchanged_time_is_no_change() {
        assertNull(LockWindowEdit.of(LockWindowField.LOCK_FROM, from, from, due))
        assertNull(LockWindowEdit.of(LockWindowField.GIVE_UP, due, from, due))
    }

    @Test fun the_other_field_matching_does_not_count_as_unchanged() {
        // Picking the give-up time's value in "Lock apps from" is still a change to the start.
        val edit = LockWindowEdit.of(LockWindowField.LOCK_FROM, due, from, due)!!
        assertEquals(due, edit.from)
    }
}
