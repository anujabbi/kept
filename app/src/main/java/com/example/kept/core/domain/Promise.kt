package com.example.kept.core.domain

/**
 * The promise screen (#218): one full-screen sentence with tappable blanks, "I will [exercise]
 * for [30] [minutes], [4 days a week].", used by onboarding, Settings → add or edit a habit, and
 * Settings → add or edit a weekly goal. Every rule here is pure; the screen only draws it.
 */
object PromiseCopy {
    const val NBSP = '\u00A0'

    const val LABEL = "Your promise"
    const val CANCEL = "Cancel"
    const val DONE = "Done"
    const val I_WILL = "I will"
    const val FOR = "for"

    /** The last activity on the list; it turns the blank into a text field. */
    const val SOMETHING_ELSE = "Something else…"
    const val TYPE_YOUR_OWN = "type your own"

    /** What the empty activity field shows. */
    const val PLACEHOLDER = "something"

    /** The one line under the sentence, for a weekly promise only. */
    const val WEEK_LINE = "Week starts on Sunday."

    /**
     * The days list's line when a saved habit or goal is edited: changing between every day and
     * some days a week after saving is out of scope (#218), so the other kind is off.
     */
    const val STAYS_DAILY = "A saved habit stays every day."
    const val STAYS_WEEKLY = "A saved weekly goal stays weekly."

    // What TalkBack calls each blank.
    const val ACTIVITY = "Activity"
    const val AMOUNT = "Amount"
    const val UNIT = "Unit"
    const val DAYS = "How often"

    /** The unit as the sentence reads it: "minutes", "pages", "times", "glasses"; singular for one. */
    fun unitWord(unit: String, amount: Int): String = when (unit) {
        HabitDraft.MIN -> if (amount == 1) "minute" else "minutes"
        HabitDraft.PAGES -> if (amount == 1) "page" else "pages"
        HabitDraft.TIMES -> if (amount == 1) "time" else "times"
        HabitDraft.GLASSES -> if (amount == 1) "glass" else "glasses"
        else -> unit
    }

    /** The units list: "minutes", "pages", "times", "glasses". */
    fun unitOption(unit: String): String = unitWord(unit, 2)

    /** The days blank: "every day", "4 days a week", "1 day a week". */
    fun days(days: Int?): String = if (days == null) "every day" else WeeklyGoalCopy.days(days)

    /** The days list: "Every day", then "6 days a week" down to "1 day a week". */
    fun daysOption(days: Int?): String = if (days == null) "Every day" else WeeklyGoalCopy.days(days)
}

/** One activity on the promise's list: what it fills in. Tidy room is proved with a photo, so it has no amount. */
data class PromisePreset(val title: String, val target: Int, val unit: String, val photo: Boolean = false)

/** The four blanks of the sentence. */
enum class PromiseBlank(val label: String) {
    ACTIVITY(PromiseCopy.ACTIVITY), AMOUNT(PromiseCopy.AMOUNT), UNIT(PromiseCopy.UNIT), DAYS(PromiseCopy.DAYS)
}

/** A piece of the sentence: plain words, or a blank with its value and the punctuation after it. */
sealed interface PromisePart {
    data class Words(val text: String) : PromisePart
    data class Blank(val blank: PromiseBlank, val value: String, val after: String = "") : PromisePart
}

/**
 * What the promise holds while the person fills it in. [days] null is every day (a daily habit);
 * 1 to 6 (7 only for a goal saved before #218) is a weekly goal. A [photo] promise (Tidy room,
 * or a saved photo habit) has no amount or unit blank. [custom] is "Something else…": the
 * activity blank is a text field.
 */
