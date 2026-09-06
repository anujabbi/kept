package com.example.kept.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.sprig.SprigView
import com.example.kept.feature.home.HabitRow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun LockScreen(
    state: LockUi,
    blockedLabel: String,
    onDoHabit: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onBreak: () -> Unit,
    onEmergency: () -> Unit,
    onOpenKept: () -> Unit,
) {
    val c = KeptTheme.colors
    val lock = state.lock
    Column(
        Modifier.fillMaxSize().background(c.surface1).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("lock_screen"),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm")), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Text(if (blockedLabel.isBlank()) "Locked" else "$blockedLabel is locked", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(14.dp))

        KeptCard(background = c.purple50, border = null, shape = RoundedCornerShape(16.dp), padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SprigView(state.sprig.form, SprigPose.BLOCK, Modifier.size(150.dp), variant = state.variant, wilted = state.sprig.wilted)
                Spacer(Modifier.height(8.dp))
                val mins = state.minutesToGo
                val headline = when {
                    mins != null -> "$mins minute${if (mins == 1) "" else "s"} left to go"
                    state.remaining == 1 -> "One thing left"
                    else -> "${state.remaining} things left"
                }
                Text(headline, style = MaterialTheme.typography.titleMedium, color = c.purple900)
                Text("Sprig grows the whole time you're away", style = MaterialTheme.typography.bodySmall, color = c.purple600, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(14.dp))

        lock?.today?.habits?.forEach { h ->
            HabitRow(h, onOpenTimer = { onDoHabit(h.id) }, onComplete = { onComplete(h.id) }, onUndo = {}, onPhoto = { onOpenKept() })
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))

        val timerHabit = lock?.today?.habits?.firstOrNull { !it.isDone && it.habit.proofType == ProofType.TIMER }
        if (timerHabit != null) {
            PrimaryButton(if (timerHabit.progress > 0) "Resume timer" else "Start ${timerHabit.habit.title.lowercase()}", onClick = { onDoHabit(timerHabit.id) })
        } else {
            PrimaryButton("Open KEPT", onClick = onOpenKept)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            QuietButton("Break lock", onClick = onBreak, modifier = Modifier.testTag("break_lock"))
            QuietButton("Emergency call", onClick = onEmergency, modifier = Modifier.testTag("emergency_call"))
        }
        Spacer(Modifier.height(16.dp))
    }
}
