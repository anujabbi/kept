package com.example.kept.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.kept.core.domain.ProofType

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val iconKey: String,
    val proofType: ProofType,
    /** Minutes for TIMER, count for MANUAL (usually 1), 1 for PHOTO. */
    val targetValue: Int,
    val unit: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long,
)

@Entity(
    tableName = "habit_entries",
    indices = [Index(value = ["habitId", "date"], unique = true), Index("date")],
)
data class HabitEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** ISO local date, e.g. 2026-09-05. */
    val date: String,
    /** Seconds for TIMER, count for MANUAL/PHOTO. */
    val progressValue: Int = 0,
    val completedAt: Long? = null,
    val photoPath: String? = null,
)

/** One row per day. Created lazily, updated live, finalized by the rollover. */
@Entity(tableName = "day_records")
data class DayRecordEntity(
    @PrimaryKey val date: String,
    val habitsDone: Int = 0,
    val habitsTotal: Int = 0,
    val lockedMillis: Long = 0,
    val pointsEarned: Long = 0,
    val breaksUsed: Int = 0,
    val unprotected: Boolean = false,
    val broken: Boolean = false,
    val writtenOff: Boolean = false,
    val countedForStreak: Boolean = false,
    val shieldConsumed: Boolean = false,
    val levelEnd: Int = 1,
    val formId: Int = 1,
    val streakEnd: Int = 0,
    val finalized: Boolean = false,
)

/** User-chosen exceptions to the default-deny lock. */
@Entity(tableName = "allowed_apps")
data class AllowedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val addedAt: Long,
)

@Entity(tableName = "lock_breaks", indices = [Index("timestamp")])
data class LockBreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val unlockedUntil: Long,
    val date: String,
    val levelCost: Int,
    val overCap: Boolean,
)

@Entity(tableName = "protection_gaps", indices = [Index("date")])
data class ProtectionGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startMillis: Long,
    val endMillis: Long,
    val reason: String,
)

@Entity(tableName = "gallery")
data class GalleryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val formId: Int,
    val variantId: Int,
    val unlockedDate: String,
    val streakAtUnlock: Int,
    val seen: Boolean = false,
)

@Entity(tableName = "buddy")
data class BuddyEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val initials: String,
    val streakDays: Int,
    val doneToday: Boolean,
    val formId: Int,
    /** Seven chars, oldest first: 1 done, 0 missed, u unprotected, - unknown. */
    val lastSevenDays: String,
    val pairedAt: Long,
    val lastCheerAt: Long? = null,
    val lastNudgeAt: Long? = null,
)
