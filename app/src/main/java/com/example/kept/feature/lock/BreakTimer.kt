package com.example.kept.feature.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.example.kept.core.domain.BREAK_COUNTDOWN_SECONDS
import com.example.kept.core.domain.BreakCountdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The break screen's state: whether it is showing, its countdown, and whether Sprig's rise has
 * played. It lives in `LockViewModel`, with when the screen opened kept in [saved], so a rotation
 * (or process death) neither drops the break screen nor restarts its minute (issue #128). The
 * countdown reads [elapsedMs], a monotonic clock (`SystemClock.elapsedRealtime`), so changing the
 * phone's time cannot shorten it.
 */
class BreakTimer(
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val elapsedMs: () -> Long,
    private val total: Int = BREAK_COUNTDOWN_SECONDS,
) {
    private var startedAt by mutableStateOf(saved.get<Long>(KEY_STARTED_AT))

    /** True while the break screen is up. */
    val breaking: Boolean get() = startedAt != null

    /** Seconds until "Unlock" is allowed; [total] when no break is running. */
    var secondsLeft by mutableIntStateOf(startedAt?.let { BreakCountdown.secondsLeft(it, elapsedMs(), total) } ?: total)
        private set

    /** False until Sprig's rise has started once for this break; a recreated screen does not replay it. */
    var introPlayed by mutableStateOf(saved.get<Boolean>(KEY_INTRO_PLAYED) ?: false)
        private set

    private var job: Job? = null

    init {
        if (breaking) tick()
    }

    /** "Break the lock": the screen opens and the minute starts now. Returns false if it was already open. */
    fun start(): Boolean {
        if (breaking) return false
        val now = elapsedMs()
        startedAt = now
        saved[KEY_STARTED_AT] = now
        setIntro(false)
        tick()
        return true
    }

    /** "Never mind, go back" or Back: the screen closes; the next break starts from a full minute. */
    fun cancel() {
        job?.cancel()
        startedAt = null
        saved.remove<Long>(KEY_STARTED_AT)
        secondsLeft = total
        setIntro(false)
    }

    fun markIntroPlayed() = setIntro(true)

    /** Whole seconds the screen has been open. */
    fun secondsWaited(): Int = startedAt?.let { BreakCountdown.secondsWaited(it, elapsedMs()) } ?: 0

    private fun setIntro(played: Boolean) {
        introPlayed = played
        saved[KEY_INTRO_PLAYED] = played
    }

    private fun tick() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                val start = startedAt ?: return@launch
                val now = elapsedMs()
                secondsLeft = BreakCountdown.secondsLeft(start, now, total)
                if (secondsLeft == 0) return@launch
                delay(BreakCountdown.untilNextTickMs(start, now))
            }
        }
    }

    private companion object {
        const val KEY_STARTED_AT = "break_started_at"
        const val KEY_INTRO_PLAYED = "break_intro_played"
    }
}
