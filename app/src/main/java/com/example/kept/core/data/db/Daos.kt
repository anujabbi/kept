package com.example.kept.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY sortOrder, id")
    suspend fun active(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Insert suspend fun insert(habit: HabitEntity): Long
    @Update suspend fun update(habit: HabitEntity)
    @Query("UPDATE habits SET isActive = 0 WHERE id = :id") suspend fun deactivate(id: Long)
    @Query("DELETE FROM habits") suspend fun clear()
}

@Dao
interface HabitEntryDao {
    @Query("SELECT * FROM habit_entries WHERE date = :date")
    fun observeForDate(date: String): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE date = :date")
    suspend fun forDate(date: String): List<HabitEntryEntity>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun forHabitOnDate(habitId: Long, date: String): HabitEntryEntity?

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date LIMIT 1")
    fun observeForHabitOnDate(habitId: Long, date: String): Flow<HabitEntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: HabitEntryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(entries: List<HabitEntryEntity>)

    @Query("SELECT * FROM habit_entries WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<HabitEntryEntity>

    @Query("DELETE FROM habit_entries") suspend fun clear()
}

@Dao
interface DayRecordDao {
    @Query("SELECT * FROM day_records WHERE date = :date")
    fun observe(date: String): Flow<DayRecordEntity?>

    @Query("SELECT * FROM day_records WHERE date = :date")
    suspend fun get(date: String): DayRecordEntity?

    @Query("SELECT * FROM day_records WHERE finalized = 1 ORDER BY date DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<DayRecordEntity>>

    @Query("SELECT * FROM day_records WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<DayRecordEntity>

    @Query("SELECT * FROM day_records WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeBetween(from: String, to: String): Flow<List<DayRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfAbsent(record: DayRecordEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(record: DayRecordEntity)

    @Query("UPDATE day_records SET lockedMillis = lockedMillis + :millis WHERE date = :date")
    suspend fun addLockedTime(date: String, millis: Long)

    @Query("UPDATE day_records SET breaksUsed = breaksUsed + 1, broken = 1, writtenOff = writtenOff OR :writtenOff WHERE date = :date")
    suspend fun recordBreak(date: String, writtenOff: Boolean)

    @Query("UPDATE day_records SET unprotected = 1 WHERE date = :date")
    suspend fun markUnprotected(date: String)

    @Query("UPDATE day_records SET habitsDone = :done, habitsTotal = :total WHERE date = :date")
    suspend fun setHabitCounts(date: String, done: Int, total: Int)

    @Query("DELETE FROM day_records") suspend fun clear()
}

@Dao
interface AllowedAppDao {
    @Query("SELECT * FROM allowed_apps ORDER BY label")
    fun observeAll(): Flow<List<AllowedAppEntity>>

    @Query("SELECT packageName FROM allowed_apps")
    suspend fun packages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(app: AllowedAppEntity)
    @Query("DELETE FROM allowed_apps WHERE packageName = :pkg") suspend fun delete(pkg: String)
}

@Dao
interface LockBreakDao {
    @Query("SELECT * FROM lock_breaks WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<LockBreakEntity>>

    @Query("SELECT * FROM lock_breaks WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun since(since: Long): List<LockBreakEntity>

    @Query("SELECT * FROM lock_breaks ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<LockBreakEntity?>

    @Insert suspend fun insert(b: LockBreakEntity): Long
    @Query("UPDATE lock_breaks SET unlockedUntil = :until WHERE id = :id") suspend fun setUnlockedUntil(id: Long, until: Long)
    @Query("DELETE FROM lock_breaks") suspend fun clear()
}

@Dao
interface ProtectionGapDao {
    @Query("SELECT * FROM protection_gaps WHERE date = :date ORDER BY startMillis")
    fun observeForDate(date: String): Flow<List<ProtectionGapEntity>>

    @Query("SELECT COUNT(*) FROM protection_gaps WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Insert suspend fun insert(gap: ProtectionGapEntity)
    @Query("DELETE FROM protection_gaps") suspend fun clear()
}

@Dao
interface GalleryDao {
    @Query("SELECT * FROM gallery ORDER BY unlockedDate DESC, id DESC")
    fun observeAll(): Flow<List<GalleryEntryEntity>>

    @Query("SELECT * FROM gallery WHERE seen = 0 ORDER BY id")
    fun observeUnseen(): Flow<List<GalleryEntryEntity>>

    @Insert suspend fun insert(e: GalleryEntryEntity): Long
    @Query("UPDATE gallery SET seen = 1 WHERE id = :id") suspend fun markSeen(id: Long)
    @Query("UPDATE gallery SET seen = 1") suspend fun markAllSeen()
    @Query("DELETE FROM gallery") suspend fun clear()
}

@Dao
interface WeeklyGoalDao {
    @Query("SELECT * FROM weekly_goals WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<WeeklyGoalEntity>>

    @Query("SELECT * FROM weekly_goals WHERE isActive = 1 ORDER BY sortOrder, id")
    suspend fun active(): List<WeeklyGoalEntity>

    @Query("SELECT * FROM weekly_goals WHERE id = :id")
    suspend fun byId(id: Long): WeeklyGoalEntity?

    @Insert suspend fun insert(goal: WeeklyGoalEntity): Long
    @Query("UPDATE weekly_goals SET isActive = 0 WHERE id = :id") suspend fun deactivate(id: Long)

    /**
     * Changes an active goal in one statement and returns the rows changed: 1, or 0 when the goal
     * is unknown or removed. Nothing is read first, so a remove that lands at the same moment
     * cannot be written over, and `isActive` is never written here. The target is written with
     * the rest (story #205).
     */
    @Query(
        "UPDATE weekly_goals SET title = :title, iconKey = :iconKey, daysPerWeek = :daysPerWeek, targetValue = :targetValue, unit = :unit " +
            "WHERE id = :id AND isActive = 1",
    )
    suspend fun updateActive(id: Long, title: String, iconKey: String, daysPerWeek: Int, targetValue: Int?, unit: String?): Int
}

@Dao
interface WeeklyGoalEntryDao {
    @Query("SELECT * FROM weekly_goal_entries WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: String, to: String): Flow<List<WeeklyGoalEntryEntity>>

    @Query("SELECT * FROM weekly_goal_entries WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<WeeklyGoalEntryEntity>

    /** Returns the new row id, or -1 when the goal already has a session on that date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfAbsent(entry: WeeklyGoalEntryEntity): Long

    /** Returns the number of rows removed: 1, or 0 when there was no session to undo. */
    @Query("DELETE FROM weekly_goal_entries WHERE goalId = :goalId AND date = :date")
    suspend fun delete(goalId: Long, date: String): Int
}
