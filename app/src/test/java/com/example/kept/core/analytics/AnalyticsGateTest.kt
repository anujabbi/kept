package com.example.kept.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The opt-out gate (issue #10).
 *
 * The wrapper mutes itself rather than leaning on `PostHog.optOut()` alone: the SDK is not set up
 * in tests or in a build without a project token, and "the user said no" has to hold either way.
 * Recording every hand-off to the SDK also pins the ordering that makes the toggle honest — the
 * `analytics_opt_out` event has to reach the SDK before the SDK is told to stop.
 */
class AnalyticsGateTest {

    private class Recorder : GatedAnalytics() {
        val sent = mutableListOf<String>()
        override fun sendCapture(event: String, properties: Map<String, Any>) { sent += "capture:$event" }
        override fun sendScreen(name: String, properties: Map<String, Any>) { sent += "screen:$name" }
        override fun sendRegister(key: String, value: Any) { sent += "register:$key=$value" }
        override fun sendOptOut() { sent += "optOut" }
        override fun sendOptIn() { sent += "optIn" }
    }

    @Test fun on_by_default_everything_reaches_the_sdk() {
        val a = Recorder()
        assertTrue(a.enabled)
        a.capture("habit_completed", mapOf("before_due" to true))
        a.screen("Home")
        a.register("streak", 3)
        assertEquals(listOf("capture:habit_completed", "screen:Home", "register:streak=3"), a.sent)
    }

    @Test fun opting_out_reports_itself_before_the_sdk_is_muted() {
        val a = Recorder()
        a.setEnabled(false)
        assertEquals(listOf("capture:analytics_opt_out", "optOut"), a.sent)
        assertFalse(a.enabled)
    }

    @Test fun nothing_is_sent_while_opted_out() {
        val a = Recorder()
        a.setEnabled(false)
        a.sent.clear()
        a.capture("habit_completed")
        a.screen("Home")
        a.register("streak", 3)
        assertEquals(emptyList<String>(), a.sent)
    }

    @Test fun opting_back_in_unmutes_the_sdk_before_reporting_itself() {
        val a = Recorder()
        a.setEnabled(false)
        a.sent.clear()
        a.setEnabled(true)
        assertEquals(listOf("optIn", "capture:analytics_opt_in"), a.sent)
        assertTrue(a.enabled)
    }

    @Test fun setting_the_toggle_to_what_it_already_is_reports_nothing() {
        val a = Recorder()
        a.setEnabled(true)
        assertEquals(emptyList<String>(), a.sent)
        a.setEnabled(false)
        a.sent.clear()
        a.setEnabled(false)
        assertEquals(emptyList<String>(), a.sent)
    }

    /** Startup applies the stored preference; the user did not just change anything, so no event. */
    @Test fun restoring_the_stored_preference_is_silent() {
        val a = Recorder()
        a.restore(false)
        assertFalse(a.enabled)
        assertEquals(listOf("optOut"), a.sent)

        val b = Recorder()
        b.restore(true)
        assertTrue(b.enabled)
        assertEquals(listOf("optIn"), b.sent)
    }

    /** A restored opt-out still mutes captures, even though it fired no event. */
    @Test fun a_restored_opt_out_mutes_captures() {
        val a = Recorder()
        a.restore(false)
        a.sent.clear()
        a.capture("day_completed")
        assertEquals(emptyList<String>(), a.sent)
    }

    @Test fun the_no_op_implementation_never_throws() {
        val a = NoOpAnalytics()
        a.capture("habit_completed", mapOf("before_due" to true))
        a.screen("Home")
        a.register("streak", 1)
        a.setEnabled(false)
        a.restore(true)
        assertTrue(a.enabled)
    }
}
