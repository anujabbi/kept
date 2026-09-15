package com.example.kept.core.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The three rungs of the reminder ladder. The string is the PostHog `kind` property. */
enum class ReminderKind(val eventValue: String) {
    /** At the lock-window start: "2 things today". */
    MORNING("morning"),

    /** Two hours before the give-up time, floored so a short window still leaves room. */
    BEFORE_DUE("before_due"),

    /** Half an hour before the give-up time. */
    LAST_CALL("last_call"),
}

/**
 * When each reminder fires (issue #9).
 *
 * Every time is derived from [GiveUpTime.instantFor], never from minute-of-day arithmetic. The old
 * scheduler computed `(dueMinute - 120).coerceAtLeast(lockFromMinute + 30)`, which on a window that
 * wraps midnight (22:00 -> 06:00) resolves to 22:30 — thirty minutes into an eight-hour window,
 * seven and a half hours before the deadline it claimed to be two hours from.
 */
object ReminderLadder {
    /** No reminder lands in the first half hour of the window; the morning one just opened it. */
    val FLOOR_AFTER_START: Duration = Duration.ofMinutes(30)
    val BEFORE_DUE_LEAD: Duration = Duration.ofHours(2)
    val LAST_CALL_LEAD: Duration = Duration.ofMinutes(30)

    data class Step(
        val kind: ReminderKind,
        val at: Instant,
        /** Real minutes between [at] and the give-up moment. The copy is phrased from this. */
        val minutesBeforeDue: Int,
    )

    /**
     * The ladder for the day that starts on [date], earliest first. A step is dropped when the
     * window has no room for it: a step at or after the give-up moment is pointless, and two steps
     * at the same instant would be two notifications in the same second, so a later rung only
     * survives if it is strictly after the one before it.
     */
    fun stepsFor(date: LocalDate, lockFromMinute: Int, dueMinute: Int, zone: ZoneId): List<Step> {
        val start = date.atTime(lockFromMinute / 60, lockFromMinute % 60).atZone(zone).toInstant()
        val due = GiveUpTime.instantFor(date, lockFromMinute, dueMinute, zone)
        val floor = start.plus(FLOOR_AFTER_START)
        val candidates = listOf(
            ReminderKind.MORNING to start,
            ReminderKind.BEFORE_DUE to maxOf(due.minus(BEFORE_DUE_LEAD), floor),
            ReminderKind.LAST_CALL to maxOf(due.minus(LAST_CALL_LEAD), floor),
        )
        val kept = mutableListOf<Step>()
        for ((kind, at) in candidates) {
            if (!at.isBefore(due)) continue
            if (kept.isNotEmpty() && !at.isAfter(kept.last().at)) continue
            kept += Step(kind, at, Duration.between(at, due).toMinutes().toInt())
        }
        return kept
    }

    /**
     * The next firing of [kind] strictly after [now], or null when the window has no room for that
     * rung at all. Yesterday is considered too, because a wrapping window's later rungs fall on the
     * morning after the day they belong to.
     */
    fun nextFiring(
        kind: ReminderKind,
        now: Instant,
        today: LocalDate,
        lockFromMinute: Int,
        dueMinute: Int,
        zone: ZoneId,
    ): Step? = listOf(today.minusDays(1), today, today.plusDays(1))
        .asSequence()
        .flatMap { stepsFor(it, lockFromMinute, dueMinute, zone).asSequence() }
        .filter { it.kind == kind && it.at.isAfter(now) }
        .minByOrNull { it.at }

    /**
     * The give-up moment the user is currently racing. On a wrapping window at 02:00 that is this
     * morning's 06:00 — the deadline of the day that started last night — not tomorrow's, so the
     * copy says "4 hours left" rather than "28 hours left".
     */
    fun deadlineAhead(now: Instant, today: LocalDate, lockFromMinute: Int, dueMinute: Int, zone: ZoneId): Instant =
        listOf(today.minusDays(1), today, today.plusDays(1))
            .map { GiveUpTime.instantFor(it, lockFromMinute, dueMinute, zone) }
            .firstOrNull { it.isAfter(now) }
            ?: GiveUpTime.instantFor(today, lockFromMinute, dueMinute, zone)
}

/** Reminder wording. Pure so the copy is testable without a notification manager. */
object ReminderCopy {

    /** "30 minutes", "2 hours", "1h 45m". */
    fun duration(minutes: Int): String {
        val m = minutes.coerceAtLeast(1)
        val hours = m / 60
        val rest = m % 60
        return when {
            hours == 0 -> if (m == 1) "1 minute" else "$m minutes"
            rest == 0 -> if (hours == 1) "1 hour" else "$hours hours"
            else -> "${hours}h ${rest}m"
        }
    }

