package com.example.kept.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.analytics.Screens
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.LockState
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.domain.LockMode
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.minuteOfDayLabel
import com.example.kept.core.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) {
    /**
     * Locked, open (every habit done in time, issue #127) or gone. Read from one emission, so the
     * screen can never show the open state for a reason [shouldDismiss] disagrees with.
     */
    val mode: LockMode get() = LockMode.of(loaded, lockActive, blockedStillLocked, finishedToday)

    /** True once the lock screen has nothing left to cover and should get out of the way. */
    val shouldDismiss: Boolean get() = mode == LockMode.DISMISS

    /** The day was finished in time: the screen turns gold and offers "Open {App}". */
    val open: Boolean get() = mode == LockMode.OPEN

    /** The lock lifted because the day was finished in time, not a break, exception or the window closing (issue #9). */
    val finishedToday: Boolean get() = lock?.today?.allDoneOnTime == true

    val remaining: Int get() = lock?.today?.remaining ?: 0
    val dueLabel: String get() = lock?.settings?.dueMinute?.minuteOfDayLabel() ?: ""
    val breaksLeft: Int get() = lock?.breaksRemaining ?: 0
    val overCap: Boolean get() = lock?.nextBreakOverCap ?: false
    val breakMinutes: Int get() = lock?.settings?.breakDurationMin ?: 30
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockRepo: LockRepository,
    private val sprigRepo: SprigRepository,
    private val actions: HabitActions,
    private val time: TimeSource,
    @ApplicationScope private val appScope: CoroutineScope,
    private val analytics: Analytics,
) : ViewModel() {

    private val blockedPackage = MutableStateFlow<String?>(null)

    /** The package this lock screen is covering, so the screen can answer to it (issue #4). */
    fun setBlockedPackage(pkg: String?) { blockedPackage.value = pkg }

    val state: StateFlow<LockUi> =
        lockUiFlow(lockRepo.observeState(), sprigRepo.state, time.ticker(1_000), blockedPackage, time)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUi())

    /**
     * Ticks and undos go through [HabitActions], exactly as on Home, so a tick on the lock screen
     * counts the same (on time or late, level, celebration, recap). They run on the application
     * scope, not `viewModelScope` (issue #9): the last tick can end with the activity finishing,
     * which clears this ViewModel, and the rest of the write must not be cancelled with it.
     */
    fun complete(habitId: Long) = appScope.launch { actions.complete(habitId) }
    fun completeWithPhoto(habitId: Long, path: String) = appScope.launch { actions.complete(habitId, path) }
    fun undo(habitId: Long) = appScope.launch { actions.undo(habitId) }

    /**
     * `LockActivity` is its own surface: no NavController reaches it, so the two screens it can
     * show report themselves through these (issue #10).
     */
    fun reportLockScreenViewed() = analytics.screen(Screens.LOCK)

    fun reportBreakScreenOpened() {
        analytics.capture("lock_break_started")
        analytics.screen(Screens.BREAK)
    }

    fun reportBreakCancelled() = analytics.capture("lock_break_cancelled")

    /** [secondsWaited] is how long the break screen was held open before the unlock was confirmed. */
    fun breakLock(secondsWaited: Int, then: () -> Unit) = viewModelScope.launch {
        lockRepo.breakLock()
        analytics.capture("lock_broken", mapOf("seconds_waited" to secondsWaited))
        then()
    }
}

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
): Flow<LockUi> = combine(lockState, sprig, ticker, blockedPackage) { lock, sprigState, _, blocked ->
    val snapshot = lock.snapshot(emptySet(), null)
    val active = LockPolicy.isLockActive(time.now(), time.localTime(), snapshot)
    LockUi(
        loaded = true, lock = lock, sprig = sprigState,
        lockActive = active,
        blockedStillLocked = LockPolicy.lockScreenShouldStay(blocked, time.now(), time.localTime(), snapshot),
    )
}
