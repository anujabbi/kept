package com.example.kept.core.domain

import java.time.LocalDate

/**
 * Applies end-of-day rules for one calendar day. Called once per day that has passed, in order,
 * so a phone that was off for three days catches up deterministically.
 */
object RolloverEngine {

    data class DayInput(
        val date: LocalDate,
        /** Habits ticked at any point that day, late ones included. Recorded, but not what counts. */
        val habitsDone: Int,
        /**
         * Habits ticked before that day's give-up time (issue #7). Only these count: the copy has
         * always said a day is missed once the give-up time passes, so the rollover enforces it.
         */
        val habitsDoneBeforeDue: Int,
        val habitsTotal: Int,
        val lockedMillis: Long,
        val breaksUsed: Int,
        val writtenOff: Boolean,
        val unprotected: Boolean,
        /**
         * Weekly goals whose must-do day this was (issue #165, story #176), worked out for this
         * date by [WeeklyDay.outcome]. 0 on a day no goal made a must-do day, and with no goal.
         */
        val weeklyDue: Int = 0,
        /** Of those, the sessions logged before that day's give-up time. Only these count. */
        val weeklyDoneBeforeDue: Int = 0,
        /** The names of the goals whose must-do session was not logged in time, for the recap. */
        val weeklyMissed: List<String> = emptyList(),
    ) {
        /**
         * The day's promise: every habit, and on a must-do day every must-do session, before the
         * give-up time. A day with no habits never counts, with or without a weekly session.
         */
        val allDone: Boolean get() = habitsTotal > 0 && habitsDoneBeforeDue >= habitsTotal && weeklyDoneBeforeDue >= weeklyDue
    }

    data class DaySummary(
        val date: LocalDate,
        val habitsDone: Int,
        val habitsTotal: Int,
        val lockedMillis: Long,
        val breaksUsed: Int,
        val unprotected: Boolean,
        /** Unprotected and not written off: shown hollow, costs nothing (issue #2). */
        val hollow: Boolean,
        val broken: Boolean,
        val writtenOff: Boolean,
        val countedForStreak: Boolean,
        val shieldConsumed: Boolean,
        val streakReset: Boolean,
        val levelEnd: Int,
        val formEnd: SprigForm,
        val streakEnd: Int,
        val weeklyDue: Int = 0,
        /** Must-do sessions logged before the give-up time. */
        val weeklyDone: Int = 0,
        val weeklyMissed: List<String> = emptyList(),
    )

    data class Unlock(val form: SprigForm, val date: LocalDate, val streakAtUnlock: Int)

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

        val unlocks = mutableListOf<Unlock>()
        val formAfter = SprigForm.forStreak(streakResult.streak)
        if (streakResult.counted) {
            // Any form whose threshold is crossed exactly today is a fresh unlock.
            SprigForm.entries
                .filter { it.streakThreshold == streakResult.streak && it.streakThreshold > 0 }
                .forEach { unlocks += Unlock(it, input.date, streakResult.streak) }
        }

        val newState = refilled.copy(
            streakDays = streakResult.streak,
            bestStreak = maxOf(refilled.bestStreak, refilled.streakDays, streakResult.streak),
            shieldAvailable = streakResult.shieldAvailable,
            lastRolloverDate = input.date,
            // A completed day heals a wilt; anything else keeps it.
            wilted = if (streakResult.counted) false else refilled.wilted,
        )

        val summary = DaySummary(
            date = input.date,
            habitsDone = input.habitsDone,
            habitsTotal = input.habitsTotal,
            lockedMillis = input.lockedMillis,
            breaksUsed = input.breaksUsed,
            unprotected = input.unprotected,
            hollow = streakResult.hollow,
            broken = input.breaksUsed > 0,
            writtenOff = input.writtenOff,
            countedForStreak = streakResult.counted,
            shieldConsumed = streakResult.shieldConsumed,
            streakReset = streakResult.reset,
            levelEnd = newState.level,
            formEnd = formAfter,
            streakEnd = streakResult.streak,
            weeklyDue = input.weeklyDue,
            weeklyDone = input.weeklyDoneBeforeDue,
            weeklyMissed = input.weeklyMissed,
        )
        return Output(newState, summary, unlocks, formBefore, formAfter)
    }

    /**
     * Which of a run's outputs the recap should show (issue #16). After a gap of several days the
     * teen sees one recap, and the day that matters is the one where something happened to the
     * streak: the first day that consumed the shield or reset the streak. Showing the last day
     * instead reported a slipped day that was already at 0 as if it were the one that cost the
     * streak. When no day did either, the most recent day is as good a summary as any.
     */
    fun recapDayFor(outputs: List<Output>): Output {
        require(outputs.isNotEmpty()) { "no outputs to pick a recap day from" }
        return outputs.firstOrNull { it.summary.streakReset || it.summary.shieldConsumed } ?: outputs.last()
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
