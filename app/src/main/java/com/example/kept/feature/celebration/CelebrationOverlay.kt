package com.example.kept.feature.celebration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.sprig.SprigView
import kotlinx.coroutines.delay

/** How long the moment holds before it takes itself away. */
private const val HOLD_MILLIS = 2_600L

/**
 * The full-screen "Promise kept." moment (issue #9). Hosted above the nav graph rather than inside
 * HomeScreen, which is already long and tangled, so it can also cover the Sprig or Settings tab
 * when a day is finished from a notification while one of those is open.
 */
@Composable
fun CelebrationHost(vm: CelebrationViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    AnimatedVisibility(s.visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(260))) {
        CelebrationOverlay(s, onDismiss = vm::dismiss)
    }
}

@Composable
private fun CelebrationOverlay(s: CelebrationUiState, onDismiss: () -> Unit) {
    val c = KeptTheme.colors
    val ctx = LocalContext.current
    val bounce = remember { Animatable(0.55f) }

    LaunchedEffect(Unit) {
        celebrate(ctx)
        bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
    }
    // Tap anywhere gets you out early; otherwise it leaves on its own.
    LaunchedEffect(Unit) {
        delay(HOLD_MILLIS)
        onDismiss()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.teal50)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SprigView(s.form, SprigPose.CHEER, Modifier.size(200.dp).scale(bounce.value), variant = s.variant)
            Spacer(Modifier.height(16.dp))
            Text("Promise kept.", style = MaterialTheme.typography.headlineMedium, color = c.teal900, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (s.streakDays > 0) "Day ${s.streakDays + 1} of your streak. Apps are open." else "Apps are open.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.teal600,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = if (c.isDark) 0.08f else 0.72f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                s.habitTitles.forEach { title ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Check, null, tint = c.green600, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Tap to carry on", style = MaterialTheme.typography.labelMedium, color = c.teal600)
        }
    }
}

/** A short double buzz. VIBRATE is already declared for the lock flow. */
private fun celebrate(ctx: Context) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)
        } ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 18, 60, 34), -1))
    }
}
