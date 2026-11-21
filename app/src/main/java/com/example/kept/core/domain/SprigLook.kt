package com.example.kept.core.domain

/** What Sprig wears. The names follow the outfit groups in `SprigA.dc.html`. */
enum class SprigOutfit(val description: String) {
    NONE("none"),
    SCARF("a red striped scarf"),
    SWEATER("a yellow cardigan"),
    BOTH("a scarf and a cardigan"),
    CROWN("a leaf crown"),
    SAGE("round glasses and a white beard"),
    STAR("a starry cape and a gold halo"),
}

/**
 * How each form looks and which pose each app state shows (spec, Sprig (Daybreak)). Pure so it
 * stays testable; the drawing lives in `core/ui/sprig`.
 */
object SprigLook {

    /** Sprout stage: 0 nub, 1 one leaf, 2 two leaves, 3 two leaves and a flower. */
    fun stage(form: SprigForm): Int = when (form) {
        SprigForm.SPRIG -> 0
        SprigForm.BUD -> 1
        else -> 3
    }

    fun outfit(form: SprigForm): SprigOutfit = when (form) {
        SprigForm.SPRIG -> SprigOutfit.NONE
        SprigForm.BUD -> SprigOutfit.SCARF
        SprigForm.BLOOM -> SprigOutfit.SWEATER
        SprigForm.THICKET -> SprigOutfit.BOTH
        SprigForm.GROVE -> SprigOutfit.CROWN
        SprigForm.ANCIENT -> SprigOutfit.SAGE
        SprigForm.CELESTIAL -> SprigOutfit.STAR
    }

    /**
     * The pose for an app state. The lock screen always guards; otherwise a wilted Sprig droops,
     * a day finished on time cheers, and anything else is idle.
     */
    fun pose(onLockScreen: Boolean = false, wilted: Boolean = false, allDone: Boolean = false): SprigPose = when {
        onLockScreen -> SprigPose.GUARD
        wilted -> SprigPose.DROOP
        allDone -> SprigPose.CHEER
        else -> SprigPose.IDLE
    }

    /** The mood word TalkBack reads: "Sprig, {form}, {mood}". */
    fun mood(pose: SprigPose, wilted: Boolean = false): String = when {
        wilted -> "wilting"
        else -> when (pose) {
            SprigPose.IDLE -> "happy"
            SprigPose.CHEER -> "cheering"
            SprigPose.GUARD -> "guarding"
            SprigPose.DROOP -> "sad"
            SprigPose.WAVE -> "waving"
            SprigPose.SLEEP -> "asleep"
        }
    }

    fun contentDescription(form: SprigForm, pose: SprigPose, wilted: Boolean = false): String =
        "Sprig, ${form.displayName}, ${mood(pose, wilted)}"
}
