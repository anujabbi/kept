package com.example.kept.core.lock

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.notify.KeptNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** How a restart was attempted. The string is the PostHog `method` property. */
enum class RestartMethod(val eventValue: String) {
    /** Straight from the watchdog worker. */
    DIRECT("direct"),

    /** From an expedited one-time worker, after a direct start was refused. */
    EXPEDITED_WORK("expedited_work"),

    /** From a near-immediate exact alarm, which *is* a documented FGS-start exemption. */
    EXACT_ALARM("exact_alarm"),
}

/**
 * Which rung to try after one fails (issue #2). Pure, so the ladder can be asserted without a
 * platform.
 *
 * The rungs are not interchangeable. Expedited work is **not** an exemption from the Android 12+
 * background foreground-service-start restriction — it gets a job run sooner, nothing more — so
 * when the direct start was refused for that reason, the expedited retry is refused for the same
 * reason. Delivery of an exact alarm *is* on the documented exemption list, which is why it is the
 * rung that can actually rescue a refused start. It costs a permission the user can revoke
 * (`SCHEDULE_EXACT_ALARM`), so when that is not granted there is nothing left to try and KEPT says
 * so rather than pretending to protect.
 */
object RestartLadder {
    enum class Next {
        EXPEDITED_WORK,
        EXACT_ALARM,

        /** Nothing left to try: post the persistent "Lock is off" notification. */
        LOCK_OFF_NOTIFICATION,
    }

    fun after(failed: RestartMethod, canScheduleExactAlarms: Boolean): Next = when (failed) {
        // Cheap and often enough: a worker start runs in a slightly more generous context, and
        // covers the ordinary "the process was killed" case without touching a permission.
        RestartMethod.DIRECT -> Next.EXPEDITED_WORK
        RestartMethod.EXPEDITED_WORK ->
            if (canScheduleExactAlarms) Next.EXACT_ALARM else Next.LOCK_OFF_NOTIFICATION
        RestartMethod.EXACT_ALARM -> Next.LOCK_OFF_NOTIFICATION
    }
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
 *    treats more generously.
 * 3. When that is refused as well, a near-immediate **exact alarm** starts the service from its
 *    receiver. Unlike expedited work, exact-alarm delivery is on the documented exemption list for
 *    the background foreground-service-start restriction, so this is the rung that can genuinely
 *    rescue a refused start. It needs `SCHEDULE_EXACT_ALARM`, which the user can revoke; when it is
 *    not granted the ladder skips straight to the end.
 * 4. The persistent "Lock is off" notification is the honest fallback: KEPT says so rather than
 *    silently not locking.
 *
 * [RestartLadder] holds that order as a pure function.
 */
@Singleton
class ServiceRestarter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val notifications: KeptNotifications,
    private val analytics: Analytics,
) {

    /**
     * Whether the exact-alarm rung is available at all. Below API 31 exact alarms need no
     * permission; from 31 the user can refuse or revoke `SCHEDULE_EXACT_ALARM`, and an unusable
     * rung must not be attempted or the ladder would end one step short of the notification.
     */
    fun canScheduleExactAlarms(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 31) true
        else ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }.getOrDefault(false)

    /** Returns true when the platform accepted the start. */
    fun restart(method: RestartMethod): Boolean {
        val started = ForegroundWatcherService.start(ctx)
        analytics.capture(
            "service_restart_attempted",
            mapOf("method" to method.eventValue, "success" to started),
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
        if (shown) analytics.capture("lock_off_notification_shown")
    }
}
