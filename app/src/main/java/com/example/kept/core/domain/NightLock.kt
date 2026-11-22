package com.example.kept.core.domain

/**
 * The night lockdown window (issue #164): from a shutdown time until morning, every night. Minutes
 * of the day, start inclusive and end exclusive, and it usually wraps midnight. Midnight is 0.
 */
object NightWindow {
    const val DEFAULT_FROM = 23 * 60
    const val DEFAULT_UNTIL = 7 * 60

    /** The starts Settings offers before "Custom…": 10:00 pm, 11:00 pm, Midnight. */
    val PRESETS = listOf(22 * 60, 23 * 60, 0)

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
