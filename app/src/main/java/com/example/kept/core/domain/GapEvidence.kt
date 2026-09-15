package com.example.kept.core.domain

/**
 * Turns a window of usage events into the two facts [GapRules] needs: when the phone was genuinely
 * being used, and whether a lockable app was resumed (issue #2).
 *
 * Kept pure and separate from the Android plumbing so the exclusions can be tested. Two of them
 * matter, because both would otherwise blame the user for KEPT's failure:
 *
 * - Screen-on time spent at the keyguard is not use. Waking a phone to check the time unlocks
 *   nothing, and nothing the lock covers can be reached from the lock screen.
 * - Time spent in KEPT itself is not use. Opening KEPT to turn the lock back on must not be the
 *   thing that marks the day unprotected.
 */
object GapEvidence {

    enum class Kind { SCREEN_ON, SCREEN_OFF, KEYGUARD_SHOWN, KEYGUARD_HIDDEN, APP_RESUMED }

    data class Event(val kind: Kind, val atMillis: Long, val packageName: String? = null)

    data class Use(
        /** Ordered moments the phone was in use: screen on, keyguard down, not KEPT's own UI. */
        val inUseAtMillis: List<Long>,
        val lockedAppResumed: Boolean,
    )

    /**
     * [isProtected] answers whether the lock covers a package — in practice
     * [LockPolicy.isProtectedPackage] bound to the current snapshot.
     */
    fun fold(events: List<Event>, ownPackage: String, isProtected: (String) -> Boolean): Use {
        val inUse = mutableListOf<Long>()
        var lockedApp = false
        // Unknown until an event says otherwise: a window that starts mid-use should not be
        // written off as locked.
        var keyguardShowing = false
        var screenOn = true
        var foreground: String? = null

        fun usable() = screenOn && !keyguardShowing && foreground != ownPackage

        events.sortedBy { it.atMillis }.forEach { e ->
            when (e.kind) {
                Kind.SCREEN_OFF -> screenOn = false
                Kind.KEYGUARD_SHOWN -> keyguardShowing = true
                Kind.SCREEN_ON -> {
                    screenOn = true
                    if (usable()) inUse += e.atMillis
                }
                Kind.KEYGUARD_HIDDEN -> {
                    keyguardShowing = false
                    // Unlocking is the clearest "I am using this phone" there is.
                    if (usable()) inUse += e.atMillis
                }
                Kind.APP_RESUMED -> {
                    val pkg = e.packageName ?: return@forEach
                    foreground = pkg
                    if (pkg == ownPackage) return@forEach
                    // An app coming to the foreground is a resume whatever the screen was doing;
                    // only the "was the phone in use" half is gated.
                    if (isProtected(pkg)) lockedApp = true
                    if (usable()) inUse += e.atMillis
                }
            }
        }
        return Use(inUse, lockedApp)
    }
}
