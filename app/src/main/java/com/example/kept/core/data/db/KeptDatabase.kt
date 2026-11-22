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
 * The old `sqlite_sequence` value is carried across before the drop (which deletes it), so ids
 * keep counting from the old sequence and a deleted id is never handed out again.
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
        // sqlite_sequence has no unique key on name, so replace the copy's row rather than upsert.
        // The old seq is always >= max(id). With no old row (nothing ever inserted) none is needed.
        db.execSQL(
            "DELETE FROM sqlite_sequence WHERE name = 'gallery_new' " +
                "AND EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'gallery')",
        )
        db.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT 'gallery_new', seq FROM sqlite_sequence WHERE name = 'gallery'")
        db.execSQL("DROP TABLE gallery")
        db.execSQL("ALTER TABLE gallery_new RENAME TO gallery")
    }
}

/**
 * v5 -> v6: the bedtime check-in preset is retired (issue #163). A habit that can only honestly
 * be ticked at night kept the phone locked for the whole lock window, so saved copies of the
 * preset are deleted, active or already removed. The match is the exact title and the preset's
 * unit: a renamed copy, a different case, or the same name with an amount is the person's own
 * habit and stays. `habit_entries` and `day_records` are left alone; today's open day record is
 * brought back in line by `RolloverRunner.runPending` on the first launch after the upgrade.
 * No table changes shape, so schema 6 is schema 5 with a new version.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM habits WHERE title = 'Sleep by 11' AND unit = 'check-in'")
    }
}

/**
 * v6 -> v7: weekly goals (issue #165). Two new tables, `weekly_goals` and `weekly_goal_entries`;
 * no existing table or row is touched. The unique index on (`goalId`, `date`) is what holds a
 * goal to one session a day. The SQL is the exported schema 7's, so Room's validation passes.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weekly_goals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
                "`daysPerWeek` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`effectiveFrom` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weekly_goal_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                "`completedAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_goal_entries_goalId_date` ON `weekly_goal_entries` (`goalId`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_weekly_goal_entries_date` ON `weekly_goal_entries` (`date`)")
    }
}

/** Every migration, in order. The app and the migration tests both use this list. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

@Database(
    entities = [
        HabitEntity::class,
        HabitEntryEntity::class,
        DayRecordEntity::class,
        AllowedAppEntity::class,
        LockBreakEntity::class,
        ProtectionGapEntity::class,
        GalleryEntryEntity::class,
        WeeklyGoalEntity::class,
        WeeklyGoalEntryEntity::class,
    ],
    version = 7,
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
    abstract fun weeklyGoalDao(): WeeklyGoalDao
    abstract fun weeklyGoalEntryDao(): WeeklyGoalEntryDao
}
