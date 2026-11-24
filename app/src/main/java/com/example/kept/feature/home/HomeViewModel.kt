package com.example.kept.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.GalleryCard
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.LockState
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.WeeklyGoalWeek
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.mustDoLoggedAt
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GiveUpTime
import com.example.kept.core.domain.HomeDay
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.WeeklyHold
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.lock.Permissions
import com.example.kept.core.lock.RequiredPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val loaded: Boolean = false,
    val today: TodaySummary = TodaySummary(emptyList()),
    val sprig: SprigState = SprigState(),
    val lock: LockState? = null,
    val lockActive: Boolean = false,
    /** True once today's give-up time has passed: ticks still work but no longer count (issue #7). */
    val pastDue: Boolean = false,
    /** Epoch millis of today's give-up time: a must-do session logged at or after it does not keep the day (story #176). */
    val dueMillis: Long = Long.MAX_VALUE,
    val date: LocalDate = LocalDate.now(),
    val pendingRecapDate: String? = null,
    val unseenUnlock: GalleryCard? = null,
    /** Lock permissions not granted right now, [RequiredPermissions.lockGate] order; empty means the lock can run. */
    val missingLockPermissions: List<PermissionKind> = emptyList(),
    val lastSeven: List<Pair<LocalDate, DayRecordEntity?>> = emptyList(),
    val todayRecord: DayRecordEntity? = null,
    /** Show the one-time "you're all set" card above everything else (issue #41). */
    val firstLanding: Boolean = false,
    /** Read live each render, so the first-landing card's reminder line reflects the current grant. */
    val notificationsGranted: Boolean = true,
    /** The active weekly goals with this week's state (issue #165). They are not habits: [today] does not count them. */
    val weeklyGoals: List<WeeklyGoalWeek> = emptyList(),
) {
    val form: SprigForm get() = sprig.form
    val nextForm: SprigForm? get() = SprigForm.next(form)
    val daysToNext: Int? get() = nextForm?.let { it.streakThreshold - sprig.streakDays }

    /**
     * What the sky, the headline and Sprig count: the habits plus each weekly goal's must-do
     * session still holding today (story #175), so Home never says "Promise kept. Apps are open."
     * while the lock is waiting on a session. With no must-do session it is the habits alone.
     * After the give-up time a must-do session that was not logged in time holds nothing, but
     * the day is missed, so Home does not say "Promise kept." for it either (story #176).
     */
    val day: HomeDay
        get() = HomeDay.of(
            today.total, today.done, today.allDoneOnTime,
            weeklyLeft = WeeklyHold.left(weeklyGoals.map { it.week }, pastDue),
            weeklyMissed = WeeklyHold.missed(weeklyGoals.mustDoLoggedAt(), pastDue, dueMillis),
        )
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
    private val time: TimeSource,
    private val weeklyGoals: WeeklyGoalRepository,
) : ViewModel() {

    private val core = combine(habits.observeToday(), sprigRepo.state, lockRepo.observeState(), prefs.settings, time.observeToday()) { today, sprig, lock, settings, date ->
        val active = LockPolicy.isLockActive(time.now(), time.localTime(), lock.snapshot(emptySet(), null))
        // Same rule the rollover uses, wrapping windows included.
        val pastDue = GiveUpTime.isPastDue(time.minuteOfDay(), settings.lockFromMinute, settings.dueMinute)
        HomeUiState(
            loaded = true, today = today, sprig = sprig, lock = lock, lockActive = active, pastDue = pastDue,
            dueMillis = GiveUpTime.instantFor(date, settings.lockFromMinute, settings.dueMinute, time.zone()).toEpochMilli(),
            date = date, pendingRecapDate = settings.pendingRecapDate,
            firstLanding = settings.firstLandingPending,
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        core, sprigRepo.unseenUnlocks, dayRepo.observeLastSeven(), dayRepo.observeToday(), weeklyGoals.observeThisWeek(),
    ) { s, unseen, seven, todayRec, weekly ->
        s.copy(
            unseenUnlock = unseen.firstOrNull(),
            missingLockPermissions = permissions.missing(RequiredPermissions.lockGate),
            notificationsGranted = permissions.notificationsGranted(),
            lastSeven = seven,
            todayRecord = todayRec,
            weeklyGoals = weekly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun complete(habitId: Long) = viewModelScope.launch { actions.complete(habitId) }
    fun undo(habitId: Long) = viewModelScope.launch { actions.undo(habitId) }
    fun completeWithPhoto(habitId: Long, path: String) = viewModelScope.launch { actions.complete(habitId, path) }

    /**
     * Logs today's session of a weekly goal. One session a day counts: the repository stores
     * nothing on a second call, so a double tap cannot count twice. Through [HabitActions], as a
     * habit tick goes: on a must-do day the session can be what finishes the day (story #176).
     */
    fun logWeekly(goalId: Long) = viewModelScope.launch { actions.logWeekly(goalId) }
    fun undoWeekly(goalId: Long) = viewModelScope.launch { actions.undoWeekly(goalId) }

    /**
     * Opens the rollover recap if one is still pending in storage, and takes it so it opens once.
     *
     * [HomeUiState.pendingRecapDate] only says when to ask. It cannot be the answer: [state] stops
     * collecting five seconds after Home leaves the screen and keeps its last value, and a Home
     * that comes back and leaves for the recap straight away is stopped again before it collects
     * a newer one. Opening on that remembered date reopened the recap on every return to Home,
     * so after more than five seconds on "Sprig is now …" Done and Back did nothing (issue #188).
     */
    fun openPendingRecap(open: (String) -> Unit) = viewModelScope.launch { prefs.claimPendingRecap()?.let(open) }

    /**
     * The first-landing card is gone for good: "Got it" was tapped, or Home stopped being shown
     * (issue #41). Cleared here rather than in the composable so a rotation, which recomposes
     * Home but keeps this ViewModel, does not count as a second visit.
     */
    fun firstLandingSeen() = viewModelScope.launch {
        // When Home stops because the activity is finishing, this ViewModel is cleared right
        // after ON_STOP; the write must outlive that or the card comes back on the next launch.
        if (state.value.firstLanding) withContext(NonCancellable) { prefs.updateSettings { it.copy(firstLandingPending = false) } }
    }
    fun refreshPermissions() = viewModelScope.launch { /* state recomputes on next emission */ }
}
