package com.example.kept.core.domain

/** Weekly goals in Settings: the row and the list (issue #165, story #173); they are made on the promise (#218). */
object WeeklyGoalCopy {
    const val NBSP = ' '

    // Settings → "Your promise"
    const val ROW = "Weekly goals"

    /** The row's value: how many goals there are, as the Habits row shows its count. */
    fun rowValue(n: Int): String = n.toString()

    // The list
    const val LIST_TITLE = "Weekly goals"
    const val ADD = "Add a weekly goal"

    /** Under the list's title: the setup screen's own line. */
    const val LIST_LINE = "For things you don't need to do every day."

    /** Under the button, at the limit: why a fourth cannot be added. */
    const val LIMIT_REACHED = "That's the limit: ${WeeklyGoalRules.MAX_GOALS} weekly goals. Remove one to add another."

    /** Under the button: "1 of 3 weekly goals", or [LIMIT_REACHED] at the limit. */
    fun count(n: Int): String =
        if (WeeklyGoalRules.canAdd(n)) "$n${NBSP}of${NBSP}${WeeklyGoalRules.MAX_GOALS}${NBSP}weekly${NBSP}goals" else LIMIT_REACHED

    /**
     * A row of the list: "Exercise · 30 min · 4 days a week", or "Exercise · 4 days a week" for a
     * goal with no [target] (one set before targets, story #205). The days part never wraps.
     */
    fun listRow(title: String, target: String?, days: Int): String =
        if (target == null) "$title · ${days(days)}" else "$title · $target · ${days(days)}"

    /**
     * A goal's target as it reads beside its cadence: "30 min", "10 pages", "1 time", "3 times";
     * null for a goal with none. As [HabitDraft.amount], except one time is "1 time", never
     * "Once a day", which would contradict "4 days a week". Never wraps.
     */
    fun target(value: Int?, unit: String?): String? = when {
        value == null || unit == null -> null
        value == 1 && unit == HabitDraft.TIMES -> "1${NBSP}time"
        else -> HabitDraft.amount(value, unit)
    }

    /** The "Add a weekly goal" row's line on Home. */
    const val SUBTITLE = "For things you don't need to do every day."
    const val CANCEL = "Cancel"

    /** "1 day a week", "4 days a week"; no-break spaces, so it never wraps. */
    fun days(n: Int): String = "$n${NBSP}${if (n == 1) "day" else "days"}${NBSP}a${NBSP}week"

    // Delete
    const val DELETE = "Delete goal"
    const val DELETE_TITLE = "Delete this weekly goal?"

    /** The confirm's line: what goes away. */
    fun deleteBody(name: String, days: Int): String = "$name, ${days(days)} will be removed from your weekly goals."
}
