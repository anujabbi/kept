package com.example.kept.core.domain

import java.time.LocalDate

enum class ProofType { TIMER, MANUAL, PHOTO }

enum class SprigPose { IDLE, BLOCK, DROOP, CHEER, WAVE }

/**
 * Evolution forms. Ordered by rarity. [streakThreshold] is the streak length at which the
 * form becomes the displayed form. DUO is buddy-only and unlocked by the pair streak.
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
    CELESTIAL(7, "Celestial", 100, "One hundred days. Beyond ordinary."),
    DUO(8, "Duo", Int.MAX_VALUE, "Only grows in pairs. Seven days together.");

    val isPairOnly: Boolean get() = this == DUO

    companion object {
        const val DUO_PAIR_STREAK = 7

        fun forStreak(streak: Int): SprigForm =
            entries.filter { !it.isPairOnly && it.streakThreshold <= streak }.maxBy { it.streakThreshold }

        fun next(form: SprigForm): SprigForm? =
            entries.filter { !it.isPairOnly && it.streakThreshold > form.streakThreshold }.minByOrNull { it.streakThreshold }

        fun fromId(id: Int): SprigForm = entries.firstOrNull { it.id == id } ?: SPRIG
    }
}

enum class Accessory { NONE, SCARF, BOW, GLASSES, HAT, STAR, SHELL, FLAME, SNOW, CROWN_TINY, HEADPHONES, BANDANA }

/** A time-bound colour + accessory. Deterministic per ISO week so a form is only "this look" for 7 days. */
data class WeekVariant(
    val id: Int,
    val name: String,
    val accentArgb: Long,
    val accessory: Accessory,
)

object Variants {
    val table: List<WeekVariant> = listOf(
        WeekVariant(0, "Lilac", 0xFF7F77DD, Accessory.NONE),
        WeekVariant(1, "Ember", 0xFFE0703C, Accessory.FLAME),
        WeekVariant(2, "Tide", 0xFF378ADD, Accessory.SHELL),
        WeekVariant(3, "Moss", 0xFF3B6D11, Accessory.BANDANA),
        WeekVariant(4, "Frost", 0xFF9FD8F0, Accessory.SNOW),
        WeekVariant(5, "Honey", 0xFFD9A441, Accessory.HAT),
        WeekVariant(6, "Rose", 0xFFE07A9B, Accessory.BOW),
        WeekVariant(7, "Ink", 0xFF26215C, Accessory.GLASSES),
        WeekVariant(8, "Mint", 0xFF0F6E56, Accessory.SCARF),
        WeekVariant(9, "Nova", 0xFFF2C14E, Accessory.STAR),
        WeekVariant(10, "Royal", 0xFF534AB7, Accessory.CROWN_TINY),
        WeekVariant(11, "Beat", 0xFFA32D2D, Accessory.HEADPHONES),
    )

    fun forDate(date: LocalDate): WeekVariant {
        val week = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
        // Mix year in so the same calendar week is a different look next year.
        val idx = ((week + year * 5) % table.size + table.size) % table.size
        return table[idx]
    }

    fun byId(id: Int): WeekVariant = table.getOrElse(id) { table[0] }
}

/** Persistent state of the creature and the streak. Stored in DataStore. */
data class SprigState(
    val level: Int = 1,
    val points: Long = 0,
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val shieldAvailable: Boolean = true,
    val shieldWeekKey: String = "",
    val lastRolloverDate: LocalDate? = null,
    val wilted: Boolean = false,
    val pairStreak: Int = 0,
) {
    val form: SprigForm get() = SprigForm.forStreak(streakDays)
}
