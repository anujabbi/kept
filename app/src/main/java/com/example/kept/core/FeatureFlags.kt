package com.example.kept.core

import com.example.kept.BuildConfig

/**
 * Flags for features that are built but not ready to ship to real users.
 * Kept in one place so a flag can be flipped without hunting through call sites.
 */
object FeatureFlags {
    /**
     * The Buddy tab is seeded stub data (a fake accountability partner named "Maya") with no
     * real pairing, nudges, or cheers behind it. Debug builds keep it visible for development;
     * it is hidden everywhere else until it does something real. See issue #6.
     */
    val showBuddy: Boolean = BuildConfig.DEBUG
}
