package com.example.kept.core.lock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.kept.core.domain.LockPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "was this phone actually being used while the lock was off?" for a past window, from
 * usage events (issue #2). The watchdog needs this to tell a real protection gap from the poll
 * loop simply being frozen in Doze; [com.example.kept.core.domain.GapRules] makes the decision.
 */
@Singleton
class UsageWindowProbe @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val apps: InstalledAppsSource,
    private val allowlist: AllowlistResolver,
) {

    data class Activity(
        val screenInteractive: Boolean,
        val lockedAppResumed: Boolean,
    )

    /**
     * [from] is the last heartbeat, [to] is now. A locked app is one [LockPolicy.isProtectedPackage]
     * would have stopped, judged against the user's exceptions as they stand now — the closest
     * thing to what the lock would have done at the time.
     */
    suspend fun activityDuring(from: Long, to: Long, snapshot: LockPolicy.Snapshot): Activity =
        withContext(Dispatchers.IO) {
            // The screen being on right now is itself evidence: the user is holding an unprotected
            // phone. On API < 28 no SCREEN_INTERACTIVE events are delivered, so this is all we get.
            var interactive = runCatching {
                ctx.getSystemService(PowerManager::class.java).isInteractive
            }.getOrDefault(false)
            var lockedApp = false

            val usm = runCatching { ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }.getOrNull()
            val events = usm?.let { runCatching { it.queryEvents(from, to) }.getOrNull() }
            if (events != null) {
                val hard = allowlist.hardAllowlist()
                val launchable = runCatching { apps.launchablePackages() }.getOrNull()
                val resolved = snapshot.copy(hardAllowlist = hard, launchable = launchable)
                val e = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(e)
                    when {
                        Build.VERSION.SDK_INT >= 28 && e.eventType == UsageEvents.Event.SCREEN_INTERACTIVE ->
                            interactive = true
                        isResume(e) -> {
                            val pkg = e.packageName ?: continue
                            if (pkg != ctx.packageName && LockPolicy.isProtectedPackage(pkg, resolved)) lockedApp = true
                        }
                    }
                }
            }
            Activity(screenInteractive = interactive, lockedAppResumed = lockedApp)
        }

    private fun isResume(e: UsageEvents.Event): Boolean =
        if (Build.VERSION.SDK_INT >= 29) e.eventType == UsageEvents.Event.ACTIVITY_RESUMED
        else @Suppress("DEPRECATION") (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
}
