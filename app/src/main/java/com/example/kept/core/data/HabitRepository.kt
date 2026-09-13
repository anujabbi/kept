package com.example.kept.core.data

import com.example.kept.core.data.db.DayRecordDao
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitDao
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryDao
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class HabitToday(
    val habit: HabitEntity,
    val entry: HabitEntryEntity?,
    /**
     * Epoch millis of that day's give-up time. A tick after it still shows as done but no longer
     * counts toward the day (issue #7). Long.MAX_VALUE means "not known here, treat as on time".
     */
    val dueMillis: Long = Long.MAX_VALUE,
) {
    val id: Long get() = habit.id
    val progress: Int get() = entry?.progressValue ?: 0
    val isDone: Boolean get() = entry?.completedAt != null
    /** Done, and done in time to count for today. */
    val countsToday: Boolean get() = entry?.completedAt?.let { it < dueMillis } == true
    /** Done, but after the give-up time, so it does nothing for today. */
    val late: Boolean get() = isDone && !countsToday
    /** 0..1 */
    val fraction: Float get() = if (isDone) 1f else 0f
    val progressLabel: String
        get() = when (habit.proofType) {
            ProofType.MANUAL -> if (isDone) "Done" else ""
            ProofType.PHOTO -> if (isDone) "Done" else "Photo"
        }
    val subtitle: String
        get() = if (habit.targetValue > 1) "${habit.targetValue} ${habit.unit}" else habit.unit
}

data class TodaySummary(val habits: List<HabitToday>) {
    val total: Int get() = habits.size
    val done: Int get() = habits.count { it.isDone }
    val remaining: Int get() = total - done
    val allDone: Boolean get() = total > 0 && remaining == 0
    /** Habits ticked before the give-up time. Only these count for the day. */
    val doneOnTime: Int get() = habits.count { it.countsToday }
    val allDoneOnTime: Boolean get() = total > 0 && doneOnTime == total
    /** At least one habit was ticked after the give-up time, so today cannot be kept. */
    val anyLate: Boolean get() = habits.any { it.late }
}

/** Habit completion transitions that other layers react to. */
sealed interface HabitEvent {
    /** [beforeDue] is false when the tick landed after the give-up time, so it counts for nothing. */
    data class Completed(val habit: HabitEntity, val allDoneNow: Boolean, val beforeDue: Boolean) : HabitEvent
    /** [wasAllDone] means the day was complete *and* on time before the undo, i.e. a level was owed. */
    data class Undone(val habit: HabitEntity, val wasAllDone: Boolean) : HabitEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val entryDao: HabitEntryDao,
    private val dayDao: DayRecordDao,
    private val prefs: KeptPreferences,
    private val time: TimeSource,
) {
    /** Epoch millis of the give-up time on [date], from the setting in effect now. */
    private fun dueMillis(date: LocalDate, dueMinute: Int): Long =
        time.instantAt(date, dueMinute).toEpochMilli()

    fun observeHabits(): Flow<List<HabitEntity>> = habitDao.observeActive()

    fun observeToday(): Flow<TodaySummary> = time.observeToday().flatMapLatest { date ->
        combine(habitDao.observeActive(), entryDao.observeForDate(date.toString()), prefs.settings) { habits, entries, settings ->
            val byHabit = entries.associateBy { it.habitId }
            val due = dueMillis(date, settings.dueMinute)
            TodaySummary(habits.map { HabitToday(it, byHabit[it.id], due) })
        }
    }

    fun observeHabit(id: Long): Flow<HabitToday?> = time.observeToday().flatMapLatest { date ->
        combine(habitDao.observeById(id), entryDao.observeForHabitOnDate(id, date.toString()), prefs.settings) { h, e, settings ->
            h?.let { HabitToday(it, e, dueMillis(date, settings.dueMinute)) }
        }
    }

    suspend fun today(): TodaySummary {
        val date = time.today()
        val habits = habitDao.active()
        val entries = entryDao.forDate(date.toString()).associateBy { it.habitId }
        val due = dueMillis(date, prefs.currentSettings().dueMinute)
        return TodaySummary(habits.map { HabitToday(it, entries[it.id], due) })
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

    suspend fun markDone(habitId: Long, photoPath: String? = null): HabitEvent.Completed? {
        val habit = habitDao.byId(habitId) ?: return null
        val date = time.todayKey()
        val cur = entryDao.forHabitOnDate(habitId, date) ?: HabitEntryEntity(habitId = habitId, date = date)
        if (cur.completedAt != null) return null
        val now = time.nowMillis()
        entryDao.upsert(cur.copy(progressValue = habit.targetValue, completedAt = now, photoPath = photoPath ?: cur.photoPath))
        val summary = today()
        syncCounts(date, summary)
        // Time only moves forward within a day, so "this tick was on time and everything is now
        // ticked" is the same thing as "the whole day was done on time".
        return HabitEvent.Completed(habit, summary.allDone, beforeDue = now < dueMillis(time.today(), prefs.currentSettings().dueMinute))
    }

    suspend fun undo(habitId: Long): HabitEvent.Undone? {
        val habit = habitDao.byId(habitId) ?: return null
        val date = time.todayKey()
        val cur = entryDao.forHabitOnDate(habitId, date) ?: return null
        if (cur.completedAt == null) return null
        // Only an on-time complete day ever granted a level, so only that can be taken back.
        val wasAllDone = today().allDoneOnTime
        entryDao.upsert(cur.copy(progressValue = 0, completedAt = null, photoPath = null))
        syncCounts(date)
        return HabitEvent.Undone(habit, wasAllDone)
    }

    suspend fun syncCounts(date: String, summary: TodaySummary? = null) {
        val s = summary ?: today()
        dayDao.insertIfAbsent(DayRecordEntity(date = date, habitsTotal = s.total))
        dayDao.setHabitCounts(date, s.done, s.total)
    }
}
