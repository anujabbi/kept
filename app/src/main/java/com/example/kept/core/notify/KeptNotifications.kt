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
        const val EXTRA_ROUTE = "route"
    }

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

    private fun post(id: Int, n: android.app.Notification) {
        if (!canPost()) return
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    fun recap(summary: RolloverEngine.DaySummary, unlocks: List<RolloverEngine.Unlock>) {
        val title = when {
            unlocks.isNotEmpty() -> "Sprig evolved into ${unlocks.last().form.displayName}"
            summary.countedForStreak -> "Yesterday's promise was kept"
            summary.shieldConsumed -> "Shield used. Streak held at ${summary.streakEnd}"
            summary.streakReset -> "Streak reset. Today is a fresh start"
            else -> "Yesterday, wrapped up"
        }
        val body = "${summary.habitsDone} of ${summary.habitsTotal} habits · ${summary.streakEnd} day streak"
        post(ID_RECAP, base(CH_RECAP, title, body, "recap").build())
    }

    fun buddy(title: String, body: String) = post(ID_BUDDY, base(CH_BUDDY, title, body, "buddy").build())

    fun reminder(title: String, body: String) = post(ID_REMINDER, base(CH_REMINDER, title, body, "home").build())

    fun protectionLost(reason: String) =
        post(ID_PROTECTION, base(CH_REMINDER, "Lock is off", "$reason. Today won't count until it's fixed.", "settings").build())

    fun clearProtection() = NotificationManagerCompat.from(ctx).cancel(ID_PROTECTION)

    private fun base(channel: String, title: String, body: String, route: String) =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(route))
}
