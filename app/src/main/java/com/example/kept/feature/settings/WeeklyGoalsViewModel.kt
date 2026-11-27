package com.example.kept.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.di.ApplicationScope
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.PromiseDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The active weekly goals, and Sprig as the setup screen draws it. [loaded] is false until the first read. */
data class WeeklyGoalsUi(
    val goals: List<WeeklyGoalEntity> = emptyList(),
    val loaded: Boolean = false,
    val form: SprigForm = SprigForm.SPRIG,
    val wilted: Boolean = false,
)

/**
 * Weekly goals in Settings (issue #165, story #173): the list, and add, edit and delete from the
 * setup screen. The rules (the limit of three, the name and the days) hold in
 * [WeeklyGoalRepository]; a draft that cannot be saved is dropped here rather than thrown there.
 */
@HiltViewModel
class WeeklyGoalsViewModel @Inject constructor(
    private val goals: WeeklyGoalRepository,
    sprig: SprigRepository,
    /** Writes outlive the page: the setup screen closes as soon as it saves, as Add a habit does. */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<WeeklyGoalsUi> = combine(goals.observeGoals(), sprig.state) { g, s ->
        WeeklyGoalsUi(g, loaded = true, form = s.form, wilted = s.wilted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyGoalsUi())

    /** A weekly promise (#218) as a new goal, counting from today. At the limit nothing is added. */
    fun add(d: PromiseDraft) = appScope.launch {
        val days = d.days
        if (d.canSave && days != null) goals.addGoal(d.name, days, d.goalTarget, d.goalUnit)
    }

    /** The new activity, target and days apply at once; this week's logged sessions are kept. */
    fun save(id: Long, d: PromiseDraft) = appScope.launch {
        val days = d.days
        if (d.canSave && days != null) goals.updateGoal(id, d.name, days, d.goalTarget, d.goalUnit)
    }

    fun remove(id: Long) = appScope.launch { goals.removeGoal(id) }
}
