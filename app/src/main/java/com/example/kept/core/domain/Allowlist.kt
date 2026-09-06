package com.example.kept.core.domain

/**
 * The hard allowlist is assembled from role holders resolved on the device (dialer, SMS, home,
 * camera, maps, IME, etc.) plus these static packages. It is enforced in code and never editable.
 */
object Allowlist {
    const val OWN_PACKAGE = "com.example.kept"

    val STATIC: Set<String> = setOf(
        OWN_PACKAGE,
        "com.android.settings",
        "com.android.systemui",
        "com.android.emergency",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.providers.downloads.ui",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.google.android.apps.maps",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.google.android.contacts",
        "com.android.contacts",
        "com.google.android.apps.wellbeing",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "android",
    )

    /**
     * Combine static packages with role holders. Anything resolved at runtime (default dialer,
     * default SMS, default home, default camera, default maps handler, current IME, emergency role)
     * comes in through [resolved].
     */
    fun build(resolved: Collection<String>): Set<String> =
        STATIC + resolved.filter { it.isNotBlank() }

    /** Filters a candidate list of installed apps to those the user is allowed to add as exceptions. */
    fun <T> pickable(items: List<T>, packageOf: (T) -> String, hard: Set<String>): List<T> =
        items.filter { packageOf(it) !in hard }
}
