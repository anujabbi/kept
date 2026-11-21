package com.example.kept.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.analytics.NoOpAnalytics
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A day finished on the lock screen gets its "Promise kept" moment there (the gold open state),
 * so it must not leave a celebration pending for KEPT as well; a day finished in KEPT still does
 * (issue #9, #127). Real repositories on an in-memory Room database and the real preferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HabitActionsCelebrationTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    // Noon, inside the default 7:00 am to 9:00 pm window, so every tick is on time.
    private val time = TimeSource(Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneId.of("UTC")))
    private val prefs = KeptPreferences(ctx)
    private var shadeCalls = 0
    private val actions = HabitActions(
        habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time),
        sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time),
        prefs = prefs,
        time = time,
        analytics = NoOpAnalytics(),
        dayCompletion = object : DayCompletionReactor { override suspend fun onDayCompleted() { shadeCalls++ } },
    )

    @After fun close() = db.close()

    private suspend fun pending() = prefs.currentSettings().celebrationPendingDate

    @Test fun `finishing on the lock screen leaves no celebration pending, finishing in KEPT does`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, celebrationPendingDate = null) }
        val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
        val a = habits.addHabit("Exercise", "run", ProofType.MANUAL, 30, "min")
        val b = habits.addHabit("Read", "book", ProofType.MANUAL, 10, "pages")

        // Finished in KEPT: the moment waits for Home (issue #9).
        actions.complete(a)
        actions.complete(b)
        assertEquals("2026-11-25", pending())

        // Undone, then finished on the lock screen: the open state was the moment.
        actions.undo(b)
        assertNull(pending())
        actions.complete(b, momentShown = true)
        assertNull("the lock screen's open state is today's moment", pending())
        assertEquals("the day still counts as finished both times", 2, shadeCalls)

        // A tick on the lock screen that does not finish the day changes nothing either way.
        actions.undo(b)
        actions.undo(a)
        actions.complete(a, momentShown = true)
        assertNull(pending())
    }
}
