package com.example.kept.core.lock

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.kept.core.domain.GapEvidence
import com.example.kept.core.domain.LockPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "was this phone actually being used while the lock was off?" for a past window (issue
 * #2). This is only the Android plumbing: it maps `UsageEvents` onto [GapEvidence], which decides
 * what counts as use, and [com.example.kept.core.domain.GapRules] decides what that means.
 */
@Singleton
class UsageWindowProbe @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val apps: InstalledAppsSource,
    private val allowlist: AllowlistResolver,
) {

    /**
     * [from] is the last heartbeat, [to] is now. A locked app is one [LockPolicy.isProtectedPackage]
     * would have stopped, judged against the user's exceptions as they stand now — the closest
     * thing to what the lock would have done at the time.
     */
    suspend fun activityDuring(from: Long, to: Long, snapshot: LockPolicy.Snapshot): GapEvidence.Use =
        withContext(Dispatchers.IO) {
            val hard = allowlist.hardAllowlist()
            val launchable = runCatching { apps.launchablePackages() }.getOrNull()
            val resolved = snapshot.copy(hardAllowlist = hard, launchable = launchable)

            val events = readEvents(from, to).toMutableList()
            // No screen or keyguard events are delivered below API 28, so the live state is all
            // there is. It is stamped at [to], which the rules treat as the wake that triggered
            // this check, so on those versions a gap needs a locked-app resume to show up.
            if (Build.VERSION.SDK_INT < 28) {
                if (inUseNow()) events += GapEvidence.Event(GapEvidence.Kind.KEYGUARD_HIDDEN, to)
                else events += GapEvidence.Event(GapEvidence.Kind.SCREEN_OFF, from)
            }

            GapEvidence.fold(events, ctx.packageName) { LockPolicy.isProtectedPackage(it, resolved) }
        }

    private fun readEvents(from: Long, to: Long): List<GapEvidence.Event> {
        val usm = runCatching { ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager }.getOrNull()
        val events = usm?.let { runCatching { it.queryEvents(from, to) }.getOrNull() } ?: return emptyList()
        val out = mutableListOf<GapEvidence.Event>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when {
                Build.VERSION.SDK_INT >= 28 && e.eventType == UsageEvents.Event.SCREEN_INTERACTIVE -> GapEvidence.Kind.SCREEN_ON
                Build.VERSION.SDK_INT >= 28 && e.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE -> GapEvidence.Kind.SCREEN_OFF
                Build.VERSION.SDK_INT >= 28 && e.eventType == UsageEvents.Event.KEYGUARD_SHOWN -> GapEvidence.Kind.KEYGUARD_SHOWN
                Build.VERSION.SDK_INT >= 28 && e.eventType == UsageEvents.Event.KEYGUARD_HIDDEN -> GapEvidence.Kind.KEYGUARD_HIDDEN
                isResume(e) -> GapEvidence.Kind.APP_RESUMED
                else -> null
            } ?: continue
            out += GapEvidence.Event(kind, e.timeStamp, e.packageName)
        }
        return out
    }

    private fun inUseNow(): Boolean = runCatching {
        val interactive = ctx.getSystemService(PowerManager::class.java).isInteractive
        val locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        interactive && !locked
    }.getOrDefault(false)

    private fun isResume(e: UsageEvents.Event): Boolean =
        if (Build.VERSION.SDK_INT >= 29) e.eventType == UsageEvents.Event.ACTIVITY_RESUMED
        else @Suppress("DEPRECATION") (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
}
