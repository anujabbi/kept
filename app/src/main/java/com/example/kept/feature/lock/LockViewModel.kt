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
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.Variants
import com.example.kept.core.domain.WeekVariant
import com.example.kept.core.domain.minuteOfDayLabel
import com.example.kept.core.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
    val variant: WeekVariant = Variants.table[0],
    val lockActive: Boolean = true,
    /**
     * Whether the package this lock screen was raised for is still blocked (issue #4). Goes false
     * the moment the user adds it as an exception, even though the lock itself stays on.
     */
    val blockedStillLocked: Boolean = true,
    val breaking: Boolean = false,
) {
    /** True once the lock screen has nothing left to cover and should get out of the way. */
    val shouldDismiss: Boolean get() = loaded && (!lockActive || !blockedStillLocked)

    /**
     * The lock lifted because the day was finished in time, rather than because of a break, an
     * exception or the window closing (issue #9). Read from the same emission as [shouldDismiss],
     * so the two can never disagree about why the screen is going away.
     */
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

    val state: StateFlow<LockUi> = combine(
        lockRepo.observeState(), sprigRepo.state, time.ticker(1_000), blockedPackage,
    ) { lock, sprig, _, blocked ->
        val snapshot = lock.snapshot(emptySet(), null)
        val active = LockPolicy.isLockActive(time.now(), time.localTime(), snapshot)
        LockUi(
            loaded = true, lock = lock, sprig = sprig, variant = Variants.forDate(time.today()),
            lockActive = active,
            blockedStillLocked = LockPolicy.lockScreenShouldStay(blocked, time.now(), time.localTime(), snapshot),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUi())

    /**
     * Ticked on the application scope, not `viewModelScope` (issue #9). Completing the last habit
     * lifts the lock, and `LockActivity` reacts by calling `finishAndRemoveTask()` — which clears
     * this ViewModel. On `viewModelScope` that cancelled the rest of the work mid-flight, so the
     * level grant and the celebration flag were written only sometimes.
     */
    fun complete(habitId: Long) = appScope.launch { actions.complete(habitId) }

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
