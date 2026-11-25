package com.example.kept

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kept.core.data.db.ALL_MIGRATIONS
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.MIGRATION_1_2
import com.example.kept.core.data.db.MIGRATION_2_3
import com.example.kept.core.data.db.MIGRATION_3_4
import com.example.kept.core.data.db.MIGRATION_4_5
import com.example.kept.core.data.db.MIGRATION_6_7
import com.example.kept.core.data.db.MIGRATION_7_8
import com.example.kept.core.data.db.MIGRATION_8_9
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Room migrations. `fallbackToDestructiveMigration` is gone (issue #5), so a user on
 * any shipped schema must be able to reach the current one without losing a row: 1 -> 2 (the
 * timer removal, issue #3), 2 -> 3 (the points removal, issue #8), 3 -> 4 (the buddy removal,
 * issue #13), 4 -> 5 (the weekly looks removal, issue #125), 6 -> 7 (the weekly goal tables, issue
 * #165), 7 -> 8 (a day's weekly must-do columns, story #176), 8 -> 9 (a weekly goal's target,
 * story #205), and 1 -> 9 end to end, which is the
 * path an installed-but-never-updated build actually takes. 5 -> 6 (the retired preset, issue #163) changes rows, not tables;
 * `RetiredPresetMigrationTest` covers its rows on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KeptDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_convertsTimerHabitsToManual() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                    "VALUES (1, 'Exercise', 'run', 'TIMER', 30, 'min', 0, 1, 0)",
            )
            execSQL(
                "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                    "VALUES (2, 'Read', 'book', 'MANUAL', 10, 'pages', 1, 1, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        val cursor = migrated.query("SELECT id, proofType FROM habits ORDER BY id")
        val rows = mutableMapOf<Long, String>()
        cursor.use {
            while (it.moveToNext()) {
                rows[it.getLong(0)] = it.getString(1)
            }
        }

        assertEquals(2, rows.size)
        assertEquals("MANUAL", rows[1]) // was TIMER
        assertEquals("MANUAL", rows[2]) // was already MANUAL, unaffected
    }

    @Test
    fun migrate2To3_dropsPointsEarnedAndKeepsOtherColumns() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, pointsEarned, breaksUsed, " +
                    "unprotected, broken, writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) " +
                    "VALUES ('2026-09-05', 2, 2, 5400000, 254, 0, 0, 0, 0, 1, 0, 3, 2, 7, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        val cursor = migrated.query(
            "SELECT date, habitsDone, habitsTotal, lockedMillis, breaksUsed, levelEnd, formId, streakEnd, finalized " +
                "FROM day_records WHERE date = '2026-09-05'",
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("2026-09-05", it.getString(0))
            assertEquals(2, it.getInt(1))
            assertEquals(2, it.getInt(2))
            assertEquals(5400000L, it.getLong(3))
            assertEquals(0, it.getInt(4))
            assertEquals(3, it.getInt(5))
            assertEquals(2, it.getInt(6))
            assertEquals(7, it.getInt(7))
            assertEquals(1, it.getInt(8))
        }

        val columns = migrated.query("PRAGMA table_info(day_records)")
        val columnNames = mutableListOf<String>()
        columns.use {
            while (it.moveToNext()) columnNames += it.getString(it.getColumnIndexOrThrow("name"))
        }
        assertFalse(columnNames.contains("pointsEarned"))
    }

    @Test
    fun migrate3To4_dropsTheBuddyTable() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO buddy (id, displayName, initials, streakDays, doneToday, formId, " +
                    "lastSevenDays, pairedAt, lastCheerAt, lastNudgeAt) " +
                    "VALUES (1, 'Maya', 'MK', 9, 1, 3, '1101110', 0, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                    "VALUES (1, 'Read', 'book', 'MANUAL', 10, 'pages', 0, 1, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        val tables = mutableListOf<String>()
        migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            while (it.moveToNext()) tables += it.getString(0)
        }
        assertFalse(tables.contains("buddy"))

        // Everything else survives the drop.
        migrated.query("SELECT title FROM habits WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Read", it.getString(0))
        }
    }

    @Test
    fun migrate4To5_dropsVariantIdAndKeepsTheCollection() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO gallery (id, formId, variantId, unlockedDate, streakAtUnlock, seen) VALUES (1, 1, 3, '2026-09-01', 0, 1)")
            execSQL("INSERT INTO gallery (id, formId, variantId, unlockedDate, streakAtUnlock, seen) VALUES (2, 3, 7, '2026-09-08', 7, 0)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        val rows = mutableListOf<String>()
        migrated.query("SELECT id, formId, unlockedDate, streakAtUnlock, seen FROM gallery ORDER BY id").use {
            while (it.moveToNext()) rows += "${it.getLong(0)} ${it.getInt(1)} ${it.getString(2)} ${it.getInt(3)} ${it.getInt(4)}"
        }
        assertEquals(listOf("1 1 2026-09-01 0 1", "2 3 2026-09-08 7 0"), rows)

        val columnNames = mutableListOf<String>()
        migrated.query("PRAGMA table_info(gallery)").use {
            while (it.moveToNext()) columnNames += it.getString(it.getColumnIndexOrThrow("name"))
        }
        assertFalse(columnNames.contains("variantId"))
    }

    /**
     * `runMigrationsAndValidate` checks both new tables and their indexes against the exported
     * schema 7; the rows a release database already holds come through untouched.
     */
    @Test
    fun migrate6To7_addsTheWeeklyGoalTablesAndKeepsExistingRows() {
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                    "VALUES (1, 'Exercise', 'run', 'MANUAL', 30, 'min', 0, 1, 0)",
            )
            execSQL("INSERT INTO habit_entries (id, habitId, date, progressValue, completedAt, photoPath) VALUES (1, 1, '2026-11-24', 30, 111, NULL)")
            execSQL(
                "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                    "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) " +
                    "VALUES ('2026-11-24', 1, 1, 5400000, 0, 0, 0, 0, 1, 0, 4, 3, 12, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        val names = mutableListOf<String>()
        migrated.query("SELECT name FROM sqlite_master WHERE name LIKE '%weekly_goal%' ORDER BY name").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        assertEquals(
            listOf("index_weekly_goal_entries_date", "index_weekly_goal_entries_goalId_date", "weekly_goal_entries", "weekly_goals"),
            names,
        )
        migrated.query("SELECT title, targetValue FROM habits WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Exercise", it.getString(0))
            assertEquals(30, it.getInt(1))
        }
        migrated.query("SELECT progressValue, completedAt FROM habit_entries WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(30, it.getInt(0))
            assertEquals(111L, it.getLong(1))
        }
        migrated.query("SELECT habitsDone, streakEnd, levelEnd, finalized FROM day_records WHERE date = '2026-11-24'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(12, it.getInt(1))
            assertEquals(4, it.getInt(2))
            assertEquals(1, it.getInt(3))
        }
        // One session a day: the second insert for the same goal and date is refused.
        migrated.execSQL("INSERT INTO weekly_goal_entries (goalId, date, completedAt) VALUES (1, '2026-11-25', 100)")
        migrated.execSQL("INSERT OR IGNORE INTO weekly_goal_entries (goalId, date, completedAt) VALUES (1, '2026-11-25', 200)")
        migrated.query("SELECT COUNT(*), MAX(completedAt) FROM weekly_goal_entries").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(100L, it.getLong(1))
        }
    }

    /**
     * `runMigrationsAndValidate` checks the three new `day_records` columns, defaults included,
     * against the exported schema 8. Days closed before the upgrade have no weekly session due
     * and keep what they came to: kept, shielded, missed, and today's open row.
     */
    @Test
    fun migrate7To8_addsTheWeeklyColumnsAndKeepsEveryDaysOutcome() {
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                    "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) VALUES " +
                    "('2026-11-21', 2, 2, 5400000, 0, 0, 0, 0, 1, 0, 4, 3, 12, 1), " +
                    "('2026-11-22', 1, 2, 7200000, 1, 0, 1, 0, 0, 1, 3, 3, 12, 1), " +
                    "('2026-11-23', 0, 2, 60000, 0, 0, 0, 0, 0, 0, 3, 1, 0, 1), " +
                    "('2026-11-24', 1, 2, 1000, 0, 0, 0, 0, 0, 0, 3, 1, 0, 0)",
            )
            execSQL(
                "INSERT INTO weekly_goals (id, title, iconKey, daysPerWeek, isActive, createdAt, effectiveFrom, sortOrder) " +
                    "VALUES (1, 'Exercise', 'run', 4, 1, 3000, '2026-11-15', 0)",
            )
            execSQL("INSERT INTO weekly_goal_entries (id, goalId, date, completedAt) VALUES (1, 1, '2026-11-22', 5000)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        val days = mutableListOf<String>()
        migrated.query(
            "SELECT date, habitsDone, habitsTotal, lockedMillis, breaksUsed, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, " +
                "finalized, weeklyDue, weeklyDone, weeklyMissed FROM day_records ORDER BY date",
        ).use { c ->
            while (c.moveToNext()) days += (0 until c.columnCount).joinToString("|") { c.getString(it) }
        }
        assertEquals(
            listOf(
                "2026-11-21|2|2|5400000|0|1|0|4|3|12|1|0|0|",
                "2026-11-22|1|2|7200000|1|0|1|3|3|12|1|0|0|",
                "2026-11-23|0|2|60000|0|0|0|3|1|0|1|0|0|",
                "2026-11-24|1|2|1000|0|0|0|3|1|0|0|0|0|",
            ),
            days,
        )
        // A row written without the new columns, as the live row for a new day is, gets the defaults.
        migrated.execSQL(
            "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, writtenOff, " +
                "countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) VALUES ('2026-11-25', 0, 2, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0)",
        )
        migrated.query("SELECT weeklyDue, weeklyDone, weeklyMissed FROM day_records WHERE date = '2026-11-25'").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
            assertEquals(0, it.getInt(1))
            assertEquals("", it.getString(2))
        }
        migrated.query("SELECT title, daysPerWeek FROM weekly_goals").use {
            assertTrue(it.moveToFirst())
            assertEquals("Exercise", it.getString(0))
            assertEquals(4, it.getInt(1))
        }
        migrated.query("SELECT COUNT(*) FROM weekly_goal_entries").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    /**
     * `runMigrationsAndValidate` checks the two new nullable `weekly_goals` columns against the
     * exported schema 9. An existing goal keeps its name, days, start date and sessions, and has
     * no target.
     */
    @Test
    fun migrate8To9_addsANullableTargetAndKeepsEveryGoalAndSession() {
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO weekly_goals (id, title, iconKey, daysPerWeek, isActive, createdAt, effectiveFrom, sortOrder) VALUES " +
                    "(1, 'Exercise', 'run', 4, 1, 3000, '2026-11-15', 0), (2, 'Tidy room', 'tidy', 2, 0, 3500, '2026-11-16', 1)",
            )
            execSQL("INSERT INTO weekly_goal_entries (id, goalId, date, completedAt) VALUES (1, 1, '2026-11-18', 4000), (2, 1, '2026-11-22', 5000)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9)

        val goals = mutableListOf<String>()
        migrated.query("SELECT id, title, daysPerWeek, isActive, effectiveFrom, sortOrder FROM weekly_goals ORDER BY id").use { c ->
            while (c.moveToNext()) goals += (0 until c.columnCount).joinToString("|") { c.getString(it) }
        }
        assertEquals(listOf("1|Exercise|4|1|2026-11-15|0", "2|Tidy room|2|0|2026-11-16|1"), goals)
        migrated.query("SELECT targetValue, unit FROM weekly_goals").use {
            while (it.moveToNext()) {
                assertTrue(it.isNull(0))
                assertNull(it.getString(1))
            }
        }
        migrated.query("SELECT goalId, date FROM weekly_goal_entries ORDER BY id").use {
            val rows = mutableListOf<String>()
            while (it.moveToNext()) rows += "${it.getLong(0)} ${it.getString(1)}"
            assertEquals(listOf("1 2026-11-18", "1 2026-11-22"), rows)
        }
        // A goal saved with a target after the upgrade keeps it.
        migrated.execSQL("UPDATE weekly_goals SET targetValue = 30, unit = 'min' WHERE id = 1")
        migrated.query("SELECT targetValue, unit FROM weekly_goals WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(30, it.getInt(0))
            assertEquals("min", it.getString(1))
        }
    }

    @Test
    fun migrate1To9_runsEveryMigrationAndKeepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                    "VALUES (1, 'Exercise', 'run', 'TIMER', 30, 'min', 0, 1, 0)",
            )
            execSQL("INSERT INTO gallery (id, formId, variantId, unlockedDate, streakAtUnlock, seen) VALUES (1, 2, 5, '2026-09-04', 3, 1)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 9, true, *ALL_MIGRATIONS)

        migrated.query("SELECT proofType FROM habits WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("MANUAL", it.getString(0))
        }
        migrated.query("SELECT formId, streakAtUnlock FROM gallery WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
            assertEquals(3, it.getInt(1))
        }
    }
}
