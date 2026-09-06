package com.example.kept.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kept.core.domain.SprigState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kept_prefs")

data class Settings(
    val onboardingDone: Boolean = false,
    val onboardingStep: Int = 0,
    val lockFromMinute: Int = 7 * 60,
    val dueMinute: Int = 21 * 60,
    val breakDurationMin: Int = 30,
    val firstUseDate: LocalDate? = null,
    val remindersEnabled: Boolean = true,
    val seeded: Boolean = false,
    val lastServiceHeartbeat: Long = 0,
    val pendingRecapDate: String? = null,
    val myInviteCode: String = "",
)

@Singleton
class KeptPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val ONBOARDING_STEP = intPreferencesKey("onboarding_step")
        val LOCK_FROM = intPreferencesKey("lock_from_minute")
        val DUE = intPreferencesKey("due_minute")
        val BREAK_MIN = intPreferencesKey("break_duration_min")
        val FIRST_USE = stringPreferencesKey("first_use_date")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val SEEDED = booleanPreferencesKey("seeded")
        val HEARTBEAT = longPreferencesKey("service_heartbeat")
        val PENDING_RECAP = stringPreferencesKey("pending_recap_date")
        val INVITE_CODE = stringPreferencesKey("my_invite_code")

        val LEVEL = intPreferencesKey("sprig_level")
        val POINTS = longPreferencesKey("sprig_points")
        val STREAK = intPreferencesKey("sprig_streak")
        val BEST_STREAK = intPreferencesKey("sprig_best_streak")
        val SHIELD = booleanPreferencesKey("sprig_shield")
        val SHIELD_WEEK = stringPreferencesKey("sprig_shield_week")
        val LAST_ROLLOVER = stringPreferencesKey("sprig_last_rollover")
        val WILTED = booleanPreferencesKey("sprig_wilted")
        val PAIR_STREAK = intPreferencesKey("sprig_pair_streak")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            onboardingDone = p[K.ONBOARDING_DONE] ?: false,
            onboardingStep = p[K.ONBOARDING_STEP] ?: 0,
            lockFromMinute = p[K.LOCK_FROM] ?: (7 * 60),
            dueMinute = p[K.DUE] ?: (21 * 60),
            breakDurationMin = p[K.BREAK_MIN] ?: 30,
            firstUseDate = p[K.FIRST_USE]?.let(LocalDate::parse),
            remindersEnabled = p[K.REMINDERS] ?: true,
            seeded = p[K.SEEDED] ?: false,
            lastServiceHeartbeat = p[K.HEARTBEAT] ?: 0L,
            pendingRecapDate = p[K.PENDING_RECAP],
            myInviteCode = p[K.INVITE_CODE] ?: "",
        )
    }

    val sprigState: Flow<SprigState> = context.dataStore.data.map { p ->
        SprigState(
            level = p[K.LEVEL] ?: 1,
            points = p[K.POINTS] ?: 0L,
            streakDays = p[K.STREAK] ?: 0,
            bestStreak = p[K.BEST_STREAK] ?: 0,
            shieldAvailable = p[K.SHIELD] ?: true,
            shieldWeekKey = p[K.SHIELD_WEEK] ?: "",
            lastRolloverDate = p[K.LAST_ROLLOVER]?.let(LocalDate::parse),
            wilted = p[K.WILTED] ?: false,
            pairStreak = p[K.PAIR_STREAK] ?: 0,
        )
    }

    suspend fun currentSettings(): Settings = settings.first()
    suspend fun currentSprig(): SprigState = sprigState.first()

    suspend fun updateSettings(block: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = settings.first()
            val n = block(cur)
            p[K.ONBOARDING_DONE] = n.onboardingDone
            p[K.ONBOARDING_STEP] = n.onboardingStep
            p[K.LOCK_FROM] = n.lockFromMinute
            p[K.DUE] = n.dueMinute
            p[K.BREAK_MIN] = n.breakDurationMin
            n.firstUseDate?.let { p[K.FIRST_USE] = it.toString() }
            p[K.REMINDERS] = n.remindersEnabled
            p[K.SEEDED] = n.seeded
            p[K.HEARTBEAT] = n.lastServiceHeartbeat
            if (n.pendingRecapDate == null) p.remove(K.PENDING_RECAP) else p[K.PENDING_RECAP] = n.pendingRecapDate
            p[K.INVITE_CODE] = n.myInviteCode
        }
    }

    suspend fun updateSprig(block: (SprigState) -> SprigState) {
        context.dataStore.edit { p ->
            val cur = sprigState.first()
            val n = block(cur)
            p[K.LEVEL] = n.level
            p[K.POINTS] = n.points
            p[K.STREAK] = n.streakDays
            p[K.BEST_STREAK] = n.bestStreak
            p[K.SHIELD] = n.shieldAvailable
            p[K.SHIELD_WEEK] = n.shieldWeekKey
            if (n.lastRolloverDate == null) p.remove(K.LAST_ROLLOVER) else p[K.LAST_ROLLOVER] = n.lastRolloverDate.toString()
            p[K.WILTED] = n.wilted
            p[K.PAIR_STREAK] = n.pairStreak
        }
    }

    suspend fun heartbeat(now: Long) {
        context.dataStore.edit { it[K.HEARTBEAT] = now }
    }
}
