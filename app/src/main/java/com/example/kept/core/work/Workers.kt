package com.example.kept.core.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LocalStubBuddyRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.RolloverRunner
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GapRules
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.lock.Permissions
import com.example.kept.core.lock.RestartMethod
import com.example.kept.core.lock.ServiceRestarter
import com.example.kept.core.lock.UsageWindowProbe
import com.example.kept.core.notify.KeptNotifications
import com.posthog.PostHog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Runs pending rollovers. Scheduled for 00:05 daily and also from the watchdog. */
@HiltWorker
class RolloverWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val runner: RolloverRunner,
    private val scheduler: WorkScheduler,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runner.runPending()
        scheduler.scheduleNextRollover()
        return Result.success()
    }
}

/**
 * Every 15 minutes: make sure the service is alive, catch missed rollovers, record a protection
 * gap when the lock should have been running but was not.
 */
@HiltWorker
class WatchdogWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val prefs: KeptPreferences,
    private val runner: RolloverRunner,
    private val lockRepo: LockRepository,
    private val permissions: Permissions,
    private val notifications: KeptNotifications,
    private val buddy: LocalStubBuddyRepository,
    private val time: TimeSource,
    private val restarter: ServiceRestarter,
    private val probe: UsageWindowProbe,
    private val scheduler: WorkScheduler,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val settings = prefs.currentSettings()
        if (!settings.onboardingDone) return Result.success()
        runner.runPending()
        buddy.tickDaily()

        val lockState = lockRepo.currentState()
        val snapshot = lockState.snapshot(hardAllowlist = emptySet(), launchable = null)
        val shouldBeLocking = LockPolicy.isLockActive(time.now(), time.localTime(), snapshot)

        val now = time.nowMillis()
        val heartbeat = settings.lastServiceHeartbeat
        val probeInput = GapRules.Input(
            lockShouldBeActive = shouldBeLocking,
            heartbeatAgeMillis = now - heartbeat,
            everHeartbeat = heartbeat > 0,
            screenInteractiveDuringWindow = false,
            lockedAppResumedDuringWindow = false,
        )
        // Only look at usage events once the heartbeat is actually stale: the query is not free and
        // nothing else can turn into a gap.
        val verdict = if (probeInput.stale && shouldBeLocking) {
            val activity = probe.activityDuring(heartbeat, now, snapshot)
            GapRules.evaluate(
                probeInput.copy(
                    screenInteractiveDuringWindow = activity.screenInteractive,
                    lockedAppResumedDuringWindow = activity.lockedAppResumed,
                ),
            )
        } else {
            GapRules.evaluate(probeInput)
        }

        if (verdict.serviceLooksDead) {
            // A direct start normally works because KEPT holds SYSTEM_ALERT_WINDOW; when it does
            // not, the expedited worker retries and owns the "Lock is off" fallback (issue #2).
            if (!restarter.restart(RestartMethod.DIRECT)) scheduler.requestServiceRestart()
        } else {
            ForegroundWatcherService.start(ctx)
        }

        if (verdict.isGap) {
            lockRepo.recordProtectionGap(heartbeat, now, verdict.reason)
            notifications.protectionLost(verdict.reason)
            PostHog.capture("protection_gap_recorded", properties = mapOf("reason" to verdict.name.lowercase()))
        }
        if (shouldBeLocking && !permissions.lockPermissionsGranted()) {
            notifications.protectionLost("A lock permission is off")
        }
        return Result.success()
    }
}

/**
 * Restarts the watcher service from an expedited job, for the case where a direct
 * `startForegroundService` was refused (issue #2). Expedited work needs no permission the user can
 * refuse, unlike `SCHEDULE_EXACT_ALARM` on API 31+. If this fails as well, the persistent
 * "Lock is off" notification is the fallback.
 */
@HiltWorker
class ServiceRestartWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val restarter: ServiceRestarter,
    private val notifications: KeptNotifications,
) : CoroutineWorker(ctx, params) {

    /** Only used on API < 31, where expedited work runs as a foreground worker instead. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(KeptNotifications.ID_RESTART, notifications.restartingNotification())

    override suspend fun doWork(): Result {
        if (!restarter.restart(RestartMethod.EXPEDITED_WORK)) restarter.showLockOff()
        return Result.success()
    }
}

class AlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun habits(): HabitRepository
        fun prefs(): KeptPreferences
        fun notifications(): KeptNotifications
        fun scheduler(): WorkScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = runCatching { EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java) }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = deps.prefs().currentSettings()
                if (settings.onboardingDone && settings.remindersEnabled) {
                    val today = deps.habits().today()
                    if (!today.allDone && today.total > 0) {
                        val streak = deps.prefs().currentSprig().streakDays
                        val left = today.remaining
                        val title = "2 hours left. ${if (streak > 0) "$streak-day streak on the line." else "Sprig is waiting."}"
                        val body = "$left habit${if (left == 1) "" else "s"} to go. Apps stay locked until then."
                        deps.notifications().reminder(title, body)
                    }
                }
                deps.scheduler().scheduleReminder()
            } finally {
                pending.finish()
            }
        }
    }
}

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: KeptPreferences,
    private val time: TimeSource,
) {
    private val wm: WorkManager? get() = runCatching { WorkManager.getInstance(ctx) }.getOrNull()

    fun scheduleAll() {
        scheduleWatchdog()
        scheduleNextRollover()
        CoroutineScope(Dispatchers.IO).launch { scheduleReminder() }
    }

    fun scheduleWatchdog() {
        val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        wm?.enqueueUniquePeriodicWork("kept_watchdog", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /**
     * Expedited one-time work that retries the service start (issue #2). Out of quota it runs as
     * ordinary work rather than being dropped, so the restart still happens, just later.
     */
    fun requestServiceRestart() {
        val req = OneTimeWorkRequestBuilder<ServiceRestartWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        wm?.enqueueUniqueWork("kept_service_restart", ExistingWorkPolicy.REPLACE, req)
    }

    fun scheduleNextRollover() {
        val now = time.localDateTime()
        var target = now.toLocalDate().plusDays(1).atTime(0, 5)
        if (now.toLocalTime().isBefore(LocalTime.of(0, 5)) && now.toLocalDate() == target.toLocalDate().minusDays(1)) {
            target = now.toLocalDate().atTime(0, 5)
        }
        val delay = Duration.between(now, target).coerceAtLeast(Duration.ofMinutes(1))
        val req = OneTimeWorkRequestBuilder<RolloverWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        wm?.enqueueUniqueWork("kept_rollover", ExistingWorkPolicy.REPLACE, req)
    }

    /** Exact-ish alarm at due - 2h. Re-scheduled after each firing. */
    suspend fun scheduleReminder() {
        val s = prefs.currentSettings()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 100, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (!s.onboardingDone || !s.remindersEnabled) return
        val minute = (s.dueMinute - 120).coerceAtLeast(s.lockFromMinute + 30)
        var at: Instant = time.instantAt(time.today(), minute)
        if (!at.isAfter(time.now())) at = time.instantAt(time.today().plusDays(1), minute)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
    }
}
