package com.example.kept.core.domain

/** Your habits and Add a habit copy (spec, Habits; `A8-Habits`, `A9-Habit-edit`). Final; do not reword here. */
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

    // Add or edit a habit (A9)
    const val ADD_TITLE = "Add a habit"
    const val EDIT_TITLE = "Edit habit"
    const val CANCEL = "Cancel"
    const val NAME_LABEL = "What will you do every day?"
    const val NAME_PLACEHOLDER = "Read, walk, practice"
    const val HOW_MUCH = "How much?"
    const val LESS = "Less"
    const val MORE = "More"
    const val UNIT = "Unit"
    const val PHOTO = "Prove it with a photo"
    const val PHOTO_HINT = "Otherwise you just tap when it is done."
    const val SAVE = "Save habit"
    const val REMOVE = "Remove habit"

    /** The existing copy for the last habit, which cannot be removed (from the old habits page). */
    const val KEEP_ONE = "Keep at least one habit. Remove this one after adding another."

    /** The existing lock-window confirm for removing a habit (issue #1). */
    const val REMOVE_WHILE_LOCKED = "Your apps are locked right now. Removing this habit opens them early. Still remove it?"
}

/** One of the four quick picks under the name field (`A9-Habit-edit`). */
data class HabitPreset(val title: String, val target: Int, val unit: String)

/**
 * What Add or edit a habit holds while the person fills it in (`A9-Habit-edit` script): the
 * name, how much (a [target] in a [unit]) and whether a photo proves it. Every change returns a
 * new draft, so the rules are tested here rather than in the screen.
 */
data class HabitDraft(
    val title: String = "",
    val target: Int = UNIT_START.getValue(MIN),
    val unit: String = MIN,
    val photo: Boolean = false,
) {
    /** The name as it will be saved: trimmed, at most [MAX_TITLE] characters. */
    val name: String get() = title.trim()

    /** "Save habit" is enabled once the name has text. */
    val canSave: Boolean get() = name.isNotEmpty()

    /** "How much?" shows unless a photo proves the habit. */
    val showsAmount: Boolean get() = !photo

    /** The unit chips show for a name that is not one of the quick picks. */
    val showsUnits: Boolean get() = name.isNotEmpty() && PRESETS.none { it.title == name }

    /** What the stepper reads: "20 min", "Once a day". */
    val amount: String get() = amount(target, unit)

    /** The stepper's step: 5 for minutes, 1 for everything else. */
    val step: Int get() = step(unit)

    /** Whether "−" can go lower: never below one step. */
    val canLess: Boolean get() = target > step
    val canMore: Boolean get() = target + step <= MAX_TARGET

    fun withTitle(t: String): HabitDraft = copy(title = t.take(MAX_TITLE))

    /** A quick pick fills the name, the amount and the unit. */
    fun pick(p: HabitPreset): HabitDraft = copy(title = p.title, target = p.target, unit = p.unit)

    /** A unit chip sets the unit and its start value (min 20, pages 10, times 1, glasses 8). */
    fun withUnit(u: String): HabitDraft = copy(unit = u, target = UNIT_START[u] ?: target)

    fun less(): HabitDraft = copy(target = maxOf(step, target - step))
    fun more(): HabitDraft = if (canMore) copy(target = target + step) else this
    fun togglePhoto(): HabitDraft = copy(photo = !photo)

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
        val UNIT_START = mapOf(MIN to 20, PAGES to 10, TIMES to 1, GLASSES to 8)

        val PRESETS = listOf(
            HabitPreset("Exercise", 30, MIN),
            HabitPreset("Read", 10, PAGES),
            HabitPreset("Practice", 20, MIN),
            HabitPreset("Study", 45, MIN),
        )

        fun step(unit: String): Int = if (unit == MIN) 5 else 1

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

        /**
         * The draft for an existing habit. A photo habit starts its amount at 20 min, so turning
         * the photo off does not read "1 photo".
         */
        fun of(title: String, proof: ProofType, target: Int, unit: String): HabitDraft =
            if (proof == ProofType.PHOTO) HabitDraft(title = title, photo = true)
            else HabitDraft(title = title, target = target.coerceIn(1, MAX_TARGET), unit = unit)
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
