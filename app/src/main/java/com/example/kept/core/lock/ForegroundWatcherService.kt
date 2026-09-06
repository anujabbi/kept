package com.example.kept.core.lock

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.LockState
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.minuteOfDayLabel
import com.example.kept.core.notify.KeptNotifications
import com.example.kept.feature.lock.LockActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/**
 * Watches the foreground app and shows [LockActivity] over anything that should be locked.
 * Uses UsageStatsManager events (no AccessibilityService). Polls every second.
 */
@AndroidEntryPoint
class ForegroundWatcherService : LifecycleService() {

    @Inject lateinit var lockRepo: LockRepository
    @Inject lateinit var sprigRepo: SprigRepository
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var notifications: KeptNotifications
    @Inject lateinit var allowlist: AllowlistResolver
    @Inject lateinit var apps: InstalledAppsSource
    @Inject lateinit var permissions: Permissions

    private var pollJob: Job? = null
    @Volatile private var state: LockState? = null
    private var lastForeground: String? = null
    private var lastLockShownAt = 0L
    private var lastLockedPkg: String? = null
    private var lockedSince: Long? = null
    private var lastAccrual = 0L
    private var gapStart: Long? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground("KEPT is running", "Checking today's habits")
        lifecycleScope.launch {
            lockRepo.observeState().collectLatest { s ->
                state = s
                updateNotification(s)
            }
        }
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (pollJob?.isActive != true) startPolling()
        return START_STICKY
    }

    private fun startInForeground(title: String, text: String) {
        val n = notifications.lockNotification(title, text)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, KeptNotifications.ID_LOCK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(KeptNotifications.ID_LOCK, n)
        }
    }

    private fun updateNotification(s: LockState) {
        val remaining = s.today.remaining
        val active = LockPolicy.isLockActive(Instant.now(), LocalTime.now(), s.snapshot(emptySet(), null))
        val (title, text) = when {
            !s.settings.onboardingDone -> "KEPT" to "Finish setup to start locking"
            s.today.total == 0 -> "KEPT" to "No habits set for today"
            s.today.allDone -> "All habits done" to "Apps are open. Promise kept."
            s.breakActiveUntil != null -> "Lock paused" to "Apps re-lock at ${timeLabel(s.breakActiveUntil)}"
            active -> "$remaining habit${if (remaining == 1) "" else "s"} left today" to "Apps locked until ${s.settings.dueMinute.minuteOfDayLabel()}"
            else -> "$remaining habit${if (remaining == 1) "" else "s"} left today" to "Apps lock at ${s.settings.lockFromMinute.minuteOfDayLabel()}"
        }
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)
                .notify(KeptNotifications.ID_LOCK, notifications.lockNotification(title, text))
        }
    }

    private fun timeLabel(i: Instant): String {
        val t = i.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return (t.hour * 60 + t.minute).minuteOfDayLabel()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            var lastQuery = System.currentTimeMillis() - 10_000
            while (isActive) {
                val now = System.currentTimeMillis()
                runCatching { tick(usm, lastQuery, now) }
                lastQuery = now
                if (now - lastAccrual > 30_000) {
                    prefs.heartbeat(now)
                    lastAccrual = now
                }
                delay(1_000)
            }
        }
    }

    private suspend fun tick(usm: UsageStatsManager, from: Long, now: Long) {
        val s = state ?: return
        val hard = allowlist.hardAllowlist()
        val launchable = apps.launchablePackages()
        val snapshot = s.snapshot(hard, launchable)
        val lockActive = LockPolicy.isLockActive(Instant.ofEpochMilli(now), LocalTime.now(), snapshot)

        // Protection monitoring: lock should be active but we cannot see the foreground app.
        if (lockActive && !permissions.lockPermissionsGranted()) {
            if (gapStart == null) {
                gapStart = now
                notifications.protectionLost(if (!permissions.usageAccessGranted()) "Usage access was turned off" else "Display over other apps was turned off")
            }
            return
        } else if (gapStart != null) {
            lockRepo.recordProtectionGap(gapStart!!, now, "A lock permission was off")
            gapStart = null
            notifications.clearProtection()
        }

        // Accrue locked time in whole-minute chunks.
        if (lockActive) {
            val since = lockedSince ?: now.also { lockedSince = it }
            if (now - since >= 60_000) {
                sprigRepo.addLockedTime(now - since)
                lockedSince = now
            }
        } else {
            lockedSince = null
        }

        val fg = foregroundPackage(usm, from - 2_000, now) ?: return
        if (fg != lastForeground) lastForeground = fg
        if (fg == packageName) return

        if (LockPolicy.shouldLock(fg, Instant.ofEpochMilli(now), LocalTime.now(), snapshot)) {
            // Debounce so a slow OEM does not get two LockActivities.
            if (fg == lastLockedPkg && now - lastLockShownAt < 1_500) return
            lastLockedPkg = fg
            lastLockShownAt = now
            LockActivity.show(this, fg, apps.label(fg))
        }
    }

    private fun foregroundPackage(usm: UsageStatsManager, from: Long, to: Long): String? {
        val events = usm.queryEvents(from, to) ?: return null
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val resumed = if (Build.VERSION.SDK_INT >= 29) e.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            else @Suppress("DEPRECATION") (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
            if (resumed) pkg = e.packageName
        }
        return pkg ?: lastForeground
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, ForegroundWatcherService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ForegroundWatcherService::class.java))
        }
    }
}
