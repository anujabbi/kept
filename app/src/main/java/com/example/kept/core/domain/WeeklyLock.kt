package com.example.kept.core.domain

// Weekly goals on the lock path (issue #165, story #175): the weekly lock screen's words, the
// must-do notification, the foreground notification's count, what Home counts, and the confirm
// for loosening a goal that holds the lock. All of it pure, so the screens and the service only
// draw and post what is decided here.

/** The weekly lock screen's copy (issue #165, Lock screen; mockup 3). Final; do not reword here. */
object WeeklyLockCopy {
    const val NBSP = WeeklyGoalCopy.NBSP

    const val CHIP = "Weekly goal"
    const val SECTION = WeeklyCardCopy.SECTION
    const val TODAY = WeeklyCardCopy.TODAY_ROW
    const val HINT = "Skip today and tomorrow locks too."

    /** The detail on a weekly session's row in the habits list, when habits hold the lock too. */
    const val ROW_DETAIL = "This week"

    const val LEGEND_DONE = "Done"
    const val LEGEND_SKIPPED = "Skipped"
    const val LEGEND_TODAY = "Today"
    const val LEGEND_COMING_UP = "Coming up"

    /** "1 day", "4 days". */
    fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

    /** "4 days left, 4 to go."; on a Saturday "1 day left, 1 to go." */
    fun headline(remaining: Int, needed: Int): String = "${days(remaining)} left, $needed to go."

    /**
     * The line under the headline. Once the week's target is out of reach it says so, and that
     * today still counts; otherwise how far the week is and what unlocks.
     */
    fun line(activity: String, done: Int, target: Int, canStillHitTarget: Boolean): String = when {
        !canStillHitTarget -> "You can't hit $target this week, but today still counts."
        done == 0 -> "No ${inSentence(activity)} yet this week. Do today's session to unlock."
        else -> "$done of $target done. Do today's session to unlock."
    }

    /** Beside "This week": "Exercise · 0 of 4". The count stays on one line. */
    fun summary(activity: String, done: Int, target: Int): String = "$activity · $done${NBSP}of${NBSP}$target"

    fun markDone(activity: String): String = "Mark today's ${inSentence(activity)} done"

    /**
     * An activity as it reads inside a sentence: "Exercise" becomes "exercise", "Tidy room"
     * "tidy room". Only the first letter is lowered, and not when the next one is a capital too,
     * so "TV-free night" and "Read Dune" keep theirs.
     */
    fun inSentence(activity: String): String {
        val a = activity.trim()
        if (a.isEmpty() || a.getOrNull(1)?.isUpperCase() == true) return a
        return a.replaceFirstChar { it.lowercase() }
    }
}

/**
 * Everything the weekly lock screen shows for the goal holding the lock, worked out from its
 * [WeeklyGoalRules.Week] so the screen only draws it.
 */
data class WeeklyLockScreen(
    val headline: String,
    val line: String,
    val summary: String,
    /** Sunday to Saturday. */
    val days: List<WeeklyCardDay>,
    /** The today row's title: the activity as the person wrote it. */
    val activity: String,
    /** "Skip today and tomorrow locks too.", or null on the one day it would be untrue. */
    val hint: String?,
    val button: String,
) {
    companion object {
        /**
         * [daysPerWeek] is the goal's full target, which decides the hint on a Saturday: tomorrow
         * is a new week, and only a 7-day goal locks on its Sunday.
         */
        fun of(activity: String, week: WeeklyGoalRules.Week, daysPerWeek: Int): WeeklyLockScreen = WeeklyLockScreen(
            headline = WeeklyLockCopy.headline(week.remaining, week.needed),
            line = WeeklyLockCopy.line(activity, week.done, week.target, week.canStillHitTarget),
            summary = WeeklyLockCopy.summary(activity, week.done, week.target),
            days = week.days.map(WeeklyCardDay::of),
            activity = activity,
            hint = WeeklyLockCopy.HINT.takeIf { week.remaining > 1 || daysPerWeek >= WeeklyGoalRules.MAX_DAYS },
            button = WeeklyLockCopy.markDone(activity),
        )
    }
}

/** Which of its faces the lock screen shows (issue #165, "One lock, many reasons"). */
enum class LockScreenKind {
    /** Night lockdown holds: its own screen, whatever else is undone (issue #164). */
    NIGHT,

    /** The habits list, with a "This week" row for each must-do session, and the gold open state. */
    HABITS,

    /** Only a weekly goal's must-do session holds the lock: the weekly variant. */
    WEEKLY,
    ;

