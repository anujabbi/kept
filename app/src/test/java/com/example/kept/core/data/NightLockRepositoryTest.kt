package com.example.kept.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.LockReason
import com.example.kept.core.domain.LockPolicy
import com.example.kept.core.domain.ProofType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Night lockdown against the real stores (issue #164, story #168): the setting's defaults and
 * keys, a break taken at night, and where a protection gap is recorded. Real repositories on an
 * in-memory Room database and the real preferences, with a clock the test winds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NightLockRepositoryTest {

    private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }

    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()
    // 23:30 on Saturday 21 November: inside the default 11:00 pm to 7:00 am night window, and
    // after the default daily window closed at 9:00 pm.
    private val clock = MutableClock(Instant.parse("2026-11-21T23:30:00Z"), ZoneId.of("UTC"))
    private val time = TimeSource(clock)
    private val prefs = KeptPreferences(ctx)
    private val habits = HabitRepository(db.habitDao(), db.habitEntryDao(), db.dayRecordDao(), prefs, time)
    private val sprig = SprigRepository(prefs, db.galleryDao(), db.dayRecordDao(), time)
    private val lock = LockRepository(db.allowedAppDao(), db.lockBreakDao(), db.protectionGapDao(), db.dayRecordDao(), prefs, habits, sprig, time, WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time))

    @Before fun reset() = runBlocking { prefs.clearAll() }

    @After fun close() = db.close()

    private suspend fun reason(): LockReason? =
        LockPolicy.lockReason(time.now(), time.localTime(), lock.currentState().snapshot(emptySet(), null))

    @Test fun `a fresh install and an upgrade with no night keys both read night lockdown off`() = runBlocking {
        // Nothing stored at all: a fresh install.
        assertEquals(Settings(), prefs.currentSettings())
        assertFalse(prefs.currentSettings().nightLockEnabled)
        // An install from before night lockdown has its other keys and none of the night ones:
        // DataStore reads a missing key as its default, which is off.
        assertFalse(Settings(onboardingDone = true, lockFromMinute = 8 * 60, dueMinute = 20 * 60).nightLockEnabled)
        prefs.heartbeat(123L)
        val upgraded = prefs.currentSettings()
        assertFalse(upgraded.nightLockEnabled)
        assertEquals(23 * 60, upgraded.nightFromMinute)
        assertEquals(7 * 60, upgraded.nightUntilMinute)
        assertNull("off, nothing locks at night", reason())
    }

    @Test fun `the three night values are stored and read back`() = runBlocking {
        prefs.updateSettings { it.copy(nightLockEnabled = true) }
        assertEquals(Triple(true, 1380, 420), prefs.currentSettings().let { Triple(it.nightLockEnabled, it.nightFromMinute, it.nightUntilMinute) })
        prefs.updateSettings { it.copy(nightFromMinute = 0, nightUntilMinute = 6 * 60 + 30) }
        assertEquals(Triple(true, 0, 390), prefs.currentSettings().let { Triple(it.nightLockEnabled, it.nightFromMinute, it.nightUntilMinute) })
        prefs.updateSettings { it.copy(nightLockEnabled = false) }
        assertEquals(Triple(false, 0, 390), prefs.currentSettings().let { Triple(it.nightLockEnabled, it.nightFromMinute, it.nightUntilMinute) })
    }

    @Test fun `the lock state carries the night setting, and night holds with every habit done`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, nightLockEnabled = true) }
        val id = habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
        habits.markDone(id)
        val state = lock.currentState()
        assertTrue(state.today.allDone)
        assertTrue(state.isLockActiveNow(time.now(), time.localTime()))
        assertTrue(state.isNightLockActiveNow(time.now(), time.localTime()))
        assertFalse(state.isDailyLockActiveNow(time.now(), time.localTime()))
        assertEquals(LockReason.NIGHT, reason())
    }

    @Test fun `a break at night opens apps for the configured time, costs a level, wilts Sprig and counts toward the cap`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, nightLockEnabled = true, breakDurationMin = 15) }
        prefs.updateSprig { it.copy(level = 4, wilted = false) }
        assertEquals(LockReason.NIGHT, reason())

        val b = lock.breakLock()
        assertEquals(time.nowMillis() + 15 * 60_000, b.unlockedUntil)
        assertFalse(b.overCap)
        assertNull("a break pauses night lockdown", reason())
        assertEquals(3, prefs.currentSprig().level)
        assertTrue(prefs.currentSprig().wilted)
        assertEquals(1, lock.currentState().breaksUsedThisWeek)
        val day = db.dayRecordDao().get("2026-11-21")!!
        assertEquals(1, day.breaksUsed)
        assertFalse(day.writtenOff)

        // The break runs out while still inside the window: locked again.
        clock.instant = clock.instant.plus(Duration.ofMinutes(16))
        assertEquals(LockReason.NIGHT, reason())
    }

    @Test fun `a fourth break in 7 days at night writes off the calendar day it happened on`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, nightLockEnabled = true, breakDurationMin = 15) }
        prefs.updateSprig { it.copy(level = 6) }
        // Three breaks on earlier nights of the same week.
        for (daysAgo in listOf(5L, 3L, 1L)) {
            clock.instant = Instant.parse("2026-11-21T23:30:00Z").minus(Duration.ofDays(daysAgo))
            assertFalse(lock.breakLock().overCap)
        }
        // The fourth, after midnight: the calendar day it happens on is the 22nd, not the 21st the
        // night started on.
        clock.instant = Instant.parse("2026-11-22T00:30:00Z")
        assertEquals(LockReason.NIGHT, reason())
        assertTrue(lock.currentState().nextBreakOverCap)
        val fourth = lock.breakLock()
        assertTrue(fourth.overCap)
        assertEquals("2026-11-22", fourth.date)
        assertTrue(db.dayRecordDao().get("2026-11-22")!!.writtenOff)
        assertFalse("the night before is not the day written off", db.dayRecordDao().get("2026-11-20")!!.writtenOff)
        assertNull("the 21st had no break and has no record from this", db.dayRecordDao().get("2026-11-21"))
        assertEquals(2, prefs.currentSprig().level)
    }

    @Test fun `night lockdown alone writes nothing to the day`() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true, nightLockEnabled = true) }
        assertEquals(LockReason.NIGHT, reason())
        clock.instant = Instant.parse("2026-11-22T00:05:00Z")
        assertEquals(LockReason.NIGHT, reason())
        assertNull(db.dayRecordDao().get("2026-11-21"))
        assertNull(db.dayRecordDao().get("2026-11-22"))
        assertTrue(lock.observeGapsToday().first().isEmpty())
    }

    @Test fun `a protection gap is recorded against today`() = runBlocking {
        clock.instant = Instant.parse("2026-11-21T12:00:00Z")
        lock.recordProtectionGap(time.nowMillis() - 600_000, time.nowMillis(), "The lock was off while you were using the phone")
        assertTrue(db.dayRecordDao().get("2026-11-21")!!.unprotected)
        assertEquals(listOf("2026-11-21"), lock.observeGapsToday().first().map { it.date })
    }
}
