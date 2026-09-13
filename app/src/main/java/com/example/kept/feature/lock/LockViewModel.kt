package com.example.kept.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.posthog.PostHog
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val breaking: Boolean = false,
) {
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
) : ViewModel() {

    val state: StateFlow<LockUi> = combine(lockRepo.observeState(), sprigRepo.state, time.ticker(1_000)) { lock, sprig, _ ->
        val active = LockPolicy.isLockActive(time.now(), time.localTime(), lock.snapshot(emptySet(), null))
        LockUi(loaded = true, lock = lock, sprig = sprig, variant = Variants.forDate(time.today()), lockActive = active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUi())

    fun complete(habitId: Long) = viewModelScope.launch { actions.complete(habitId) }

    fun breakLock(then: () -> Unit) = viewModelScope.launch {
        lockRepo.breakLock()
        PostHog.capture("lock_broken")
        then()
    }
}
