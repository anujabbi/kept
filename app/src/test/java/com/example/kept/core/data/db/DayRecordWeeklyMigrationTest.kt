package com.example.kept.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.domain.RecapOutcome
import com.example.kept.feature.recap.RecapRules
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A day records its weekly must-do sessions (issue #165, story #176). Schema 7 -> 8 adds
 * `weeklyDue`, `weeklyDone` and `weeklyMissed` to `day_records`; a day closed before the upgrade
 * has no weekly session due and keeps the outcome it had.
 *
 * Built like [WeeklyGoalMigrationTest]: the 2.1 database is created from the exported schema 7
 * and opened by Room with the app's migrations, on the JVM. Room checks every table against the
 * current entities when it opens, defaults included, so a column the migration got wrong fails
 * the open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DayRecordWeeklyMigrationTest {
    private val dbName = "day-record-weekly-migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: KeptDatabase? = null

    /** Every table of schema 7, with the column that orders its rows. */
    private val oldTables = mapOf(
        "habits" to "id", "habit_entries" to "id", "day_records" to "date", "allowed_apps" to "packageName",
        "lock_breaks" to "id", "protection_gaps" to "id", "gallery" to "id", "weekly_goals" to "id", "weekly_goal_entries" to "id",
    )
    private val newColumns = listOf("weeklyDue", "weeklyDone", "weeklyMissed")

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    private fun schema(version: Int): JSONObject {
        val path = "schemas/com.example.kept.core.data.db.KeptDatabase/$version.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** Creates [dbName] at schema 7 from its exported schema, then lets [seed] add rows. */
    private fun createReleaseDatabase(seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(dbName)
        val s = schema(7)
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
        db.version = 7
        db.seed()
        db.close()
    }

    /**
     * A phone on 2.1 with a weekly goal: one closed day of every outcome, in the order kept,
     * shield, lock off, too late, slipped, written off, and today's open row.
     */
    private fun SQLiteDatabase.settledUser() {
        execSQL(
            "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) VALUES " +
                "(1, 'Read', 'book', 'MANUAL', 10, 'pages', 0, 1, 1000), (2, 'Study', 'book', 'MANUAL', 45, 'min', 1, 1, 2000)",
        )
        execSQL("INSERT INTO habit_entries (id, habitId, date, progressValue, completedAt, photoPath) VALUES (1, 1, '2026-11-24', 10, 111, NULL)")
        execSQL(
            "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) VALUES " +
                "('2026-11-18', 2, 2, 3600000, 0, 0, 0, 0, 1, 0, 4, 3, 12, 1), " +
                "('2026-11-19', 1, 2, 7200000, 1, 0, 1, 0, 0, 1, 3, 3, 12, 1), " +
                "('2026-11-20', 2, 2, 0, 0, 1, 0, 0, 0, 0, 3, 3, 12, 1), " +
                "('2026-11-21', 2, 2, 5400000, 0, 0, 0, 0, 0, 0, 3, 1, 0, 1), " +
                "('2026-11-22', 0, 2, 60000, 0, 0, 0, 0, 0, 0, 3, 1, 0, 1), " +
                "('2026-11-23', 1, 2, 60000, 4, 0, 1, 1, 0, 0, 2, 1, 0, 1), " +
                "('2026-11-24', 1, 2, 1000, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0)",
        )
        execSQL("INSERT INTO allowed_apps (packageName, label, addedAt) VALUES ('com.example.music', 'Music', 500)")
        execSQL("INSERT INTO lock_breaks (id, timestamp, unlockedUntil, date, levelCost, overCap) VALUES (1, 900, 1800, '2026-11-19', 1, 0)")
        execSQL("INSERT INTO protection_gaps (id, date, startMillis, endMillis, reason) VALUES (1, '2026-11-20', 10, 20, 'usage_access')")
        execSQL("INSERT INTO gallery (id, formId, unlockedDate, streakAtUnlock, seen) VALUES (1, 1, '2026-11-01', 0, 1)")
        execSQL(
            "INSERT INTO weekly_goals (id, title, iconKey, daysPerWeek, isActive, createdAt, effectiveFrom, sortOrder) VALUES " +
                "(1, 'Exercise', 'run', 4, 1, 3000, '2026-11-15', 0)",
        )
        execSQL("INSERT INTO weekly_goal_entries (id, goalId, date, completedAt) VALUES (1, 1, '2026-11-18', 4000), (2, 1, '2026-11-22', 5000)")
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

    /** Each row as column name to text, in the table's column order. */
    private fun android.database.Cursor.rows(): List<Map<String, String>> = buildList {
        while (moveToNext()) add((0 until columnCount).associate { getColumnName(it) to (getString(it) ?: "null") })
    }

    private fun rowsBefore(): Map<String, List<Map<String, String>>> =
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            oldTables.mapValues { (table, order) -> db.rawQuery("SELECT * FROM $table ORDER BY $order", null).use { it.rows() } }
        }

    private fun SupportSQLiteDatabase.rowsAfter(): Map<String, List<Map<String, String>>> =
        oldTables.mapValues { (table, order) -> query("SELECT * FROM $table ORDER BY $order").use { it.rows() } }

    @Test
    fun migrate7To8_addsTheThreeColumns_withNoWeeklySessionDueOnExistingDays() {
        createReleaseDatabase { settledUser() }
        val before = rowsBefore()
        assertEquals(7, before.getValue("day_records").size)

        val db = upgrade()
        val after = db.rowsAfter()

        assertEquals(10, db.version)
        assertEquals(
            listOf(
                "date", "habitsDone", "habitsTotal", "lockedMillis", "breaksUsed", "unprotected", "broken", "writtenOff",
                "countedForStreak", "shieldConsumed", "levelEnd", "formId", "streakEnd", "finalized", "weeklyDue", "weeklyDone", "weeklyMissed",
            ),
            after.getValue("day_records").first().keys.toList(),
        )
        for (row in after.getValue("day_records")) {
            assertEquals("${row["date"]}: no weekly session due", listOf("0", "0", ""), newColumns.map { row[it] })
        }
        // Every other column of every row, in every table, is as it was.
        // weekly_goals gains targetValue and unit at schema 9 (story #205); both are null for an existing goal.
        // 9 -> 10 moves the goals after the habits in Your promises (#236): PromiseOrderMigrationTest.
        fun ordered(m: Map<String, List<Map<String, String>>>) = m.mapValues { (t, r) -> if (t == "weekly_goals") r.map { it - "sortOrder" } else r }
        assertEquals(ordered(before), ordered(after.mapValues { (table, rows) -> rows.map { if (table == "weekly_goals") it - setOf("targetValue", "unit") else it - newColumns.toSet() } }))
    }

    @Test
    fun everyPastDayKeepsItsOutcome() = runBlocking {
        createReleaseDatabase { settledUser() }
        upgrade()
        val days = room!!.dayRecordDao().between("2026-11-18", "2026-11-23")

        assertEquals(
            listOf(RecapOutcome.KEPT, RecapOutcome.SHIELD, RecapOutcome.LOCK_OFF, RecapOutcome.TOO_LATE, RecapOutcome.SLIPPED, RecapOutcome.WRITTEN_OFF),
            days.map(RecapRules::outcome),
        )
        assertEquals("no day names a weekly goal", emptyList<String>(), days.flatMap(RecapRules::weeklyMissed))
        assertEquals(listOf(12, 12, 12, 0, 0, 0), days.map { it.streakEnd })
        assertEquals(listOf(true, false, false, false, false, false), days.map { it.countedForStreak })
    }

    @Test
    fun theUpgradedTableTakesTheNewColumns_andARowWrittenWithoutThemGetsTheDefaults() = runBlocking {
        createReleaseDatabase { settledUser() }
        val db = upgrade()
        val dao = room!!.dayRecordDao()

        // Today's open row from before the upgrade is closed by the new rollover.
        dao.upsert(dao.get("2026-11-24")!!.copy(finalized = true, weeklyDue = 2, weeklyDone = 1, weeklyMissed = "Exercise"))
        val closed = dao.get("2026-11-24")!!
        assertEquals(Triple(2, 1, "Exercise"), Triple(closed.weeklyDue, closed.weeklyDone, closed.weeklyMissed))

        // The live row the app creates for a new day does not name the columns.
        dao.insertIfAbsent(DayRecordEntity(date = "2026-11-25", habitsTotal = 2))
        db.execSQL("INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) VALUES ('2026-11-26', 0, 2, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0)")
        for (date in listOf("2026-11-25", "2026-11-26")) {
            val r = dao.get(date)!!
            assertEquals(date, Triple(0, 0, ""), Triple(r.weeklyDue, r.weeklyDone, r.weeklyMissed))
        }
        // The goal and its sessions are still there for the next rollover to read.
        assertEquals(listOf("Exercise"), room!!.weeklyGoalDao().active().map { it.title })
        assertEquals(listOf("2026-11-18", "2026-11-22"), room!!.weeklyGoalEntryDao().between("2026-11-01", "2026-11-30").map { it.date })
    }

    @Test
    fun anEmptyReleaseDatabaseUpgrades() {
        createReleaseDatabase {}
        val db = upgrade()
        assertEquals(10, db.version)
        db.query("SELECT COUNT(*) FROM day_records").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
}
