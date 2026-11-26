package com.example.kept.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.PromiseList
import com.example.kept.core.domain.ProofType
import com.example.kept.feature.settings.promise
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Your promises (#236) lists daily habits and weekly goals by one shared `sortOrder`. Schema 9 ->
 * 10 moves every weekly goal after the active habits, keeping the goals' own order, so an
 * upgraded list reads habits first, as the two old screens did. Every other column of every row
 * is untouched. Built like [WeeklyGoalTargetMigrationTest]: the database is made from the
 * exported schema 9 and opened by Room with the app's migrations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PromiseOrderMigrationTest {
    private val dbName = "promise-order-migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: KeptDatabase? = null

    private val tables = mapOf(
        "habits" to "id", "habit_entries" to "id", "day_records" to "date", "allowed_apps" to "packageName",
        "lock_breaks" to "id", "protection_gaps" to "id", "gallery" to "id", "weekly_goals" to "id", "weekly_goal_entries" to "id",
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

    private fun createRelease9(seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(dbName)
        val s = schema(9)
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
        db.version = 9
        db.seed()
        db.close()
    }

    /**
     * Three active habits at 0, 1 and 4 (one removed at 7, which does not count), two active goals
     * at 0 and 1 and a removed one at 2, each with a day of history.
     */
    private fun SQLiteDatabase.settledUser() {
        execSQL(
            "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) VALUES " +
                "(1, 'Read', 'book', 'MANUAL', 10, 'pages', 0, 1, 1000), (2, 'Drink water', 'water', 'MANUAL', 8, 'glasses', 1, 1, 1000), " +
                "(3, 'Walk', 'run', 'MANUAL', 20, 'min', 7, 0, 1000), (4, 'Practice guitar', 'music', 'MANUAL', 20, 'min', 4, 1, 1000)",
        )
        execSQL("INSERT INTO habit_entries (id, habitId, date, progressValue, completedAt, photoPath) VALUES (1, 1, '2026-11-24', 10, 111, NULL)")
        execSQL(
            "INSERT INTO weekly_goals (id, title, iconKey, daysPerWeek, isActive, createdAt, effectiveFrom, sortOrder, targetValue, unit) VALUES " +
                "(1, 'Exercise', 'run', 4, 1, 3000, '2026-11-15', 0, 30, 'min'), (2, 'Study', 'study', 2, 1, 3500, '2026-11-16', 1, 45, 'min'), " +
                "(3, 'Tidy room', 'tidy', 2, 0, 3500, '2026-11-16', 2, NULL, NULL)",
        )
        execSQL("INSERT INTO weekly_goal_entries (id, goalId, date, completedAt) VALUES (1, 1, '2026-11-24', 6000)")
        execSQL(
            "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized, weeklyDue, weeklyDone, weeklyMissed) VALUES " +
                "('2026-11-23', 3, 3, 3600000, 0, 0, 0, 0, 1, 0, 4, 3, 12, 1, 1, 1, '')",
        )
    }

    private fun upgrade(): SupportSQLiteDatabase {
        val r = Room.databaseBuilder(context, KeptDatabase::class.java, dbName).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        room = r
        return r.openHelper.writableDatabase
    }

    private fun android.database.Cursor.rows(): List<Map<String, String>> = buildList {
        while (moveToNext()) add((0 until columnCount).associate { getColumnName(it) to (getString(it) ?: "null") })
    }

    private fun rowsBefore() = SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        tables.mapValues { (t, o) -> db.rawQuery("SELECT * FROM $t ORDER BY $o", null).use { it.rows() } }
    }

    @Test fun migrate9To10_movesTheGoalsAfterTheHabits_andTouchesNothingElse() {
        createRelease9 { settledUser() }
        val before = rowsBefore()
        val db = upgrade()
        val after = tables.mapValues { (t, o) -> db.query("SELECT * FROM $t ORDER BY $o").use { it.rows() } }

        assertEquals(10, db.version)
        // The highest active habit is at 4, so the goals start at 5, in their own order.
        assertEquals(listOf("5", "6", "7"), after.getValue("weekly_goals").map { it["sortOrder"] })
        assertEquals(before.getValue("weekly_goals").map { it - "sortOrder" }, after.getValue("weekly_goals").map { it - "sortOrder" })
        assertEquals(before - "weekly_goals", after - "weekly_goals")
    }

    @Test fun anUpgradedListReadsHabitsFirst_andANewPromiseGoesAtTheEnd() = runBlocking {
        createRelease9 { settledUser() }
        upgrade()
        val r = room!!
        val clock = object : Clock() {
            override fun instant(): Instant = Instant.parse("2026-11-25T09:00:00Z")
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId): Clock = this
        }
        val time = TimeSource(clock)
        val habits = HabitRepository(r.habitDao(), r.habitEntryDao(), r.dayRecordDao(), KeptPreferences(context), time)
        val goals = WeeklyGoalRepository(r.weeklyGoalDao(), r.weeklyGoalEntryDao(), time)
        suspend fun titles() = PromiseList.ordered(r.habitDao().active().map { it.promise() } + r.weeklyGoalDao().active().map { it.promise() }).map { it.title }

        assertEquals(listOf("Read", "Drink water", "Practice guitar", "Exercise", "Study"), titles())
        habits.addHabit("Stretch", "star", ProofType.MANUAL, 10, "min")
        goals.addGoal("Swim", 1, 30, "min")
        assertEquals(listOf("Read", "Drink water", "Practice guitar", "Exercise", "Study", "Stretch", "Swim"), titles())
    }

    @Test fun anEmptyDatabaseUpgrades() {
        createRelease9 {}
        assertEquals(10, upgrade().version)
    }
}
