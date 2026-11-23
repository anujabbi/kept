package com.example.kept.feature.recap

import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.RecapOutcome
import com.example.kept.core.domain.WeeklyMissedCopy

object RecapRules {
    /**
     * Whether [record]'s day is the one that reset the streak (issue #16). `day_records` does not
     * store the engine's `streakReset` flag, and adding it would mean a migration for one line of
     * copy, so it is derived instead: the day did not count, did not spend the shield, ended at 0,
     * and the day before ended above 0. A day that slipped when the streak was already 0 is a
     * fresh start, not a reset, so the recap must not claim one.
     */
    fun streakReset(record: DayRecordEntity?, previous: DayRecordEntity?): Boolean {
        if (record == null || previous == null) return false
        return !record.countedForStreak && !record.shieldConsumed && record.streakEnd == 0 && previous.streakEnd > 0
    }

    /** The recap outcome for a day record ([RecapOutcome.of]). */
    fun outcome(r: DayRecordEntity): RecapOutcome =
        RecapOutcome.of(r.countedForStreak, r.unprotected, r.writtenOff, r.shieldConsumed, r.habitsDone, r.habitsTotal, r.weeklyDone < r.weeklyDue)

    /**
     * The weekly goals to name for [r]'s day (story #176): the ones whose must-do session was not
     * logged in time, when that is why the day slipped or needed the shield. A day the lock was
     * off, or one written off, has its own reason and names none.
     */
    fun weeklyMissed(r: DayRecordEntity): List<String> = when (outcome(r)) {
        RecapOutcome.SLIPPED, RecapOutcome.SHIELD -> WeeklyMissedCopy.read(r.weeklyMissed)
        else -> emptyList()
    }
}
