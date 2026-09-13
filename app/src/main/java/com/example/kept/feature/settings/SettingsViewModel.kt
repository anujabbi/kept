package com.example.kept.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
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
import com.posthog.PostHog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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

/**
 * Builds the exceptions picker: every installed app the user is allowed to exempt, flagged with
 * whether it is exempt already. Pure so the flag can be tested without a device (issue #4: the
 * flags used to be read from a possibly-cold StateFlow and came back all-false).
 */
fun pickableApps(installed: List<InstalledApp>, hard: Set<String>, allowed: Set<String>): List<PickableApp> =
    Allowlist.pickable(installed, { it.packageName }, hard).map { PickableApp(it, it.packageName in allowed) }

/** A lock-affecting settings change (issue #1), snake_case matching the PostHog `setting` property. */
enum class LockAffectingSetting(val eventValue: String) {
    LOCK_WINDOW("lock_window"),
    HABIT_REMOVED("habit_removed"),
    EXCEPTION_ON("exception_on"),
}

/** A guarded change waiting on the user to confirm it while a lock is active. */
data class PendingLockChange(val setting: LockAffectingSetting, val message: String, val apply: () -> Unit)

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
    private val time: TimeSource,
    val permissions: Permissions,
) : ViewModel() {

    val state: StateFlow<SettingsUi> = combine(
        prefs.settings, habitsRepo.observeHabits(), lockRepo.observeExceptions(), sprigRepo.state, dayRepo.observeHistory(45),
    ) { s, h, e, sp, hist -> SettingsUi(s, h, e, sp, hist) }
        .combine(lockRepo.observeBreaksThisWeek()) { s, b -> s.copy(breaksThisWeek = b.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    /** Re-checked every 30s in addition to whenever the underlying lock state changes (issue #1). */
    val lockActive: StateFlow<Boolean> = combine(lockRepo.observeState(), time.ticker(30_000)) { s, _ ->
        s.isLockActiveNow(time.now(), time.localTime())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _pendingLockChange = MutableStateFlow<PendingLockChange?>(null)
    val pendingLockChange: StateFlow<PendingLockChange?> = _pendingLockChange

    private val _apps = MutableStateFlow<List<PickableApp>?>(null)
    val apps: StateFlow<List<PickableApp>?> = _apps
    val hardAllowlistLabels: StateFlow<List<String>> = MutableStateFlow(emptyList())

    init {
        // Installing or removing an app invalidates the cached list (issue #4); rebuild the picker
        // so it never shows a list the lock no longer agrees with.
        viewModelScope.launch {
            appsSource.revision.drop(1).collect { if (_apps.value != null) loadApps().join() }
        }
    }

    fun loadApps() = viewModelScope.launch {
        val hard = withContext(Dispatchers.IO) { allowlist.hardAllowlist() }
        val installed = appsSource.listLaunchable()
        // Read the exceptions from the repository, not from `state`: that is a WhileSubscribed
        // StateFlow, so on a screen that does not collect it `state.value` is still the empty
        // initial value and every app would render as un-exempt (issue #4).
        val allowed = lockRepo.observeExceptions().first().map { it.packageName }.toSet()
        _apps.value = pickableApps(installed, hard, allowed)
        (hardAllowlistLabels as MutableStateFlow).value = installed.filter { it.packageName in hard }.map { it.label }.distinct().sorted()
    }

    fun toggleException(app: InstalledApp, allow: Boolean) = viewModelScope.launch {
        if (allow) lockRepo.addException(app.packageName, app.label) else lockRepo.removeException(app.packageName)
        PostHog.capture(
            if (allow) "exception_added" else "exception_removed",
            properties = mapOf("package" to app.packageName),
        )
        _apps.value = _apps.value?.map { if (it.app.packageName == app.packageName) it.copy(allowed = allow) else it }
    }

    /** Adding an exception (or turning one on) opens an app early during an active lock (issue #1). */
    fun requestToggleException(app: InstalledApp, allow: Boolean) {
        if (!allow) { toggleException(app, false); return }
        guard(
            LockAffectingSetting.EXCEPTION_ON,
            "Your apps are locked right now. Adding ${app.label} as an exception opens it early. Still add it?",
        ) { toggleException(app, true) }
    }

    fun setLockWindow(from: Int, due: Int) = viewModelScope.launch {
        prefs.updateSettings { it.copy(lockFromMinute = from, dueMinute = due) }
        scheduler.scheduleReminder()
    }

    /** Moving the give-up time or lock start opens apps early during an active lock (issue #1). */
    fun requestSetLockWindow(from: Int, due: Int) = guard(
        LockAffectingSetting.LOCK_WINDOW,
        "Your apps are locked right now. Changing the give-up time opens them early. Still change it?",
    ) { setLockWindow(from, due) }

    fun setBreakDuration(min: Int) = viewModelScope.launch { prefs.updateSettings { it.copy(breakDurationMin = min) } }
    fun setReminders(on: Boolean) = viewModelScope.launch { prefs.updateSettings { it.copy(remindersEnabled = on) }; scheduler.scheduleReminder() }

    fun addHabit(title: String, iconKey: String, proof: ProofType, target: Int, unit: String) = viewModelScope.launch {
        habitsRepo.addHabit(title, iconKey, proof, target, unit)
        PostHog.capture("habit_added", properties = mapOf("proof_type" to proof.name.lowercase()))
    }
    fun updateHabit(h: HabitEntity) = viewModelScope.launch { habitsRepo.updateHabit(h) }
    fun removeHabit(id: Long) = viewModelScope.launch {
        habitsRepo.removeHabit(id)
        PostHog.capture("habit_removed")
    }

    /** Removing the last undone habit opens apps early during an active lock (issue #1). */
    fun requestRemoveHabit(id: Long) = guard(
        LockAffectingSetting.HABIT_REMOVED,
        "Your apps are locked right now. Removing this habit opens them early. Still remove it?",
    ) { removeHabit(id) }

    /**
     * Runs [action] immediately unless a lock is active right now, in which case it is held until
     * the user confirms via [confirmPendingLockChange] (issue #1). No cost, no delay: this is
     * friction and self-awareness, not enforcement.
     */
    private fun guard(setting: LockAffectingSetting, message: String, action: () -> Unit) {
        if (lockActive.value) _pendingLockChange.value = PendingLockChange(setting, message, action) else action()
    }

    fun confirmPendingLockChange() {
        val pending = _pendingLockChange.value ?: return
        _pendingLockChange.value = null
        PostHog.capture("settings_changed_during_lock", properties = mapOf("setting" to pending.setting.eventValue))
        pending.apply()
    }

    fun cancelPendingLockChange() {
        _pendingLockChange.value = null
    }

    fun restartService() = ForegroundWatcherService.start(ctx)
}
