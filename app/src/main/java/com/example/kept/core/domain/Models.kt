package com.example.kept.core.domain

import java.time.LocalDate

enum class ProofType { MANUAL, PHOTO }

/** Sprig's six Daybreak poses (spec, Sprig (Daybreak), Poses). */
enum class SprigPose { IDLE, CHEER, GUARD, DROOP, WAVE, SLEEP }

/**
 * Evolution forms. Ordered by rarity. [streakThreshold] is the streak length at which the
 * form becomes the displayed form.
 */
enum class SprigForm(
    val id: Int,
    val displayName: String,
    val streakThreshold: Int,
    val tagline: String,
) {
    SPRIG(1, "Sprig", 0, "Every promise starts small."),
    BUD(2, "Bud", 3, "Three days. Something is growing."),
    BLOOM(3, "Bloom", 7, "A whole week kept."),
    THICKET(4, "Thicket", 14, "Two weeks. Roots are deep now."),
    GROVE(5, "Grove", 30, "A month of promises kept."),
    ANCIENT(6, "Ancient", 60, "Sixty days. Older than most habits."),
    CELESTIAL(7, "Celestial", 100, "One hundred days. Beyond ordinary.");

    companion object {
        fun forStreak(streak: Int): SprigForm =
            entries.filter { it.streakThreshold <= streak }.maxBy { it.streakThreshold }

        fun next(form: SprigForm): SprigForm? =
            entries.filter { it.streakThreshold > form.streakThreshold }.minByOrNull { it.streakThreshold }

        fun fromId(id: Int): SprigForm = entries.firstOrNull { it.id == id } ?: SPRIG
    }
}

/** Persistent state of the creature and the streak. Stored in DataStore. */
data class SprigState(
    val level: Int = 1,
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val shieldAvailable: Boolean = true,
    val shieldWeekKey: String = "",
    val lastRolloverDate: LocalDate? = null,
    val wilted: Boolean = false,
    /**
     * ISO date of the day whose completion granted the same-day level, or null. Stored rather than
     * recomputed so an undo can only take back a level that was actually given (issue #7).
     */
    val levelGrantedDate: String? = null,
) {
    val form: SprigForm get() = SprigForm.forStreak(streakDays)
}
