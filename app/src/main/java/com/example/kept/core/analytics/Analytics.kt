package com.example.kept.core.analytics

/**
 * Everything KEPT reports (issue #10).
 *
 * One seam in front of PostHog so the app never talks to the SDK directly. That buys three things:
 * events can be muted without the SDK's cooperation, a test can inject [NoOpAnalytics] and get a
 * silent build, and the opt-out rule lives in one place instead of at every call site.
 *
 * KEPT never calls `identify`. The distinct ID is the SDK's random anonymous one, and no event
 * carries a name, an email, a habit title or an app label the user typed.
 */
interface Analytics {

    /** False while the user has "Send anonymous usage data" off. */
    val enabled: Boolean

    fun capture(event: String, properties: Map<String, Any> = emptyMap())

    /** A `$screen` view. [name] becomes `$screen_name`. */
    fun screen(name: String, properties: Map<String, Any> = emptyMap())

    /** A super property: sent with every event from here on, and persisted across sessions. */
    fun register(key: String, value: Any)

    /**
     * Applies the preference stored in Settings at startup. Silent: the user did not just decide
     * anything, so neither `analytics_opt_in` nor `analytics_opt_out` belongs here.
     */
    fun restore(enabled: Boolean)

    /** The Settings toggle. Reports the change itself, on whichever side of it can still be heard. */
    fun setEnabled(enabled: Boolean)
}

/**
 * The opt-out gate, kept separate from the SDK.
 *
 * `PostHog.optOut()` alone would not be enough: the SDK is not set up at all in tests or in a build
 * with no project token, so "the user said no" has to be enforced here to be true everywhere and
 * testable anywhere.
 */
abstract class GatedAnalytics : Analytics {

    @Volatile private var on = true

    final override val enabled: Boolean get() = on

    final override fun capture(event: String, properties: Map<String, Any>) {
        if (!on) return
        sendCapture(event, properties)
    }

    final override fun screen(name: String, properties: Map<String, Any>) {
        if (!on) return
        sendScreen(name, properties)
    }

    final override fun register(key: String, value: Any) {
        if (!on) return
        sendRegister(key, value)
    }

    final override fun restore(enabled: Boolean) {
        on = enabled
        if (enabled) sendOptIn() else sendOptOut()
    }

    final override fun setEnabled(enabled: Boolean) {
        if (enabled == on) return
        if (enabled) {
            // Unmute first, or the event announcing the opt-in would be dropped by its own gate.
            sendOptIn()
            on = true
            capture(EVENT_OPT_IN)
        } else {
            // The last thing sent before the gate closes, so a project can see people leaving.
            capture(EVENT_OPT_OUT)
            on = false
            sendOptOut()
        }
    }

    protected abstract fun sendCapture(event: String, properties: Map<String, Any>)
    protected abstract fun sendScreen(name: String, properties: Map<String, Any>)
    protected abstract fun sendRegister(key: String, value: Any)
    protected abstract fun sendOptOut()
    protected abstract fun sendOptIn()

    companion object {
        const val EVENT_OPT_IN = "analytics_opt_in"
        const val EVENT_OPT_OUT = "analytics_opt_out"
    }
}

/** Reports nothing. Used by tests that build a ViewModel by hand. */
class NoOpAnalytics : GatedAnalytics() {
    override fun sendCapture(event: String, properties: Map<String, Any>) = Unit
    override fun sendScreen(name: String, properties: Map<String, Any>) = Unit
    override fun sendRegister(key: String, value: Any) = Unit
    override fun sendOptOut() = Unit
    override fun sendOptIn() = Unit
}
