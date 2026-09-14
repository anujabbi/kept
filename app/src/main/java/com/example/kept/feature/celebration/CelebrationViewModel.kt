package com.example.kept.feature.celebration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.Variants
import com.example.kept.core.domain.WeekVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CelebrationUiState(
    val visible: Boolean = false,
    val habitTitles: List<String> = emptyList(),
    val form: SprigForm = SprigForm.SPRIG,
    val variant: WeekVariant = Variants.table[0],
    val streakDays: Int = 0,
)

/**
 * Shows the "Promise kept." moment once per finished day (issue #9).
 *
 * The trigger is `Settings.celebrationPendingDate`, written by `HabitActions` when the last habit
 * is ticked in time. Going through storage rather than an in-memory signal is what makes the
 * notification path work: the day can be finished from a "Mark done" button with no UI alive, and
 * the moment is still waiting the next time KEPT is opened.
 */
@HiltViewModel
class CelebrationViewModel @Inject constructor(
    private val prefs: KeptPreferences,
    private val habits: HabitRepository,
    private val sprig: SprigRepository,
    private val time: TimeSource,
    val analytics: Analytics,
) : ViewModel() {

    val state: StateFlow<CelebrationUiState> =
        combine(prefs.settings, habits.observeToday(), sprig.state, time.observeToday()) { settings, today, sprigState, date ->
            // Only today's celebration: a pending one left over from yesterday is stale, and the
            // rollover recap is the right thing to show for that day instead.
            val pending = settings.celebrationPendingDate != null && settings.celebrationPendingDate == date.toString()
            CelebrationUiState(
                visible = pending && today.allDoneOnTime,
                habitTitles = today.habits.map { it.habit.title },
                form = sprigState.form,
                variant = Variants.forDate(date),
                streakDays = sprigState.streakDays,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CelebrationUiState())

    /** Marks today's celebration as shown. Called as the moment opens, not when it closes. */
    fun consume() = viewModelScope.launch {
        prefs.updateSettings { if (it.celebrationPendingDate == time.todayKey()) it.copy(celebrationPendingDate = null) else it }
    }
}
