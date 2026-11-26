package com.example.kept.core.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Onboarding step 3, "When does the lock run?" (`A11-Onb-Rule`): the lock window on a 6 am to
 * midnight bar, moved with − / + in 30 minute steps. The rules are the design script's, in
 * minutes of the day: the start never goes before 6:00 am, give-up never after 11:30 pm, and
 * give-up stays at least two hours after the start.
 *
 * The values start from the stored lock window (the Settings defaults on a fresh install). A
 * stored window outside the bar (one that runs past midnight, set in Settings) is brought inside
 * it by [normalize] when shown; the step only writes it back if the person moves it.
 */
object LockWindowRules {
    const val STEP_MIN = 30
    const val MIN_GAP_MIN = 120
    /** The bar's left end, and the earliest start: 6:00 am. */
    const val BAR_START_MIN = 6 * 60
    /** The bar's right end: midnight. */
    const val BAR_END_MIN = 24 * 60
    /** The latest give-up time: 11:30 pm. */
    const val LATEST_DUE_MIN = BAR_END_MIN - STEP_MIN
    /** The latest start, so that give-up still fits two hours later: 9:30 pm. */
    const val LATEST_FROM_MIN = LATEST_DUE_MIN - MIN_GAP_MIN

    data class Window(val fromMinute: Int, val dueMinute: Int)

    fun normalize(fromMinute: Int, dueMinute: Int): Window {
        val from = fromMinute.coerceIn(BAR_START_MIN, LATEST_FROM_MIN)
        val due = dueMinute.coerceIn(from + MIN_GAP_MIN, LATEST_DUE_MIN)
        return Window(from, due)
    }

    fun fromEarlier(w: Window): Window = w.copy(fromMinute = maxOf(BAR_START_MIN, w.fromMinute - STEP_MIN))
    fun fromLater(w: Window): Window = w.copy(fromMinute = minOf(w.dueMinute - MIN_GAP_MIN, w.fromMinute + STEP_MIN))
    fun dueEarlier(w: Window): Window = w.copy(dueMinute = maxOf(w.fromMinute + MIN_GAP_MIN, w.dueMinute - STEP_MIN))
    fun dueLater(w: Window): Window = w.copy(dueMinute = minOf(LATEST_DUE_MIN, w.dueMinute + STEP_MIN))

    fun canFromEarlier(w: Window): Boolean = fromEarlier(w) != w
    fun canFromLater(w: Window): Boolean = fromLater(w) != w
    fun canDueEarlier(w: Window): Boolean = dueEarlier(w) != w
    fun canDueLater(w: Window): Boolean = dueLater(w) != w

    /** Where [minute] falls on the bar, 0 at 6 am and 1 at midnight. */
    fun barFraction(minute: Int): Float =
        ((minute - BAR_START_MIN).toFloat() / (BAR_END_MIN - BAR_START_MIN)).coerceIn(0f, 1f)
}

/** Onboarding copy (spec, Welcome and Onboarding; `A1`, `A10`–`A14`). Final; do not reword here. */
object OnboardingCopy {
    const val STEPS = 6
    private const val NBSP = ' '

    // Welcome (A1)
    const val BRAND = "KEPT"
    const val WELCOME_HEADLINE = "Habits first. Phone after."
    const val WELCOME_LINE = "Your apps stay locked until today's habits are done."
    const val WELCOME_BUTTON = "Pick my habits"
    const val WELCOME_CAPTION = "This is Sprig. It grows every day you keep your promise."

    // Habits (A10)
    const val HABITS_TITLE = "What habits will you keep?"
    const val HABITS_LINE = "Pick two or three. You can change them later."
    const val ADD_YOUR_OWN = "Add your own"

    // How often (#205): the chip on a ticked row and its sheet.
    const val EVERY_DAY = "Every day"
    const val SOME_DAYS = "Some days a week"
    const val DONE = "Done"
    const val CHANGE_HOW_OFTEN = "Change how often"
    const val NEEDS_DAILY = "Set one habit to every day to continue."

    /** A chip: "Every day", or "4 days a week" ([WeeklyGoalCopy.days]) for [days] a week. */
    fun cadence(days: Int?): String = if (days == null) EVERY_DAY else WeeklyGoalCopy.days(days)

