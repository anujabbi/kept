package com.example.kept.core.data

import com.example.kept.core.data.db.BuddyDao
import com.example.kept.core.data.db.BuddyEntity
import com.example.kept.core.data.db.DayRecordDao
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitDao
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryDao
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.StreakRules
import com.example.kept.core.domain.Variants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only. Populates 12 days of history, a paired buddy, two habits and a partially complete
 * today so every screen has something to show. Never runs in release builds.
 */
@Singleton
class DebugSeeder @Inject constructor(
    private val habitDao: HabitDao,
    private val entryDao: HabitEntryDao,
    private val dayDao: DayRecordDao,
    private val buddyDao: BuddyDao,
    private val sprig: SprigRepository,
    private val prefs: KeptPreferences,
    private val time: TimeSource,
) {
    suspend fun seedIfNeeded() {
        val settings = prefs.currentSettings()
        if (settings.seeded) return
        val today = time.today()
        val now = time.nowMillis()

        val exercise = habitDao.insert(HabitEntity(title = "Exercise", iconKey = "run", proofType = ProofType.TIMER, targetValue = 30, unit = "min", sortOrder = 0, createdAt = now))
        val read = habitDao.insert(HabitEntity(title = "Read", iconKey = "book", proofType = ProofType.MANUAL, targetValue = 10, unit = "pages", sortOrder = 1, createdAt = now))

        // 12 finished days, streak building from 1 to 12, one shielded miss on day 5 (streak holds).
        var streak = 0
        var level = 1
        for (i in 12 downTo 1) {
            val date = today.minusDays(i.toLong())
            val missed = i == 5
            val done = if (missed) 1 else 2
            if (!missed) { streak += 1; level += 1 }
            val key = date.toString()
            entryDao.upsertAll(
                listOf(
                    HabitEntryEntity(habitId = exercise, date = key, progressValue = 30 * 60, completedAt = time.instantAt(date, 8 * 60 + 12).toEpochMilli()),
                    HabitEntryEntity(habitId = read, date = key, progressValue = if (missed) 0 else 10, completedAt = if (missed) null else time.instantAt(date, 20 * 60 + 5).toEpochMilli()),
                ),
            )
            dayDao.upsert(
                DayRecordEntity(
                    date = key, habitsDone = done, habitsTotal = 2,
                    lockedMillis = (2L + (i % 3)) * 3_600_000L + 24 * 60_000L,
                    pointsEarned = 200L + i * 9, breaksUsed = 0,
                    unprotected = false, broken = false, writtenOff = false,
                    countedForStreak = !missed, shieldConsumed = missed,
                    levelEnd = level, formId = SprigForm.forStreak(streak).id, streakEnd = streak, finalized = true,
                ),
            )
            // Gallery unlocks as thresholds were crossed.
            if (!missed && SprigForm.entries.any { !it.isPairOnly && it.streakThreshold == streak && streak > 0 }) {
                val form = SprigForm.forStreak(streak)
                sprig.insertUnlock(form, Variants.forDate(date), date, streak, seen = true)
            }
        }
        // Base form is always in the gallery.
        sprig.insertUnlock(SprigForm.SPRIG, Variants.forDate(today.minusDays(12)), today.minusDays(12), 0, seen = true)

        // Today: exercise 18/30 in progress, reading not done.
        val todayKey = today.toString()
        entryDao.upsert(HabitEntryEntity(habitId = exercise, date = todayKey, progressValue = 18 * 60))
        dayDao.upsert(DayRecordEntity(date = todayKey, habitsDone = 0, habitsTotal = 2, lockedMillis = 96 * 60_000L, pointsEarned = 192))

        buddyDao.upsert(
            BuddyEntity(
                displayName = "Maya", initials = "MK", streakDays = 9, doneToday = true,
                formId = SprigForm.BLOOM.id, lastSevenDays = "1101110", pairedAt = now - 9L * 86_400_000L,
            ),
        )

        sprig.write(
            SprigState(
                level = level, points = 2_840, streakDays = streak, bestStreak = streak,
                shieldAvailable = true, shieldWeekKey = StreakRules.weekKey(today),
                lastRolloverDate = today.minusDays(1), wilted = false, pairStreak = 4,
            ),
        )
        prefs.updateSettings {
            it.copy(
                seeded = true, onboardingDone = true, firstUseDate = today.minusDays(12),
                myInviteCode = "K4W-92B",
            )
        }
    }
}
