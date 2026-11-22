package com.example.kept.feature.lock

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.analytics.Screens
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitEvent
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.LockState
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.domain.LockMode
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.LockReason
import com.example.kept.core.domain.NightWindow
import com.example.kept.core.domain.MomentRules
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.minuteOfDayLabel
import com.example.kept.core.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockUi(
    val loaded: Boolean = false,
    val lock: LockState? = null,
    val sprig: SprigState = SprigState(),
    val lockActive: Boolean = true,
    /**
     * Whether the package this lock screen was raised for is still blocked (issue #4). Goes false
     * the moment the user adds it as an exception, even though the lock itself stays on.
     */
    val blockedStillLocked: Boolean = true,
    val breaking: Boolean = false,
    /** Which lock is holding the phone, or null when none is (issue #164). */
    val reason: LockReason? = null,
    /**
     * Which showing of the lock screen this state was computed for (`LockViewModel.onShown`). A
     * state from an earlier showing is stale (issue #127): see [forShowing].
     */
    val showing: Int = 0,
) {
    /**
     * Locked, open (every habit done in time, issue #127) or gone. Read from one emission, so the
     * screen can never show the open state for a reason [shouldDismiss] disagrees with.
     */
    val mode: LockMode get() = LockMode.of(loaded, lockActive, blockedStillLocked, finishedToday, night)

    /**
     * Night lockdown is what holds the phone (issue #164): the screen shows its night variant, with
     * no habit list and no "Open {App}", and a finished day does not open it.
     */
    val night: Boolean get() = reason == LockReason.NIGHT

    /** When night lockdown ends, as a minute of the day, for "Apps open at 7:00 am." */
    val nightUntilMinute: Int get() = lock?.settings?.nightUntilMinute ?: NightWindow.DEFAULT_UNTIL

    /** True once the lock screen has nothing left to cover and should get out of the way. */
    val shouldDismiss: Boolean get() = mode == LockMode.DISMISS

    /** The day was finished in time: the screen turns gold and offers "Open {App}". */
    val open: Boolean get() = mode == LockMode.OPEN

    /** The lock lifted because the day was finished in time, not a break, exception or the window closing (issue #9). */
    val finishedToday: Boolean get() = lock?.today?.allDoneOnTime == true

    /**
     * This state if it was computed for [showing], else a blank, not-yet-loaded one. A lock screen
     * left in its gold open state with Home (rather than "Open {App}") stays in the back stack;
     * when the watcher raises it again, maybe the next day, the last state it held must not flash
     * up before today's arrives.
     */
    fun forShowing(showing: Int): LockUi = if (this.showing == showing) this else LockUi(showing = showing)

    val remaining: Int get() = lock?.today?.remaining ?: 0
    val dueLabel: String get() = lock?.settings?.dueMinute?.minuteOfDayLabel() ?: ""
    val breaksLeft: Int get() = lock?.breaksRemaining ?: 0
    val overCap: Boolean get() = lock?.nextBreakOverCap ?: false
    val breakMinutes: Int get() = lock?.settings?.breakDurationMin ?: 30
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val lockRepo: LockRepository,
    private val sprigRepo: SprigRepository,
    private val actions: HabitActions,
    private val time: TimeSource,
    @ApplicationScope private val appScope: CoroutineScope,
    private val analytics: Analytics,
) : ViewModel() {

    /**
     * The package this lock screen is covering and its label, from the intent that raised it last.
     * Kept here, in the saved state, rather than in the activity: a configuration change rebuilds
     * the activity from its first intent, which may name an earlier app, and process death loses
     * the activity's fields. The screen answers to this package (issue #4).
     */
    private val blockedPackage: StateFlow<String?> = saved.getStateFlow(KEY_PKG, null)
    val blockedLabel: StateFlow<String> = saved.getStateFlow(KEY_LABEL, "")

    /** The package the screen covers now, for "Open {App}". */
    val blockedPackageNow: String? get() = blockedPackage.value

    /** Called with the extras of each intent that raises the screen; a missing extra keeps the last value. */
    fun setBlocked(pkg: String?, label: String?) {
        if (pkg != null) saved[KEY_PKG] = pkg
        if (label != null) saved[KEY_LABEL] = label
    }

    /**
     * Which showing of the lock screen this is: bumped each time the watcher raises the screen
     * again ([onShown]). It lives only here, so it survives a configuration change with the
     * ViewModel and can never disagree with the states below. It is Compose state so the
     * activity's very next frame drops the last showing's state, before the flows catch up.
     */
    var showing by mutableIntStateOf(0)
        private set
    private val showingFlow = MutableStateFlow(0)

    /**
     * The lock screen was raised again (`LockActivity.onNewIntent`): recompute everything for it.
     * A break screen left open on an earlier showing (break, then Home) is closed without a
     * "lock_break_cancelled" event, so the new showing starts on the lock screen and a break taken
     * from it waits its own full minute (review of PR #141).
     */
    fun onShown() {
        showing += 1
        showingFlow.value = showing
        breakTimer.cancel()
        saved[KEY_MOMENT] = false
    }

    /** The current state as it may be shown: anything from an earlier showing reads as not loaded yet. */
    fun shown(live: LockUi): LockUi = live.forShowing(showing)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LockUi> = showingFlow.flatMapLatest { n ->
        lockUiFlow(lockRepo.observeState(), sprigRepo.state, time.ticker(1_000), blockedPackage, time, n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUi())

    /**
     * Ticks and undos go through [HabitActions], exactly as on Home, so a tick on the lock screen
     * counts the same (on time or late, level, recap). The one difference: a day finished here in
     * time plays its "Promise kept" moment on this screen, before the gold open state, so nothing
     * is left pending for KEPT (`momentShown`, issue #130). They run on the application scope, not
     * `viewModelScope` (issue #9): the write must not be cancelled if the activity finishes.
     */
    fun complete(habitId: Long) { beforeTick(); appScope.launch { afterTick(actions.complete(habitId, momentShown = true)) } }
    fun completeWithPhoto(habitId: Long, path: String) { beforeTick(); appScope.launch { afterTick(actions.complete(habitId, path, momentShown = true)) } }
    fun undo(habitId: Long) = appScope.launch { actions.undo(habitId) }

    /**
     * The tick finished the day in time: take today's moment (it plays once per day across KEPT and
     * the lock screen) and put it up for this showing. A day finished late gets nothing.
     */
    private suspend fun afterTick(event: HabitEvent.Completed?) {
        val claimed = event != null && MomentRules.finishedInTime(event.allDoneNow, event.beforeDue) && actions.claimMoment()
        val today = time.todayKey()
        viewModelScope.launch {
            // Carried on already today (perhaps while this tick was still in flight): the moment has
            // been seen, so it is not raised again, whatever order two ticks' claims land in.
            if (MomentRules.lockRaisesAfterClaim(claimed, saved.get<String>(KEY_CARRIED_ON_DATE), today)) {
                saved[KEY_MOMENT] = true
                analytics.screen(Screens.CELEBRATION)
            }
            finishing = false
        }
    }

    /**
     * The last habit left is being ticked: the day may be about to finish. Until [afterTick] knows,
     * the moment stands in for the open state, so the gold open state does not flash up for a
     * frame before the moment covers it.
     *
     * At most once per day, and only while today's moment can still play: not once it has played
     * (here or in KEPT, per the stored date), and not for a second finishing tick while the first
     * one's claim may still be in flight (undo and re-tick right after carrying on, QA on PR #144),
     * which the ViewModel remembers itself because the stored date may not be written yet.
     */
    var finishing by mutableStateOf(false)
        private set

    private fun beforeTick() {
        val now = shown(state.value)
        val today = time.todayKey()
        val shownDate = now.lock?.settings?.celebrationShownDate
        if (MomentRules.lockStandsIn(now.remaining, shownDate, saved.get<String>(KEY_STAND_IN_DATE), today)) {
            saved[KEY_STAND_IN_DATE] = today
            saved[KEY_MOMENT_INTRO_PLAYED] = false
            finishing = true
        }
    }

    /**
     * The "Promise kept" moment is up (issue #130). Kept in the saved state, so a rotation keeps
     * it on screen, and a new showing of the lock screen ([onShown]) takes it down.
     */
    val moment: StateFlow<Boolean> = saved.getStateFlow(KEY_MOMENT, false)

    /** The moment's pop, ring and petals have not played yet: they play once, not again after a rotation. */
    val momentIntroPending: Boolean get() = saved.get<Boolean>(KEY_MOMENT_INTRO_PLAYED) != true

    fun momentIntroStarted() {
        saved[KEY_MOMENT_INTRO_PLAYED] = true
    }

    /** "Tap to carry on" (or Back): the gold open state with "Open {App}" shows; today's moment has been seen. */
    fun carryOn() {
        saved[KEY_CARRIED_ON_DATE] = time.todayKey()
        finishing = false
        saved[KEY_MOMENT] = false
    }

    /**
     * `LockActivity` is its own surface: no NavController reaches it, so the two screens it can
     * show report themselves through these (issue #10).
     */
    fun reportLockScreenViewed() = analytics.screen(Screens.LOCK)

    /**
     * The break screen (issue #128): whether it is up, its countdown and Sprig's one-time rise. Kept
     * here rather than in the composable, so a rotation keeps the screen and the countdown going.
     */
    val breakTimer = BreakTimer(saved, viewModelScope, { SystemClock.elapsedRealtime() })

    /** "Break the lock": open the break screen and start its minute. Reported once, not on every recreation. */
    fun startBreak() {
        if (breakTimer.start()) {
            analytics.capture("lock_break_started")
            analytics.screen(Screens.BREAK)
        }
    }

    /** "Never mind, go back" or Back. */
    fun cancelBreak() {
        breakTimer.cancel()
        analytics.capture("lock_break_cancelled")
    }

    /**
     * "Unlock for {minutes} min". `seconds_waited` is how long the break screen was held open before
     * the unlock was confirmed: the countdown only unlocks the button after a minute, so this says
     * how much longer than that the user sat with it (issue #10).
     */
    fun breakLock(then: () -> Unit) {
        val waited = breakTimer.secondsWaited()
        viewModelScope.launch {
            lockRepo.breakLock()
            analytics.capture("lock_broken", mapOf("seconds_waited" to waited))
            then()
        }
    }
}

private const val KEY_PKG = "blocked_pkg"
private const val KEY_LABEL = "blocked_label"
private const val KEY_MOMENT = "lock_moment"
private const val KEY_MOMENT_INTRO_PLAYED = "lock_moment_intro_played"
private const val KEY_STAND_IN_DATE = "lock_moment_stand_in_date"
private const val KEY_CARRIED_ON_DATE = "lock_moment_carried_on_date"

/**
 * The `combine` behind [LockViewModel.state], kept as a plain function of flows so a JVM test can
 * drive it with a pinned clock: the repositories it would otherwise need bottom out in Room and a
 * DataStore-backed `KeptPreferences(Context)`. [ticker] is what makes the result live: every
 * emission re-reads [time] for the lock policy, so the window ending lifts the lock on time.
 */
internal fun lockUiFlow(
    lockState: Flow<LockState>,
    sprig: Flow<SprigState>,
    ticker: Flow<Long>,
    blockedPackage: Flow<String?>,
    time: TimeSource,
    showing: Int = 0,
): Flow<LockUi> = combine(lockState, sprig, ticker, blockedPackage) { lock, sprigState, _, blocked ->
    val snapshot = lock.snapshot(emptySet(), null)
    val reason = LockPolicy.lockReason(time.now(), time.localTime(), snapshot)
    LockUi(
        loaded = true, lock = lock, sprig = sprigState,
        lockActive = reason != null,
        blockedStillLocked = LockPolicy.lockScreenShouldStay(blocked, time.now(), time.localTime(), snapshot),
        reason = reason,
        showing = showing,
    )
}
