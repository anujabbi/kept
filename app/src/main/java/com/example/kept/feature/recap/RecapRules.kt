package com.example.kept.feature.recap

import com.example.kept.core.data.db.DayRecordEntity

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
}