    companion object {
        fun of(night: Boolean, habitsIncomplete: Boolean, weeklyMustDoIncomplete: Boolean): LockScreenKind = when {
            night -> NIGHT
            weeklyMustDoIncomplete && !habitsIncomplete -> WEEKLY
            else -> HABITS
        }
    }
}

/**
 * The must-do sessions that stand between the person and open apps today, as Home, the
 * foreground notification and the "Promise kept" moment count them. A must-do day's session
 * counts from the morning until the give-up time; after it the lock is over for the day, so
 * nothing is left to wait for ([pastDue]).
 */
object WeeklyHold {
    fun left(weeks: List<WeeklyGoalRules.Week>, pastDue: Boolean): Int = if (pastDue) 0 else weeks.count { it.isMustDay }

    /**
     * Every must-do session of today is logged, and before the give-up time (story #176): the
     * weekly half of "today's promise is kept". [loggedAt] has one entry for each goal whose
     * today counts: when its session was logged (epoch millis), or null. True with none.
     */
    fun allInTime(loggedAt: List<Long?>, dueMillis: Long): Boolean = loggedAt.all { it != null && it < dueMillis }

    /**
     * Must-do sessions today has lost (story #176): after the give-up time, each one not logged
     * before it. Until then nothing is lost; a session still to do is [left].
     */
    fun missed(loggedAt: List<Long?>, pastDue: Boolean, dueMillis: Long): Int =
        if (pastDue) loggedAt.count { it == null || it >= dueMillis } else 0
}

/**
 * What Home's sky, headline and Sprig count: the daily habits plus each must-do session still
 * holding ([WeeklyHold.left]). With none, it is the habits alone, as before weekly goals.
 *
 * A must-do session not logged before the give-up time ([WeeklyHold.missed]) stops holding, but
 * the day is missed (story #176): [keptOnTime] is false and [weeklyMissed] says why.
 */
data class HomeDay(val total: Int, val done: Int, val keptOnTime: Boolean, val weeklyMissed: Boolean = false) {
    companion object {
        fun of(habitsTotal: Int, habitsDone: Int, habitsAllDoneOnTime: Boolean, weeklyLeft: Int, weeklyMissed: Int = 0): HomeDay =
            HomeDay(habitsTotal + weeklyLeft, habitsDone, habitsAllDoneOnTime && weeklyLeft == 0 && weeklyMissed == 0, weeklyMissed > 0)
    }
}

/**
 * The must-do notification (issue #165, The mechanic): once per goal per day, at the lock start
 * or as soon as the must-do state appears later in the window.
 */
object MustDoNotice {
    /** "No exercise yet this week", or "1 of 4 exercise so far this week" once a session is logged. */
    fun title(activity: String, done: Int, target: Int): String {
        val a = WeeklyLockCopy.inSentence(activity)
        return if (done == 0) "No $a yet this week" else "$done of $target $a so far this week"
    }

    fun body(remaining: Int, needed: Int): String =
        "Only ${WeeklyLockCopy.days(remaining)} left and you need $needed. Your apps are locked until today's session is done."

    data class Goal(val id: Long, val activity: String, val week: WeeklyGoalRules.Week)

    /** [date] is the day the notification is for: what gets stored as the goal's once-a-day mark. */
    data class Post(val goalId: Long, val date: String, val title: String, val body: String)

    data class Decision(val post: List<Post>, val clear: List<Long>) {
        val nothing: Boolean get() = post.isEmpty() && clear.isEmpty()
    }

    /**
     * What to post and what to take down right now.
     *
     * A goal is posted when today is its must-do day, the lock window is open ([windowActive]),
     * and it has not been posted today: [notified] is the stored mark, goal id to ISO date, and
     * it is what keeps the notification to once a day across an undo, an edit and a restart of
     * the process. A break does not hold it back: the lock it explains comes back when the break
     * ends.
     *
     * A notification already in the shade ([showing], goal ids) comes down once what it says is
     * no longer true: the session is logged, the goal is changed or removed, or the give-up time
     * has ended the lock. It does not come back that day.
     */
    fun decide(
        onboardingDone: Boolean,
        windowActive: Boolean,
        goals: List<Goal>,
        notified: Map<Long, String>,
        showing: Set<Long>,
    ): Decision {
        val locking = if (onboardingDone && windowActive) goals.filter { it.week.isMustDay } else emptyList()
        val post = locking.filter { notified[it.id] != it.week.today.toString() }.map {
            Post(it.id, it.week.today.toString(), title(it.activity, it.week.done, it.week.target), body(it.week.remaining, it.week.needed))
        }
        val lockingIds = locking.map { it.id }.toSet()
        return Decision(post, showing.filter { it !in lockingIds }.sorted())
    }
}

