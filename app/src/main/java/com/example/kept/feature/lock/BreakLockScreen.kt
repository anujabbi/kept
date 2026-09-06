package com.example.kept.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.domain.LevelRules
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.sprig.SprigView
import kotlinx.coroutines.delay

const val BREAK_COUNTDOWN_SECONDS = 60

@Composable
fun BreakLockScreen(state: LockUi, onCancel: () -> Unit, onConfirm: () -> Unit, countdownSeconds: Int = BREAK_COUNTDOWN_SECONDS) {
    val c = KeptTheme.colors
    var left by remember { mutableIntStateOf(countdownSeconds) }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1_000); left -= 1 }
    }
    val newLevel = LevelRules.down(state.sprig.level)
    val streak = state.sprig.streakDays

    Column(
        Modifier.fillMaxSize().background(c.surface1).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp).testTag("break_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Break the lock", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(22.dp))
        SprigView(state.sprig.form, SprigPose.DROOP, Modifier.size(150.dp), variant = state.variant, wilted = true, animate = false)
        Spacer(Modifier.height(18.dp))
        Text(
            if (state.sprig.level > 1) "This drops Sprig to level $newLevel" else "Sprig will wilt",
            style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        val body = when {
            state.overCap -> "This is your fourth break this week, the last resort. Today is written off: it won't count toward your ${streak}-day streak, and your shield can't save it."
            streak > 0 -> "Your $streak-day streak stays. Sprig wilts until tomorrow's habits are done. You have ${state.breaksLeft} break${if (state.breaksLeft == 1) "" else "s"} left this week."
            else -> "Sprig wilts until tomorrow's habits are done. You have ${state.breaksLeft} break${if (state.breaksLeft == 1) "" else "s"} left this week."
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        KeptCard(Modifier.fillMaxWidth(), background = c.surface2, border = c.border) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (left > 0) "Unlocking in" else "Apps unlock for ${state.breakMinutes} min", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "%d:%02d".format(left / 60, left % 60),
                    fontFamily = FontFamily.Monospace, fontSize = 36.sp, fontWeight = FontWeight.Medium,
                    color = if (left > 0) c.textPrimary else c.danger,
                    modifier = Modifier.testTag("break_countdown"),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Never mind, go back", onClick = onCancel)
        Spacer(Modifier.height(4.dp))
        QuietButton(
            if (left > 0) "Unlock anyway (${left}s)" else "Unlock anyway",
            onClick = onConfirm, enabled = left == 0, color = c.danger,
            modifier = Modifier.testTag("unlock_anyway"),
        )
        Spacer(Modifier.weight(1f))
    }
}
