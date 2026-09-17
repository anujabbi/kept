package com.example.kept.core.data

import com.example.kept.core.analytics.Analytics
import com.example.kept.core.data.db.DayRecordDao
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitDao
import com.example.kept.core.data.db.HabitEntryDao
import com.example.kept.core.data.db.LockBreakDao
import com.example.kept.core.data.db.ProtectionGapDao
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.GiveUpTime
import com.example.kept.core.domain.RolloverEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Something that can show the end-of-day recap notification. Implemented in the Android layer. */
fun interface RecapNotifier {
    fun showRecap(summary: RolloverEngine.DaySummary, unlocks: List<RolloverEngine.Unlock>)
}

/**
 * Runs the rollover for every day that has passed since the last one. Safe to call often: it is
 * idempotent and serialized.
 */
@Singleton
class RolloverRunner @Inject constructor(
    private val prefs: KeptPreferences,
    private val habitDao: HabitDao,
    private val entryDao: HabitEntryDao,
    private val dayDao: DayRecordDao,
    private val breakDao: LockBreakDao,
    private val gapDao: ProtectionGapDao,
    private val sprig: SprigRepository,
    private val time: TimeSource,
    private val recap: RecapNotifier,
    private val analytics: Analytics,
) {
    private val mutex = Mutex()

    /** Returns the outputs produced, newest last. Empty when nothing was pending. */
    suspend fun runPending(notify: Boolean = true): List<RolloverEngine.Output> = mutex.withLock {
        val settings = prefs.currentSettings()
        if (!settings.onboardingDone) return emptyList()
        val firstUse = settings.firstUseDate ?: time.today()
        val yesterday = time.today().minusDays(1)
        var state = prefs.currentSprig()
        val pending = RolloverEngine.pendingDates(state.lastRolloverDate, yesterday, firstUse)
        val outputs = mutableListOf<RolloverEngine.Output>()
        for (date in pending) {
            val input = buildInput(date, settings.lockFromMinute, settings.dueMinute)
            val out = RolloverEngine.rollover(state, input)
            persist(out)
            report(out)
            state = out.state
            outputs += out
        }
        // Make sure today has a live record so the service can accumulate into it.
        dayDao.insertIfAbsent(DayRecordEntity(date = time.todayKey(), habitsTotal = habitDao.active().size))
        if (notify && outputs.isNotEmpty()) {
            val last = outputs.last()
            recap.showRecap(last.summary, outputs.flatMap { it.unlocks })
            prefs.updateSettings { it.copy(pendingRecapDate = last.summary.date.toString()) }
        }
        outputs
    }

    /**
     * The give-up time used is the one in effect right now, not the one that was set while the day
     * was running: a day's completions are judged against the current setting (issue #7). A lock
     * window that wraps midnight expires the next morning, which [GiveUpTime] resolves.
     */
    private suspend fun buildInput(date: LocalDate, lockFromMinute: Int, dueMinute: Int): RolloverEngine.DayInput {
        val key = date.toString()
        val record = dayDao.get(key)
        val habitsTotal = record?.habitsTotal?.takeIf { it > 0 } ?: habitDao.active().size
        val entries = entryDao.forDate(key)
        val dueMillis = GiveUpTime.instantFor(date, lockFromMinute, dueMinute, time.zone()).toEpochMilli()
        val done = entries.count { it.completedAt != null }
        val doneBeforeDue = entries.count { it.completedAt?.let { at -> at < dueMillis } == true }
        val gaps = gapDao.countForDate(key)
        val breaks = breakDao.since(0).filter { it.date == key }
        return RolloverEngine.DayInput(
            date = date,
            habitsDone = done,
            habitsDoneBeforeDue = doneBeforeDue,
            habitsTotal = habitsTotal,
            lockedMillis = record?.lockedMillis ?: 0,
            breaksUsed = breaks.size,
            writtenOff = breaks.any { it.overCap } || (record?.writtenOff ?: false),
            unprotected = gaps > 0 || (record?.unprotected ?: false),
        )
    }

    /**
     * One `rollover` per day closed, not per run: a phone that was off for three days closes three
     * days and reports three (issue #10). `kept` is whether the day counted toward the streak.
     */
    private fun report(out: RolloverEngine.Output) {
        val s = out.summary
        analytics.capture(
            "rollover",
            mapOf(
                "kept" to s.countedForStreak,
                "protected" to !s.unprotected,
                "streak" to s.streakEnd,
                "form" to s.formEnd.name.lowercase(),
            ),
        )
        if (out.formAfter != out.formBefore) {
            analytics.capture("form_evolved", mapOf("from" to out.formBefore.name.lowercase(), "to" to out.formAfter.name.lowercase()))
        }
    }

    private suspend fun persist(out: RolloverEngine.Output) {
        val s = out.summary
        dayDao.upsert(
            DayRecordEntity(
                date = s.date.toString(),
                habitsDone = s.habitsDone,
                habitsTotal = s.habitsTotal,
                lockedMillis = s.lockedMillis,
                breaksUsed = s.breaksUsed,
                unprotected = s.unprotected,
                broken = s.broken,
                writtenOff = s.writtenOff,
                countedForStreak = s.countedForStreak,
                shieldConsumed = s.shieldConsumed,
                levelEnd = s.levelEnd,
                formId = s.formEnd.id,
                streakEnd = s.streakEnd,
                finalized = true,
            ),
        )
        out.unlocks.forEach { sprig.insertUnlock(it.form, it.variant, it.date, it.streakAtUnlock) }
        sprig.write(out.state)
    }
}
