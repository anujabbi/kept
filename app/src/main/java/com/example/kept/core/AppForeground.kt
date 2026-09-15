package com.example.kept.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.kept.core.data.ForegroundSignal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ForegroundSignal] backed by the process lifecycle (issue #9).
 *
 * It asks `ProcessLifecycleOwner` rather than tracking one Activity, because KEPT has two: the
 * first version was written by `MainActivity.onResume`/`onPause` only, so finishing the last habit
 * on the **lock screen** looked like "nothing is on screen" and posted a notification instead of
 * celebrating. Any KEPT activity now counts.
 */
@Singleton
class AppForeground @Inject constructor() : ForegroundSignal {
    override val isForeground: Boolean
        get() = runCatching {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }.getOrDefault(false)
}
