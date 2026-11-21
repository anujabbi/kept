package com.example.kept.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Daybreak colour tokens, ported from the spec's "Design tokens" tables and the design source
 * (`docs/design/daybreak/`). Screens use these names; a hex that has a token is never written
 * anywhere else. The app is light only, so these are plain constants, not a theme switch.
 */
object DaybreakColors {
    val ink = Color(0xFF1E1B3C)
    val inkSoft = Color(0xFF3A3658)
    val muted = Color(0xFF5C5878)
    val faint = Color(0xFF8D88A8)
    val cream = Color(0xFFFFF9F1)
    val white = Color(0xFFFFFFFF)
    val line = Color(0xFFEFE4D6)
    val lineSoft = Color(0xFFF1E8DC)
    val field = Color(0xFFE6DCCE)
    val rowLine = Color(0xFFEADFD2)
    val purple = Color(0xFF5B4BD6)
    val purpleTint = Color(0xFFECE9FF)
    val purplePale = Color(0xFFF3EFFF)
    val purpleTrack = Color(0xFFDCD6FA)
    val purpleDone = Color(0xFFB9B0F7)
    val ring = Color(0xFFCFC8E6)
    val disabled = Color(0xFFB9B4CC)
    val streakGold = Color(0xFFFFB81F)
    val shieldBlue = Color(0xFF2F6FC4)
    val shieldTint = Color(0xFFD9E6FA)
    val danger = Color(0xFFA32D2D)
    val dangerTint = Color(0xFFFBE3E3)
    val amber = Color(0xFFB26A00)
    val amberTint = Color(0xFFFFF0D6)
    val green = Color(0xFF1F7A45)
    val greenBright = Color(0xFF2FA866)
    val greenTint = Color(0xFFE6F5EC)

    // Values the design source uses that the spec's table does not name. They get tokens too,
    // so no screen has to hard-code them.
    /** Text on a selectable purple-tint chip (`A9-Habit-edit`). */
    val purpleInk = Color(0xFF3A2FA0)
    /** Body text of the "Lock is off" banner (`A18-Parts`). */
    val dangerInk = Color(0xFF6E1C1C)
    /** Body text of the first-landing banner (`A18-Parts`). */
    val greenText = Color(0xFF1F4D33)
    /** Title and button text of the first-landing banner (`A18-Parts`). */
    val greenInk = Color(0xFF14532D)
    /** Body text of the "Too late" banner (`A18-Parts`). */
    val amberInk = Color(0xFF5C3A00)
    /** "Too late" label on a habit row done late (`A18-Parts`). */
    val amberLabel = Color(0xFF8A4B00)
    /** Ground of a habit row done late (`A18-Parts`). */
    val lateRow = Color(0xFFF6F2EC)
    /** A missed day in Home's week row (spec, Home). */
    val missedDay = Color(0xFFE6E2EE)
    /** Text on a sky in the dark half of its ramp. */
    val skyTextLight = Color(0xFFFFF4E4)
}

/** The top band of each screen. See [SkyRamp] for which one a screen shows. */
object Sky {
    val night = Color(0xFF2B2760)
    val gold = Color(0xFFFFC23D)
    val dawn = Color(0xFFFFD0A6)
    val overcast = Color(0xFFCFCBDD)
    val tide = Color(0xFFBFD9F2)
    val lilac = Color(0xFFECE9FF)
    /** Lock screen hill and ground while locked. */
    val lockGround = Color(0xFF1B1842)
    /** Recap when the lock was off (`A5-Recap-lockoff`). */
    val recapLockOff = Color(0xFFDCD6EA)
    /** Recap when everything was ticked too late (`A5-Recap-late`). */
    val recapTooLate = Color(0xFFE9C3B0)

    /** Home: index `round(done / total × 6)`. */
    val progressRamp: List<Color> = listOf(
        night, Color(0xFF3E3478), Color(0xFF6E5590), Color(0xFFB57F98), Color(0xFFE9A99A), dawn, gold,
    )

    /** Lock while locked: index `round(done / total × 4)`; gold once everything is done. */
    val lockRamp: List<Color> = listOf(
        night, Color(0xFF332D6C), Color(0xFF3E3478), Color(0xFF4A3E86), Color(0xFF5A4A9A),
    )
}
