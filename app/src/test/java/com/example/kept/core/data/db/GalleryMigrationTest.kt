package com.example.kept.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.domain.SprigForm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

/**
 * Weekly looks are removed (issue #125), and with them `gallery.variantId`. These run on the JVM
 * (Robolectric), so the upgrade from the current release (schema 4) is checked on every
 * `testDebugUnitTest`, not only on a device (`androidTest/MigrationTest` covers the same on one).
 *
 * Robolectric cannot see test assets, so `MigrationTestHelper` cannot read the schemas here. The
 * old database is built instead from the exported schema JSON in `app/schemas`, exactly as Room
 * would have created it, and then opened by Room with the app's migrations. Room validates every
 * table against the current entities when it opens, so a migration that leaves a column behind,
 * or loses one, fails the open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GalleryMigrationTest {
    private val dbName = "gallery-migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var room: KeptDatabase? = null

    @After fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    private fun schema(version: Int): JSONObject {
        val path = "schemas/com.example.kept.core.data.db.KeptDatabase/$version.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** Creates [dbName] at [version] from its exported schema, then lets [seed] add rows. */
    private fun createDatabase(version: Int, seed: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(dbName)
        val s = schema(version)
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
        db.version = version
        db.seed()
        db.close()
    }

    /** Opens [dbName] the way the app does, with [migrations], and returns the raw database. */
    private fun open(vararg migrations: Migration): SupportSQLiteDatabase {
        val r = Room.databaseBuilder(context, KeptDatabase::class.java, dbName)
            .addMigrations(*migrations)
            .allowMainThreadQueries()
            .build()
        room = r
        return r.openHelper.writableDatabase
    }

    /** A phone on the current release: three forms collected, one not yet seen, a habit and a day. */
    private fun createReleaseDatabase() = createDatabase(4) {
        execSQL("INSERT INTO gallery (id, formId, variantId, unlockedDate, streakAtUnlock, seen) VALUES (1, 1, 3, '2026-09-01', 0, 1)")
        execSQL("INSERT INTO gallery (id, formId, variantId, unlockedDate, streakAtUnlock, seen) VALUES (2, 2, 11, '2026-09-04', 3, 1)")
        execSQL("INSERT INTO gallery (id, formId, variantId, unlockedDate, streakAtUnlock, seen) VALUES (3, 3, 7, '2026-09-08', 7, 0)")
        execSQL(
            "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                "VALUES (1, 'Read', 'book', 'MANUAL', 10, 'pages', 0, 1, 0)",
        )
        execSQL(
            "INSERT INTO day_records (date, habitsDone, habitsTotal, lockedMillis, breaksUsed, unprotected, broken, " +
                "writtenOff, countedForStreak, shieldConsumed, levelEnd, formId, streakEnd, finalized) " +
                "VALUES ('2026-09-08', 1, 1, 5400000, 0, 0, 0, 0, 1, 0, 3, 3, 7, 1)",
        )
    }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> {
        val names = mutableListOf<String>()
        query("PRAGMA table_info($table)").use { while (it.moveToNext()) names += it.getString(it.getColumnIndexOrThrow("name")) }
        return names
    }

    @Test
    fun migrate4To5_dropsVariantIdAndKeepsEveryGalleryRow() {
        createReleaseDatabase()

        val db = open(MIGRATION_4_5)

        assertEquals(5, db.version)
        assertFalse(db.columns("gallery").contains("variantId"))
        val rows = mutableListOf<List<Any>>()
        db.query("SELECT id, formId, unlockedDate, streakAtUnlock, seen FROM gallery ORDER BY id").use {
            while (it.moveToNext()) rows += listOf(it.getLong(0), it.getInt(1), it.getString(2), it.getInt(3), it.getInt(4))
        }
        assertEquals(
            listOf<List<Any>>(
                listOf(1L, 1, "2026-09-01", 0, 1),
                listOf(2L, 2, "2026-09-04", 3, 1),
                listOf(3L, 3, "2026-09-08", 7, 0),
            ),
            rows,
        )
        // Habits and the streak history are untouched.
        db.query("SELECT title FROM habits WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("Read", it.getString(0)) }
        db.query("SELECT streakEnd FROM day_records WHERE date = '2026-09-08'").use { assertTrue(it.moveToFirst()); assertEquals(7, it.getInt(0)) }
    }

    @Test
    fun newUnlocksAfterTheMigrationNeverReuseAnOldId() = runTest {
        createReleaseDatabase()
        open(MIGRATION_4_5)

        val id = room!!.galleryDao().insert(GalleryEntryEntity(formId = 4, unlockedDate = "2026-09-22", streakAtUnlock = 14))
        assertEquals(4L, id)
    }

    /** First launch after the update: the collection still lists every form reached, newest first. */
    @Test
    fun theCollectionKeepsItsFormsAfterTheUpgrade() = runTest {
        createReleaseDatabase()
        open(*ALL_MIGRATIONS)

        val forms = room!!.galleryDao().observeAll().first().map { SprigForm.fromId(it.formId) }
        assertEquals(listOf(SprigForm.BLOOM, SprigForm.BUD, SprigForm.SPRIG), forms)
        assertEquals(listOf(3L), room!!.galleryDao().observeUnseen().first().map { it.id })
    }

    /** The path an installed v1 build that never updated takes, end to end, to schema 5. */
    @Test
    fun migrate1To5_runsEveryMigration() {
        createDatabase(1) {
            execSQL(
                "INSERT INTO habits (id, title, iconKey, proofType, targetValue, unit, sortOrder, isActive, createdAt) " +
                    "VALUES (1, 'Exercise', 'run', 'TIMER', 30, 'min', 0, 1, 0)",
            )
            execSQL("INSERT INTO gallery (id, formId, variantId, unlockedDate, streakAtUnlock, seen) VALUES (1, 2, 5, '2026-09-04', 3, 1)")
        }

        val db = open(*ALL_MIGRATIONS)

        assertEquals(5, db.version)
        db.query("SELECT proofType FROM habits WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("MANUAL", it.getString(0)) }
        db.query("SELECT formId, streakAtUnlock FROM gallery WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
            assertEquals(3, it.getInt(1))
        }
    }
}
