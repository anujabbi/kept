package com.example.kept.feature.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.DebugSeeder
import com.example.kept.core.data.RolloverRunner
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: KeptPreferences,
    private val rollover: RolloverRunner,
    private val scheduler: WorkScheduler,
    private val seeder: DebugSeeder,
) : ViewModel() {

    private val _start = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _start

    init {
        viewModelScope.launch {
            val s = prefs.settings.first()
            _start.value = if (s.onboardingDone) Routes.HOME else Routes.ONBOARDING
        }
    }

    fun onResume() {
        viewModelScope.launch {
            val s = prefs.currentSettings()
            if (s.onboardingDone) {
                rollover.runPending()
                prefs.heartbeat(System.currentTimeMillis())
                ForegroundWatcherService.start(ctx)
                scheduler.scheduleAll()
            }
        }
    }

    /** Debug helper: -1 leaves a value unchanged. */
    fun setLockWindow(from: Int, due: Int) {
        viewModelScope.launch {
            prefs.updateSettings { it.copy(lockFromMinute = if (from >= 0) from else it.lockFromMinute, dueMinute = if (due >= 0) due else it.dueMinute) }
        }
    }

    fun seedDemo() {
        viewModelScope.launch {
            seeder.seedIfNeeded()
            _start.value = Routes.HOME
            onResume()
        }
    }
}
