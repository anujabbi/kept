package com.example.kept.core.lock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.LockState
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GiveUpTime
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
    @Inject lateinit var analytics: Analytics

    private var pollJob: Job? = null
    @Volatile private var state: LockState? = null
    private var lastForeground: String? = null
    private var lastLockShownAt = 0L
    private var lastLockedPkg: String? = null
    private var lockedSince: Long? = null
    private var lastHeartbeat = 0L
    private var gapStart: Long? = null

    /**
     * Installing, replacing or removing an app changes which packages the lock can see (issue #4).
     * Registered here at runtime rather than in the manifest: since Android 8 a manifest receiver
     * is not delivered ACTION_PACKAGE_ADDED at all, while a context-registered one still is.
     */
    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            apps.invalidate()
            allowlist.invalidate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground("KEPT is running", "Checking today's habits")
        // The lock is running again, so the "Lock is off" fallback has done its job (issue #2).
        runCatching { notifications.clearLockOff() }
        registerPackageChanges()
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

    private fun registerPackageChanges() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(this, packageChanges, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
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
            s.today.allDoneOnTime -> "All habits done" to "Apps are open. Promise kept."
            s.today.allDone -> "All habits done" to "Ticked after the give-up time, so today is missed."
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

        // The heartbeat lives inside tick, past the permission gate (issue #2): it says "the lock
        // is being enforced right now", not merely "a process is alive". Revoking usage access
        // mid-window returns above, the heartbeat goes stale, and the watchdog notices.
        if (now - lastHeartbeat > 30_000) {
            prefs.heartbeat(now)
            lastHeartbeat = now
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
            // Reported here rather than in LockActivity: this is the moment the lock actually
            // stepped in front of something, and the debounce above has already collapsed the
            // duplicates an OEM can cause (issue #10).
            analytics.capture(
                "lock_shown",
                mapOf("blocked_package" to fg, "minutes_to_due" to minutesToDue(s, now)),
            )
            LockActivity.show(this, fg, apps.label(fg))
        }
    }

    /** Minutes left before the give-up time. Negative once the window has run past it. */
    private fun minutesToDue(s: LockState, now: Long): Int {
        val due = GiveUpTime.instantFor(
            java.time.LocalDate.now(), s.settings.lockFromMinute, s.settings.dueMinute, java.time.ZoneId.systemDefault(),
        )
        return ((due.toEpochMilli() - now) / 60_000L).toInt()
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
        runCatching { unregisterReceiver(packageChanges) }
        super.onDestroy()
    }

    companion object {
        /**
         * Starts the watcher. Returns false when the platform refused, which on Android 12+ means
         * `ForegroundServiceStartNotAllowedException` from the background (issue #2). KEPT is
         * normally exempt because it holds SYSTEM_ALERT_WINDOW, but that permission can be revoked
         * and OEMs vary, so the caller has to be able to see the failure and fall back.
         */
        fun start(ctx: Context): Boolean = runCatching {
            val i = Intent(ctx, ForegroundWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }.isSuccess

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ForegroundWatcherService::class.java))
        }
    }
}
