package com.example.kept.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.PromiseRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.account.AccountRepository
import com.example.kept.core.data.account.AccountState
import com.example.kept.core.data.account.DeleteResult
import com.example.kept.core.data.account.toUi
import com.example.kept.core.data.db.AllowedAppEntity
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.Allowlist
import com.example.kept.core.domain.DeleteUi
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.domain.LockWindowEdit
import com.example.kept.core.domain.LockWindowField
import com.example.kept.core.domain.NightEdit
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseSwitch
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SignInUi
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.WeeklyGoalLockEdit
import com.example.kept.core.lock.AllowlistResolver
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.lock.InstalledApp
import com.example.kept.core.lock.InstalledAppsSource
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.lock.Permissions
import com.example.kept.core.notify.ReminderPoster
import com.example.kept.core.work.WorkScheduler
import com.example.kept.core.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class SettingsUi(
    val settings: Settings = Settings(),
    val habits: List<HabitEntity> = emptyList(),
    val exceptions: List<AllowedAppEntity> = emptyList(),
    val sprig: SprigState = SprigState(),
    val history: List<DayRecordEntity> = emptyList(),
    val breaksThisWeek: Int = 0,
    /** False until the first read: the promise waits for it rather than open on an empty list. */
    val loaded: Boolean = false,
)

data class PickableApp(val app: InstalledApp, val allowed: Boolean)

/**
 * Builds the exceptions picker: every installed app the user is allowed to exempt, flagged with
 * whether it is exempt already. Pure so the flag can be tested without a device (issue #4: the
 * flags used to be read from a possibly-cold StateFlow and came back all-false).
 */
fun pickableApps(installed: List<InstalledApp>, hard: Set<String>, allowed: Set<String>): List<PickableApp> =
    Allowlist.pickable(installed, { it.packageName }, hard).map { PickableApp(it, it.packageName in allowed) }

/**
 * The order the exceptions picker shows its rows in (issue #39): the rows that are switched on
 * first, then everything else, each group alphabetical by label ignoring case. [query] narrows
 * the rows by label first, so an on row stays pinned above the locked rows it is filtered with.
 * A blank query is no filter. Pure, so the rule is tested rather than incidental to the screen.
 */
fun orderForPicker(apps: List<PickableApp>, query: String): List<PickableApp> =
    apps.filter { query.isBlank() || it.app.label.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<PickableApp> { it.allowed }.thenBy { it.app.label.lowercase() })

/** [h] with what Edit habit changed (issue #134). Its id, order and history stay. */
fun editedHabit(h: HabitEntity, d: HabitDraft): HabitEntity = h.copy(
    title = d.name,
    iconKey = if (d.name == h.title) h.iconKey else d.iconKey,
    proofType = d.proof,
    targetValue = d.savedTarget,
    unit = d.savedUnit,
)

/** A lock-affecting settings change (issue #1), snake_case matching the PostHog `setting` property. */
enum class LockAffectingSetting(val eventValue: String) {
    LOCK_WINDOW("lock_window"),
    HABIT_REMOVED("habit_removed"),
    EXCEPTION_ON("exception_on"),
    NIGHT_LOCK("night_lock"),
    WEEKLY_GOAL("weekly_goal"),
    PROMISE_SWITCH("promise_switch"),
}

/**
 * Which locks are holding the phone right now. A guarded change asks only while the lock it would
 * open is one of them (issue #164): during night lockdown alone, moving the give-up time or
 * removing a habit opens nothing, so it is not asked about.
 *
 * [mustDoGoals] are the weekly goals whose must-do session is not logged today (issue #165).
 * One of them holds the lock while the daily lock is in force ([weeklyGoalHolds]), and only then
 * is lowering or deleting it asked about.
 */
data class LockHold(val daily: Boolean = false, val night: Boolean = false, val mustDoGoals: Set<Long> = emptySet()) {
    val any: Boolean get() = daily || night

