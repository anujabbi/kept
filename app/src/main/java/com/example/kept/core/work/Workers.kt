package com.example.kept.core.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.RolloverRunner
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GapRules
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.LockWindowStart
import com.example.kept.core.domain.ReminderKind
import com.example.kept.core.domain.ReminderLadder
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.lock.Permissions
import com.example.kept.core.lock.RestartLadder
import com.example.kept.core.lock.RestartMethod
import com.example.kept.core.lock.ServiceRestarter
import com.example.kept.core.lock.UsageWindowProbe
import com.example.kept.core.notify.KeptNotifications
import com.example.kept.core.notify.ReminderPoster
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
    private val time: TimeSource,
    private val restarter: ServiceRestarter,
    private val probe: UsageWindowProbe,
    private val scheduler: WorkScheduler,
    private val analytics: Analytics,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val settings = prefs.currentSettings()
        if (!settings.onboardingDone) return Result.success()
        runner.runPending()

        val lockState = lockRepo.currentState()
        val snapshot = lockState.snapshot(hardAllowlist = emptySet(), launchable = null)
        val shouldBeLocking = LockPolicy.isLockActive(time.now(), time.localTime(), snapshot)

        val now = time.nowMillis()
        val heartbeat = settings.lastServiceHeartbeat
        // Evidence only counts from inside the window being judged (issue #2). The heartbeat can be
        // far older than the window start — the service stops when a window closes and the phone is
        // used all evening — and probing from it would let last night's screen-on events mark today
        // unprotected.
        val windowStart = LockWindowStart
            .instantFor(time.localDateTime(), settings.lockFromMinute, time.zone())
            .toEpochMilli()
        val evidenceFrom = maxOf(heartbeat, windowStart)
        val base = GapRules.Input(
            lockShouldBeActive = shouldBeLocking,
            lockPermissionsGranted = permissions.lockPermissionsGranted(),
            heartbeatMillis = heartbeat,
            nowMillis = now,
            everHeartbeat = heartbeat > 0,
            screenInUseAtMillis = emptyList(),
            lockedAppResumedDuringWindow = false,
            evidenceFromMillis = evidenceFrom,
        )
        // Only look at usage events once the heartbeat is actually stale during an enforceable
        // window: the query is not free and nothing else can turn into a gap.
        val verdict = if (base.stale && shouldBeLocking && base.lockPermissionsGranted && evidenceFrom < now) {
            val activity = probe.activityDuring(evidenceFrom, now, snapshot)
            GapRules.evaluate(
                base.copy(
                    screenInUseAtMillis = activity.inUseAtMillis,
                    lockedAppResumedDuringWindow = activity.lockedAppResumed,
                ),
            )
        } else {
            GapRules.evaluate(base)
        }

        if (verdict == GapRules.Verdict.PERMISSION_MISSING) {
            // The service is still running and records this gap itself from inside tick. A second
            // overlapping record, and a restart of a service that never stopped, would both be
            // wrong (issue #2).
            notifications.protectionLost("A lock permission is off")
            return Result.success()
        }

        if (verdict.serviceLooksDead) {
            // A direct start normally works because KEPT holds SYSTEM_ALERT_WINDOW; when it does
            // not, the expedited worker retries and owns the "Lock is off" fallback (issue #2).
            if (!restarter.restart(RestartMethod.DIRECT)) scheduler.requestServiceRestart()
        } else {
            ForegroundWatcherService.start(ctx)
        }

        if (verdict.isGap) {
            // Recorded from the clamped start, not the raw heartbeat: the gap is the part of the
            // window that went unprotected, not the hours before it opened.
            lockRepo.recordProtectionGap(evidenceFrom, now, verdict.reason)
            notifications.protectionLost(verdict.reason)
            analytics.capture("protection_gap_recorded", mapOf("reason" to verdict.name.lowercase()))
        }
        return Result.success()
    }
}

/**
 * Restarts the watcher service from an expedited job, for the case where a direct
 * `startForegroundService` was refused (issue #2). Expedited work needs no permission the user can
 * refuse, unlike `SCHEDULE_EXACT_ALARM` on API 31+ — but it is also *not* an exemption from the
 * Android 12+ background-FGS-start restriction, so when the direct start was refused for that
 * reason this rung is refused for it too. [RestartLadder] then hands the job to the exact alarm,
 * which is an exemption, or to the "Lock is off" notification when exact alarms are not allowed.
 */
