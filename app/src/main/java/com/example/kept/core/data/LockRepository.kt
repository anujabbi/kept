package com.example.kept.core.data

import com.example.kept.core.data.db.AllowedAppDao
import com.example.kept.core.data.db.AllowedAppEntity
import com.example.kept.core.data.db.DayRecordDao
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.LockBreakDao
import com.example.kept.core.data.db.LockBreakEntity
import com.example.kept.core.data.db.ProtectionGapDao
import com.example.kept.core.data.db.ProtectionGapEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.BreakCap
import com.example.kept.core.domain.LockPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the enforcement layer and the UI need to know about the lock right now. */
data class LockState(
    val settings: Settings,
    val today: TodaySummary,
    val breakActiveUntil: Instant?,
    val breaksUsedThisWeek: Int,
    val userExceptions: Set<String>,
    val unprotectedToday: Boolean,
    /** The active weekly goals with this week's state (issue #165). Empty with no goal. */
    val weekly: List<WeeklyGoalWeek> = emptyList(),
) {
    val breaksRemaining: Int get() = maxOf(0, BreakCap.MAX_PER_ROLLING_WEEK - breaksUsedThisWeek)
    val nextBreakOverCap: Boolean get() = breaksUsedThisWeek >= BreakCap.MAX_PER_ROLLING_WEEK

    /** The goals whose must-do session is not logged today, in the goals' order (issue #165). */
    val weeklyMustDo: List<WeeklyGoalWeek> get() = weekly.filter { it.week.isMustDay }

    /** Any active goal whose today is a must-do day with no session yet: it holds the daily lock. */
    val weeklyMustDoIncomplete: Boolean get() = weekly.any { it.week.isMustDay }

    /** A habit is left today: the other thing that holds the daily lock. */
    val habitsIncomplete: Boolean get() = !today.allDone && today.total > 0

    fun snapshot(hardAllowlist: Set<String>, launchable: Set<String>?): LockPolicy.Snapshot = LockPolicy.Snapshot(
        lockFromMinute = settings.lockFromMinute,
        dueMinute = settings.dueMinute,
        habitsIncomplete = habitsIncomplete,
        breakActiveUntil = breakActiveUntil,
        hardAllowlist = hardAllowlist,
        userExceptions = userExceptions,
        launchable = launchable,
        onboardingDone = settings.onboardingDone,
        nightEnabled = settings.nightLockEnabled,
        nightFromMinute = settings.nightFromMinute,
        nightUntilMinute = settings.nightUntilMinute,
        weeklyMustDoIncomplete = weeklyMustDoIncomplete,
    )

    /**
     * True when a lock is in force right now: the daily lock (window open, a habit or a weekly
     * goal's must-do session still undone) or night lockdown (issue #164), and no active break. Used to gate the "are you
     * sure" confirmation on lock-affecting settings changes
     * (issue #1): which package is foreground is irrelevant here, so [hardAllowlist] and
     * [launchable] play no part in [LockPolicy.isLockActive] and are passed empty/null.
     */
    fun isLockActiveNow(now: Instant, localTime: LocalTime): Boolean =
        LockPolicy.isLockActive(now, localTime, snapshot(hardAllowlist = emptySet(), launchable = null))

    /**
     * The daily lock alone: habits and must-do sessions. A change to the lock window, the habits
     * or a weekly goal can only open apps early while this one holds; during night lockdown alone it opens nothing (issue #164).
     */
    fun isDailyLockActiveNow(now: Instant, localTime: LocalTime): Boolean =
        LockPolicy.isDailyLockActive(now, localTime, snapshot(hardAllowlist = emptySet(), launchable = null))

    /** Night lockdown alone: what the Night lockdown group's own confirm asks about. */
    fun isNightLockActiveNow(now: Instant, localTime: LocalTime): Boolean =
        LockPolicy.isNightLockActive(now, localTime, snapshot(hardAllowlist = emptySet(), launchable = null))
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LockRepository @Inject constructor(
    private val allowedDao: AllowedAppDao,
    private val breakDao: LockBreakDao,
    private val gapDao: ProtectionGapDao,
    private val dayDao: DayRecordDao,
    private val prefs: KeptPreferences,
    private val habits: HabitRepository,
    private val sprig: SprigRepository,
    private val time: TimeSource,
    private val weeklyGoals: WeeklyGoalRepository,
) {
    fun observeExceptions(): Flow<List<AllowedAppEntity>> = allowedDao.observeAll()

    suspend fun addException(pkg: String, label: String) =
        allowedDao.insert(AllowedAppEntity(pkg, label, time.nowMillis()))

    suspend fun removeException(pkg: String) = allowedDao.delete(pkg)

    fun observeBreaksThisWeek(): Flow<List<LockBreakEntity>> =
        time.ticker(60_000).flatMapLatest { now -> breakDao.observeSince(now - BreakCap.WINDOW.toMillis()) }

    fun observeState(): Flow<LockState> = combine(
        prefs.settings,
        habits.observeToday(),
        breakDao.observeLatest(),
        observeBreaksThisWeek(),
        allowedDao.observeAll(),
    ) { settings, today, latestBreak, weekBreaks, exceptions ->
        val until = latestBreak?.unlockedUntil?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(time.now()) }
        LockState(
            settings = settings,
            today = today,
            breakActiveUntil = until,
            breaksUsedThisWeek = weekBreaks.size,
            userExceptions = exceptions.map { it.packageName }.toSet(),
            unprotectedToday = false,
        )
    }.combine(gapDao.observeForDate(time.todayKey())) { s, gaps -> s.copy(unprotectedToday = gaps.isNotEmpty()) }
        // Emits again when a session is logged or undone, a goal is edited or removed, and on a
        // new day, so the lock follows a weekly goal as it follows a habit (issue #165).
        .combine(weeklyGoals.observeThisWeek()) { s, weekly -> s.copy(weekly = weekly) }

    suspend fun currentState(): LockState = observeState().first()

    /** Records a break: unlocks apps for the configured duration, costs a level, wilts Sprig. */
    suspend fun breakLock(): LockBreakEntity {
        val now = time.now()
        val settings = prefs.currentSettings()
        val recent = breakDao.since(now.toEpochMilli() - BreakCap.WINDOW.toMillis()).map { Instant.ofEpochMilli(it.timestamp) }
        val overCap = BreakCap.nextBreakIsOverCap(recent, now)
        val until = now.plusSeconds(settings.breakDurationMin * 60L)
        val date = time.todayKey()
        val entity = LockBreakEntity(
            timestamp = now.toEpochMilli(), unlockedUntil = until.toEpochMilli(),
            date = date, levelCost = 1, overCap = overCap,
        )
        breakDao.insert(entity)
        dayDao.insertIfAbsent(DayRecordEntity(date = date))
        dayDao.recordBreak(date, writtenOff = overCap)
        sprig.onLockBreak()
        return entity
    }

    /** Ends an active break early (user chose to re-lock). */
    suspend fun endBreakEarly() {
        val latest = breakDao.observeLatest().first() ?: return
        if (latest.unlockedUntil > time.nowMillis()) breakDao.setUnlockedUntil(latest.id, time.nowMillis())
    }

    suspend fun recordProtectionGap(startMillis: Long, endMillis: Long, reason: String) {
        val date = time.todayKey()
        gapDao.insert(ProtectionGapEntity(date = date, startMillis = startMillis, endMillis = endMillis, reason = reason))
        dayDao.insertIfAbsent(DayRecordEntity(date = date))
        dayDao.markUnprotected(date)
    }

    fun observeGapsToday(): Flow<List<ProtectionGapEntity>> =
        time.observeToday().flatMapLatest { gapDao.observeForDate(it.toString()) }

    fun observeBreakActive(): Flow<Instant?> = combine(breakDao.observeLatest(), time.ticker(1_000)) { b, now ->
        b?.unlockedUntil?.takeIf { it > now }?.let(Instant::ofEpochMilli)
    }
}
