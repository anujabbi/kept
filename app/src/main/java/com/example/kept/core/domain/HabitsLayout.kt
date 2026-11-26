package com.example.kept.core.domain

/** Your habits copy (spec, Habits; `A8-Habits`). Final; do not reword here. */
object HabitsCopy {
    const val NBSP = ' '

    // Your habits (A8)
    const val TITLE = "Your habits"
    const val LINE = "Apps stay locked until every one is done. Two or three is plenty."
    const val ADD = "Add a habit"

    /** Under the button: "6 of 10 habits", or [HabitLimits.LIMIT_REACHED] at the limit. */
    fun count(n: Int): String =
        if (HabitLimits.canAdd(n)) "$n${NBSP}of${NBSP}${HabitLimits.MAX_HABITS}${NBSP}habits" else HabitLimits.LIMIT_REACHED

    /** A row's second line: "30 min, tap when done", "Once a day, tap when done", "Photo check-in", "Tap when done". */
    fun detail(proof: ProofType, target: Int, unit: String): String = when {
        proof == ProofType.PHOTO -> "Photo check-in"
        unit.isBlank() || unit == HabitDraft.CHECK_IN -> "Tap when done"
        else -> "${HabitDraft.amount(target, unit)}, tap when done"
    }

    // Edit habit, on the promise (#218)
    const val REMOVE = "Remove habit"

    /** The existing copy for the last habit, which cannot be removed (from the old habits page). */
    const val KEEP_ONE = "Keep at least one habit. Remove this one after adding another."

    /** The existing lock-window confirm for removing a habit (issue #1). */
    const val REMOVE_WHILE_LOCKED = "Your apps are locked right now. Removing this habit opens them early. Still remove it?"
}

/**
 * A daily habit as it will be saved: the name, how much (a [target] in a [unit]) and whether a
 * photo proves it. The promise (#218) makes one for "every day".
 */
data class HabitDraft(
    val title: String = "",
    val target: Int = UNIT_START.getValue(MIN),
    val unit: String = MIN,
    val photo: Boolean = false,
) {
    /** The name as it will be saved: trimmed. */
    val name: String get() = title.trim()

    /** The icon key for this name ([HabitIconRules]). */
    val iconKey: String get() = HabitIconRules.forName(name)

    /** What is stored: a photo habit is 1 "photo", as the old editor saved it. */
    val proof: ProofType get() = if (photo) ProofType.PHOTO else ProofType.MANUAL
    val savedTarget: Int get() = if (photo) 1 else target
    val savedUnit: String get() = if (photo) PHOTO_UNIT else unit

    companion object {
        const val MIN = "min"
        const val PAGES = "pages"
        const val TIMES = "times"
        const val GLASSES = "glasses"
        const val PHOTO_UNIT = "photo"

        /** Legacy one-tap unit, no amount; no longer offered for new habits. */
        const val CHECK_IN = "check-in"

        /** The old editor's limits: 28 characters, three digits. */
        const val MAX_TITLE = 28
        const val MAX_TARGET = 999

        val UNITS = listOf(MIN, PAGES, TIMES, GLASSES)

        /** Where a new unit starts: min 20, pages 10, times 1, glasses 8. */
        val UNIT_START = mapOf(MIN to 20, PAGES to 10, TIMES to 1, GLASSES to 8)

        /**
         * "20 min", "10 pages", "3 times"; one time (or one check-in) reads "Once a day", and one
         * page or glass is singular. Joined by a no-break space so it never wraps.
         */
        fun amount(target: Int, unit: String): String = when {
            target == 1 && (unit == TIMES || unit == CHECK_IN) -> "Once a day"
            target == 1 && unit == PAGES -> "1${HabitsCopy.NBSP}page"
            target == 1 && unit == GLASSES -> "1${HabitsCopy.NBSP}glass"
            else -> "$target${HabitsCopy.NBSP}$unit"
        }
    }
}

/**
 * The habit's icon, chosen from its name (spec, Habits): the first rule with a keyword that
 * starts any word of the name wins ("Reading" is a book, "Practice guitar" a note), and a name
 * no rule knows gets the pencil. Keys are [com.example.kept.core.ui.HabitIcons] keys.
 */
object HabitIconRules {
    const val FALLBACK = "write"

    /** In order: the activity rules come before tidy, so "Read in my room" is a book, not a broom. */
    val RULES: List<Pair<String, List<String>>> = listOf(
        "sleep" to listOf("sleep", "bedtime", "nap", "wake"),
        "water" to listOf("water", "drink", "hydrat", "glass"),
        "book" to listOf("read", "book", "novel", "page"),
        "music" to listOf("practi", "guitar", "piano", "music", "sing", "violin", "drum", "instrument", "song"),
        "study" to listOf("study", "homework", "revis", "learn", "school", "class", "math", "language", "spanish", "french", "code", "coding", "duolingo"),
        "gym" to listOf("gym", "workout", "lift", "weight", "push", "squat", "plank", "sit-up", "situp"),
        "run" to listOf("exercis", "run", "jog", "walk", "hike", "bike", "cycl", "swim", "sport", "football", "soccer", "basketball", "tennis", "dance", "skate"),
        "calm" to listOf("meditat", "breath", "yoga", "pray", "calm", "mindful", "stretch"),
        "art" to listOf("draw", "paint", "art", "sketch", "craft", "design"),
        "write" to listOf("write", "writing", "journal", "diary", "poem", "blog"),
        "tidy" to listOf("tidy", "clean", "room", "chore", "dish", "laundry", "bed", "vacuum", "wash"),
    )

    fun forName(name: String): String {
        val words = name.lowercase().split(Regex("[^a-z0-9-]+")).filter { it.isNotEmpty() }
        return RULES.firstOrNull { (_, keys) -> words.any { w -> keys.any { w.startsWith(it) } } }?.first ?: FALLBACK
    }
}
