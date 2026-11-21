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

/**
 * v3 -> v4: the buddy feature is gone (issue #13). The `buddy` table held at most one row of
 * seeded stub data ("Maya"), so it is dropped outright rather than migrated anywhere.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS buddy")
    }
}

/**
 * v4 -> v5: weekly looks are removed (issue #125). `gallery.variantId` named the week's colour
 * and accessory a form was reached in; nothing reads it any more. The table is recreated without
 * it (no `DROP COLUMN` on minSdk 26) and every row keeps its id, form, date, streak and seen flag.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS gallery_new (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `formId` INTEGER NOT NULL, " +
                "`unlockedDate` TEXT NOT NULL, `streakAtUnlock` INTEGER NOT NULL, `seen` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO gallery_new (id, formId, unlockedDate, streakAtUnlock, seen) " +
                "SELECT id, formId, unlockedDate, streakAtUnlock, seen FROM gallery",
        )
        db.execSQL("DROP TABLE gallery")
        db.execSQL("ALTER TABLE gallery_new RENAME TO gallery")
    }
}

/** Every migration, in order. The app and the migration tests both use this list. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

@Database(
    entities = [
        HabitEntity::class,
        HabitEntryEntity::class,
        DayRecordEntity::class,
        AllowedAppEntity::class,
        LockBreakEntity::class,
        ProtectionGapEntity::class,
        GalleryEntryEntity::class,
    ],
    version = 5,
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
}
