package com.example.kept.core.data

import com.example.kept.core.data.db.WeeklyGoalDao
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.db.WeeklyGoalEntryDao
import com.example.kept.core.data.db.WeeklyGoalEntryEntity
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.HabitIconRules
import com.example.kept.core.domain.WeeklyGoalRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An active weekly goal and its current week, worked out by [WeeklyGoalRules.week].
 * [loggedTodayAt] is when today's session was logged (epoch millis), or null with none: a must-do
 * session only keeps the day when it is logged before the give-up time (story #176).
 */
data class WeeklyGoalWeek(val goal: WeeklyGoalEntity, val week: WeeklyGoalRules.Week, val loggedTodayAt: Long? = null) {
    val id: Long get() = goal.id
}

/**
 * For each goal whose today is a must-do day, when its session was logged, or null: what
 * [com.example.kept.core.domain.WeeklyHold.allInTime] and `missed` ask about.
 */
fun List<WeeklyGoalWeek>.mustDoLoggedAt(): List<Long?> = filter { it.week.todayCounts }.map { it.loggedTodayAt }

/**
 * Weekly goals (issue #165): "X days a week", at most one session a day. The week's progress is
 * never stored; every read counts `weekly_goal_entries` for the Sunday-to-Saturday week that
 * holds today's local date, so an edit or an undo cannot leave a count behind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WeeklyGoalRepository @Inject constructor(
    private val goalDao: WeeklyGoalDao,
    private val entryDao: WeeklyGoalEntryDao,
    private val time: TimeSource,
) {
    fun observeGoals(): Flow<List<WeeklyGoalEntity>> = goalDao.observeActive()

    /** The active goals with this week's state; emits again on a new day, a session, an undo or an edit. */
    fun observeThisWeek(): Flow<List<WeeklyGoalWeek>> = time.observeToday().flatMapLatest { today ->
        val from = WeeklyGoalRules.weekStart(today).toString()
        val to = WeeklyGoalRules.weekEnd(today).toString()
        combine(goalDao.observeActive(), entryDao.observeBetween(from, to)) { goals, entries -> weeks(goals, entries, today) }
    }

    suspend fun thisWeek(): List<WeeklyGoalWeek> {
        val today = time.today()
        val entries = entryDao.between(WeeklyGoalRules.weekStart(today).toString(), WeeklyGoalRules.weekEnd(today).toString())
        return weeks(goalDao.active(), entries, today)
    }

    private fun weeks(goals: List<WeeklyGoalEntity>, entries: List<WeeklyGoalEntryEntity>, today: LocalDate): List<WeeklyGoalWeek> {
        val byGoal = entries.groupBy { it.goalId }
        val todayKey = today.toString()
        return goals.map { goal ->
            val mine = byGoal[goal.id].orEmpty()
            val rules = WeeklyGoalRules.Goal(goal.daysPerWeek, LocalDate.parse(goal.effectiveFrom))
            WeeklyGoalWeek(
                goal,
                WeeklyGoalRules.week(rules, mine.map { LocalDate.parse(it.date) }.toSet(), today),
                loggedTodayAt = mine.firstOrNull { it.date == todayKey }?.completedAt,
            )
        }
    }

    /** Serialises [addGoal], so two saves at once cannot both see room for one more. */
    private val addLock = Mutex()

    /**
     * Adds a goal that counts from today. Returns the new id, or null when there are already
     * [WeeklyGoalRules.MAX_GOALS] active goals and nothing was added. Daily habits are not counted.
     * [targetValue] and [unit] are how much each session is (story #205), or both null for none.
     */
    suspend fun addGoal(title: String, daysPerWeek: Int, targetValue: Int? = null, unit: String? = null): Long? {
        val name = checked(title, daysPerWeek)
        checkedTarget(targetValue, unit)
        return addLock.withLock {
            val active = goalDao.active()
            if (!WeeklyGoalRules.canAdd(active.size)) {
                null
            } else {
                goalDao.insert(
                    WeeklyGoalEntity(
                        title = name, iconKey = HabitIconRules.forName(name), daysPerWeek = daysPerWeek,
                        createdAt = time.nowMillis(), effectiveFrom = time.todayKey(),
                        sortOrder = (active.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                        targetValue = targetValue, unit = unit,
                    ),
                )
            }
        }
    }

    /**
     * Changes a goal's activity, days a week and target (both null for none). The new days a
     * week apply to the current week at once and logged sessions are kept; the day the goal
     * started counting does not move.
     * Returns false when there is no such active goal.
     *
     * One conditional UPDATE, not a read and a write of the whole row: an edit racing a remove
     * used to be able to write `isActive = 1` back over it, bringing the goal back and, with three
     * others added meanwhile, passing [WeeklyGoalRules.MAX_GOALS] (review on #177).
     */
    suspend fun updateGoal(id: Long, title: String, daysPerWeek: Int, targetValue: Int? = null, unit: String? = null): Boolean {
        val name = checked(title, daysPerWeek)
        checkedTarget(targetValue, unit)
        return goalDao.updateActive(id, name, HabitIconRules.forName(name), daysPerWeek, targetValue, unit) > 0
    }

    /** Removes a goal from the active set. Its row and its sessions stay, as a removed habit's do. */
    suspend fun removeGoal(id: Long) = goalDao.deactivate(id)

    /**
     * Logs today's session. Idempotent: a second call the same day stores nothing and returns
     * false, as does an unknown or removed goal. Returns true when a session was stored.
     */
    suspend fun logToday(goalId: Long): Boolean {
        goalDao.byId(goalId)?.takeIf { it.isActive } ?: return false
        return entryDao.insertIfAbsent(WeeklyGoalEntryEntity(goalId = goalId, date = time.todayKey(), completedAt = time.nowMillis())) != -1L
    }

    /** Removes today's session. Returns false when there was none. */
    suspend fun undoToday(goalId: Long): Boolean = entryDao.delete(goalId, time.todayKey()) > 0

    /** The name as saved. The setup screen enforces the same limits; this is the last guard. */
    private fun checked(title: String, daysPerWeek: Int): String {
        val name = title.trim()
        require(name.isNotEmpty() && name.length <= HabitDraft.MAX_TITLE) { "weekly goal title must be 1 to ${HabitDraft.MAX_TITLE} characters" }
        require(WeeklyGoalRules.isValidDaysPerWeek(daysPerWeek)) { "days per week must be ${WeeklyGoalRules.MIN_DAYS} to ${WeeklyGoalRules.MAX_DAYS}" }
        return name
    }

    /** A target is 1 to [HabitDraft.MAX_TARGET] in a named unit, or no target at all: both null. */
    private fun checkedTarget(targetValue: Int?, unit: String?) {
        if (targetValue == null && unit == null) return
        require(targetValue != null && targetValue in 1..HabitDraft.MAX_TARGET) { "weekly goal target must be 1 to ${HabitDraft.MAX_TARGET}" }
        require(!unit.isNullOrBlank()) { "weekly goal target needs a unit" }
    }
}
