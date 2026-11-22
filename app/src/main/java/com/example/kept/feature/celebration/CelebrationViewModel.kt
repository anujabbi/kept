package com.example.kept.feature.celebration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.analytics.Screens
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GiveUpTime
import com.example.kept.core.domain.MomentRules
import com.example.kept.core.domain.WeeklyHold
import com.example.kept.core.domain.SprigForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CelebrationUiState(
    /** Today's moment is waiting to be shown in KEPT. */
    val due: Boolean = false,
    val form: SprigForm = SprigForm.SPRIG,
    val streakDays: Int = 0,
)

/**
 * Shows the "Promise kept." moment in KEPT once per finished day (issue #9, #130).
 *
 * The trigger is `Settings.celebrationPendingDate`, written by `HabitActions` when the last habit
 * is ticked in time in KEPT or from a notification. Going through storage rather than an
 * in-memory signal is what makes the notification path work: the day can be finished from a
 * "Mark done" button with no UI alive, and the moment is still waiting the next time KEPT is
 * opened. A day whose moment already played (on the lock screen, issue #130) is not due.
 *
 * The moment says "Apps are open.", so it also waits while a weekly goal's must-do session is
 * still holding the lock (issue #165, story #175): it plays once that session is logged.
 *
 * Whether the moment is up, and whether its intro has played, live in [SavedStateHandle], so a
 * rotation neither loses the moment nor plays it again.
 */
@HiltViewModel
class CelebrationViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    prefs: KeptPreferences,
    habits: HabitRepository,
    sprig: SprigRepository,
    time: TimeSource,
    private val actions: HabitActions,
    private val analytics: Analytics,
    weeklyGoals: WeeklyGoalRepository,
) : ViewModel() {

    val state: StateFlow<CelebrationUiState> =
        combine(prefs.settings, habits.observeToday(), sprig.state, time.observeToday(), weeklyGoals.observeThisWeek()) { settings, today, sprigState, date, weekly ->
            val pastDue = GiveUpTime.isPastDue(time.minuteOfDay(), settings.lockFromMinute, settings.dueMinute)
            val weeklyLeft = WeeklyHold.left(weekly.map { it.week }, pastDue)
            // Only today's celebration: a pending one left over from yesterday is stale, and the
            // rollover recap is the right thing to show for that day instead.
            CelebrationUiState(
                due = weeklyLeft == 0 &&
                    MomentRules.dueInKept(settings.celebrationPendingDate, settings.celebrationShownDate, date.toString(), today.allDoneOnTime),
                form = sprigState.form,
                streakDays = sprigState.streakDays,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CelebrationUiState())

    /** The moment is on screen. */
    val showing: StateFlow<Boolean> = saved.getStateFlow(KEY_SHOWING, false)

    /** Its pop, ring and petals have not played yet in this showing. */
    val introPending: Boolean get() = saved.get<Boolean>(KEY_INTRO_PLAYED) != true

    fun introStarted() {
        saved[KEY_INTRO_PLAYED] = true
    }

    /**
     * Today's moment is due: claim it and put it up. It counts as shown from the moment it opens,
     * not when it closes: leaving it pending until dismissal meant a process death mid-animation
     * replayed the whole thing on next open. If the lock screen claimed it first, nothing shows.
     */
    fun begin() = viewModelScope.launch {
        if (showing.value) return@launch
        if (!actions.claimMoment()) return@launch
        saved[KEY_INTRO_PLAYED] = false
        saved[KEY_SHOWING] = true
        // A full-screen moment over whatever tab is open, so it is a screen in its own right even
        // though it is not a nav destination (issue #10).
        analytics.screen(Screens.CELEBRATION)
    }

    /** "Tap to carry on", or Back. */
    fun dismiss() {
        saved[KEY_SHOWING] = false
    }

    companion object {
        const val KEY_SHOWING = "celebration_showing"
        const val KEY_INTRO_PLAYED = "celebration_intro_played"
    }
}
