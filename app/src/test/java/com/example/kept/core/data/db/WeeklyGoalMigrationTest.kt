package com.example.kept.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Weekly goals add two tables (issue #165, story #172). Schema 6 -> 7 creates `weekly_goals` and
 * `weekly_goal_entries` with their indexes and touches nothing that was there.
 *
 * Built like [GalleryMigrationTest] and [RetiredPresetMigrationTest]: the release database is
 * created from the exported schema JSON and opened by Room with the app's migrations, on the JVM.
 * Room checks every table against the current entities when it opens, so a column or an index the
 * migration got wrong fails the open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeeklyGoalMigrationTest {
    private val dbName = "weekly-goal-migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: KeptDatabase? = null

    /** Every table of schema 6, with the column that orders its rows. */
    private val oldTables = mapOf(
        "habits" to "id", "habit_entries" to "id", "day_records" to "date", "allowed_apps" to "packageName",
        "lock_breaks" to "id", "protection_gaps" to "id", "gallery" to "id",
    )

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    private fun schema(version: Int): JSONObject {
        val path = "schemas/com.example.kept.core.data.db.KeptDatabase/$version.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** Creates [dbName] at schema 6 from its exported schema, then lets [seed] add rows. */
    private fun createReleaseDatabase(seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(dbName)
        val s = schema(6)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }, null)
        val entities = s.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            e.optJSONArray("indices")?.let { idx ->
                for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = s.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = 6
        db.seed()
        db.close()
    }

    /** A phone on the current release: habits (one removed), entries, days, and a row in every other table. */
    private fun SQLiteDatabase.settledUser() {
        execSQL(
            "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) VALUES " +
                "(1, 'Exercise', 'run', 'MANUAL', 30, 'min', 0, 1, 1000), " +
                "(2, 'Read', 'book', 'PHOTO', 1, 'check-in', 1, 1, 2000), " +
                "(3, 'Tidy room', 'tidy', 'MANUAL', 1, 'check-in', 2, 0, 3000)",
        )
        execSQL(
            "INSERT INTO habit_entries (id, habitId, date, progressValue, completedAt, photoPath) VALUES " +
                "(1, 1, '2026-11-24', 30, 111, NULL), " +
                "(2, 2, '2026-11-24', 1, 222, '/photos/read.jpg'), " +
                "(3, 1, '2026-11-25', 0, NULL, NULL)",
        )
        execSQL(
            "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) VALUES " +
                "('2026-11-23', 1, 2, 7200000, 1, 0, 1, 0, 0, 1, 3, 2, 11, 1), " +
                "('2026-11-24', 2, 2, 3600000, 0, 0, 0, 0, 1, 0, 4, 3, 12, 1), " +
                "('2026-11-25', 0, 2, 60000, 0, 1, 0, 0, 0, 0, 4, 3, 12, 0)",
        )
        execSQL("INSERT INTO allowed_apps (packageName, label, addedAt) VALUES ('com.example.music', 'Music', 500)")
        execSQL("INSERT INTO lock_breaks (id, timestamp, unlockedUntil, date, levelCost, overCap) VALUES (1, 900, 1800, '2026-11-23', 1, 0)")
        execSQL("INSERT INTO protection_gaps (id, date, startMillis, endMillis, reason) VALUES (1, '2026-11-25', 10, 20, 'usage_access')")
        execSQL("INSERT INTO gallery (id, formId, unlockedDate, streakAtUnlock, seen) VALUES (1, 1, '2026-11-01', 0, 1), (2, 3, '2026-11-20', 7, 0)")
    }

    /** Opens the database the way the app does (`AppModule.database`): every migration, in order. */
    private fun upgrade(): SupportSQLiteDatabase {
        val r = Room.databaseBuilder(context, KeptDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        room = r
        return r.openHelper.writableDatabase
    }

    private fun android.database.Cursor.rowsAsText(): List<String> = buildList {
        while (moveToNext()) add((0 until columnCount).joinToString("|") { "${getColumnName(it)}=${getString(it) ?: "null"}" })
    }

    /** Every row of every schema-6 table, columns named, read without Room before the upgrade. */
    private fun rowsBefore(): Map<String, List<String>> =
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            oldTables.mapValues { (table, order) -> db.rawQuery("SELECT * FROM $table ORDER BY $order", null).use { it.rowsAsText() } }
        }

    private fun SupportSQLiteDatabase.rowsAfter(): Map<String, List<String>> =
        oldTables.mapValues { (table, order) -> query("SELECT * FROM $table ORDER BY $order").use { it.rowsAsText() } }

    private fun SupportSQLiteDatabase.names(type: String, table: String): List<String> = buildList {
        query("SELECT name FROM sqlite_master WHERE type = '$type' AND tbl_name = '$table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use {
            while (it.moveToNext()) add(it.getString(0))
        }
    }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> = buildList {
        query("PRAGMA table_info($table)").use { while (it.moveToNext()) add(it.getString(it.getColumnIndexOrThrow("name"))) }
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    // --- the migration -------------------------------------------------------------------------

    @Test
    fun migrate6To7_addsBothTablesAndIndexes_andKeepsEveryExistingRow() {
        createReleaseDatabase { settledUser() }
        val before = rowsBefore()
        assertEquals(mapOf("habits" to 3, "habit_entries" to 3, "day_records" to 3, "allowed_apps" to 1, "lock_breaks" to 1, "protection_gaps" to 1, "gallery" to 2), before.mapValues { it.value.size })

        val db = upgrade()

        assertEquals(7, db.version)
        assertEquals(
            listOf("id", "title", "iconKey", "daysPerWeek", "isActive", "createdAt", "effectiveFrom", "sortOrder"),
            db.columns("weekly_goals"),
        )
        assertEquals(listOf("id", "goalId", "date", "completedAt"), db.columns("weekly_goal_entries"))
        assertEquals(listOf("weekly_goals"), db.names("table", "weekly_goals"))
        assertEquals(listOf("weekly_goal_entries"), db.names("table", "weekly_goal_entries"))
        assertEquals(
            listOf("index_weekly_goal_entries_date", "index_weekly_goal_entries_goalId_date"),
            db.names("index", "weekly_goal_entries"),
        )
        assertEquals(0, db.count("weekly_goals"))
        assertEquals(0, db.count("weekly_goal_entries"))
        assertEquals("every existing row, column for column", before, db.rowsAfter())
    }

    @Test
    fun theIndexOnGoalAndDateIsUnique_soADayHoldsOneSession() {
        createReleaseDatabase { settledUser() }
        val db = upgrade()

        db.query("PRAGMA index_list(weekly_goal_entries)").use {
            val unique = mutableMapOf<String, Int>()
            while (it.moveToNext()) unique[it.getString(it.getColumnIndexOrThrow("name"))] = it.getInt(it.getColumnIndexOrThrow("unique"))
            assertEquals(mapOf("index_weekly_goal_entries_goalId_date" to 1, "index_weekly_goal_entries_date" to 0), unique)
        }
        db.execSQL("INSERT INTO weekly_goal_entries (goalId, date, completedAt) VALUES (1, '2026-11-25', 100)")
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL("INSERT INTO weekly_goal_entries (goalId, date, completedAt) VALUES (1, '2026-11-25', 200)")
        }
        // Another day, and another goal the same day, are fine.
        db.execSQL("INSERT INTO weekly_goal_entries (goalId, date, completedAt) VALUES (1, '2026-11-26', 300)")
        db.execSQL("INSERT INTO weekly_goal_entries (goalId, date, completedAt) VALUES (2, '2026-11-25', 400)")
        assertEquals(3, db.count("weekly_goal_entries"))
    }

    @Test
    fun theNewDaosWorkOnTheUpgradedDatabase_andOldIdsKeepCounting() = runBlocking {
        createReleaseDatabase { settledUser() }
        upgrade()
        val r = room!!

        val goalId = r.weeklyGoalDao().insert(
            WeeklyGoalEntity(title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 5_000, effectiveFrom = "2026-11-25"),
        )
        assertEquals(1L, goalId)
        assertTrue(r.weeklyGoalEntryDao().insertIfAbsent(WeeklyGoalEntryEntity(goalId = goalId, date = "2026-11-25", completedAt = 6_000)) > 0)
        assertEquals(-1L, r.weeklyGoalEntryDao().insertIfAbsent(WeeklyGoalEntryEntity(goalId = goalId, date = "2026-11-25", completedAt = 7_000)))
        assertEquals(listOf(6_000L), r.weeklyGoalEntryDao().between("2026-11-22", "2026-11-28").map { it.completedAt })
        assertEquals(listOf("Exercise"), r.weeklyGoalDao().active().map { it.title })

        // The existing tables still take rows where they left off.
        assertEquals(listOf("Exercise", "Read"), r.habitDao().active().map { it.title })
        assertEquals(3L, r.galleryDao().insert(GalleryEntryEntity(formId = 4, unlockedDate = "2026-11-25", streakAtUnlock = 14)))
    }

    @Test
    fun anEmptyReleaseDatabaseUpgrades() {
        createReleaseDatabase {}
        val db = upgrade()
        assertEquals(7, db.version)
        assertEquals(0, db.count("weekly_goals"))
        assertEquals(0, db.count("habits"))
    }
}
