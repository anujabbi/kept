package com.example.kept.core

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a KEPT screen is currently in front of the user (issue #9).
 *
 * The celebration is a full-screen moment, so when the day is finished from a notification action
 * with nothing on screen there is nothing to show it on: KEPT posts "Apps are open. Promise kept."
 * instead and holds the celebration until the app is opened. [MainActivity] is the only writer;
 * `lifecycle-process` would give the same answer but is not a dependency of this module.
 */
@Singleton
class AppForeground @Inject constructor() {
    private val inForeground = AtomicBoolean(false)

    val isForeground: Boolean get() = inForeground.get()

    fun onResumed() = inForeground.set(true)
    fun onPaused() = inForeground.set(false)
}
