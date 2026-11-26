package com.example.kept.core.data

import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.PromiseDao
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseSwitch
import com.example.kept.core.domain.WeeklyGoalRules
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switching a saved promise between every day and some days a week (#236), atomically through
 * [PromiseDao]. The rules (where the goal starts counting, what carries over, when the lock can
 * open) are [PromiseSwitch]'s; this reads the clock and the stores for them.
 */
@Singleton
class PromiseRepository @Inject constructor(
    private val dao: PromiseDao,
    private val habits: HabitRepository,
    private val time: TimeSource,
) {
    private fun startOf(h: HabitEntity): LocalDate = Instant.ofEpochMilli(h.createdAt).atZone(time.zone()).toLocalDate()

    /**
     * Whether making habit [habitId] [days] a week can open apps today while the daily lock holds
     * ([PromiseSwitch.opensLock]). False for a habit that is gone.
     */
    suspend fun toWeeklyOpens(habitId: Long, days: Int): Boolean {
        val h = dao.habit(habitId)?.takeIf { it.isActive } ?: return false
        val today = time.today()
        val done = dao.habitDone(habitId, WeeklyGoalRules.weekStart(today).toString(), today.toString()).map { LocalDate.parse(it.date) }
        return PromiseSwitch.opensLock(today in done, PromiseSwitch.weekAfter(days, startOf(h), done, today))
    }

    /**
     * Habit [habitId] becomes the weekly goal [d] describes, keeping its place and this week's done
     * days. A renamed one gets the icon for its new name. Returns the goal's id, or null when
     * nothing changed (see [PromiseDao.habitToGoal]).
     */
    suspend fun toWeekly(habitId: Long, d: PromiseDraft): Long? {
        val days = d.days ?: return null
        if (!d.canSave || d.name.length > HabitDraft.MAX_TITLE || !WeeklyGoalRules.isValidDaysPerWeek(days)) return null
        val h = dao.habit(habitId) ?: return null
        val today = time.today()
        val goal = WeeklyGoalEntity(
            title = d.name, iconKey = if (d.name == h.title) h.iconKey else d.iconKey, daysPerWeek = days,
            createdAt = time.nowMillis(), effectiveFrom = PromiseSwitch.goalStart(startOf(h), today).toString(),
            targetValue = d.goalTarget, unit = d.goalUnit,
        )
        val id = dao.habitToGoal(habitId, goal, WeeklyGoalRules.weekStart(today).toString(), today.toString())
        if (id != null) habits.syncCounts(today.toString())
        return id
    }

    /**
     * Weekly goal [goalId] becomes the daily habit [d] describes, keeping its place; a session
     * logged today makes today done. Returns the habit's id, or null when nothing changed (see
     * [PromiseDao.goalToHabit]).
     */
    suspend fun toDaily(goalId: Long, d: PromiseDraft): Long? {
        if (d.weekly || !d.canSave || d.name.length > HabitDraft.MAX_TITLE) return null
        val g = dao.goal(goalId) ?: return null
        val h = d.habit()
        val habit = HabitEntity(
            title = h.name, iconKey = if (h.name == g.title) g.iconKey else d.iconKey, proofType = h.proof,
            targetValue = h.savedTarget, unit = h.savedUnit, createdAt = time.nowMillis(),
        )
        val today = time.todayKey()
        val id = dao.goalToHabit(goalId, habit, today)
        if (id != null) habits.syncCounts(today)
        return id
    }
}
