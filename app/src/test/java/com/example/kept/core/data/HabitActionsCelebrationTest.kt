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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * A day finished on the lock screen gets its "Promise kept" moment there, so it must not leave a
 * celebration pending for KEPT as well; a day finished in KEPT still does (issue #9, #127). The
 * moment plays once per day across both (issue #130). Real repositories on an in-memory Room database and the real preferences.
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

    private fun actionsAt(clock: TimeSource) = HabitActions(
        habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, clock),
        sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), clock),
        prefs = prefs,
        time = clock,
        analytics = NoOpAnalytics(),
        dayCompletion = object : DayCompletionReactor { override suspend fun onDayCompleted() { shadeCalls++ } },
    )

    private suspend fun pending() = prefs.currentSettings().celebrationPendingDate

    @Test fun `finishing on the lock screen leaves no celebration pending, finishing in KEPT does`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, celebrationPendingDate = null, celebrationShownDate = null, dueMinute = 21 * 60) }
        val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
        val a = habits.addHabit("Exercise", "run", ProofType.MANUAL, 30, "min")
        val b = habits.addHabit("Read", "book", ProofType.MANUAL, 10, "pages")

        // Finished in KEPT: the moment waits for Home (issue #9).
        actions.complete(a)
        actions.complete(b)
        assertEquals("2026-11-25", pending())

        // Undone, then finished on the lock screen, which plays the moment itself.
        actions.undo(b)
        assertNull(pending())
        actions.complete(b, momentShown = true)
        assertNull("the lock screen plays today's moment itself", pending())
        assertEquals("the day still counts as finished both times", 2, shadeCalls)

        // A tick on the lock screen that does not finish the day changes nothing either way.
        actions.undo(b)
        actions.undo(a)
        actions.complete(a, momentShown = true)
        assertNull(pending())
    }

    @Test fun `the moment plays once per day across the lock screen and KEPT`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, celebrationPendingDate = null, celebrationShownDate = null, dueMinute = 21 * 60) }
        val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
        val a = habits.addHabit("Exercise", "run", ProofType.MANUAL, 30, "min")

        // Finished on the lock screen, which takes the moment and plays it.
        actions.complete(a, momentShown = true)
        assertTrue("the lock screen gets today's moment", actions.claimMoment())
        assertEquals("2026-11-25", prefs.currentSettings().celebrationShownDate)
        assertFalse("nobody gets it twice", actions.claimMoment())

        // Undone in KEPT and finished again there: already played today, so nothing waits for KEPT.
        actions.undo(a)
        actions.complete(a)
        assertNull("KEPT does not show it a second time", pending())

        // The next day it plays again.
        val tomorrow = actionsAt(TimeSource(Clock.fixed(Instant.parse("2026-11-26T12:00:00Z"), ZoneId.of("UTC"))))
        assertTrue(tomorrow.claimMoment())
    }

    @Test fun `a day finished after the give-up time leaves nothing to celebrate`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, celebrationPendingDate = null, celebrationShownDate = null, dueMinute = 11 * 60) }
        val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
        val a = habits.addHabit("Read", "book", ProofType.MANUAL, 10, "pages")
        val event = actions.complete(a)!!
        assertTrue(event.allDoneNow)
        assertFalse("noon is after the 11:00 am give-up time", event.beforeDue)
        assertNull(pending())
        assertEquals("not a finished day", 0, shadeCalls)
    }
}
