package com.example.kept.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.analytics.NoOpAnalytics
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.LockState
import com.example.kept.core.data.RecapNotifier
import com.example.kept.core.data.RolloverRunner
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "Sleep by 11" preset is retired (issue #163). Schema 5 -> 6 deletes the saved copies of it
 * and nothing else; the first rollover pass after the upgrade then brings today's open day record
 * back in line with the habits that are left, so the lock stops waiting on a habit that is gone.
 *
 * Built like [GalleryMigrationTest]: the release database is created from the exported schema
 * JSON and opened by Room with the app's migrations, on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RetiredPresetMigrationTest {
    private val dbName = "retired-preset-migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: KeptDatabase? = null

    // Noon, inside the default 7:00 am to 9:00 pm lock window.
    private val now = Instant.parse("2026-11-25T12:00:00Z")
    private val today = "2026-11-25"
    private val time = TimeSource(Clock.fixed(now, ZoneId.of("UTC")))
    private val prefs = KeptPreferences(context)

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    // --- the release (schema 5) database -------------------------------------------------------

    private fun createReleaseDatabase(seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(dbName)
        val path = "schemas/com.example.kept.core.data.db.KeptDatabase/5.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        val schema = JSONObject(file.readText()).getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            e.optJSONArray("indices")?.let { idx ->
                for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = 5
        db.seed()
        db.close()
    }

    private fun SQLiteDatabase.habit(id: Long, title: String, unit: String, target: Int = 1, active: Boolean = true, icon: String = "sleep") =
        execSQL(
            "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                "VALUES ($id, '$title', '$icon', 'MANUAL', $target, '$unit', $id, ${if (active) 1 else 0}, 0)",
        )

    private fun SQLiteDatabase.entry(id: Long, habitId: Long, date: String, completedAt: Long?) =
        execSQL(
            "INSERT INTO habit_entries (id, habitId, date, progressValue, completedAt, photoPath) " +
                "VALUES ($id, $habitId, '$date', ${if (completedAt == null) 0 else 1}, ${completedAt ?: "NULL"}, NULL)",
        )

    private fun SQLiteDatabase.day(date: String, done: Int, total: Int, finalized: Boolean, streakEnd: Int = 0) =
        execSQL(
            "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) " +
                "VALUES ('$date', $done, $total, 3600000, 0, 0, 0, 0, ${if (finalized) 1 else 0}, 0, 4, 3, $streakEnd, ${if (finalized) 1 else 0})",
        )

    /** Opens the database the way the app does (`AppModule.database`): every migration, in order. */
    private fun upgrade(): SupportSQLiteDatabase {
        val r = Room.databaseBuilder(context, KeptDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        room = r
        return r.openHelper.writableDatabase
    }

    /** Every row of [table] as text, in a fixed order, read without Room. */
    private fun rows(table: String, orderBy: String): List<String> = buildList {
        val read: (android.database.Cursor) -> Unit = { c ->
            while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" })
        }
        val sql = "SELECT * FROM $table ORDER BY $orderBy"
        val open = room
        if (open != null) {
            open.openHelper.writableDatabase.query(sql).use(read)
        } else {
            SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(sql, null).use(read)
            }
        }
    }

    // --- the app's own objects over the upgraded database --------------------------------------

    private fun habitRepository() = room!!.let { HabitRepository(it.habitDao(), it.habitEntryDao(), it.dayRecordDao(), prefs, time) }

    private fun lockRepository(): LockRepository {
        val db = room!!
        return LockRepository(
            db.allowedAppDao(), db.lockBreakDao(), db.protectionGapDao(), db.dayRecordDao(), prefs,
            habitRepository(), SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time), time,
            com.example.kept.core.data.WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time),
        )
    }

    /** The real first-launch call site: `AppViewModel.onResume` and both workers run this. */
    private suspend fun firstLaunchAfterUpgrade() {
        val db = room!!
        RolloverRunner(
            prefs, db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), db.lockBreakDao(), db.protectionGapDao(),
            SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time), habitRepository(), time,
            RecapNotifier { _, _ -> error("no day closes in these tests, so no recap") }, NoOpAnalytics(),
            db.weeklyGoalDao(), db.weeklyGoalEntryDao(),
        ).runPending()
    }

    /** A settled user: onboarded, yesterday already rolled over, a streak and a level to lose. */
    private suspend fun settledUser(): SprigState {
        prefs.updateSettings { it.copy(onboardingDone = true, firstUseDate = LocalDate.parse("2026-11-01"), lockFromMinute = 7 * 60, dueMinute = 21 * 60) }
        val sprig = SprigState(
            level = 4, streakDays = 12, bestStreak = 15, shieldAvailable = false, shieldWeekKey = "2026-W48",
            lastRolloverDate = LocalDate.parse("2026-11-24"), wilted = false, levelGrantedDate = "2026-11-24",
        )
        prefs.updateSprig { sprig }
        return sprig
    }

    /** The lock state the release build computes from this database: every active habit counts. */
    private fun lockStateBeforeUpgrade(): LockState {
        val habits = mutableListOf<HabitToday>()
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT h.id, h.title, h.unit, e.completedAt FROM habits h " +
                    "LEFT JOIN habit_entries e ON e.habitId = h.id AND e.date = '$today' WHERE h.isActive = 1 ORDER BY h.id",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val habit = HabitEntity(id = c.getLong(0), title = c.getString(1), iconKey = "star", proofType = ProofType.MANUAL, targetValue = 1, unit = c.getString(2), createdAt = 0)
                    val entry = if (c.isNull(3)) null else HabitEntryEntity(habitId = habit.id, date = today, progressValue = 1, completedAt = c.getLong(3))
                    habits += HabitToday(habit, entry)
                }
            }
        }
        return LockState(
            settings = com.example.kept.core.data.prefs.Settings(onboardingDone = true),
            today = TodaySummary(habits), breakActiveUntil = null, breaksUsedThisWeek = 0, userExceptions = emptySet(), unprotectedToday = false,
        )
    }

    private fun LockState.habitsIncomplete() = snapshot(hardAllowlist = emptySet(), launchable = null).habitsIncomplete

    // --- the migration -------------------------------------------------------------------------

    @Test
    fun migrate5To6_deletesOnlyTheExactPresetRows() {
        createReleaseDatabase {
            habit(1, "Sleep by 11", "check-in")
            habit(2, "Sleep by 11", "check-in", active = false)
            habit(3, "sleep by 11", "check-in")
            habit(4, "Sleep by 11", "min", target = 30)
            habit(5, "Lights out", "check-in")
            habit(6, "Exercise", "min", target = 30, icon = "run")
            // Entries of the two rows that go, and of two that stay.
            entry(1, habitId = 1, date = "2026-11-24", completedAt = 1_000)
            entry(2, habitId = 1, date = today, completedAt = null)
            entry(3, habitId = 2, date = "2026-11-20", completedAt = 2_000)
            entry(4, habitId = 5, date = "2026-11-24", completedAt = 3_000)
            entry(5, habitId = 6, date = today, completedAt = 4_000)
            day("2026-11-23", done = 6, total = 6, finalized = true, streakEnd = 11)
            day("2026-11-24", done = 5, total = 6, finalized = true, streakEnd = 0)
            day(today, done = 1, total = 5, finalized = false)
        }
        val entriesBefore = rows("habit_entries", "id")
        val daysBefore = rows("day_records", "date")
        assertEquals(5, entriesBefore.size)
        assertEquals(3, daysBefore.size)

        val db = upgrade()

        assertEquals(10, db.version)
        val left = mutableListOf<String>()
        db.query("SELECT id, title, unit FROM habits ORDER BY id").use { while (it.moveToNext()) left += "${it.getLong(0)} ${it.getString(1)} ${it.getString(2)}" }
        assertEquals(listOf("3 sleep by 11 check-in", "4 Sleep by 11 min", "5 Lights out check-in", "6 Exercise min"), left)
        assertEquals("habit_entries are left alone, the removed habits' included", entriesBefore, rows("habit_entries", "id"))
        // Every column a day had is as it was; 7 -> 8 adds the three weekly ones (story #176).
        assertEquals("the migration itself touches no day record", daysBefore.map { "$it|0|0|" }, rows("day_records", "date"))
    }

    // --- first launch after the upgrade --------------------------------------------------------

    @Test
    fun theLockStopsWaitingOnTheRemovedHabit_andTodaysRecordAgrees() = runBlocking {
        val sprigBefore = settledUser()
        createReleaseDatabase {
            habit(1, "Exercise", "min", target = 30, icon = "run")
            habit(2, "Sleep by 11", "check-in")
            entry(1, habitId = 1, date = today, completedAt = now.toEpochMilli() - 3_600_000)
            day("2026-11-24", done = 2, total = 2, finalized = true, streakEnd = 12)
            day(today, done = 1, total = 2, finalized = false)
        }
        val yesterdayBefore = rows("day_records", "date").first()
        val before = lockStateBeforeUpgrade()
        assertTrue("locked before the upgrade", before.habitsIncomplete())
        assertTrue(before.isLockActiveNow(now, time.localTime()))

        upgrade()
        firstLaunchAfterUpgrade()

        val after = lockRepository().currentState()
        assertEquals(listOf("Exercise"), after.today.habits.map { it.habit.title })
        assertFalse("lock off after the upgrade", after.habitsIncomplete())
        assertFalse(after.isLockActiveNow(now, time.localTime()))
        val record = room!!.dayRecordDao().get(today)!!
        assertEquals(1, record.habitsDone)
        assertEquals(1, record.habitsTotal)
        assertFalse("today is still open", record.finalized)
        // 7 -> 8 gives every day its three weekly columns (story #176): none due, none done, no goal named.
        assertEquals("the finalized day is not touched", "$yesterdayBefore|0|0|", rows("day_records", "date").first())
        assertEquals("no level granted or revoked, streak and shield as they were", sprigBefore, prefs.currentSprig())
    }

    @Test
    fun aRemainingHabitNotYetDoneStillLocks() = runBlocking {
        settledUser()
        createReleaseDatabase {
            habit(1, "Exercise", "min", target = 30, icon = "run")
            habit(2, "Sleep by 11", "check-in")
            day(today, done = 0, total = 2, finalized = false)
        }

        upgrade()
        firstLaunchAfterUpgrade()

        val after = lockRepository().currentState()
        assertTrue(after.habitsIncomplete())
        val record = room!!.dayRecordDao().get(today)!!
        assertEquals(0, record.habitsDone)
        assertEquals(1, record.habitsTotal)
    }

    @Test
    fun aUserWhoseOnlyHabitWasThePresetEndsWithNoHabitsAndNoLock() = runBlocking {
        val sprigBefore = settledUser()
        createReleaseDatabase {
            habit(1, "Sleep by 11", "check-in")
            day(today, done = 0, total = 1, finalized = false)
        }
        assertTrue(lockStateBeforeUpgrade().habitsIncomplete())

        upgrade()
        firstLaunchAfterUpgrade()

        val after = lockRepository().currentState()
        assertEquals(0, after.today.total)
        assertFalse(after.habitsIncomplete())
        assertFalse(after.isLockActiveNow(now, time.localTime()))
        val record = room!!.dayRecordDao().get(today)!!
        assertEquals(0, record.habitsDone)
        assertEquals(0, record.habitsTotal)
        assertEquals(sprigBefore, prefs.currentSprig())
        // The add-a-habit path still saves.
        val id = habitRepository().addHabitWithinLimit("Drink water", "water", ProofType.MANUAL, 8, "glasses")
        assertEquals(listOf("Drink water"), room!!.habitDao().active().map { it.title })
        assertTrue(id != null)
    }
}
