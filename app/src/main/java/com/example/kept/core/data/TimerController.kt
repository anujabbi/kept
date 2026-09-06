package com.example.kept.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped habit timer. Survives screen changes; persists progress to the habit entry every
 * 15 seconds so a process death loses at most 15 seconds.
 */
@Singleton
class TimerController @Inject constructor(
    private val actions: HabitActions,
    private val habits: HabitRepository,
    private val time: TimeSource,
) {
    data class State(
        val habitId: Long? = null,
        val running: Boolean = false,
        /** Seconds not yet flushed to the database. */
        val unflushedSeconds: Int = 0,
        val lastTickMillis: Long = 0,
        val justCompleted: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun start(habitId: Long) {
        val cur = _state.value
        if (cur.running && cur.habitId == habitId) return
        if (cur.habitId != null && cur.habitId != habitId) flushNow()
        _state.value = State(habitId = habitId, running = true, lastTickMillis = time.nowMillis())
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(1_000)
                val now = time.nowMillis()
                _state.update { it.copy(unflushedSeconds = it.unflushedSeconds + 1, lastTickMillis = now) }
                if (_state.value.unflushedSeconds >= 15) flush()
            }
        }
    }

    fun pause() {
        job?.cancel(); job = null
        _state.update { it.copy(running = false) }
        flushNow()
    }

    fun stop() {
        job?.cancel(); job = null
        flushNow()
        _state.value = State()
    }

    fun clearCompleted() = _state.update { it.copy(justCompleted = false) }

    private fun flushNow() = scope.launch { flush() }

    private suspend fun flush() {
        val s = _state.value
        val id = s.habitId ?: return
        val secs = s.unflushedSeconds
        if (secs <= 0) return
        _state.update { it.copy(unflushedSeconds = 0) }
        val event = actions.addTimerSeconds(id, secs)
        if (event is HabitEvent.Completed) {
            job?.cancel(); job = null
            _state.update { it.copy(running = false, justCompleted = true) }
        }
    }
}