    /** What TalkBack reads on a chip: "Exercise, 4 days a week. Change how often". */
    fun cadenceDescription(title: String, days: Int?): String =
        "$title, ${if (days == null) "every day" else WeeklyGoalCopy.days(days)}. $CHANGE_HOW_OFTEN"

    /** The sheet's title: "How often will you exercise?" ([WeeklyLockCopy.inSentence]). */
    fun sheetTitle(title: String): String = "How often will you ${WeeklyLockCopy.inSentence(title)}?"

    /**
     * The sheet's summary: "Exercise, 30 min, 4 days a week", "Exercise, 30 min, every day"; a
     * photo habit has no [target] there ("Tidy room, 2 days a week").
     */
    fun sheetSummary(title: String, target: String?, days: Int?): String = when {
        days != null -> WeeklyGoalCopy.summary(title, target, days)
        target == null -> "$title, every day"
        else -> "$title, $target, every day"
    }

    /**
     * Under Continue: "2 daily habits", "1 daily habit · 1 weekly goal"; "Pick at least one" with
     * nothing ticked, and [NEEDS_DAILY] when every ticked habit is weekly.
     */
    fun habitsFooter(daily: Int, weekly: Int): String = when {
        daily == 0 && weekly == 0 -> "Pick at least one"
        daily == 0 -> NEEDS_DAILY
        weekly == 0 -> dailyCount(daily)
        else -> "${dailyCount(daily)} · $weekly weekly ${if (weekly == 1) "goal" else "goals"}"
    }

    private fun dailyCount(n: Int) = "$n daily ${if (n == 1) "habit" else "habits"}"

    /** A habit row's second line: "30 min", "10 pages", "Photo check-in", "Tap when done". */
    fun habitDetail(proof: ProofType, target: Int, unit: String): String = when {
        proof == ProofType.PHOTO -> "Photo check-in"
        target <= 1 -> "Tap when done"
        else -> "$target $unit"
    }

    // Shutdown time (#164; no mockup, built on the step page like A10 and A11)
    const val SHUTDOWN_TITLE = "Pick a shutdown time"
    const val SHUTDOWN_LINE = "A shutdown time helps you stop doomscrolling at night."
    const val SHUTDOWN_DETAIL = "Apps lock until 7:00 am. Phone, messages and maps still work."
    const val SKIP = "Skip"

    /** The starts the shutdown step offers, as minutes of the day: 10 PM, 11 PM, Midnight. */
    val SHUTDOWN_TIMES: List<Int> = NightWindow.PRESETS

    /** A shutdown row's title: "10 PM", "11 PM", "Midnight" (minute 0). */
    fun shutdownLabel(minute: Int): String = when {
        minute == 0 -> SettingsCopy.MIDNIGHT
        else -> "${(minute / 60 + 11) % 12 + 1} ${if (minute < 12 * 60) "AM" else "PM"}"
    }

    // Lock window (A11)
    const val RULE_TITLE = "When does the lock run?"
    const val RULE_LINE = "Pick the window. Apps lock at the start and open once your habits are done."
    const val LOCK_FROM = "Lock apps from"
    const val LOCK_FROM_HINT = "Most people pick when they wake up"
    const val GIVE_UP = "Give up at"
    const val GIVE_UP_HINT = "After this, the day is missed"
    val BAR_LABELS = listOf("6${NBSP}am", "noon", "6${NBSP}pm", "midnight")

    /** "7:00 am", with a no-break space so the time never wraps. */
    fun time(minute: Int): String = minute.minuteOfDayLabel().replace(' ', NBSP)

    /** The sentence under the bar, split around the two bold times. */
    fun ruleSentence(fromMinute: Int, dueMinute: Int): List<Pair<String, Boolean>> = listOf(
        "From " to false,
        time(fromMinute) to true,
        ", everything but the essentials locks until your habits are done. If they are not done by " to false,
        time(dueMinute) to true,
        ", apps open and the day is missed." to false,
    )

    // What stays open (A12)
    const val EXCEPTIONS_TITLE = "What stays open?"
    const val EXCEPTIONS_LINE = "Phone, messages, maps and camera always work. Add only apps you truly need: every exception is a gap in the lock."
    const val ALWAYS_OPEN = "Always open"
    const val YOUR_EXCEPTIONS = "Your exceptions"
    const val SEARCH_APPS = "Search apps"
    const val STAYS_OPEN = "Stays open"
    const val LOCKS = "Locks"
    /** What the chips show before the device's own always-open apps have loaded. */
    val ALWAYS_OPEN_DEFAULT = listOf("Phone", "Messages", "Maps", "Camera", "Settings", "Clock")

