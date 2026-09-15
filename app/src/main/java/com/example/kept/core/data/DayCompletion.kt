package com.example.kept.core.data

/**
 * Whether a KEPT screen is in front of the user right now (issue #9).
 *
 * An interface so the data layer can ask without depending on Android: the celebration is a
 * full-screen moment, and when nothing of KEPT is on screen there is nothing to show it on.
 */
interface ForegroundSignal {
    val isForeground: Boolean
}

/**
 * What happens outside the data layer when the day is finished on time (issue #9).
 *
 * [HabitActions] records the day and raises this; the notification layer decides what the shade
 * should look like afterwards. Keeping it an interface is what lets `HabitActions` stay a plain
 * use-case class: it no longer constructor-injects `KeptNotifications`, so building one does not
 * create notification channels, and the data layer keeps no dependency on `core/notify`.
 */
interface DayCompletionReactor {
    suspend fun onDayCompleted()
}
