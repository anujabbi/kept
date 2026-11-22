package com.example.kept.core.analytics

/**
 * Every `$screen_name` KEPT reports (issue #10).
 *
 * Named here rather than derived from a route so a `$screen` never carries a date or a row id, and
 * so renaming a route cannot silently rename a screen in the dashboard.
 */
object Screens {
    const val ONBOARDING = "Onboarding"
    const val HOME = "Home"
    const val GALLERY = "Gallery"
    const val SETTINGS = "Settings"
    const val RECAP = "Recap"
    const val EXCEPTIONS = "Exceptions"
    const val HABITS = "Habits"
    const val ADD_HABIT = "Add habit"
    const val EDIT_HABIT = "Edit habit"
    const val WEEKLY_GOALS = "Weekly goals"
    const val ADD_WEEKLY_GOAL = "Add weekly goal"
    const val EDIT_WEEKLY_GOAL = "Edit weekly goal"
    const val PERMISSIONS = "Permissions"
    const val HISTORY = "History"
    const val REVEAL = "Reveal"

    /** Not in the nav graph: `LockActivity` and the overlay above it are their own surfaces. */
    const val LOCK = "Lock"
    const val BREAK = "Break"
    const val CELEBRATION = "Celebration"
}
