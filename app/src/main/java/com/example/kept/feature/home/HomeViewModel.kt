package com.example.kept.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.BuddyRepository
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.GalleryCard
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.LockState
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.Variants
import com.example.kept.core.domain.WeekVariant
import com.example.kept.core.lock.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val loaded: Boolean = false,
    val today: TodaySummary = TodaySummary(emptyList()),
    val sprig: SprigState = SprigState(),
    val lock: LockState? = null,
    val lockActive: Boolean = false,
    val variant: WeekVariant = Variants.table[0],
    val date: LocalDate = LocalDate.now(),
    val pendingRecapDate: String? = null,
    val unseenUnlock: GalleryCard? = null,
    val usageAccessMissing: Boolean = false,
    val lastSeven: List<Pair<LocalDate, DayRecordEntity?>> = emptyList(),
    val todayRecord: DayRecordEntity? = null,
    val buddyDoneToday: Boolean? = null,
) {
    val form: SprigForm get() = sprig.form
    val nextForm: SprigForm? get() = SprigForm.next(form)
    val daysToNext: Int? get() = nextForm?.let { it.streakThreshold - sprig.streakDays }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val actions: HabitActions,
    private val sprigRepo: SprigRepository,
    private val lockRepo: LockRepository,
    private val dayRepo: DayRepository,
    private val prefs: KeptPreferences,
    private val permissions: Permissions,
    private val buddy: BuddyRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val core = combine(habits.observeToday(), sprigRepo.state, lockRepo.observeState(), prefs.settings, time.observeToday()) { today, sprig, lock, settings, date ->
        val active = LockPolicy.isLockActive(time.now(), time.localTime(), lock.snapshot(emptySet(), null))
        HomeUiState(
            loaded = true, today = today, sprig = sprig, lock = lock, lockActive = active,
            variant = Variants.forDate(date), date = date, pendingRecapDate = settings.pendingRecapDate,
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        core, sprigRepo.unseenUnlocks, dayRepo.observeLastSeven(), dayRepo.observeToday(), buddy.observeBuddy(),
    ) { s, unseen, seven, todayRec, b ->
        s.copy(
            unseenUnlock = unseen.firstOrNull(),
            usageAccessMissing = !permissions.lockPermissionsGranted(),
            lastSeven = seven,
            todayRecord = todayRec,
            buddyDoneToday = b?.doneToday,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun complete(habitId: Long) = viewModelScope.launch { actions.complete(habitId) }
    fun undo(habitId: Long) = viewModelScope.launch { actions.undo(habitId) }
    fun completeWithPhoto(habitId: Long, path: String) = viewModelScope.launch { actions.complete(habitId, path) }
    fun dismissRecap() = viewModelScope.launch { prefs.updateSettings { it.copy(pendingRecapDate = null) } }
    fun refreshPermissions() = viewModelScope.launch { /* state recomputes on next emission */ }
}
