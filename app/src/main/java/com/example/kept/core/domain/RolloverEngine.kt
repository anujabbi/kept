package com.example.kept.core.domain

import java.time.LocalDate

/**
 * Applies end-of-day rules for one calendar day. Called once per day that has passed, in order,
 * so a phone that was off for three days catches up deterministically.
 */
object RolloverEngine {

    data class DayInput(
        val date: LocalDate,
        val habitsDone: Int,
        val habitsTotal: Int,
        val lockedMillis: Long,
        val pointsEarned: Long,
        val breaksUsed: Int,
        val writtenOff: Boolean,
        val unprotected: Boolean,
        /** Null when no buddy is paired. */
        val buddyDoneThatDay: Boolean?,
    ) {
        val allDone: Boolean get() = habitsTotal > 0 && habitsDone >= habitsTotal
    }

    data class DaySummary(
        val date: LocalDate,
        val habitsDone: Int,
        val habitsTotal: Int,
        val lockedMillis: Long,
        val pointsEarned: Long,
        val breaksUsed: Int,
        val unprotected: Boolean,
        val broken: Boolean,
        val writtenOff: Boolean,
        val countedForStreak: Boolean,
        val shieldConsumed: Boolean,
        val streakReset: Boolean,
        val levelEnd: Int,
        val formEnd: SprigForm,
        val streakEnd: Int,
    )

    data class Unlock(val form: SprigForm, val variant: WeekVariant, val date: LocalDate, val streakAtUnlock: Int)

    data class Output(
        val state: SprigState,
        val summary: DaySummary,
        val unlocks: List<Unlock>,
        val formBefore: SprigForm,
        val formAfter: SprigForm,
    )

    fun rollover(state: SprigState, input: DayInput): Output {
        val refilled = StreakRules.refillShield(state, input.date)
        val formBefore = refilled.form
        val outcome = StreakRules.DayOutcome(
            allHabitsDone = input.allDone,
            unprotected = input.unprotected,
            writtenOff = input.writtenOff,
        )
        val streakResult = StreakRules.apply(refilled.streakDays, refilled.shieldAvailable, outcome)

        val pairStreak = when {
            input.buddyDoneThatDay == null -> 0
            outcome.counts && input.buddyDoneThatDay -> refilled.pairStreak + 1
            else -> 0
        }

        val unlocks = mutableListOf<Unlock>()
        val variant = Variants.forDate(input.date)
        val formAfter = SprigForm.forStreak(streakResult.streak)
        if (streakResult.counted) {
            // Any form whose threshold is crossed exactly today is a fresh unlock.
            SprigForm.entries
                .filter { !it.isPairOnly && it.streakThreshold == streakResult.streak && it.streakThreshold > 0 }
                .forEach { unlocks += Unlock(it, variant, input.date, streakResult.streak) }
            if (pairStreak == SprigForm.DUO_PAIR_STREAK) {
                unlocks += Unlock(SprigForm.DUO, variant, input.date, streakResult.streak)
            }
        }

        val newState = refilled.copy(
            streakDays = streakResult.streak,
            bestStreak = maxOf(refilled.bestStreak, refilled.streakDays, streakResult.streak),
            shieldAvailable = streakResult.shieldAvailable,
            lastRolloverDate = input.date,
            // A completed day heals a wilt; anything else keeps it.
            wilted = if (streakResult.counted) false else refilled.wilted,
            pairStreak = pairStreak,
        )

        val summary = DaySummary(
            date = input.date,
            habitsDone = input.habitsDone,
            habitsTotal = input.habitsTotal,
            lockedMillis = input.lockedMillis,
            pointsEarned = input.pointsEarned,
            breaksUsed = input.breaksUsed,
            unprotected = input.unprotected,
            broken = input.breaksUsed > 0,
            writtenOff = input.writtenOff,
            countedForStreak = streakResult.counted,
            shieldConsumed = streakResult.shieldConsumed,
            streakReset = streakResult.reset,
            levelEnd = newState.level,
            formEnd = formAfter,
            streakEnd = streakResult.streak,
        )
        return Output(newState, summary, unlocks, formBefore, formAfter)
    }

    /** Dates that still need a rollover: from the day after the last rollover up to and including [yesterday]. */
    fun pendingDates(lastRolloverDate: LocalDate?, yesterday: LocalDate, firstUseDate: LocalDate): List<LocalDate> {
        val start = (lastRolloverDate?.plusDays(1) ?: firstUseDate)
        if (start.isAfter(yesterday)) return emptyList()
        val out = mutableListOf<LocalDate>()
        var d = start
        // Guard against pathological clock jumps.
        var guard = 0
        while (!d.isAfter(yesterday) && guard < 400) {
            out += d; d = d.plusDays(1); guard++
        }
        return out
    }
}
