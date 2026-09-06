package com.example.kept.core.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var scheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (prefs.currentSettings().onboardingDone) {
                    ForegroundWatcherService.start(context)
                    scheduler.scheduleAll()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
