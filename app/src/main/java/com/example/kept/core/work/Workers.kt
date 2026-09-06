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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LocalStubBuddyRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.RolloverRunner
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.lock.Permissions
import com.example.kept.core.notify.KeptNotifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
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
    private val habits: HabitRepository,
    private val permissions: Permissions,
    private val notifications: KeptNotifications,
    private val buddy: LocalStubBuddyRepository,
    private val time: TimeSource,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val settings = prefs.currentSettings()
        if (!settings.onboardingDone) return Result.success()
        runner.runPending()
        buddy.tickDaily()

        val today = habits.today()
        val snap = LockPolicy.Snapshot(
            settings.lockFromMinute, settings.dueMinute, habitsIncomplete = !today.allDone && today.total > 0,
            breakActiveUntil = null, hardAllowlist = emptySet(), userExceptions = emptySet(), launchable = null,
        )
        val shouldBeLocking = LockPolicy.isLockActive(time.now(), time.localTime(), snap)
        val heartbeatAge = time.nowMillis() - settings.lastServiceHeartbeat
        if (shouldBeLocking && heartbeatAge > 2 * 60_000 && settings.lastServiceHeartbeat > 0) {
            lockRepo.recordProtectionGap(settings.lastServiceHeartbeat, time.nowMillis(), "Lock service was stopped")
            notifications.protectionLost("The lock was stopped")
        }
        if (shouldBeLocking && !permissions.usageAccessGranted()) {
            notifications.protectionLost("Usage access is off")
        }
        ForegroundWatcherService.start(ctx)
        return Result.success()
    }
}

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var notifications: KeptNotifications
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var sprigPrefs: KeptPreferences

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = prefs.currentSettings()
                if (settings.onboardingDone && settings.remindersEnabled) {
                    val today = habits.today()
                    if (!today.allDone && today.total > 0) {
                        val streak = sprigPrefs.currentSprig().streakDays
                        val left = today.remaining
                        val title = "2 hours left. ${if (streak > 0) "$streak-day streak on the line." else "Sprig is waiting."}"
                        val body = "$left habit${if (left == 1) "" else "s"} to go. Apps stay locked until then."
                        notifications.reminder(title, body)
                    }
                }
                scheduler.scheduleReminder()
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
    private val wm get() = WorkManager.getInstance(ctx)

    fun scheduleAll() {
        scheduleWatchdog()
        scheduleNextRollover()
        CoroutineScope(Dispatchers.IO).launch { scheduleReminder() }
    }

    fun scheduleWatchdog() {
        val req = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork("kept_watchdog", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun scheduleNextRollover() {
        val now = time.localDateTime()
        var target = now.toLocalDate().plusDays(1).atTime(0, 5)
        if (now.toLocalTime().isBefore(LocalTime.of(0, 5)) && now.toLocalDate() == target.toLocalDate().minusDays(1)) {
            target = now.toLocalDate().atTime(0, 5)
        }
        val delay = Duration.between(now, target).coerceAtLeast(Duration.ofMinutes(1))
        val req = OneTimeWorkRequestBuilder<RolloverWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        wm.enqueueUniqueWork("kept_rollover", ExistingWorkPolicy.REPLACE, req)
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