@HiltWorker
class ServiceRestartWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val restarter: ServiceRestarter,
    private val notifications: KeptNotifications,
    private val scheduler: WorkScheduler,
) : CoroutineWorker(ctx, params) {

    /** Only used on API < 31, where expedited work runs as a foreground worker instead. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(KeptNotifications.ID_RESTART, notifications.restartingNotification())

    override suspend fun doWork(): Result {
        if (restarter.restart(RestartMethod.EXPEDITED_WORK)) return Result.success()
        val next = RestartLadder.after(RestartMethod.EXPEDITED_WORK, restarter.canScheduleExactAlarms())
        val armed = next == RestartLadder.Next.EXACT_ALARM && scheduler.requestExactAlarmRestart()
        if (!armed) restarter.showLockOff()
        return Result.success()
    }
}

/**
 * The exact-alarm rung of the restart ladder (issue #2). Delivery of an exact alarm is one of the
 * documented exemptions from the background foreground-service-start restriction, so the service
 * start attempted from here can succeed where the two rungs before it were refused. Nothing is left
 * after this one, so a refusal posts the "Lock is off" notification.
 */
class ServiceRestartAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun restarter(): ServiceRestarter
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        }.getOrNull() ?: return
        // Synchronous and short: starting a service is a single binder call, so there is no need
        // for goAsync() and nothing here may be allowed to throw out of a receiver.
        runCatching {
            val restarter = deps.restarter()
            if (!restarter.restart(RestartMethod.EXACT_ALARM)) restarter.showLockOff()
        }.onFailure { Log.w(TAG, "exact-alarm restart rung failed", it) }
    }

    private companion object {
        const val TAG = "ServiceRestartAlarm"
    }
}

/**
 * Fires one rung of the reminder ladder (issue #9). Three alarms exist, one per [ReminderKind],
 * each carrying its kind and each re-armed for its next occurrence after it fires.
 */
class AlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun poster(): ReminderPoster
        fun scheduler(): WorkScheduler
        fun analytics(): Analytics
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = runCatching { EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java) }.getOrNull() ?: return
        val kind = ReminderKind.entries.firstOrNull { it.eventValue == intent.getStringExtra(KeptNotifications.EXTRA_REMINDER_KIND) }
            ?: ReminderKind.BEFORE_DUE
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // An uncaught throw here would be an uncaught throw on a bare scope, which kills
                // the process. A reminder that failed to draw is not worth a crash.
                runCatching {
                    if (deps.poster().post(kind)) {
                        deps.analytics().capture("reminder_fired", mapOf("kind" to kind.eventValue))
                    }
                    deps.scheduler().scheduleReminders()
                }.onFailure { Log.w(TAG, "reminder alarm for ${kind.eventValue} failed", it) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
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
        CoroutineScope(Dispatchers.IO).launch { scheduleReminders() }
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

    /**
     * The last rung: a near-immediate exact alarm whose delivery exempts the receiver from the
     * background foreground-service-start restriction (issue #2). Returns false when the alarm
     * could not be armed at all, which is the caller's signal to fall through to the "Lock is off"
     * notification rather than wait for a start that will never come.
     *
     * [EXACT_ALARM_DELAY_MILLIS] is short but non-zero: `setExactAndAllowWhileIdle` is documented
     * to fire no more often than once every few minutes per app in Doze, and asking for "now"
     * would be no faster than asking for two seconds from now.
     */
    fun requestExactAlarmRestart(): Boolean = runCatching {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return false
        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + EXACT_ALARM_DELAY_MILLIS,
            restartAlarmIntent(),
        )
        true
    }.getOrDefault(false)

    private fun restartAlarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        RESTART_REQUEST_CODE,
        Intent(ctx, ServiceRestartAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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

    /**
     * Arms the three reminder alarms (issue #9). Each is set to its next occurrence — today's if it
     * is still ahead, otherwise tomorrow's — so a rung whose time has already passed is simply
     * skipped for today. A rung the window has no room for is cancelled rather than left armed.
     */
    suspend fun scheduleReminders() {
        val s = prefs.currentSettings()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        val now = time.now()
        ReminderKind.entries.forEach { kind ->
            val pi = reminderIntent(kind)
            am.cancel(pi)
            if (!s.onboardingDone || !s.remindersEnabled) return@forEach
            val step = ReminderLadder.nextFiring(kind, now, time.today(), s.lockFromMinute, s.dueMinute, time.zone())
                ?: return@forEach
            val at = step.at.toEpochMilli()
            runCatching {
                if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    /** One PendingIntent per rung: same receiver, distinct request codes so they never replace one another. */
    private fun reminderIntent(kind: ReminderKind): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        REMINDER_REQUEST_BASE + kind.ordinal,
        Intent(ctx, AlarmReceiver::class.java).putExtra(KeptNotifications.EXTRA_REMINDER_KIND, kind.eventValue),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REMINDER_REQUEST_BASE = 100

        /** Distinct from every reminder rung's request code so they never replace one another. */
        const val RESTART_REQUEST_CODE = 200
        const val EXACT_ALARM_DELAY_MILLIS = 2_000L
    }
}
