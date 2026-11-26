package com.example.kept.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.domain.WeeklyCard
import com.example.kept.core.domain.WeeklyCardCopy
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.domain.WeeklyLockCopy
import com.example.kept.core.domain.WeeklyLockScreen
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A weekly goal keeps a target (story #205, task #206). Schema 8 -> 9 adds a nullable
 * `targetValue` and `unit` to `weekly_goals`; a goal set before the upgrade has neither, keeps
 * its name, days, start date and logged sessions, and reads exactly as it did.
 *
 * Built like [DayRecordWeeklyMigrationTest]: the 2.1 database is created from the exported
 * schema 8 and opened by Room with the app's migrations, on the JVM. Room checks every table
 * against the current entities when it opens, so a column the migration got wrong fails the open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeeklyGoalTargetMigrationTest {
    private val dbName = "weekly-goal-target-migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: KeptDatabase? = null

    /** Every table of schema 8, with the column that orders its rows. */
    private val oldTables = mapOf(
        "habits" to "id", "habit_entries" to "id", "day_records" to "date", "allowed_apps" to "packageName",
        "lock_breaks" to "id", "protection_gaps" to "id", "gallery" to "id", "weekly_goals" to "id", "weekly_goal_entries" to "id",
    )
    private val newColumns = listOf("targetValue", "unit")

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    private fun schema(version: Int): JSONObject {
        val path = "schemas/com.example.kept.core.data.db.KeptDatabase/$version.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** Creates [dbName] at schema 8 from its exported schema, then lets [seed] add rows. */
    private fun createReleaseDatabase(seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(dbName)
        val s = schema(8)
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
        db.version = 8
        db.seed()
        db.close()
    }

    /** A phone on 2.1 with two weekly goals (one removed), their sessions, a habit and a closed day. */
    private fun SQLiteDatabase.settledUser() {
        execSQL(
            "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) VALUES " +
                "(1, 'Read', 'book', 'MANUAL', 10, 'pages', 0, 1, 1000)",
        )
        execSQL("INSERT INTO habit_entries (id, habitId, date, progressValue, completedAt, photoPath) VALUES (1, 1, '2026-11-24', 10, 111, NULL)")
        execSQL(
            "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized, weeklyDue, weeklyDone, weeklyMissed) VALUES " +
                "('2026-11-23', 1, 1, 3600000, 0, 0, 0, 0, 1, 0, 4, 3, 12, 1, 1, 0, 'Exercise')",
        )
        execSQL("INSERT INTO allowed_apps (packageName, label, addedAt) VALUES ('com.example.music', 'Music', 500)")
        execSQL("INSERT INTO lock_breaks (id, timestamp, unlockedUntil, date, levelCost, overCap) VALUES (1, 900, 1800, '2026-11-19', 1, 0)")
        execSQL("INSERT INTO protection_gaps (id, date, startMillis, endMillis, reason) VALUES (1, '2026-11-20', 10, 20, 'usage_access')")
        execSQL("INSERT INTO gallery (id, formId, unlockedDate, streakAtUnlock, seen) VALUES (1, 1, '2026-11-01', 0, 1)")
        execSQL(
            "INSERT INTO weekly_goals (id, title, iconKey, daysPerWeek, isActive, createdAt, effectiveFrom, sortOrder) VALUES " +
                "(1, 'Exercise', 'run', 4, 1, 3000, '2026-11-15', 0), (2, 'Tidy room', 'tidy', 2, 0, 3500, '2026-11-16', 1)",
        )
        execSQL(
            "INSERT INTO weekly_goal_entries (id, goalId, date, completedAt) VALUES " +
                "(1, 1, '2026-11-18', 4000), (2, 1, '2026-11-22', 5000), (3, 1, '2026-11-24', 6000)",
        )
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
    fun migrate8To9_addsTheTargetColumns_andExistingGoalsHaveNoTarget() {
        createReleaseDatabase { settledUser() }
        val before = rowsBefore()

        val db = upgrade()
        val after = db.rowsAfter()

        assertEquals(9, db.version)
        assertEquals(
            listOf("id", "title", "iconKey", "daysPerWeek", "isActive", "createdAt", "effectiveFrom", "sortOrder", "targetValue", "unit"),
            after.getValue("weekly_goals").first().keys.toList(),
        )
        for (row in after.getValue("weekly_goals")) {
            assertEquals("${row["title"]}: no target", listOf("null", "null"), newColumns.map { row[it] })
        }
        // Every other column of every row, in every table, is as it was: names, days, the start
        // date and every logged session.
        assertEquals(before, after.mapValues { (table, rows) -> if (table == "weekly_goals") rows.map { it - newColumns.toSet() } else rows })
    }

    @Test
    fun aMigratedGoalCountsItsSessionsAndReadsAsBefore() = runBlocking {
        createReleaseDatabase { settledUser() }
        upgrade()
        val r = room!!
        // Tuesday 24 November 2026: Sunday's and today's sessions count this week.
        val clock = object : Clock() {
            override fun instant(): Instant = Instant.parse("2026-11-24T12:00:00Z")
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId): Clock = this
        }
        val goals = WeeklyGoalRepository(r.weeklyGoalDao(), r.weeklyGoalEntryDao(), TimeSource(clock))
        val g = goals.thisWeek().single()
        assertEquals("Exercise", g.goal.title)
        assertEquals(4, g.goal.daysPerWeek)
        assertEquals("2026-11-15", g.goal.effectiveFrom)
        assertNull(g.goal.targetValue)
        assertNull(g.goal.unit)
        assertEquals(2, g.week.done)

        val target = WeeklyGoalCopy.target(g.goal.targetValue, g.goal.unit)
        assertNull(target)
        val nb = { s: String -> s.replace(WeeklyGoalCopy.NBSP, ' ') }
        assertEquals("Exercise · 4 days a week", nb(WeeklyGoalCopy.listRow(g.goal.title, target, g.goal.daysPerWeek)))
        val card = WeeklyCard.of(g.goal.title, g.week, goalTarget = target)
        assertEquals("Exercise · 2 of 4 this week", nb(card.title))
        assertEquals(WeeklyCardCopy.TODAY_ROW, card.todayDetail)
        assertEquals(WeeklyLockCopy.TODAY, WeeklyLockScreen.of(g.goal.title, g.week, g.goal.daysPerWeek, target).todayDetail)
        assertEquals(WeeklyLockCopy.ROW_DETAIL, WeeklyLockCopy.rowDetail(target))

        // Saving it from the setup screen gives it a target, as editing does for any goal.
        assertEquals(true, goals.updateGoal(g.id, "Exercise", 4, 30, "min"))
        assertEquals(30 to "min", r.weeklyGoalDao().byId(g.id)!!.let { it.targetValue to it.unit })
    }

    @Test
    fun anEmptyReleaseDatabaseUpgrades() {
        createReleaseDatabase {}
        val db = upgrade()
        assertEquals(9, db.version)
        db.query("SELECT COUNT(*) FROM weekly_goals").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
}
