package com.example.kept.core.data

import com.example.kept.core.data.db.DayRecordDao
import com.example.kept.core.data.db.DayRecordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DayRepository @Inject constructor(
    private val dayDao: DayRecordDao,
    private val time: TimeSource,
) {
    fun observeToday(): Flow<DayRecordEntity?> = time.observeToday().flatMapLatest { dayDao.observe(it.toString()) }

    fun observe(date: LocalDate): Flow<DayRecordEntity?> = dayDao.observe(date.toString())

    suspend fun get(date: LocalDate): DayRecordEntity? = dayDao.get(date.toString())

    suspend fun ensureToday(habitsTotal: Int) {
        dayDao.insertIfAbsent(DayRecordEntity(date = time.todayKey(), habitsTotal = habitsTotal))
    }

    fun observeHistory(limit: Int = 60): Flow<List<DayRecordEntity>> = dayDao.observeHistory(limit)

    /** Last seven days ending today (inclusive), oldest first; missing days are null. */
    fun observeLastSeven(): Flow<List<Pair<LocalDate, DayRecordEntity?>>> = time.observeToday().flatMapLatest { today ->
        val from = today.minusDays(6)
        kotlinx.coroutines.flow.combine(
            dayDao.observeBetween(from.toString(), today.toString()),
            kotlinx.coroutines.flow.flowOf(today),
        ) { records, t ->
            val byDate = records.associateBy { it.date }
            (0..6).map { i -> val d = from.plusDays(i.toLong()); d to byDate[d.toString()] }
        }
    }
}
