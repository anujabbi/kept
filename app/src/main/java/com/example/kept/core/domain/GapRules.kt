package com.example.kept.core.domain

/**
 * Decides whether a stale service heartbeat is a real protection gap (issue #2).
 *
 * The heartbeat is written by the watcher service's poll loop. In deep Doze that loop is frozen,
 * so the heartbeat goes stale on a phone that is face-down on a desk and nothing at all has gone
 * wrong. Treating that as a gap marked the day unprotected for a user who did nothing.
 *
 * A gap needs evidence that the phone was actually being used while unprotected: the screen was
 * interactive during the stale window, or a lockable app was resumed during it. Pure Doze
 * staleness is not a gap — it is still worth restarting the service, which is a separate question
 * ([serviceLooksDead]).
 */
object GapRules {

    /** How old the heartbeat may get before the service is presumed stopped. */
    const val STALE_AFTER_MILLIS: Long = 2 * 60_000

    data class Input(
        /** True when the lock window is open, habits are undone and no break is running. */
        val lockShouldBeActive: Boolean,
        val heartbeatAgeMillis: Long,
        /** False before the service has ever run, when there is no window to judge. */
        val everHeartbeat: Boolean,
        /** The screen was interactive at some point between the last heartbeat and now. */
        val screenInteractiveDuringWindow: Boolean,
        /** A package the lock would have blocked was resumed in that same window. */
        val lockedAppResumedDuringWindow: Boolean,
    ) {
        val stale: Boolean get() = everHeartbeat && heartbeatAgeMillis > STALE_AFTER_MILLIS
    }

    /** [reason] is stored on the gap and shown in the "Lock is off" notification. */
    enum class Verdict(val isGap: Boolean, val reason: String) {
        LOCK_NOT_DUE(false, ""),
        NO_HEARTBEAT_YET(false, ""),
        SERVICE_ALIVE(false, ""),
        DOZE_NOT_A_GAP(false, ""),
        GAP_SCREEN_ON(true, "The lock was off while you were using the phone"),
        GAP_LOCKED_APP_USED(true, "A locked app was opened while the lock was off"),
        ;

        /** True when the service should be restarted, whether or not the day is marked. */
        val serviceLooksDead: Boolean
            get() = this == DOZE_NOT_A_GAP || isGap
    }

    fun evaluate(input: Input): Verdict = when {
        !input.lockShouldBeActive -> Verdict.LOCK_NOT_DUE
        !input.everHeartbeat -> Verdict.NO_HEARTBEAT_YET
        !input.stale -> Verdict.SERVICE_ALIVE
        input.lockedAppResumedDuringWindow -> Verdict.GAP_LOCKED_APP_USED
        input.screenInteractiveDuringWindow -> Verdict.GAP_SCREEN_ON
        else -> Verdict.DOZE_NOT_A_GAP
    }
}
