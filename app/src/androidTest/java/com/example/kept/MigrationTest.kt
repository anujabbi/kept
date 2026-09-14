package com.example.kept

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.MIGRATION_1_2
import com.example.kept.core.data.db.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Room migrations. `fallbackToDestructiveMigration` is gone (issue #5), so a user on
 * any shipped schema must be able to reach the current one without losing a row: 1 -> 2 (the
 * timer removal, issue #3), 2 -> 3 (the points removal, issue #8), and 1 -> 3 end to end, which is
 * the path an installed-but-never-updated build actually takes.
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

    /**
     * The path a phone that installed v1 and skipped v2 takes. Both migrations run in one go and
     * `runMigrationsAndValidate` checks the result against the exported schema 3, so a column the
     * migrations forgot fails here rather than in the field.
     */
    @Test
    fun migrate1To3_runsBothMigrationsAndKeepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                    "VALUES (1, 'Exercise', 'run', 'TIMER', 30, 'min', 0, 1, 0)",
            )
            execSQL(
                "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, pointsEarned, breaksUsed, " +
                    "unprotected, broken, writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) " +
                    "VALUES ('2026-09-05', 1, 1, 5400000, 254, 0, 0, 0, 0, 1, 0, 3, 2, 7, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        migrated.query("SELECT proofType FROM habits WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("MANUAL", it.getString(0))
        }
        migrated.query("SELECT streakEnd, levelEnd FROM day_records WHERE date = '2026-09-05'").use {
            assertTrue(it.moveToFirst())
            assertEquals(7, it.getInt(0))
            assertEquals(3, it.getInt(1))
        }

        val columnNames = mutableListOf<String>()
        migrated.query("PRAGMA table_info(day_records)").use {
            while (it.moveToNext()) columnNames += it.getString(it.getColumnIndexOrThrow("name"))
        }
        assertFalse(columnNames.contains("pointsEarned"))
    }
}
