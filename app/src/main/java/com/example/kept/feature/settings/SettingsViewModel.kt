package com.example.kept.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.analytics.Analytics
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
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.lock.Permissions
import com.example.kept.core.notify.ReminderPoster
import com.example.kept.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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
    private val reminders: ReminderPoster,
    private val time: TimeSource,
    val permissions: Permissions,
    private val analytics: Analytics,
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

    /** What [loadApps] found on the device. Null until the first load finishes. */
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val hardAllowlist = MutableStateFlow<Set<String>>(emptySet())
    val hardAllowlistLabels: StateFlow<List<String>> = MutableStateFlow(emptyList())

    /**
     * The exceptions picker. Derived from the stored exceptions rather than kept in sync by hand
     * (issue #4): an optimistic local edit could be overwritten by a reload that started before
     * the insert landed, flipping a toggle back in the user's face, and a hand-maintained copy is
     * one more thing that can disagree with what the lock actually enforces.
     */
    val apps: StateFlow<List<PickableApp>?> =
        combine(installed, hardAllowlist, lockRepo.observeExceptions()) { apps, hard, exceptions ->
            apps?.let { pickableApps(it, hard, exceptions.map { e -> e.packageName }.toSet()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Installing or removing an app invalidates the cached list (issue #4); re-read it so the
        // picker never shows a list the lock no longer agrees with.
        viewModelScope.launch {
            appsSource.revision.drop(1).collect { if (installed.value != null) loadApps().join() }
        }
    }

    /** Re-reads what is on the device. Which of those are exempt comes from [apps], not from here. */
    fun loadApps() = viewModelScope.launch {
        val hard = withContext(Dispatchers.IO) { allowlist.hardAllowlist() }
        val found = appsSource.listLaunchable()
        hardAllowlist.value = hard
        installed.value = found
        (hardAllowlistLabels as MutableStateFlow).value = found.filter { it.packageName in hard }.map { it.label }.distinct().sorted()
    }

    fun toggleException(app: InstalledApp, allow: Boolean) = viewModelScope.launch {
        if (allow) lockRepo.addException(app.packageName, app.label) else lockRepo.removeException(app.packageName)
        analytics.capture(
            if (allow) "exception_added" else "exception_removed",
            mapOf("package" to app.packageName),
        )
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
        scheduler.scheduleReminders()
    }

    /** Moving the give-up time or lock start opens apps early during an active lock (issue #1). */
    fun requestSetLockWindow(from: Int, due: Int) = guard(
        LockAffectingSetting.LOCK_WINDOW,
        "Your apps are locked right now. Changing the give-up time opens them early. Still change it?",
    ) { setLockWindow(from, due) }

    fun setBreakDuration(min: Int) = viewModelScope.launch { prefs.updateSettings { it.copy(breakDurationMin = min) } }
    /**
     * Switching reminders off cancels the alarms, but a reminder already in the shade keeps its
     * live "Mark done" buttons until something takes it down (issue #9).
     */
    fun setReminders(on: Boolean) = viewModelScope.launch {
        prefs.updateSettings { it.copy(remindersEnabled = on) }
        scheduler.scheduleReminders()
        if (!on) reminders.clear()
    }

    fun addHabit(title: String, iconKey: String, proof: ProofType, target: Int, unit: String) = viewModelScope.launch {
        habitsRepo.addHabit(title, iconKey, proof, target, unit)
        analytics.capture("habit_added", mapOf("proof_type" to proof.name.lowercase()))
    }
    fun updateHabit(h: HabitEntity) = viewModelScope.launch { habitsRepo.updateHabit(h) }
    fun removeHabit(id: Long) = viewModelScope.launch {
        habitsRepo.removeHabit(id)
        analytics.capture("habit_removed")
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
        analytics.capture("settings_changed_during_lock", mapOf("setting" to pending.setting.eventValue))
        pending.apply()
    }

    fun cancelPendingLockChange() {
        _pendingLockChange.value = null
    }

    /** One Grant tap, answered (issue #10). */
    fun reportPermissionAnswer(kind: PermissionKind, granted: Boolean) = analytics.capture(
        if (granted) "permission_granted" else "permission_denied",
        mapOf("permission" to kind.eventValue),
    )

    /**
     * "Send anonymous usage data" (issue #10). The preference is stored first so the switch and the
     * next cold start agree, then the wrapper reports the change and mutes or unmutes itself.
     */
    fun setAnalyticsEnabled(on: Boolean) = viewModelScope.launch {
        prefs.updateSettings { it.copy(analyticsEnabled = on) }
        analytics.setEnabled(on)
    }

    fun restartService() = ForegroundWatcherService.start(ctx)
}
