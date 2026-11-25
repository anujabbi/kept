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

    /**
     * The summary card's title: "Exercise, 30 min, 4 days a week", "Exercise, 4 days a week" with
     * no [target]; the days alone until there is an activity.
     */
    fun summary(name: String, target: String?, days: Int): String = when {
        name.isEmpty() -> days(days)
        target == null -> "$name, ${days(days)}"
        else -> "$name, $target, ${days(days)}"
    }

    // Delete
    const val DELETE = "Delete goal"
    const val DELETE_TITLE = "Delete this weekly goal?"

    /** The confirm's line: what goes away. */
    fun deleteBody(name: String, days: Int): String = "${summary(name, null, days)} will be removed from your weekly goals."
}

/**
 * What the weekly goal setup screen holds while the person fills it in: an activity, how much
 * each session is (a [target] in a [unit], as a habit's; story #205) and how many days a week.
 * Every change returns a new draft, so the rules are tested here, not in the screen.
 *
 * A draft always has a target, so a goal saved from it always gets one; only goals set before
 * targets have none ([of]). The amount and its steps are [HabitDraft]'s.
 */
data class WeeklyGoalDraft(
    val title: String = "",
    val days: Int = WeeklyGoalRules.DEFAULT_DAYS,
    val target: Int = HabitDraft.UNIT_START.getValue(HabitDraft.MIN),
    val unit: String = HabitDraft.MIN,
) {
    /** The activity as it will be saved: trimmed, at most [HabitDraft.MAX_TITLE] characters. */
    val name: String get() = title.trim()

    /** "Save goal" is enabled once the activity has text; blank or spaces alone is no activity. */
    val canSave: Boolean get() = name.isNotEmpty()

    /** The field stops at [HabitDraft.MAX_TITLE] characters, typed or pasted. */
    fun withTitle(t: String): WeeklyGoalDraft = copy(title = t.take(HabitDraft.MAX_TITLE))

    /** A quick pick fills the activity, and the target too when it is a habit quick pick (Exercise 30 min). */
    fun pick(activity: String): WeeklyGoalDraft {
        val preset = HabitDraft.PRESETS.firstOrNull { it.title == activity }
        val named = withTitle(activity)
        return if (preset == null) named else named.copy(target = preset.target, unit = preset.unit)
    }

    /** The unit chips show for an activity that is not a habit quick pick, as on Add a habit. */
    val showsUnits: Boolean get() = name.isNotEmpty() && HabitDraft.PRESETS.none { it.title == name }

    /** What the stepper reads: "30 min", "1 time". */
    val amount: String get() = WeeklyGoalCopy.target(target, unit)!!

    /** The stepper's step: 5 for minutes, 1 for everything else. */
    val step: Int get() = HabitDraft.step(unit)
    val canLess: Boolean get() = target > step
    val canMore: Boolean get() = target + step <= HabitDraft.MAX_TARGET

    fun less(): WeeklyGoalDraft = copy(target = maxOf(step, target - step))
    fun more(): WeeklyGoalDraft = if (canMore) copy(target = target + step) else this

    /** A unit chip sets the unit and its start value (min 20, pages 10, times 1, glasses 8). */
    fun withUnit(u: String): WeeklyGoalDraft = copy(unit = u, target = HabitDraft.UNIT_START[u] ?: target)

    /** A day chip; anything outside 1 to 7 is pulled back in. */
    fun withDays(n: Int): WeeklyGoalDraft = copy(days = n.coerceIn(WeeklyGoalRules.MIN_DAYS, WeeklyGoalRules.MAX_DAYS))

    /** Which quick pick is lit: the one the activity matches exactly. */
    fun isPicked(activity: String): Boolean = name == activity

    /** At 7 the screen warns that every day locks. */
    val showsSevenHint: Boolean get() = days == WeeklyGoalRules.MAX_DAYS

    val daysCount: String get() = WeeklyGoalCopy.daysCount(days)
    val summary: String get() = WeeklyGoalCopy.summary(name, amount, days)

    /** The icon the goal will get ([HabitIconRules]). */
    val iconKey: String get() = HabitIconRules.forName(name)

    companion object {
        /** The quick picks under the field, in order; "+ Custom" follows them. */
        val QUICK_PICKS = listOf("Exercise", "Read", "Practice", "Study", "Tidy room")

        val DAY_CHOICES: List<Int> = (WeeklyGoalRules.MIN_DAYS..WeeklyGoalRules.MAX_DAYS).toList()

        /**
         * The draft for a saved goal. One with no target (set before targets) starts at the
         * default for its name, its habit quick pick's or 20 min, and saving writes it: that is
         * how editing adds one.
         */
        fun of(title: String, days: Int, target: Int?, unit: String?): WeeklyGoalDraft =
            if (target == null || unit == null) WeeklyGoalDraft(days = days).pick(title)
            else WeeklyGoalDraft(title, days, target.coerceIn(1, HabitDraft.MAX_TARGET), unit)
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
