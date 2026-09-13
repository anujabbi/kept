package com.example.kept.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kept.core.domain.ProofType

class Converters {
    @TypeConverter fun proofToString(p: ProofType): String = p.name
    @TypeConverter fun stringToProof(s: String): ProofType = ProofType.valueOf(s)
}

/**
 * v1 -> v2: timer habits are removed (issue #3). Every habit is now MANUAL or PHOTO, so
 * existing TIMER rows become MANUAL rather than being dropped.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE habits SET proofType = 'MANUAL' WHERE proofType = 'TIMER'")
    }
}

@Database(
    entities = [
        HabitEntity::class,
        HabitEntryEntity::class,
        DayRecordEntity::class,
        AllowedAppEntity::class,
        LockBreakEntity::class,
        ProtectionGapEntity::class,
        GalleryEntryEntity::class,
        BuddyEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KeptDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitEntryDao(): HabitEntryDao
    abstract fun dayRecordDao(): DayRecordDao
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun lockBreakDao(): LockBreakDao
    abstract fun protectionGapDao(): ProtectionGapDao
    abstract fun galleryDao(): GalleryDao
    abstract fun buddyDao(): BuddyDao
}
