package com.example.kept.core.domain

import java.time.LocalDate

/**
 * Your promises (#236): one list in Settings for every daily habit and weekly goal, styled like
 * onboarding's habit step, and the rules for moving one between every day and some days a week.
 */
object PromiseListCopy {
    /** The Settings row, and the page's title. */
    const val TITLE = "Your promises"
    const val LINE = "Tap one to change it, even how often."
    const val ADD = "Add a habit"

    /** Where "Add a habit" was, when both sides are full. */
    const val FULL =
        "That's the limit: ${HabitLimits.MAX_HABITS} habits and ${WeeklyGoalRules.MAX_GOALS} weekly goals. Remove one to add another."

    /** What TalkBack says a card does. */
    const val CHANGE = "Change"

    /** The Settings row's value: every promise. */
    fun rowValue(daily: Int, weekly: Int): String = (daily + weekly).toString()

    /** The Settings row's line: "4 every day, 2 weekly". */
    fun rowHint(daily: Int, weekly: Int): String = "$daily every day, $weekly weekly"

    /** Under the list, worded as onboarding's footer: "4 daily habits · 2 weekly goals". */
    fun count(daily: Int, weekly: Int): String = listOfNotNull(
        daily.takeIf { it > 0 }?.let { "$it daily ${if (it == 1) "habit" else "habits"}" },
        weekly.takeIf { it > 0 }?.let { "$it weekly ${if (it == 1) "goal" else "goals"}" },
    ).joinToString(" · ")

    /** A daily habit's amount line: "10 pages", "30 min", "Once a day", "Photo check-in", "Tap when done". */
    fun habitAmount(photo: Boolean, target: Int, unit: String): String = when {
        photo -> PHOTO
        unit.isBlank() || unit == HabitDraft.CHECK_IN -> TAP
        else -> HabitDraft.amount(target, unit)
    }

    /**
     * A weekly goal's amount line: its target ("30 min", "1 time"); a goal with none is Tidy room's
     * "Photo check-in" or "Tap when done".
     */
    fun goalAmount(title: String, target: Int?, unit: String?): String =
        WeeklyGoalCopy.target(target, unit) ?: if (PromiseRules.PRESETS.any { it.photo && it.title == title }) PHOTO else TAP

    private const val PHOTO = "Photo check-in"
    private const val TAP = "Tap when done"
}

/**
 * One card of Your promises: a daily habit ([days] null) or a weekly goal, with the place it holds
 * in the person's own [order], which daily habits and weekly goals share.
 */
data class PromiseItem(
    val id: Long,
    val days: Int?,
    val title: String,
    val iconKey: String,
    val amount: String,
    val order: Int,
) {
    val weekly: Boolean get() = days != null

    /** Unique across both kinds, for list keys and test tags: "habit_4", "goal_2". */
    val key: String get() = "${if (weekly) "goal" else "habit"}_$id"

    /** The how-often chip: "Every day", "4 days a week". */
    val chip: String get() = OnboardingCopy.cadence(days)
}

object PromiseList {
    /** The person's own order; a daily habit before a weekly goal at the same place, then oldest first. */
    fun ordered(items: List<PromiseItem>): List<PromiseItem> =
        items.sortedWith(compareBy<PromiseItem> { it.order }.thenBy { it.weekly }.thenBy { it.id })

    /** "Add a habit" offers a row while either side has room; the promise greys out the full side. */
    fun canAdd(daily: Int, weekly: Int): Boolean = HabitLimits.canAdd(daily) || WeeklyGoalRules.canAdd(weekly)
}

/**
 * Moving a saved promise between every day and some days a week (#236). Nothing is deleted: the
 * old row is set inactive, as removing it does, so its past days stay in History, and the new row
 * takes its place in the list.
 *
 * Every day → N days a week: the goal counts from the day the habit was set (or today, if that is
 * later), so its first week is not cut short, and the days done earlier this week carry over as
 * logged sessions (owner's decision). Mon and Tue done, switched on Wednesday to 4 days a week,
 * is 2 of 4.
 *
 * N days a week → every day: a session logged today makes today done.
 */
object PromiseSwitch {
    /** The lock-change confirm's question for a switch that would open apps today. */
    const val TO_WEEKLY_WHILE_LOCKED =
        "Your apps are locked right now. Making this habit weekly can open them early. Still change it?"

    /** The day the new goal counts from: the habit's own start, never later than [today]. */
    fun goalStart(habitStart: LocalDate, today: LocalDate): LocalDate = minOf(habitStart, today)

    /** Of the days the habit was done, those in this week up to [today]: the sessions the goal starts with. */
    fun carried(done: Collection<LocalDate>, today: LocalDate): Set<LocalDate> {
        val start = WeeklyGoalRules.weekStart(today)
        return done.filter { !it.isBefore(start) && !it.isAfter(today) }.toSet()
    }

    /** The new goal's week right after the switch. */
    fun weekAfter(days: Int, habitStart: LocalDate, done: Collection<LocalDate>, today: LocalDate): WeeklyGoalRules.Week =
        WeeklyGoalRules.week(WeeklyGoalRules.Goal(days, goalStart(habitStart, today)), carried(done, today), today)

    /**
     * Whether making a habit weekly can open apps today, while the daily lock holds: the habit is
     * not done today, so it was holding the lock, and today is not a must-do day for the new goal,
     * so the goal will not hold it.
     */
    fun opensLock(doneToday: Boolean, after: WeeklyGoalRules.Week): Boolean = !doneToday && !after.isMustDay
}