    fun appsOpen(n: Int): String = when (n) {
        0 -> "Everything locks"
        1 -> "1 app stays open"
        else -> "$n apps stay open"
    }

    // Permissions (A13)
    const val PERMISSIONS_TITLE = "Let KEPT do its job"
    const val PERMISSIONS_LINE = "The lock needs to see which app is in front and step in front of it. It never reads what is inside your apps."
    const val PERMISSIONS_HOLLOW = "If usage access goes off during a lock window, that day shows as hollow: it does not count, and it does not break your streak."
    const val GRANT = "Grant"

    // How Sprig works (A14)
    const val READY_TITLE = "How Sprig works"
    const val RULE_GROWS_BOLD = "Every day you keep your promise, Sprig grows."
    const val RULE_GROWS = " At streak milestones it changes form and gets a new outfit."
    const val RULE_BREAKS_BOLD = "Breaking the lock always works."
    const val RULE_BREAKS = " It takes a 60-second wait and costs Sprig a level. After three breaks in a week, the day is written off."
    const val RULE_SHIELD_BOLD = "One shield a week"
    const val RULE_SHIELD = " forgives a missed day. Everything else is on you."
    /** Only when a weekly goal was chosen (#205). */
    const val RULE_WEEKLY_BOLD = "Weekly goals lock only when you'd fall behind."
    const val RULE_WEEKLY = " On other days you can skip them."
    const val START_TODAY = "Start today"
    const val STARTING = "Starting…"

    const val CONTINUE = "Continue"

    fun stepOf(step: Int): String = "Step $step of $STEPS"
}

/**
 * The hill's top edge (spec, The hill): the ellipse `(-60, crest, width + 60, crest + 96)` in dp,
 * on a screen [screenDp] wide, at [x].
 */
object HillEdge {
    const val OVERHANG_DP = 60f
    const val RADIUS_DP = 48f

    fun topDp(crestDp: Float, screenDp: Float, x: Float): Float {
        val a = screenDp / 2f + OVERHANG_DP
        val k = ((x - screenDp / 2f) / a).coerceIn(-1f, 1f)
        return crestDp + RADIUS_DP - RADIUS_DP * sqrt(1f - k * k)
    }

    /** How far below the hill's edge a Sprig box's bottom corners sit, at least. */
    const val UNDER_DP = 2f

    /**
     * The lowest of the hill's edge at a box from [left] to [right], plus [UNDER_DP]: a Sprig box
     * whose bottom is at least here has its lower body behind the hill at both corners.
     */
    fun bottomUnderDp(crestDp: Float, screenDp: Float, left: Float, right: Float): Float =
        maxOf(topDp(crestDp, screenDp, left), topDp(crestDp, screenDp, right)) + UNDER_DP
}

/**
 * The welcome screen's layout (`A1-Welcome`, 390 × 844): the text block from 72dp, Sprig 350dp
 * centred with its bottom 10dp under the hill's crest, and the button block 36dp from the bottom
 * with the crest 58dp above it. Every value is in dp from the top of the window, below the
 * status bar.
 *
 * Measured from the real screen: the crest sits above the button block; Sprig is as big as fits
 * between the text block and the crest (at most 350dp, and 20dp in from each side), with its
 * bottom also under the hill at both lower corners. When even [SPRIG_MIN_DP] does not fit (small
 * screen, large font), the page grows taller than the screen and scrolls.
 */
object WelcomeLayout {
    const val TEXT_TOP_DP = 72
    const val TEXT_SIDE_DP = 28
    const val SPRIG_DP = 350
    const val SPRIG_SIDE_DP = 20
    const val SPRIG_MIN_DP = 120
    /** Sprig's bottom sits this far below the crest (`top: 300` + 350 against the hill at 640). */
    const val SPRIG_BELOW_CREST_DP = 10
    /** The crest sits this far above the button block (`A1`: 640 against about 698). */
    const val CREST_ABOVE_BUTTONS_DP = 58
    const val BOTTOM_DP = 36
    /** Space kept between the text block and Sprig's box. */
    const val TEXT_GAP_DP = 12