    fun weeklyGoalHolds(goalId: Long): Boolean = daily && goalId in mustDoGoals
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
    private val promises: PromiseRepository,
    private val accounts: AccountRepository,
    /** Habit writes outlive the page: Add and Edit habit close as soon as they save (issue #134). */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<SettingsUi> = combine(
        prefs.settings, habitsRepo.observeHabits(), lockRepo.observeExceptions(), sprigRepo.state, dayRepo.observeHistory(45),
    ) { s, h, e, sp, hist -> SettingsUi(s, h, e, sp, hist, loaded = true) }
        .combine(lockRepo.observeBreaksThisWeek()) { s, b -> s.copy(breaksThisWeek = b.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    /** Re-checked every 30s in addition to whenever the underlying lock state changes (issue #1). */
    val lockHold: StateFlow<LockHold> = combine(lockRepo.observeState(), time.ticker(30_000)) { s, _ ->
        LockHold(
            daily = s.isDailyLockActiveNow(time.now(), time.localTime()),
            night = s.isNightLockActiveNow(time.now(), time.localTime()),
            mustDoGoals = s.weeklyMustDo.map { it.id }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockHold())

    /** Who is signed in, for the "Sign in with Google" row (#193). */
    val account: StateFlow<AccountState> = accounts.account

    private val _signIn = MutableStateFlow<SignInUi>(SignInUi.Idle)
    val signIn: StateFlow<SignInUi> = _signIn

    /** Runs the Google sheet from [activityContext]; a tap while it is open does nothing. */
    fun signIn(activityContext: Context) {
        if (_signIn.value == SignInUi.Busy) return
        _signIn.value = SignInUi.Busy
        clearDeleted()
        viewModelScope.launch { _signIn.value = accounts.signInWithGoogle(activityContext).toUi() }
    }

    private val _signingOut = MutableStateFlow(false)
    /** True while a sign out runs (#194); a second tap meanwhile does nothing. */
    val signingOut: StateFlow<Boolean> = _signingOut

    /**
     * Signs out (#194), after the user confirmed it. Busy until the account has left signed in, so
     * "Sign out" never flashes back before the group turns into "Sign in with Google".
     */
    fun signOut() {
        if (_signingOut.value || _delete.value == DeleteUi.Busy) return
        _signingOut.value = true
        _signIn.value = SignInUi.Idle
        _delete.value = DeleteUi.Idle
        viewModelScope.launch {
            try {
                accounts.signOut()
                withTimeoutOrNull(2_000) { accounts.account.first { it !is AccountState.SignedIn } }
            } finally {
                _signingOut.value = false
            }
        }
    }

    private val _delete = MutableStateFlow<DeleteUi>(DeleteUi.Idle)
    /** Delete account (#195): busy while it runs, then the line it leaves under the group. */
    val delete: StateFlow<DeleteUi> = _delete

    /**
     * Deletes the account (#195), after the user confirmed it. The server call and the local
     * sign-out run on the app scope, so leaving Settings midway cannot split them. Busy until the
     * account has left signed in, so "Delete account" never flashes back before the group turns
     * into "Sign in with Google".
     */
    fun deleteAccount() {
        if (_delete.value == DeleteUi.Busy || _signingOut.value) return
        _delete.value = DeleteUi.Busy
        _signIn.value = SignInUi.Idle
        viewModelScope.launch {
            val result = appScope.async { accounts.deleteAccount() }.await()
            if (result == DeleteResult.Success) withTimeoutOrNull(2_000) { accounts.account.first { it !is AccountState.SignedIn } }
            _delete.value = result.toUi()
        }
    }

    /** The user left the Settings tab: "Your account was deleted." goes (#195, item 4). */
    fun clearDeleted() {
        if (_delete.value == DeleteUi.Deleted) _delete.value = DeleteUi.Idle
    }

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
        // Which app was excepted is never reported. "No name, no email, never which apps you use"
        // is what the Settings toggle, the README and the privacy policy all promise, and the
        // package name is exactly "which apps you use" (issue #10).
        analytics.capture(if (allow) "exception_added" else "exception_removed")
    }

    /** Adding an exception (or turning one on) opens an app early during an active lock (issue #1). */
    fun requestToggleException(app: InstalledApp, allow: Boolean) {
        if (!allow) { toggleException(app, false); return }
        guard(
            LockAffectingSetting.EXCEPTION_ON,
            "Your apps are locked right now. Adding ${app.label} as an exception opens it early. Still add it?",
            held = { it.any },
        ) { toggleException(app, true) }
    }

    fun setLockWindow(from: Int, due: Int) = viewModelScope.launch {
        prefs.updateSettings { it.copy(lockFromMinute = from, dueMinute = due) }
        scheduler.scheduleReminders()
    }

    /**
     * Set in a lock window editor. Moving the give-up time or lock start opens apps early during
     * an active lock (issue #1); each editor asks in its own words, and a Set that changes nothing
     * saves nothing and asks nothing (issue #154). [from] and [due] are the window saved now.
     */
    fun requestSetLockWindow(field: LockWindowField, picked: Int, from: Int, due: Int) {
        val edit = LockWindowEdit.of(field, picked, from, due) ?: return
        guard(LockAffectingSetting.LOCK_WINDOW, edit.confirm) { setLockWindow(edit.from, edit.due) }
    }

    /**
     * A change in the Night lockdown group (issue #164). While night lockdown is holding the phone,
     * one that can open it (off, a later start, an earlier end) asks first, in its own words; any
     * other applies at once. Null is a change with nothing to store: see [NightEdit].
     */
    fun requestNightEdit(edit: NightEdit?) {
        edit ?: return
        val apply = { setNightLock(edit.result) }
        if (edit.confirm == null) apply() else guard(LockAffectingSetting.NIGHT_LOCK, edit.confirm, held = { it.night }) { apply() }
    }

    private fun setNightLock(n: NightSetting) = viewModelScope.launch {
        prefs.updateSettings { it.copy(nightLockEnabled = n.enabled, nightFromMinute = n.fromMinute, nightUntilMinute = n.untilMinute) }
    }

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

    /** At the limit nothing is added and nothing is reported (issue #134). */
    fun addHabit(title: String, iconKey: String, proof: ProofType, target: Int, unit: String) = appScope.launch {
        if (habitsRepo.addHabitWithinLimit(title, iconKey, proof, target, unit) != null) {
            analytics.capture("habit_added", mapOf("proof_type" to proof.name.lowercase()))
        }
    }
    /** Add a habit (issue #134): the icon is chosen from the name. */
    fun addHabit(d: HabitDraft) = addHabit(d.name, d.iconKey, d.proof, d.savedTarget, d.savedUnit)

    fun updateHabit(h: HabitEntity) = appScope.launch { habitsRepo.updateHabit(h) }

    /** Edit habit (issue #134). A renamed habit gets the icon for its new name; otherwise it keeps its own. */
    fun saveHabit(h: HabitEntity, d: HabitDraft) = updateHabit(editedHabit(h, d))
    fun removeHabit(id: Long) = appScope.launch {
        habitsRepo.removeHabit(id)
        analytics.capture("habit_removed")
    }

    /** Removing the last undone habit opens apps early during an active lock (issue #1). */
    fun requestRemoveHabit(id: Long, onRemoved: () -> Unit = {}) = guard(
        LockAffectingSetting.HABIT_REMOVED,
        HabitsCopy.REMOVE_WHILE_LOCKED,
    ) { removeHabit(id); onRemoved() }

    /**
     * Every day → some days a week (#236): habit [habitId] becomes the weekly goal [d]. While the
     * daily lock holds and the switch would open apps today ([PromiseSwitch.opensLock]) it asks
     * first; Cancel changes nothing and never runs [onSaved]. Otherwise, or once confirmed, the
     * switch is written and [onSaved] runs.
     */
    fun requestToWeekly(habitId: Long, d: PromiseDraft, onSaved: () -> Unit) {
        val days = d.days ?: return
        viewModelScope.launch {
            val opens = promises.toWeeklyOpens(habitId, days)
            guard(LockAffectingSetting.PROMISE_SWITCH, PromiseSwitch.TO_WEEKLY_WHILE_LOCKED, held = { it.daily && opens }) {
                appScope.launch { promises.toWeekly(habitId, d) }
                onSaved()
            }
        }
    }

    /** Some days a week → every day (#236): goal [goalId] becomes the daily habit [d]. It never opens the lock, so it never asks. */
    fun toDaily(goalId: Long, d: PromiseDraft) = appScope.launch { promises.toDaily(goalId, d) }

    /**
     * What saving weekly goal [goalId] with [newDays] a week (it has [savedDays] now), or
     * deleting it ([newDays] null), has to ask first: the lock-change question while the goal
     * holds the lock and the change can open it, else null (issue #165, Edge cases).
     */
    fun weeklyGoalConfirm(goalId: Long, savedDays: Int, newDays: Int?): String? =
        WeeklyGoalLockEdit.confirm(lockHold.value.weeklyGoalHolds(goalId), savedDays, newDays)

    /**
     * Saves or deletes a weekly goal ([action]) through the lock-change confirm: lowering or
     * deleting a goal that holds the lock right now asks first, in its own words; raising it,
     * renaming it, or any change to a goal that does not hold the lock applies at once.
     */
    fun requestWeeklyGoalChange(goalId: Long, savedDays: Int, newDays: Int?, action: () -> Unit) {
        val confirm = weeklyGoalConfirm(goalId, savedDays, newDays)
        if (confirm == null) action() else guard(LockAffectingSetting.WEEKLY_GOAL, confirm, held = { it.weeklyGoalHolds(goalId) }) { action() }
    }

    /**
     * Runs [action] immediately unless the lock it would open is holding right now ([held]; the
     * daily habit lock unless said otherwise), in which case it is kept until the user confirms
     * via [confirmPendingLockChange] (issue #1). No cost, no delay: this is friction and
     * self-awareness, not enforcement.
     */
    private fun guard(
        setting: LockAffectingSetting,
        message: String,
        held: (LockHold) -> Boolean = { it.daily },
        action: () -> Unit,
    ) {
        if (held(lockHold.value)) _pendingLockChange.value = PendingLockChange(setting, message, action) else action()
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
