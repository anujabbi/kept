package com.example.kept.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of "now" so tests can pin the clock. */
@Singleton
class TimeSource @Inject constructor(private val clock: Clock) {
    fun now(): Instant = clock.instant()
    fun nowMillis(): Long = clock.millis()
    fun zone(): ZoneId = clock.zone
    fun today(): LocalDate = LocalDate.now(clock)
    fun todayKey(): String = today().toString()
    fun localTime(): LocalTime = LocalTime.now(clock)
    fun localDateTime(): LocalDateTime = LocalDateTime.now(clock)
    fun minuteOfDay(): Int = localTime().let { it.hour * 60 + it.minute }

    fun instantAt(date: LocalDate, minuteOfDay: Int): Instant =
        date.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(clock.zone).toInstant()

    /** Emits the local date now and again whenever it changes (checked every 20 s). */
    fun observeToday(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(20_000)
        }
    }.distinctUntilChanged()

    /** Emits every [periodMillis]; used for live timers. */
    fun ticker(periodMillis: Long = 1_000): Flow<Long> = flow {
        while (true) {
            emit(nowMillis())
            delay(periodMillis)
        }
    }
}
