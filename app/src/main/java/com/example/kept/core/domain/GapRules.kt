package com.example.kept.core.domain

/**
 * Decides whether a stale service heartbeat is a real protection gap (issue #2).
 *
 * The heartbeat is written by the watcher service's poll loop. In deep Doze that loop is frozen,
 * so the heartbeat goes stale on a phone that is face-down on a desk and nothing at all has gone
 * wrong. Treating that as a gap marked the day unprotected for a user who did nothing.
 *
 * A gap needs evidence that the phone was actually being used while unprotected: the phone was in
 * use during the stale window, or a lockable app was resumed during it. Two exclusions matter:
 *
 * - A missing lock permission is not judged here at all. The service is still running and records
 *   that gap itself from inside its tick, so a second, overlapping record from the watchdog would
 *   double-count it and blame the wrong thing.
 * - Evidence from outside the window being judged is discarded. See [Input.evidenceFromMillis]:
 *   the heartbeat can predate the lock window by hours, and last night's use is not today's gap.
 * - Use within [WAKE_TAIL_MILLIS] of the end of the window does not count on its own. The watchdog
 *   usually runs *because* the phone woke, so the wake that triggered the check would otherwise
 *   turn a quiet night into an unprotected day.
 *
 * Pure Doze staleness is not a gap — it is still worth restarting the service, which is a separate
 * question ([Verdict.serviceLooksDead]).
 */
object GapRules {

    /** How old the heartbeat may get before the service is presumed stopped. */
    const val STALE_AFTER_MILLIS: Long = 2 * 60_000

    /** Use this recent is presumed to be the wake that let this check run, not evidence of a gap. */
    const val WAKE_TAIL_MILLIS: Long = 60_000

    data class Input(
        /** True when the lock window is open, habits are undone and no break is running. */
        val lockShouldBeActive: Boolean,
        /** False when usage access or the overlay permission is off; the service handles that. */
        val lockPermissionsGranted: Boolean,
        val heartbeatMillis: Long,
        val nowMillis: Long,
        /** False before the service has ever run, when there is no window to judge. */
        val everHeartbeat: Boolean,
        /** Moments the phone was genuinely in use: screen on, unlocked, not KEPT's own UI. */
        val screenInUseAtMillis: List<Long>,
        /** A package the lock would have blocked was resumed in the window. */
        val lockedAppResumedDuringWindow: Boolean,
        /**
         * The earliest moment evidence may come from: the later of [heartbeatMillis] and the start
         * of the lock window being judged (issue #2).
         *
         * `UsageStatsManager` is queried over `[heartbeat, now]`, and a heartbeat can be much older
         * than the window: the service stops at 23:00 when last night's window closed, the phone is
         * used all evening, and a watchdog run after this morning's window opens would otherwise
         * find yesterday's screen-on events and mark *today* unprotected. Defaults to
         * [heartbeatMillis], so a caller with no window to clamp to keeps the old behaviour.
         */
        val evidenceFromMillis: Long = heartbeatMillis,
    ) {
        val heartbeatAgeMillis: Long get() = nowMillis - heartbeatMillis
        val stale: Boolean get() = everHeartbeat && heartbeatAgeMillis > STALE_AFTER_MILLIS

        /** The lower bound evidence is judged against: never earlier than the heartbeat. */
        val evidenceFloorMillis: Long get() = maxOf(heartbeatMillis, evidenceFromMillis)

        /**
         * Use that actually falls inside the window being judged. A probe can hand back events
         * from outside `[floor, now]` — the query is inclusive at its edges and an OEM can stamp
         * an event a little late — and an out-of-window event must never decide a day.
         */
        val useInWindow: List<Long>
            get() = screenInUseAtMillis.filter { it >= evidenceFloorMillis && it <= nowMillis }

        /** Use old enough that it cannot be the wake that let this check run. */
        val inUseBeforeWakeTail: Boolean
            get() = useInWindow.any { it <= nowMillis - WAKE_TAIL_MILLIS }

        /** The phone was used, but only in the last moments of the window. */
        val inUseOnlyAtWakeTail: Boolean
            get() = useInWindow.isNotEmpty() && !inUseBeforeWakeTail
    }

    enum class Verdict(val isGap: Boolean, val reason: String) {
        LOCK_NOT_DUE(false, ""),
        PERMISSION_MISSING(false, ""),
        NO_HEARTBEAT_YET(false, ""),
        SERVICE_ALIVE(false, ""),
        DOZE_NOT_A_GAP(false, ""),

        /** The only use was the wake that triggered this check. Restart, do not blame the day. */
        WOKE_AT_END_NOT_A_GAP(false, ""),
        GAP_SCREEN_ON(true, "The lock was off while you were using the phone"),
        GAP_LOCKED_APP_USED(true, "A locked app was opened while the lock was off"),
        ;

        /** True when the service should be restarted, whether or not the day is marked. */
        val serviceLooksDead: Boolean
            get() = this == DOZE_NOT_A_GAP || this == WOKE_AT_END_NOT_A_GAP || isGap
    }

    fun evaluate(input: Input): Verdict = when {
        !input.lockShouldBeActive -> Verdict.LOCK_NOT_DUE
        !input.lockPermissionsGranted -> Verdict.PERMISSION_MISSING
        !input.everHeartbeat -> Verdict.NO_HEARTBEAT_YET
        !input.stale -> Verdict.SERVICE_ALIVE
        input.lockedAppResumedDuringWindow -> Verdict.GAP_LOCKED_APP_USED
        input.inUseBeforeWakeTail -> Verdict.GAP_SCREEN_ON
        input.inUseOnlyAtWakeTail -> Verdict.WOKE_AT_END_NOT_A_GAP
        else -> Verdict.DOZE_NOT_A_GAP
    }
}
