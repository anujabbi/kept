package com.example.kept.core.lock

/**
 * Which permissions gate what, and the copy that names them. Pure Kotlin so the matrix is unit
 * tested; [Permissions] answers the "is it granted" half on a device.
 *
 * Two gates, on purpose (issue #40): onboarding waits for notifications as well, because without
 * them reminders, the recap and the "Lock is off" warning never arrive. The lock itself only
 * needs usage access and overlay, so a denied notification permission is not a protection gap:
 * the foreground service still enforces the lock without its visible notification, and the day
 * still counts. Home and the watcher both read [lockGate]; see DECISIONS.md.
 */
object RequiredPermissions {
    /** What the onboarding Continue button waits for, in the order the cards show them. */
    val onboardingGate: List<PermissionKind> = listOf(PermissionKind.USAGE_ACCESS, PermissionKind.OVERLAY, PermissionKind.NOTIFICATIONS)

    /** What the lock cannot enforce without. Drives Home's "Lock is off" card and the watcher. */
    val lockGate: List<PermissionKind> = listOf(PermissionKind.USAGE_ACCESS, PermissionKind.OVERLAY)

    fun missing(kinds: List<PermissionKind>, granted: (PermissionKind) -> Boolean): List<PermissionKind> =
        kinds.filterNot(granted)

    /** Lower-case name as it reads mid-sentence; the card title is the capitalised form. */
    fun name(kind: PermissionKind): String = when (kind) {
        PermissionKind.USAGE_ACCESS -> "usage access"
        PermissionKind.OVERLAY -> "display over other apps"
        PermissionKind.NOTIFICATIONS -> "notifications"
        PermissionKind.BATTERY -> "battery exemption"
        PermissionKind.CAMERA -> "camera"
    }

    /** "a", "a and b", "a, b and c". */
    fun names(kinds: List<PermissionKind>): String = when (kinds.size) {
        0 -> ""
        1 -> name(kinds[0])
        else -> kinds.dropLast(1).joinToString(", ") { name(it) } + " and " + name(kinds.last())
    }

    fun continueLabel(missing: List<PermissionKind>): String =
        if (missing.isEmpty()) "Continue" else "Continue without the lock"

    /** The line under the onboarding button, or null when nothing is missing. */
    fun onboardingWarning(missing: List<PermissionKind>): String? = when {
        missing.isEmpty() -> null
        missing.any { it in lockGate } ->
            "Without ${names(missing)} nothing locks and days won't count. You can grant ${if (missing.size == 1) "it" else "them"} later in Settings."
        else ->
            "Without notifications there are no reminders, no daily recap, and no warning if the lock stops. You can grant it later in Settings."
    }

    /** Body of Home's "Lock is off" card; [missing] is non-empty and drawn from [lockGate]. */
    fun lockOffBody(missing: List<PermissionKind>): String {
        val plural = missing.size > 1
        return "${names(missing).replaceFirstChar { it.uppercase() }} ${if (plural) "are" else "is"} off. Today won't count until ${if (plural) "they're" else "it's"} back on."
    }
}