data class PromiseDraft(
    val activity: String,
    val target: Int = HabitDraft.UNIT_START.getValue(HabitDraft.MIN),
    val unit: String = HabitDraft.MIN,
    val days: Int? = null,
    val photo: Boolean = false,
    val custom: Boolean = false,
) {
    /**
     * The activity as it will be saved: trimmed, at most [HabitDraft.MAX_TITLE] characters, and
     * starting with a capital as every habit does ("walk the dog" is saved as "Walk the dog").
     */
    val name: String get() = activity.trim().replaceFirstChar { it.uppercaseChar() }

    /** Done needs an activity. */
    val canSave: Boolean get() = name.isNotEmpty()

    val weekly: Boolean get() = days != null

    /** The sentence, blank by blank (#218): the joining word follows the unit, and a photo promise has no amount. */
    val parts: List<PromisePart>
        get() = buildList {
            add(PromisePart.Words(PromiseCopy.I_WILL))
            val activityText = if (name.isEmpty()) PromiseCopy.PLACEHOLDER else WeeklyLockCopy.inSentence(name)
            if (photo) {
                add(PromisePart.Blank(PromiseBlank.ACTIVITY, activityText, ","))
            } else {
                add(PromisePart.Blank(PromiseBlank.ACTIVITY, activityText))
                if (unit == HabitDraft.MIN) add(PromisePart.Words(PromiseCopy.FOR))
                add(PromisePart.Blank(PromiseBlank.AMOUNT, target.toString()))
                add(PromisePart.Blank(PromiseBlank.UNIT, PromiseCopy.unitWord(unit, target), ","))
            }
            add(PromisePart.Blank(PromiseBlank.DAYS, PromiseCopy.days(days), "."))
        }

    /** The sentence as one line: "I will exercise for 30 minutes, 4 days a week." */
    val sentence: String
        get() = parts.joinToString(" ") {
            when (it) {
                is PromisePart.Words -> it.text
                is PromisePart.Blank -> it.value + it.after
            }
        }

    /** The line under the sentence: the week's start for a weekly promise, nothing for a daily habit. */
    val line: String? get() = if (weekly) PromiseCopy.WEEK_LINE else null

    /** The activity list's ticked row: the preset this is, or none for "Something else…". */
    val preset: PromisePreset? get() = if (custom) null else PromiseRules.PRESETS.firstOrNull { it.title == name }

    /** A preset fills the activity, its amount and unit, and whether a photo proves it. */
    fun pick(p: PromisePreset): PromiseDraft =
        copy(activity = p.title, target = p.target, unit = p.unit, photo = p.photo, custom = false)

    /** "Something else…": an empty text field, no photo; the amount and the days stay. */
    fun somethingElse(): PromiseDraft {
        val amount = if (photo) copy(target = HabitDraft.UNIT_START.getValue(HabitDraft.MIN), unit = HabitDraft.MIN) else this
        return amount.copy(activity = "", photo = false, custom = true)
    }

    /** The text field stops at [HabitDraft.MAX_TITLE] characters, typed or pasted. */
    fun withActivity(text: String): PromiseDraft = copy(activity = text.take(HabitDraft.MAX_TITLE))

    fun withTarget(n: Int): PromiseDraft = copy(target = n.coerceIn(1, HabitDraft.MAX_TARGET))

    /** A new unit starts at its own value (minutes 20, pages 10, times 1, glasses 8). */
    fun withUnit(u: String): PromiseDraft = if (u == unit) this else copy(unit = u, target = HabitDraft.UNIT_START[u] ?: target)

    fun withDays(d: Int?): PromiseDraft = copy(days = d?.coerceIn(WeeklyGoalRules.MIN_DAYS, WeeklyGoalRules.MAX_DAYS))

    /** The amounts the list offers for this unit, with the current one if it is not among them. */
    val amounts: List<Int> get() = PromiseRules.amounts(unit, target)

    /** The days the list offers: every day, then 6 down to 1, and 7 only for a goal that has it. */
    val dayChoices: List<Int?> get() = PromiseRules.dayChoices(days)

    /** The icon the habit or goal will get ([HabitIconRules]). */
    val iconKey: String get() = HabitIconRules.forName(name)

    /** As a daily habit: what Settings and onboarding save for every day. */
    fun habit(): HabitDraft = HabitDraft(title = name, target = target, unit = unit, photo = photo)

    /** A weekly goal's target: none for a photo promise, as weekly goals have no photo proof (#205). */
    val goalTarget: Int? get() = if (photo) null else target
    val goalUnit: String? get() = if (photo) null else unit
}

object PromiseRules {
    /** The activity list, in order; "Something else…" follows. */
    val PRESETS = listOf(
        PromisePreset("Exercise", 30, HabitDraft.MIN),
        PromisePreset("Read", 10, HabitDraft.PAGES),
        PromisePreset("Practice", 20, HabitDraft.MIN),
        PromisePreset("Study", 45, HabitDraft.MIN),
        PromisePreset("Tidy room", 1, HabitDraft.PHOTO_UNIT, photo = true),
    )

    val UNITS = HabitDraft.UNITS

    /** The amount lists (#218): short, and fitted to the unit. */
    private val AMOUNTS = mapOf(
        HabitDraft.MIN to listOf(5, 10, 15, 20, 30, 45, 60, 90),
        HabitDraft.PAGES to listOf(5, 10, 20, 30, 50),
        HabitDraft.TIMES to (1..10).toList(),
        HabitDraft.GLASSES to (1..10).toList(),
    )