/** The lock service's own notification (issue #165: it counts must-do sessions with the habits). */
object LockNotice {
    data class Input(
        val onboardingDone: Boolean,
        /** Night lockdown would hold right now, break or no break. */
        val night: Boolean,
        val nightUntilMinute: Int,
        /** When the running break ends, as a label ("3:30 pm"), or null when there is none. */
        val breakUntil: String?,
        val habitsTotal: Int,
        val habitsLeft: Int,
        val allDoneOnTime: Boolean,
        /** Must-do sessions still holding today ([WeeklyHold.left]). */
        val weeklyLeft: Int,
        /** Must-do sessions not logged before the give-up time ([WeeklyHold.missed]). */
        val weeklyMissed: Int = 0,
        val dailyLockActive: Boolean,
        val lockFromMinute: Int,
        val dueMinute: Int,
    )

    /** "1 habit and 1 weekly session left today", or either half alone. */
    fun left(habits: Int, weekly: Int): String {
        val parts = listOfNotNull(
            "$habits habit${if (habits == 1) "" else "s"}".takeIf { habits > 0 || weekly == 0 },
            "$weekly weekly session${if (weekly == 1) "" else "s"}".takeIf { weekly > 0 },
        )
        return parts.joinToString(" and ") + " left today"
    }

    /** Habits done in time, a must-do session not: "Promise kept." would be untrue (DECISIONS.md, #176). */
    const val WEEKLY_MISSED = "A weekly session wasn't logged in time, so today is missed."

    /** Title and text. Night lockdown is asked first: it holds whatever the day's promise says. */
    fun of(i: Input): Pair<String, String> = when {
        !i.onboardingDone -> "KEPT" to "Finish setup to start locking"
        i.night && i.breakUntil != null -> "Lock paused" to "Apps re-lock at ${i.breakUntil}"
        i.night -> NightNotice.TITLE to NightNotice.text(i.nightUntilMinute)
        i.weeklyLeft == 0 && i.habitsTotal == 0 -> "KEPT" to "No habits set for today"
        i.weeklyLeft == 0 && i.allDoneOnTime && i.weeklyMissed == 0 -> "All habits done" to "Apps are open. Promise kept."
        i.weeklyLeft == 0 && i.allDoneOnTime -> "All habits done" to WEEKLY_MISSED
        i.weeklyLeft == 0 && i.habitsLeft == 0 -> "All habits done" to "Ticked after the give-up time, so today is missed."
        i.breakUntil != null -> "Lock paused" to "Apps re-lock at ${i.breakUntil}"
        i.dailyLockActive -> left(i.habitsLeft, i.weeklyLeft) to "Apps locked until ${i.dueMinute.minuteOfDayLabel()}"
        else -> left(i.habitsLeft, i.weeklyLeft) to "Apps lock at ${i.lockFromMinute.minuteOfDayLabel()}"
    }
}

/**
 * Changing a weekly goal that is holding the lock (issue #165, Edge cases). Lowering its days a
 * week or deleting it can open the apps early, so it asks first through the lock-change confirm;
 * raising it or renaming it opens nothing and applies at once.
 */
object WeeklyGoalLockEdit {
    const val LOWER_WHILE_LOCKED = "Your apps are locked right now. Lowering this goal can open them early. Still lower it?"
    const val DELETE_WHILE_LOCKED = "Your apps are locked right now. Deleting this goal can open them early. Still delete it?"

    /** A goal holds the lock when today is its must-do day and the daily lock is in force. */
    fun holdsLock(dailyLockActive: Boolean, week: WeeklyGoalRules.Week): Boolean = dailyLockActive && week.isMustDay

    /** What a save from [savedDays] to [newDays] a week asks while the goal holds the lock; null asks nothing. */
    fun saveConfirm(savedDays: Int, newDays: Int): String? = if (newDays < savedDays) LOWER_WHILE_LOCKED else null

    /** What a save or a delete asks first; null when it applies at once. */
    fun confirm(holdsLock: Boolean, savedDays: Int, newDays: Int?): String? = when {
        !holdsLock -> null
        newDays == null -> DELETE_WHILE_LOCKED
        else -> saveConfirm(savedDays, newDays)
    }
}
