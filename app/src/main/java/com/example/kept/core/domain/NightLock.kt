package com.example.kept.core.domain

/**
 * The night lockdown window (issue #164): from a shutdown time until morning, every night. Minutes
 * of the day, start inclusive and end exclusive, and it usually wraps midnight. Midnight is 0.
 */
object NightWindow {
    const val DEFAULT_FROM = 23 * 60
    const val DEFAULT_UNTIL = 7 * 60

    const val MIN_MINUTES = 60
    const val MAX_MINUTES = 12 * 60
    private const val DAY = 24 * 60

    /** Minutes from [from] forward to [until], past midnight if need be. 0 when they are equal. */
    fun lengthMinutes(from: Int, until: Int): Int = Math.floorMod(until - from, DAY)

    /** 1 to 12 hours long, so the end can never equal the start. */
    fun isValid(from: Int, until: Int): Boolean = lengthMinutes(from, until) in MIN_MINUTES..MAX_MINUTES

    /** What an editor says about a window it will not set, or null when it may. */
    fun error(from: Int, until: Int): String? = if (isValid(from, until)) null else SettingsCopy.NIGHT_WINDOW_ERROR

    /**
     * True when every minute the old window locked is still locked by the new one. A change that
     * does not cover the old window (a later start, an earlier end) can open a lock that is
     * holding right now, so it asks first.
     */
    fun covers(newFrom: Int, newUntil: Int, oldFrom: Int, oldUntil: Int): Boolean =
        Math.floorMod(oldFrom - newFrom, DAY) + lengthMinutes(oldFrom, oldUntil) <= lengthMinutes(newFrom, newUntil)
}

/** The three stored night lockdown values. Off, 11:00 pm to 7:00 am, until the user says otherwise. */
data class NightSetting(
    val enabled: Boolean = false,
    val fromMinute: Int = NightWindow.DEFAULT_FROM,
    val untilMinute: Int = NightWindow.DEFAULT_UNTIL,
)

/**
 * What one change in the Night lockdown group does: the setting to store, and the question to ask
 * first when night lockdown is holding the phone right now ([confirm]; null when the change
 * cannot open a held lock). Each factory is null when there is nothing to store: the value is the
 * one already saved, or the window would break the 1 to 12 hour rule.
 */
data class NightEdit(val result: NightSetting, val confirm: String?) {
    companion object {
        fun switch(current: NightSetting, on: Boolean): NightEdit? = when {
            on == current.enabled -> null
            on -> NightEdit(current.copy(enabled = true), null)
            else -> NightEdit(current.copy(enabled = false), SettingsCopy.NIGHT_OFF_WHILE_LOCKED)
        }

        fun start(current: NightSetting, picked: Int): NightEdit? =
            window(current, picked, current.untilMinute, SettingsCopy.NIGHT_START_WHILE_LOCKED)

        fun end(current: NightSetting, picked: Int): NightEdit? =
            window(current, current.fromMinute, picked, SettingsCopy.NIGHT_END_WHILE_LOCKED)

        private fun window(current: NightSetting, from: Int, until: Int, ask: String): NightEdit? = when {
            from == current.fromMinute && until == current.untilMinute -> null
            !NightWindow.isValid(from, until) -> null
            else -> NightEdit(
                current.copy(fromMinute = from, untilMinute = until),
                if (NightWindow.covers(from, until, current.fromMinute, current.untilMinute)) null else ask,
            )
        }
    }
}

/**
 * The two time sliders (issue #218): "Shutdown at" from 8:00 pm to 2:00 am and "Phone back at"
 * from 5:00 am to 10:00 am, both in 15 minute steps. A slider position is a step index from the
 * left end. Picking one end keeps the window inside [NightWindow]'s 1 to 12 hour rule by moving
 * the other end, so the pair the sliders show can always be saved.
 */
object NightSliders {
    const val STEP_MIN = 15
    private const val DAY = 24 * 60

    /** "Shutdown at": 8:00 pm, then 24 steps to 2:00 am. */
    const val FROM_FIRST = 20 * 60
    const val FROM_STEPS = 24

    /** "Phone back at": 5:00 am, then 20 steps to 10:00 am. */
    const val UNTIL_FIRST = 5 * 60
    const val UNTIL_STEPS = 20

    /** The labels under each slider, with where they sit (0 at the left end, 1 at the right). */
    val FROM_LABELS = listOf(0f to "8 pm", 1f / 3 to "10 pm", 2f / 3 to "midnight", 1f to "2 am")
    val UNTIL_LABELS = listOf(0f to "5 am", 0.5f to "7:30 am", 1f to "10 am")

    fun fromAt(index: Int): Int = (FROM_FIRST + index.coerceIn(0, FROM_STEPS) * STEP_MIN) % DAY
    fun untilAt(index: Int): Int = UNTIL_FIRST + index.coerceIn(0, UNTIL_STEPS) * STEP_MIN

    /** The step nearest [minute] on the shutdown slider; a time outside it goes to the nearer end. */
    fun fromIndex(minute: Int): Int = indexOf(minute, FROM_FIRST, FROM_STEPS)
    fun untilIndex(minute: Int): Int = indexOf(minute, UNTIL_FIRST, UNTIL_STEPS)

    private fun indexOf(minute: Int, first: Int, steps: Int): Int {
        val span = steps * STEP_MIN
        val off = Math.floorMod(minute - first, DAY)
        if (off <= span) return Math.round(off.toFloat() / STEP_MIN).coerceIn(0, steps)
        // Outside: whichever end is closer around the clock.
        return if (off - span <= DAY - off) steps else 0
    }

    /** A new shutdown time; the morning end comes earlier if the night would pass 12 hours. */
    fun pickFrom(w: NightSetting, from: Int): NightSetting {
        val until = if (NightWindow.lengthMinutes(from, w.untilMinute) > NightWindow.MAX_MINUTES) (from + NightWindow.MAX_MINUTES) % DAY else w.untilMinute
        return w.copy(fromMinute = from, untilMinute = until)
    }

    /** A new morning end; the shutdown time comes later if the night would pass 12 hours. */
    fun pickUntil(w: NightSetting, until: Int): NightSetting {
        val from = if (NightWindow.lengthMinutes(w.fromMinute, until) > NightWindow.MAX_MINUTES) Math.floorMod(until - NightWindow.MAX_MINUTES, DAY) else w.fromMinute
        return w.copy(fromMinute = from, untilMinute = until)
    }
}

/** The shutdown step and the "Starts at" dialog (issue #218). Final; do not reword here. */
object NightCopy {
    const val SHUTDOWN_AT = "Shutdown at"
    const val PHONE_BACK_AT = "Phone back at"

    /** The large time: "11:00 pm", "Midnight". */
    fun big(minute: Int): String = SettingsCopy.nightTime(minute)

    /** A time inside a sentence: "11:00 pm", "midnight". */
    fun inSentence(minute: Int): String = if (minute == 0) "midnight" else OnboardingCopy.time(minute)

    /** Under the shutdown step's sliders, split around the bold times. */
    fun shutdownSentence(from: Int, until: Int): List<Pair<String, Boolean>> = listOf(
        "From " to false, inSentence(from) to true, " to " to false, inSentence(until) to true,
        ", apps stay locked. Phone, messages and maps still work." to false,
    )

    /** Under the "Starts at" slider: "Apps lock from 11:00 pm until 7:00 am." */
    fun startSentence(from: Int, until: Int): List<Pair<String, Boolean>> = listOf(
        "Apps lock from " to false, inSentence(from) to true, " until " to false, inSentence(until) to true, "." to false,
    )
}
