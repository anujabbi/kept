package com.example.kept.core.analytics

import android.os.Build
import com.example.kept.BuildConfig
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings analytics up at startup (issue #10): applies the stored opt-out, registers the super
 * properties, and keeps the two that move — habit count and streak — up to date for the life of
 * the process.
 *
 * Super properties ride along with every event, so a question like "do people with three habits
 * break the lock more often?" needs no join. They persist across sessions, which is also why they
 * have to be refreshed: a stale `streak` would be attached to events for weeks.
 */
@Singleton
class AnalyticsInitializer @Inject constructor(
    private val analytics: Analytics,
    private val prefs: KeptPreferences,
    private val habits: HabitRepository,
    private val sprig: SprigRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            // The opt-out has to land before anything else is sent, so it is read first and the
            // rest of the work happens after.
            analytics.restore(prefs.currentSettings().analyticsEnabled)

            analytics.register("app_version", BuildConfig.VERSION_NAME)
            analytics.register("android_sdk", Build.VERSION.SDK_INT)
            analytics.register("manufacturer", Build.MANUFACTURER)

            launch {
                habits.observeHabits().map { it.size }.distinctUntilChanged().collect {
                    analytics.register("habit_count", it)
                }
            }
            launch {
                sprig.state.map { it.streakDays }.distinctUntilChanged().collect {
                    analytics.register("streak", it)
                }
            }
        }
    }
}
