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

        /** Stands in for the SDK's persisted super properties, which outlive an opt-out. */
        val superProperties = mutableMapOf<String, Any>()

        override fun sendCapture(event: String, properties: Map<String, Any>) { sent += "capture:$event" }
        override fun sendScreen(name: String, properties: Map<String, Any>) { sent += "screen:$name" }
        override fun sendRegister(key: String, value: Any) {
            sent += "register:$key=$value"
            superProperties[key] = value
        }
        override fun sendOptOut() { sent += "optOut" }
        override fun sendOptIn() { sent += "optIn" }
    }

    /** What `AnalyticsInitializer` registers, in the order it registers them. */
    private fun Recorder.registerAllSuperProperties(streak: Int = 7) {
        register("app_version", "1.0")
        register("android_sdk", 35)
        register("manufacturer", "Google")
        register("habit_count", 2)
        register("streak", streak)
    }

    private fun allSuperProperties(streak: Int = 7) = mapOf<String, Any>(
        "app_version" to "1.0", "android_sdk" to 35, "manufacturer" to "Google",
        "habit_count" to 2, "streak" to streak,
    )

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

    @Test fun nothing_is_reported_while_opted_out() {
        val a = Recorder()
        a.setEnabled(false)
        a.sent.clear()
        a.capture("habit_completed")
        a.screen("Home")
        assertEquals(emptyList<String>(), a.sent)
    }

    /**
     * A super property is bookkeeping, not a report: it travels with future events and sends
     * nothing by itself. Dropping it while muted lost every one of them for a user who opted out
     * and back in, because the sources behind them are `distinctUntilChanged` and never re-emit.
     */
    @Test fun super_properties_survive_an_opt_out_and_opt_in_cycle() {
        val a = Recorder()
        a.registerAllSuperProperties(streak = 1)
        a.setEnabled(false)

        // The streak moved while the user was opted out. Its source is distinctUntilChanged, so
        // this is the only emission there will ever be for the value 7.
        a.register("streak", 7)

        a.setEnabled(true)
        assertEquals(allSuperProperties(streak = 7), a.superProperties)
    }

    /** A user who launches already opted out, then changes their mind, is not left with none. */
    @Test fun super_properties_registered_while_opted_out_are_there_after_opting_in() {
        val a = Recorder()
        a.restore(false)
        a.registerAllSuperProperties()
        assertEquals(allSuperProperties(), a.superProperties)

        a.setEnabled(true)
        a.sent.clear()
        a.capture("habit_completed")
        assertEquals(listOf("capture:habit_completed"), a.sent)
        assertEquals(allSuperProperties(), a.superProperties)
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
