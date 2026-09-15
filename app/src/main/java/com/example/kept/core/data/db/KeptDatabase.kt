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

/**
 * v2 -> v3: points are removed entirely (issue #8). `day_records.pointsEarned` is dropped;
 * SQLite on minSdk 26 predates `ALTER TABLE ... DROP COLUMN`, so the table is recreated without
 * it and the rest of the data is copied across.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS day_records_new (" +
                "`date` TEXT NOT NULL, `habitsDone` INTEGER NOT NULL, `habitsTotal` INTEGER NOT NULL, " +
                "`lockedMillis` INTEGER NOT NULL, `breaksUsed` INTEGER NOT NULL, `unprotected` INTEGER NOT NULL, " +
                "`broken` INTEGER NOT NULL, `writtenOff` INTEGER NOT NULL, `countedForStreak` INTEGER NOT NULL, " +
                "`shieldConsumed` INTEGER NOT NULL, `levelEnd` INTEGER NOT NULL, `formId` INTEGER NOT NULL, " +
                "`streakEnd` INTEGER NOT NULL, `finalized` INTEGER NOT NULL, PRIMARY KEY(`date`))",
        )
        db.execSQL(
            "INSERT INTO day_records_new (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, " +
                "broken, writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) " +
                "SELECT date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, writtenOff, " +
                "countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized FROM day_records",
        )
        db.execSQL("DROP TABLE day_records")
        db.execSQL("ALTER TABLE day_records_new RENAME TO day_records")
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
    version = 3,
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
