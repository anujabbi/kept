package com.example.kept.core.domain

/** Weekly goals in Settings: the row, the list and the setup screen (issue #165, Setup; story #173). */
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

    /** A row of the list: "Exercise · 4 days a week". The days part never wraps. */
    fun listRow(title: String, days: Int): String = "$title · ${days(days)}"

    // Setup
    const val TITLE = "Weekly goal"
    const val SUBTITLE = "For things you don't need to do every day."
    const val NAME_LABEL = "What will you do?"
    const val CUSTOM = "+ Custom"
    const val DAYS_LABEL = "How many days a week?"
    const val SEVEN_HINT = "7 days a week locks every day until it's done."
    const val SUMMARY_BODY = "Week runs Sunday to Saturday. If you fall behind, KEPT locks your phone on the day you can't skip anymore."
    const val SAVE = "Save goal"
    const val CANCEL = "Cancel"

    /** "1 day a week", "4 days a week"; no-break spaces, so it never wraps. */
    fun days(n: Int): String = "$n${NBSP}${if (n == 1) "day" else "days"}${NBSP}a${NBSP}week"

    /** Beside "How many days a week?": "4 of 7". */
    fun daysCount(n: Int): String = "$n${NBSP}of${NBSP}${WeeklyGoalRules.MAX_DAYS}"

    /** The summary card's title: "Exercise, 4 days a week"; the days alone until there is an activity. */
    fun summary(name: String, days: Int): String = if (name.isEmpty()) days(days) else "$name, ${days(days)}"

    // Delete
    const val DELETE = "Delete goal"
    const val DELETE_TITLE = "Delete this weekly goal?"

    /** The confirm's line: what goes away. */
    fun deleteBody(name: String, days: Int): String = "${summary(name, days)} will be removed from your weekly goals."
}

/**
 * What the weekly goal setup screen holds while the person fills it in: an activity and how many
 * days a week. Every change returns a new draft, so the rules are tested here, not in the screen.
 */
data class WeeklyGoalDraft(val title: String = "", val days: Int = WeeklyGoalRules.DEFAULT_DAYS) {
    /** The activity as it will be saved: trimmed, at most [HabitDraft.MAX_TITLE] characters. */
    val name: String get() = title.trim()

    /** "Save goal" is enabled once the activity has text; blank or spaces alone is no activity. */
    val canSave: Boolean get() = name.isNotEmpty()

    /** The field stops at [HabitDraft.MAX_TITLE] characters, typed or pasted. */
    fun withTitle(t: String): WeeklyGoalDraft = copy(title = t.take(HabitDraft.MAX_TITLE))

    /** A quick pick fills the activity. */
    fun pick(activity: String): WeeklyGoalDraft = withTitle(activity)

    /** A day chip; anything outside 1 to 7 is pulled back in. */
    fun withDays(n: Int): WeeklyGoalDraft = copy(days = n.coerceIn(WeeklyGoalRules.MIN_DAYS, WeeklyGoalRules.MAX_DAYS))

    /** Which quick pick is lit: the one the activity matches exactly. */
    fun isPicked(activity: String): Boolean = name == activity

    /** At 7 the screen warns that every day locks. */
    val showsSevenHint: Boolean get() = days == WeeklyGoalRules.MAX_DAYS

    val daysCount: String get() = WeeklyGoalCopy.daysCount(days)
    val summary: String get() = WeeklyGoalCopy.summary(name, days)

    /** The icon the goal will get ([HabitIconRules]). */
    val iconKey: String get() = HabitIconRules.forName(name)

    companion object {
        /** The quick picks under the field, in order; "+ Custom" follows them. */
        val QUICK_PICKS = listOf("Exercise", "Read", "Practice", "Study", "Tidy room")

        val DAY_CHOICES: List<Int> = (WeeklyGoalRules.MIN_DAYS..WeeklyGoalRules.MAX_DAYS).toList()
    }
}

/**
 * Where the seven day chips sit. The design has them 6dp apart between the page's 24dp sides,
 * which leaves each under 48dp wide on every phone; here each chip's touch target is its whole
 * cell, the cells touch, and the row may reach past the 24dp sides on a narrow screen so no cell
 * is under 48dp while the screen has room for seven.
 */
object WeeklyGoalSetupLayout {
    const val SIDE_DP = 24
    const val GAP_DP = 6
    const val MIN_CELL_DP = 48

    /**
     * The row's side padding at [screenWidthDp]: the drawn chips start at 24dp when seven 48dp
     * cells fit. When they do not, the sides give way, and by 1dp more than the sum asks, so a
     * cell rounded down to whole pixels is still 48dp.
     */
    fun dayRowSideDp(screenWidthDp: Int): Int {
        val designSide = SIDE_DP - GAP_DP / 2
        val needed = MIN_CELL_DP * WeeklyGoalDraft.DAY_CHOICES.size
        return minOf(designSide, ((screenWidthDp - needed) / 2 - 1).coerceAtLeast(0))
    }

    /** Sprig sits beside the title only below this font scale; above it the title needs the width. */
    const val SPRIG_MAX_FONT_SCALE = 1.5f
    const val SPRIG_DP = 84
    fun showsSprig(fontScale: Float): Boolean = fontScale < SPRIG_MAX_FONT_SCALE
}
