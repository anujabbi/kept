package com.example.kept.core.data

import com.example.kept.core.data.db.DayRecordDao
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitDao
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryDao
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.domain.ProofType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

data class HabitToday(
    val habit: HabitEntity,
    val entry: HabitEntryEntity?,
) {
    val id: Long get() = habit.id
    val progress: Int get() = entry?.progressValue ?: 0
    val isDone: Boolean get() = entry?.completedAt != null
    /** 0..1 */
    val fraction: Float
        get() = when (habit.proofType) {
            ProofType.TIMER -> (progress / (habit.targetValue * 60f)).coerceIn(0f, 1f)
            else -> if (isDone) 1f else 0f
        }
    val progressLabel: String
        get() = when (habit.proofType) {
            ProofType.TIMER -> "${progress / 60}/${habit.targetValue}"
            ProofType.MANUAL -> if (isDone) "Done" else habit.unit.ifBlank { "" }
            ProofType.PHOTO -> if (isDone) "Done" else "Photo"
        }
    val subtitle: String
        get() = when (habit.proofType) {
            ProofType.TIMER -> "${habit.targetValue} min"
            else -> if (habit.targetValue > 1) "${habit.targetValue} ${habit.unit}" else habit.unit
        }
}

data class TodaySummary(val habits: List<HabitToday>) {
    val total: Int get() = habits.size
    val done: Int get() = habits.count { it.isDone }
    val remaining: Int get() = total - done
    val allDone: Boolean get() = total > 0 && remaining == 0
}

/** Habit completion transitions that other layers react to. */
sealed interface HabitEvent {
    data class Completed(val habit: HabitEntity, val allDoneNow: Boolean) : HabitEvent
    data class Undone(val habit: HabitEntity, val wasAllDone: Boolean) : HabitEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val entryDao: HabitEntryDao,
    private val dayDao: DayRecordDao,
    private val time: TimeSource,
) {
    fun observeHabits(): Flow<List<HabitEntity>> = habitDao.observeActive()

    fun observeToday(): Flow<TodaySummary> = time.observeToday().flatMapLatest { date ->
        combine(habitDao.observeActive(), entryDao.observeForDate(date.toString())) { habits, entries ->
            val byHabit = entries.associateBy { it.habitId }
            TodaySummary(habits.map { HabitToday(it, byHabit[it.id]) })
        }
    }

    fun observeHabit(id: Long): Flow<HabitToday?> = time.observeToday().flatMapLatest { date ->
        combine(habitDao.observeById(id), entryDao.observeForHabitOnDate(id, date.toString())) { h, e ->
            h?.let { HabitToday(it, e) }
        }
    }

    suspend fun today(): TodaySummary {
        val date = time.todayKey()
        val habits = habitDao.active()
        val entries = entryDao.forDate(date).associateBy { it.habitId }
        return TodaySummary(habits.map { HabitToday(it, entries[it.id]) })
    }

    suspend fun addHabit(title: String, iconKey: String, proofType: ProofType, targetValue: Int, unit: String): Long {
        val order = habitDao.active().size
        return habitDao.insert(
            HabitEntity(
                title = title.trim(), iconKey = iconKey, proofType = proofType,
                targetValue = targetValue, unit = unit, sortOrder = order, createdAt = time.nowMillis(),
            ),
        )
    }

    suspend fun updateHabit(habit: HabitEntity) = habitDao.update(habit)
    suspend fun removeHabit(id: Long) = habitDao.deactivate(id)

    /** Replace the active habit set (used by onboarding). */
    suspend fun replaceAll(habits: List<HabitEntity>) {
        habitDao.active().forEach { habitDao.deactivate(it.id) }
        habits.forEachIndexed { i, h -> habitDao.insert(h.copy(id = 0, sortOrder = i, createdAt = time.nowMillis())) }
    }

    /** Adds timer progress. Returns an event when this crosses the target. */
    suspend fun addTimerSeconds(habitId: Long, seconds: Int): HabitEvent? {
        val habit = habitDao.byId(habitId) ?: return null
        val date = time.todayKey()
        val cur = entryDao.forHabitOnDate(habitId, date) ?: HabitEntryEntity(habitId = habitId, date = date)
        if (cur.completedAt != null) return null
        val target = habit.targetValue * 60
        val next = (cur.progressValue + seconds).coerceAtMost(target)
        val done = next >= target
        entryDao.upsert(cur.copy(progressValue = next, completedAt = if (done) time.nowMillis() else null))
        return if (done) completedEvent(habit) else null
    }

    suspend fun markDone(habitId: Long, photoPath: String? = null): HabitEvent? {
        val habit = habitDao.byId(habitId) ?: return null
        val date = time.todayKey()
        val cur = entryDao.forHabitOnDate(habitId, date) ?: HabitEntryEntity(habitId = habitId, date = date)
        if (cur.completedAt != null) return null
        val target = if (habit.proofType == ProofType.TIMER) habit.targetValue * 60 else habit.targetValue
        entryDao.upsert(cur.copy(progressValue = target, completedAt = time.nowMillis(), photoPath = photoPath ?: cur.photoPath))
        return completedEvent(habit)
    }

    suspend fun undo(habitId: Long): HabitEvent? {
        val habit = habitDao.byId(habitId) ?: return null
        val date = time.todayKey()
        val cur = entryDao.forHabitOnDate(habitId, date) ?: return null
        if (cur.completedAt == null) return null
        val wasAllDone = today().allDone
        val progress = if (habit.proofType == ProofType.TIMER) cur.progressValue else 0
        entryDao.upsert(cur.copy(progressValue = progress, completedAt = null, photoPath = null))
        syncCounts(date)
        return HabitEvent.Undone(habit, wasAllDone)
    }

    private suspend fun completedEvent(habit: HabitEntity): HabitEvent {
        val summary = today()
        syncCounts(time.todayKey(), summary)
        return HabitEvent.Completed(habit, summary.allDone)
    }

    suspend fun syncCounts(date: String, summary: TodaySummary? = null) {
        val s = summary ?: today()
        dayDao.insertIfAbsent(DayRecordEntity(date = date, habitsTotal = s.total))
        dayDao.setHabitCounts(date, s.done, s.total)
    }
}
