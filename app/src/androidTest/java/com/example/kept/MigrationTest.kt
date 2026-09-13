package com.example.kept

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the timer-removal migration (issue #3): TIMER habit rows become MANUAL in place,
 * nothing is dropped.
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
}
