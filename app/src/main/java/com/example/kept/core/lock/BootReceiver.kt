package com.example.kept.core.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.work.WorkScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts the lock after a reboot or an app update. Uses an entry point instead of
 * @AndroidEntryPoint so a broadcast arriving before Hilt is ready (e.g. inside an instrumentation
 * process) is ignored rather than crashing.
 */
class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun prefs(): KeptPreferences
        fun scheduler(): WorkScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val deps = runCatching { EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java) }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = deps.prefs()
                if (prefs.currentSettings().onboardingDone) {
                    // Fresh heartbeat so the watchdog does not count the time the phone was off as a gap.
                    prefs.heartbeat(System.currentTimeMillis())
                    ForegroundWatcherService.start(context)
                    deps.scheduler().scheduleAll()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
