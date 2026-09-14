package com.example.kept.core.lock

import android.content.Context
import com.example.kept.core.notify.KeptNotifications
import com.posthog.PostHog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** How a restart was attempted. The string is the PostHog `method` property. */
enum class RestartMethod(val eventValue: String) {
    /** Straight from the watchdog worker. */
    DIRECT("direct"),

    /** From an expedited one-time worker, after a direct start was refused. */
    EXPEDITED_WORK("expedited_work"),
}

/**
 * Brings the watcher service back after an OEM or the system killed it (issue #2).
 *
 * Android 12 forbids starting a foreground service from the background, and the old watchdog
 * swallowed the resulting `ForegroundServiceStartNotAllowedException`, so a killed service stayed
 * dead until the user next opened KEPT. Two things make the restart work now:
 *
 * 1. KEPT holds SYSTEM_ALERT_WINDOW (the lock screen cannot be shown without it), which is one of
 *    the documented exemptions from the background foreground-service start restriction, so the
 *    direct start usually succeeds — and, unlike an exact alarm, it needs no permission the user
 *    can refuse. If the user revokes that permission the start is refused instead of pretending.
 * 2. When the direct start is refused, an expedited worker retries from a context the platform
 *    treats more generously. If that fails too, the persistent "Lock is off" notification is the
 *    honest fallback: KEPT says so rather than silently not locking.
 */
@Singleton
class ServiceRestarter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val notifications: KeptNotifications,
) {

    /** Returns true when the platform accepted the start. */
    fun restart(method: RestartMethod): Boolean {
        val started = ForegroundWatcherService.start(ctx)
        PostHog.capture(
            "service_restart_attempted",
            properties = mapOf("method" to method.eventValue, "success" to started),
        )
        if (started) runCatching { notifications.clearLockOff() }
        return started
    }

    /**
     * Last resort: tell the user the lock is off and give them one tap to fix it. The event only
     * fires when a notification was actually posted — without the notifications permission there
     * is nothing to show, and reporting one would overstate what the user saw.
     */
    fun showLockOff() {
        val shown = runCatching { notifications.lockOff() }.getOrDefault(false)
        if (shown) PostHog.capture("lock_off_notification_shown")
    }
}
