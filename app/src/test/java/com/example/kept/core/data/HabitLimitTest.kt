package com.example.kept.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.ProofType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock

/**
 * The 10-habit limit holds in the write, not only in the disabled button (review on #149): two
 * Add pages open at 9 habits must not save an 11th. A real in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HabitLimitTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), KeptPreferences(ctx), TimeSource(Clock.systemUTC()))

    @After fun close() = db.close()

    private suspend fun add(n: Int) = habits.addHabitWithinLimit("Habit $n", "write", ProofType.MANUAL, 1, "times")

    @Test fun an_eleventh_habit_is_refused() = runBlocking {
        repeat(HabitLimits.MAX_HABITS) { assertNotNull("habit ${it + 1}", add(it + 1)) }
        assertNull(add(11))
        assertEquals(HabitLimits.MAX_HABITS, db.habitDao().active().size)
    }

    @Test fun two_saves_at_nine_add_only_one() = runBlocking {
        repeat(9) { add(it + 1) }
        val results = (1..2).map { async(Dispatchers.IO) { add(10 + it) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(HabitLimits.MAX_HABITS, db.habitDao().active().size)
    }

    @Test fun a_removed_habit_makes_room_again() = runBlocking {
        val ids = (1..10).map { add(it)!! }
        assertNull(add(11))
        habits.removeHabit(ids.first())
        assertNotNull(add(11))
    }
}