    data class Spec(val pageDp: Float, val crestDp: Float, val sprigDp: Float, val sprigTopDp: Float, val sprigLeftDp: Float)

    /**
     * For a screen [screenW] × [screenH] dp (below the status bar, above the navigation bar),
     * a text block ending at [textBottomDp] and a button block [buttonsDp] tall.
     */
    fun spec(screenW: Float, screenH: Float, textBottomDp: Float, buttonsDp: Float): Spec {
        val maxSprig = minOf(SPRIG_DP.toFloat(), screenW - 2 * SPRIG_SIDE_DP)
        fun fit(page: Float): Spec {
            val crest = page - BOTTOM_DP - buttonsDp - CREST_ABOVE_BUTTONS_DP
            var size = maxSprig
            // The bottom also has to clear the hill's curve at the box's corners, which a wider
            // box reaches further down; shrink until the box fits above the text gap.
            repeat(40) {
                val left = (screenW - size) / 2
                val bottom = maxOf(crest + SPRIG_BELOW_CREST_DP, HillEdge.bottomUnderDp(crest, screenW, left, left + size))
                val room = bottom - textBottomDp - TEXT_GAP_DP
                if (room >= size) return Spec(page, crest, size, bottom - size, left)
                size = maxOf(floor(room), 0f)
            }
            val left = (screenW - size) / 2
            val bottom = maxOf(crest + SPRIG_BELOW_CREST_DP, HillEdge.bottomUnderDp(crest, screenW, left, left + size))
            return Spec(page, crest, size, bottom - size, left)
        }
        val onScreen = fit(screenH)
        if (onScreen.sprigDp >= SPRIG_MIN_DP) return onScreen
        // Grow the page until a Sprig of SPRIG_MIN_DP fits; the page then scrolls.
        var page = screenH
        while (page < screenH + 2000) {
            page += 8f
            val s = fit(page)
            if (s.sprigDp >= SPRIG_MIN_DP) return s.copy(pageDp = ceil(page))
        }
        return fit(page)
    }
}

/**
 * Step 6, "How Sprig works" (`A14-Onb-Ready`): a 300dp dawn sky with the title at (24, 96),
 * 170dp wide, and Sprig 190dp on the right (`left: 186px` in a 390 frame, so 14dp from the edge)
 * with its top at 92, i.e. its bottom 30dp under the crest at 252.
 *
 * As on the Sprig tab ([CollectionSprig]), widths come from the real screen: the title column
 * and Sprig's box never touch; Sprig gives way first, down to [SPRIG_MIN_DP]. A taller title
 * (large font) moves the crest down, and Sprig with it.
 */
object ReadySprig {
    const val TEXT_SIDE_DP = 24
    const val TEXT_TOP_DP = 96
    const val TEXT_DP = 170
    const val SPRIG_DP = 190
    const val SPRIG_MIN_DP = 140
    const val SPRIG_END_DP = 14
    const val GAP_DP = 4
    const val DESIGN_CREST_DP = 252
    const val SPRIG_BELOW_CREST_DP = 30
    const val TEXT_TO_CREST_DP = 12

    data class Widths(val textDp: Int, val sprigDp: Int)

    fun widths(screenDp: Int): Widths {
        val room = screenDp - TEXT_SIDE_DP - SPRIG_END_DP - GAP_DP
        val sprig = (room - TEXT_DP).coerceIn(SPRIG_MIN_DP, SPRIG_DP)
        val text = (room - sprig).coerceAtMost(TEXT_DP)
        return Widths(text, sprig)
    }

    fun crestDp(textBottomDp: Int): Int = maxOf(DESIGN_CREST_DP, textBottomDp + TEXT_TO_CREST_DP)

    /** Sprig's top: its bottom 30dp under the crest, and under the hill at both lower corners. */
    fun sprigTopDp(crestDp: Int, sprigDp: Int, screenDp: Int): Int {
        val right = (screenDp - SPRIG_END_DP).toFloat()
        val left = right - sprigDp
        val bottom = maxOf(crestDp + SPRIG_BELOW_CREST_DP, ceil(HillEdge.bottomUnderDp(crestDp.toFloat(), screenDp.toFloat(), left, right)).toInt())
        return bottom - sprigDp
    }
}