    /** [unit]'s list, with [current] added in order when a saved habit has an amount the list lacks. */
    fun amounts(unit: String, current: Int): List<Int> {
        val base = AMOUNTS[unit] ?: AMOUNTS.getValue(HabitDraft.MIN)
        return if (current in base) base else (base + current).sorted()
    }

    /** Every day first, then 6 down to 1; a goal saved at 7 days a week keeps 7 on its list. */
    fun dayChoices(current: Int?): List<Int?> =
        listOf<Int?>(null) + (if (current == WeeklyGoalRules.MAX_DAYS) listOf(WeeklyGoalRules.MAX_DAYS) else emptyList()) + (6 downTo 1).toList()

    /** A new promise starts on the first activity not already kept, or on an empty "Something else…". */
    fun fresh(taken: Collection<String>, days: Int?): PromiseDraft {
        val p = PRESETS.firstOrNull { pr -> taken.none { it.equals(pr.title, ignoreCase = true) } }
        val base = PromiseDraft("", days = days)
        return if (p == null) base.somethingElse() else base.pick(p)
    }

    /**
     * A saved daily habit, filled in. A legacy one-tap habit (unit "check-in") reads as once: 1 time.
     * A name that is not a preset is "Something else…", so its blank is the text field.
     */
    fun ofHabit(title: String, proof: ProofType, target: Int, unit: String): PromiseDraft {
        val photo = proof == ProofType.PHOTO
        val (t, u) = when {
            photo -> HabitDraft.UNIT_START.getValue(HabitDraft.MIN) to HabitDraft.MIN
            unit !in HabitDraft.UNITS -> 1 to HabitDraft.TIMES
            else -> target.coerceIn(1, HabitDraft.MAX_TARGET) to unit
        }
        return PromiseDraft(title, t, u, days = null, photo = photo, custom = PRESETS.none { it.title == title })
    }

    /**
     * A saved weekly goal, filled in. One with no target is a photo goal (Tidy room) or one set
     * before targets (#206): the latter starts at its preset's target or 20 minutes, and saving
     * writes it, as before.
     */
    fun ofGoal(title: String, days: Int, target: Int?, unit: String?): PromiseDraft {
        val preset = PRESETS.firstOrNull { it.title == title }
        val custom = preset == null
        return when {
            target != null && unit != null && unit in HabitDraft.UNITS -> PromiseDraft(title, target.coerceIn(1, HabitDraft.MAX_TARGET), unit, days, custom = custom)
            preset?.photo == true -> PromiseDraft(title, days = days, photo = true)
            preset != null -> PromiseDraft(title, preset.target, preset.unit, days)
            else -> PromiseDraft(title, days = days, custom = true)
        }
    }
}

/**
 * What the days list lets the person pick, and the line under it when something is off: at three
 * weekly goals the weekly choices are off ([WeeklyGoalCopy.LIMIT_REACHED]); at ten every-day
 * habits "Every day" is ([HabitLimits.LIMIT_REACHED]); a saved habit or goal keeps its kind.
 */
data class PromiseLimits(val everyDay: Boolean = true, val weekly: Boolean = true, val line: String? = null) {
    fun allows(days: Int?): Boolean = if (days == null) everyDay else weekly

    /** [d] moved onto a choice it may have: a new promise that opens on an off choice takes the other kind. */
    fun fit(d: PromiseDraft): PromiseDraft = when {
        allows(d.days) -> d
        d.days == null -> d.withDays(WeeklyGoalRules.DEFAULT_DAYS)
        else -> d.withDays(null)
    }

    companion object {
        /** A new promise beside [daily] every-day habits and [weekly] weekly goals (the one being made not counted). */
        fun new(daily: Int, weekly: Int): PromiseLimits {
            val canWeekly = WeeklyGoalRules.canAdd(weekly)
            val canDaily = HabitLimits.canAdd(daily)
            val line = when {
                !canWeekly -> WeeklyGoalCopy.LIMIT_REACHED
                !canDaily -> HabitLimits.LIMIT_REACHED
                else -> null
            }
            return PromiseLimits(canDaily, canWeekly, line)
        }

        /** A saved daily habit: it stays every day. */
        val SAVED_HABIT = PromiseLimits(everyDay = true, weekly = false, line = PromiseCopy.STAYS_DAILY)

        /** A saved weekly goal: it stays weekly. */
        val SAVED_GOAL = PromiseLimits(everyDay = false, weekly = true, line = PromiseCopy.STAYS_WEEKLY)
    }
}