    fun title(kind: ReminderKind, remaining: Int, minutesBeforeDue: Int, streakDays: Int): String = when (kind) {
        ReminderKind.MORNING -> "$remaining thing${plural(remaining)} today"
        else -> "${duration(minutesBeforeDue)} left. " +
            if (streakDays > 0) "$streakDays-day streak on the line." else "Sprig is waiting."
    }

    fun body(kind: ReminderKind, remaining: Int, dueMinute: Int): String = when (kind) {
        ReminderKind.MORNING -> "Apps lock until they're done. You have until ${dueMinute.minuteOfDayLabel()}."
        else -> "$remaining habit${plural(remaining)} to go. Apps stay locked until then."
    }

    /** One habit needs no name on its button; several do. */
    fun actionLabel(habitTitle: String, single: Boolean): String =
        if (single) "Mark done" else "Done: $habitTitle"

    private fun plural(n: Int) = if (n == 1) "" else "s"
}

/**
 * Which habits get a "Mark done" button. Android draws at most three notification actions, so a
 * fourth habit costs a slot: the first two keep their buttons and the third becomes "Open KEPT".
 *
 * **A photo habit never gets one (issue #9).** Its whole point is that the tick costs a photo; a
 * notification button would complete it with no proof at all, from the lock screen, which is a
 * strictly easier path than the one the app offers. Those habits send the user into KEPT instead,
 * where the camera is, so a reminder listing any of them always spends a slot on "Open KEPT".
 */
object ReminderActions {
    const val MAX_ACTIONS = 3

    data class Plan(val markDone: List<Long>, val openApp: Boolean)

    /**
     * [markDoneEligibleIds] are the MANUAL habits still outstanding, in display order.
     * [hasProofRequiredHabits] is true when at least one outstanding habit needs a photo.
     */
    fun plan(markDoneEligibleIds: List<Long>, hasProofRequiredHabits: Boolean = false): Plan {
        // "Open KEPT" is mandatory when a photo habit is outstanding: without it the reminder would
        // offer no way to finish the day at all.
        val slots = if (hasProofRequiredHabits) MAX_ACTIONS - 1 else MAX_ACTIONS
        return if (markDoneEligibleIds.size <= slots) {
            Plan(markDoneEligibleIds, openApp = hasProofRequiredHabits)
        } else {
            Plan(markDoneEligibleIds.take(MAX_ACTIONS - 1), openApp = true)
        }
    }
}

/**
 * Whether a reminder should appear at all, and what it says (issue #9).
 *
 * Pure, so every skip rule is decidable without a notification manager, a DataStore or a database.
 * `ReminderPoster` is then only the Android adapter that gathers the inputs and draws the result.
 */
object ReminderPlan {
    /**
     * A habit still outstanding when the reminder fires. [proof] decides whether it may be ticked
     * from the shade at all: a PHOTO habit cannot, since the notification has no camera.
     */
    data class Habit(val id: Long, val title: String, val proof: ProofType = ProofType.MANUAL)

    data class Action(val habitId: Long, val label: String)

    sealed interface Decision {
        /**
         * Nothing to show. [clearExisting] is true when a reminder already on screen has been made
         * untrue by this decision and must come down — reminders turned off, the day finished, the
         * habits deleted, the deadline passed.
         */
        data class Skip(val clearExisting: Boolean) : Decision

        data class Post(
            val title: String,
            val body: String,
            val actions: List<Action>,
            val openApp: Boolean,
        ) : Decision
    }

    fun decide(
        kind: ReminderKind,
        onboardingDone: Boolean,
        remindersEnabled: Boolean,
        remaining: List<Habit>,
        totalHabits: Int,
        minutesLeft: Int,
        streakDays: Int,
        dueMinute: Int,
    ): Decision {
        if (!onboardingDone) return Decision.Skip(clearExisting = false)
        if (!remindersEnabled) return Decision.Skip(clearExisting = true)
        if (totalHabits == 0) return Decision.Skip(clearExisting = true)
        if (remaining.isEmpty()) return Decision.Skip(clearExisting = true)
        // A reminder's whole content is the time still left; past the give-up time there is none,
        // and a notification still claiming minutes would be a lie.
        if (minutesLeft <= 0) return Decision.Skip(clearExisting = true)

        val (manual, needsProof) = remaining.partition { it.proof == ProofType.MANUAL }
        val plan = ReminderActions.plan(manual.map { it.id }, hasProofRequiredHabits = needsProof.isNotEmpty())
        val single = plan.markDone.size == 1 && !plan.openApp
        val actions = plan.markDone.mapNotNull { id ->
            remaining.firstOrNull { it.id == id }
                ?.let { Action(id, ReminderCopy.actionLabel(it.title, single)) }
        }
        return Decision.Post(
            title = ReminderCopy.title(kind, remaining.size, minutesLeft, streakDays),
            body = ReminderCopy.body(kind, remaining.size, dueMinute),
            actions = actions,
            openApp = plan.openApp,
        )
    }
}
