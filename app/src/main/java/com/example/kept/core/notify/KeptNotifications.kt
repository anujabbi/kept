package com.example.kept.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kept.MainActivity
import com.example.kept.R
import com.example.kept.core.domain.ReminderKind
import com.example.kept.core.domain.RolloverEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeptNotifications @Inject constructor(@ApplicationContext private val ctx: Context) {

    companion object {
        const val CH_LOCK = "kept_lock"
        const val CH_RECAP = "kept_recap"
        const val CH_BUDDY = "kept_buddy"
        const val CH_REMINDER = "kept_reminder"
        const val ID_LOCK = 1
        const val ID_RECAP = 2
        const val ID_BUDDY = 3
        const val ID_REMINDER = 4
        const val ID_PROTECTION = 5
        const val ID_LOCK_OFF = 6
        const val ID_RESTART = 7
        const val ID_DAY_DONE = 8
        const val EXTRA_ROUTE = "route"

        /**
         * The [com.example.kept.core.domain.ReminderKind] a reminder intent belongs to. One
         * constant, shared by the alarm that fires a rung, the "Mark done" broadcast and the
         * "Open KEPT" activity intent (issue #9).
         */
        const val EXTRA_REMINDER_KIND = "reminder_kind"

        /** Request-code bases, kept apart so one action's PendingIntent never replaces another's. */
        private const val REQ_MARK_DONE = 1_000
        private const val REQ_OPEN_FROM_REMINDER = 900
    }

    /** One "Mark done" button on a reminder. */
    data class MarkDoneAction(val habitId: Long, val label: String)

    init { createChannels() }

    private fun createChannels() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LOCK, "KEPT lock status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows while apps are locked. Required to keep the lock running."
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_RECAP, "KEPT daily recap", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BUDDY, "KEPT buddy", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "KEPT reminders", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(route: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            route?.let { putExtra(EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(ctx, route?.hashCode() ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** The persistent notification the foreground service owns. */
    fun lockNotification(title: String, text: String): android.app.Notification =
        NotificationCompat.Builder(ctx, CH_LOCK)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(null))
            .build()

    /** Returns false when the notification could not be posted (permission not granted). */
    private fun post(id: Int, n: android.app.Notification): Boolean {
        if (!canPost()) return false
        NotificationManagerCompat.from(ctx).notify(id, n)
        return true
    }

    fun recap(summary: RolloverEngine.DaySummary, unlocks: List<RolloverEngine.Unlock>) {
        val title = when {
            unlocks.isNotEmpty() -> "Sprig evolved into ${unlocks.last().form.displayName}"
            summary.hollow -> "The lock was off. Streak held at ${summary.streakEnd}"
            summary.countedForStreak -> "Yesterday's promise was kept"
            summary.shieldConsumed -> "Shield used. Streak held at ${summary.streakEnd}"
            summary.streakReset -> "Streak reset. Today is a fresh start"
            else -> "Yesterday, wrapped up"
        }
        val body = "${summary.habitsDone} of ${summary.habitsTotal} habits · ${summary.streakEnd} day streak"
        post(ID_RECAP, base(CH_RECAP, title, body, "recap").build())
    }

    fun buddy(title: String, body: String) = post(ID_BUDDY, base(CH_BUDDY, title, body, "buddy").build())

    /**
     * A rung of the reminder ladder (issue #9). Each remaining habit carries a "Mark done" action
     * that completes it without opening KEPT; past three habits the last slot becomes "Open KEPT",
     * because Android draws no more than three actions.
     */
    fun reminder(
        kind: ReminderKind,
        title: String,
        body: String,
        actions: List<MarkDoneAction> = emptyList(),
        openApp: Boolean = false,
        /** True for a redraw after a habit was ticked from the buttons: update, do not buzz again. */
        alertOnce: Boolean = false,
    ): Boolean {
        val b = base(CH_REMINDER, title, body, "home").setOnlyAlertOnce(alertOnce)
        actions.forEach { a ->
            val i = Intent(ctx, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_DONE
                putExtra(NotificationActionReceiver.EXTRA_HABIT_ID, a.habitId)
                putExtra(EXTRA_REMINDER_KIND, kind.eventValue)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, REQ_MARK_DONE + a.habitId.toInt(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(R.drawable.ic_stat_kept, a.label, pi)
        }
        if (openApp) {
            // An activity PendingIntent, not a broadcast: Android 12 forbids a notification action
            // from starting an activity via a receiver. The event is captured in MainActivity.
            val i = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ROUTE, "home")
                putExtra(EXTRA_REMINDER_KIND, kind.eventValue)
            }
            val pi = PendingIntent.getActivity(
                ctx, REQ_OPEN_FROM_REMINDER + kind.ordinal, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(R.drawable.ic_stat_kept, "Open KEPT", pi)
        }
        return post(ID_REMINDER, b.build())
    }

    fun clearReminder() = NotificationManagerCompat.from(ctx).cancel(ID_REMINDER)

    /**
     * The day was finished with nothing of KEPT on screen (issue #9), so the celebration cannot be
     * shown. This says the one thing that matters; the full-screen moment waits for the next open.
     */
    fun dayComplete(): Boolean =
        post(ID_DAY_DONE, base(CH_RECAP, "All habits done", "Apps are open. Promise kept.", "home").build())

    /**
     * The day could not be protected. Distinct from [lockOff], which is about the service being
     * down: this one is about the day, and it now says the streak survives (issue #2).
     */
    fun protectionLost(reason: String) =
        post(ID_PROTECTION, base(CH_REMINDER, "The lock stopped", "$reason. Today won't count, but your streak is safe.", "settings").build())

    fun clearProtection() = NotificationManagerCompat.from(ctx).cancel(ID_PROTECTION)

    /**
     * Last resort when the lock service could not be restarted (issue #2). Ongoing and without an
     * auto-cancel, so it stays until the lock is actually running again: tapping it opens KEPT,
     * which starts the service on resume.
     */
    fun lockOff(): Boolean = post(
        ID_LOCK_OFF,
        NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setContentTitle("Lock is off")
            .setContentText("Tap to turn it back on.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Tap to turn it back on."))
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp("home"))
            .build(),
    )

    fun clearLockOff() = NotificationManagerCompat.from(ctx).cancel(ID_LOCK_OFF)

    /** Shown only while an expedited restart runs as a foreground worker (API < 31). */
    fun restartingNotification(): android.app.Notification =
        NotificationCompat.Builder(ctx, CH_LOCK)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setContentTitle("KEPT")
            .setContentText("Turning the lock back on")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(null))
            .build()

    private fun base(channel: String, title: String, body: String, route: String) =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(route))
}
