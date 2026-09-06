package com.example.kept.feature.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.TimerController
import com.example.kept.core.domain.PointsRules
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.MutedText
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.SecondaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TimerUi(val habit: HabitToday? = null, val running: Boolean = false, val unflushed: Int = 0, val justCompleted: Boolean = false) {
    val elapsedSeconds: Int get() = (habit?.progress ?: 0) + unflushed
    val targetSeconds: Int get() = (habit?.habit?.targetValue ?: 0) * 60
    val remainingSeconds: Int get() = (targetSeconds - elapsedSeconds).coerceAtLeast(0)
    val fraction: Float get() = if (targetSeconds == 0) 0f else (elapsedSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f)
    val done: Boolean get() = habit?.isDone == true || justCompleted
}

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val timer: TimerController,
    savedState: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {
    private val habitId: Long = savedState.get<Long>("habitId") ?: 0L

    val state = combine(habits.observeHabit(habitId), timer.state) { h, t ->
        val mine = t.habitId == habitId
        TimerUi(h, running = mine && t.running, unflushed = if (mine) t.unflushedSeconds else 0, justCompleted = mine && t.justCompleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimerUi())

    fun start() = timer.start(habitId)
    fun pause() = timer.pause()
    fun acknowledgeDone() = timer.clearCompleted()
}

@Composable
fun TimerScreen(habitId: Long, onBack: () -> Unit, vm: TimerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    val h = s.habit

    // Auto-start on entry for a not-yet-done habit.
    LaunchedEffect(h?.id, h?.isDone) { if (h != null && !h.isDone && !s.running) vm.start() }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.textSecondary) }
            Text(h?.habit?.title ?: "", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Ring(fraction = s.fraction, done = s.done)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val m = s.remainingSeconds / 60; val sec = s.remainingSeconds % 60
                Text(
                    if (s.done) "Done" else "%d:%02d".format(m, sec),
                    fontFamily = FontFamily.Monospace, fontSize = if (s.done) 34.sp else 48.sp, fontWeight = FontWeight.Medium, color = if (s.done) c.teal600 else c.textPrimary,
                )
                Text(if (s.done) "Promise kept" else if (s.running) "remaining" else "paused", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(28.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Elapsed", "${s.elapsedSeconds / 60} min", Modifier.weight(1f))
            Stat("Finish bonus", "+${PointsRules.HABIT_BONUS}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        if (s.done) {
            PrimaryButton("Back to today", onClick = { vm.acknowledgeDone(); onBack() })
        } else if (s.running) {
            SecondaryButton("Pause", onClick = vm::pause)
        } else {
            PrimaryButton("Resume", onClick = vm::start)
        }
        Spacer(Modifier.height(12.dp))
        MutedText(if (s.done) "Nice. Sprig grew a little." else "Apps stay locked while this runs. Leaving this screen keeps the timer going.", Modifier.fillMaxWidth(), androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = KeptTheme.colors
    KeptCard(modifier, background = c.surface1, border = null) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
    }
}

@Composable
private fun Ring(fraction: Float, done: Boolean) {
    val c = KeptTheme.colors
    val f by animateFloatAsState(fraction, tween(600), label = "ring")
    val track = c.surface0; val fg = if (done) c.teal600 else c.purple400
    Canvas(Modifier.size(240.dp)) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(fg, -90f, 360f * f, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
