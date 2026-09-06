package com.example.kept.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.db.AllowedAppEntity
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.Allowlist
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import com.example.kept.core.lock.AllowlistResolver
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.lock.InstalledApp
import com.example.kept.core.lock.InstalledAppsSource
import com.example.kept.core.lock.Permissions
import com.example.kept.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUi(
    val settings: Settings = Settings(),
    val habits: List<HabitEntity> = emptyList(),
    val exceptions: List<AllowedAppEntity> = emptyList(),
    val sprig: SprigState = SprigState(),
    val history: List<DayRecordEntity> = emptyList(),
    val breaksThisWeek: Int = 0,
)

data class PickableApp(val app: InstalledApp, val allowed: Boolean)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: KeptPreferences,
    private val habitsRepo: HabitRepository,
    private val lockRepo: LockRepository,
    private val sprigRepo: SprigRepository,
    private val dayRepo: DayRepository,
    private val appsSource: InstalledAppsSource,
    private val allowlist: AllowlistResolver,
    private val scheduler: WorkScheduler,
    val permissions: Permissions,
) : ViewModel() {

    val state: StateFlow<SettingsUi> = combine(
        prefs.settings, habitsRepo.observeHabits(), lockRepo.observeExceptions(), sprigRepo.state, dayRepo.observeHistory(45),
    ) { s, h, e, sp, hist -> SettingsUi(s, h, e, sp, hist) }
        .combine(lockRepo.observeBreaksThisWeek()) { s, b -> s.copy(breaksThisWeek = b.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    private val _apps = MutableStateFlow<List<PickableApp>?>(null)
    val apps: StateFlow<List<PickableApp>?> = _apps
    val hardAllowlistLabels: StateFlow<List<String>> = MutableStateFlow(emptyList())

    fun loadApps() = viewModelScope.launch {
        val hard = withContext(Dispatchers.IO) { allowlist.hardAllowlist() }
        val installed = appsSource.listLaunchable()
        val allowed = state.value.exceptions.map { it.packageName }.toSet()
        _apps.value = Allowlist.pickable(installed, { it.packageName }, hard).map { PickableApp(it, it.packageName in allowed) }
        (hardAllowlistLabels as MutableStateFlow).value = installed.filter { it.packageName in hard }.map { it.label }.distinct().sorted()
    }

    fun toggleException(app: InstalledApp, allow: Boolean) = viewModelScope.launch {
        if (allow) lockRepo.addException(app.packageName, app.label) else lockRepo.removeException(app.packageName)
        _apps.value = _apps.value?.map { if (it.app.packageName == app.packageName) it.copy(allowed = allow) else it }
    }

    fun setLockWindow(from: Int, due: Int) = viewModelScope.launch {
        prefs.updateSettings { it.copy(lockFromMinute = from, dueMinute = due) }
        scheduler.scheduleReminder()
    }

    fun setBreakDuration(min: Int) = viewModelScope.launch { prefs.updateSettings { it.copy(breakDurationMin = min) } }
    fun setReminders(on: Boolean) = viewModelScope.launch { prefs.updateSettings { it.copy(remindersEnabled = on) }; scheduler.scheduleReminder() }

    fun addHabit(title: String, iconKey: String, proof: ProofType, target: Int, unit: String) = viewModelScope.launch {
        habitsRepo.addHabit(title, iconKey, proof, target, unit)
    }
    fun updateHabit(h: HabitEntity) = viewModelScope.launch { habitsRepo.updateHabit(h) }
    fun removeHabit(id: Long) = viewModelScope.launch { habitsRepo.removeHabit(id) }

    fun restartService() = ForegroundWatcherService.start(ctx)
}
