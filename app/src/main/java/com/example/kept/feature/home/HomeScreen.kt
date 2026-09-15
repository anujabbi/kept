package com.example.kept.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.HabitToday
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.minuteOfDayLabel
import com.example.kept.core.ui.DayDot
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.InfoBox
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.Pill
import com.example.kept.core.ui.SectionLabel
import com.example.kept.core.ui.ThinProgress
import com.example.kept.core.ui.WeekDots
import com.example.kept.core.ui.sprig.SprigView
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenRecap: (String) -> Unit,
    onOpenReveal: (Long) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenRoadmap: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors

    // Surface a fresh evolution first, then a pending recap.
    LaunchedEffect(s.unseenUnlock?.id) { s.unseenUnlock?.let { onOpenReveal(it.id) } }
    LaunchedEffect(s.pendingRecapDate, s.unseenUnlock) {
        val d = s.pendingRecapDate
        if (d != null && s.unseenUnlock == null) { vm.dismissRecap(); onOpenRecap(d) }
    }

    if (!s.loaded) return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(s.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(s.date.format(DateTimeFormatter.ofPattern("d MMMM")), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.sprig.shieldAvailable) Pill("Shield", background = c.blue50, foreground = c.blue800, icon = Icons.Outlined.Shield)
                StreakPill(s.sprig.streakDays)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (s.usageAccessMissing) {
            KeptCard(background = c.dangerSoft, border = null, onClick = onOpenPermissions, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = c.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Lock is off", style = MaterialTheme.typography.titleSmall, color = c.danger)
                        Text("A required permission is missing. Today won't count until it's back on.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Text("Fix", style = MaterialTheme.typography.labelLarge, color = c.danger)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SprigPanel(s, onOpenRoadmap)
        Spacer(Modifier.height(20.dp))

        SectionLabel("Today", trailing = if (s.today.total > 0) "${s.today.done} of ${s.today.total}" else null)
        Spacer(Modifier.height(8.dp))
        if (s.pastDue && s.today.total > 0 && !s.today.allDoneOnTime) {
            InfoBox(
                "Too late for today. You can still tick these off, but today won't count.",
                icon = Icons.Outlined.HourglassBottom,
                background = c.surface2,
                foreground = c.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (s.today.total == 0) {
            KeptCard(onClick = onOpenHabits) {
                Text("No habits yet", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text("Add one or two. Apps lock until they're done.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        s.today.habits.forEach { h ->
            HabitRow(h, onComplete = { vm.complete(h.id) }, onUndo = { vm.undo(h.id) }, onPhoto = { vm.completeWithPhoto(h.id, it) })
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))
        LockStatusBox(s)
        Spacer(Modifier.height(20.dp))

        SectionLabel("This week", trailing = s.lastSeven.firstOrNull()?.first?.format(DateTimeFormatter.ofPattern("d MMM"))?.let { "$it – today" })
        Spacer(Modifier.height(8.dp))
        KeptCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                WeekDots(s.lastSeven.map { (d, r) -> dotFor(d == s.date, r, s.today.allDoneOnTime) }, c.purple400)
                Text("Best ${s.sprig.bestStreak}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            // A hollow square is a day KEPT could not watch. It is explained rather than left to
            // look like a miss, because it costs nothing (issue #2).
            if (s.lastSeven.any { (d, r) -> d != s.date && r != null && r.unprotected && !r.writtenOff }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hollow days are days the lock was off. They don't count, and they don't break your streak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StreakPill(streak: Int) {
    val c = KeptTheme.colors
    val scale by animateFloatAsState(if (streak > 0) 1f else 0.96f, tween(300), label = "streak")
    Pill(
        if (streak == 1) "1 day streak" else "$streak day streak",
        Modifier.scale(scale),
        background = if (streak > 0) c.amber50 else c.surface0,
        foreground = if (streak > 0) c.amber800 else c.textMuted,
        icon = Icons.Rounded.LocalFireDepartment,
    )
}

@Composable
private fun SprigPanel(s: HomeUiState, onOpenRoadmap: () -> Unit) {
    val c = KeptTheme.colors
    // Only a day finished before the give-up time is worth cheering about (issue #7).
    val allDone = s.today.allDoneOnTime
    val pose = when {
        s.sprig.wilted -> SprigPose.DROOP
        allDone -> SprigPose.CHEER
        else -> SprigPose.IDLE
    }
    val bg = when {
        s.sprig.wilted -> c.surface0
        allDone -> c.teal50
        else -> c.purple50
    }
    val fg = when {
        s.sprig.wilted -> c.textSecondary
        allDone -> c.teal900
        else -> c.purple900
    }
    val sub = when {
        s.sprig.wilted -> c.textMuted
        allDone -> c.teal600
        else -> c.purple600
    }
    KeptCard(background = bg, border = null, shape = RoundedCornerShape(16.dp), onClick = onOpenRoadmap, padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            SprigView(s.form, pose, Modifier.size(170.dp), variant = s.variant, wilted = s.sprig.wilted)
            Spacer(Modifier.height(10.dp))
            val headline = when {
                s.sprig.wilted -> "Sprig is wilting"
                allDone -> "Promise kept today"
                s.today.anyLate && s.today.allDone -> "Ticked, but too late"
                s.lockActive -> "Sprig is growing"
                else -> "Sprig is ${if (s.form.streakThreshold >= 7) "thriving" else "stretching"}"
            }
            Text(headline, style = MaterialTheme.typography.titleMedium, color = fg)
            Spacer(Modifier.height(2.dp))
            val detail = when {
                s.sprig.wilted -> "Finish today's habits and Sprig recovers."
                s.today.anyLate && s.today.allDone ->
                    "The give-up time passed, so today doesn't count. Start earlier tomorrow."
                s.nextForm == null -> "${s.form.displayName} · Lv ${s.sprig.level} · the final form"
                else -> "${s.form.displayName} · Lv ${s.sprig.level} · ${s.daysToNext} day${if (s.daysToNext == 1) "" else "s"} to ${s.nextForm!!.displayName}"
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = sub, textAlign = TextAlign.Center)
            if (s.nextForm != null && !s.sprig.wilted) {
                Spacer(Modifier.height(12.dp))
                val prev = s.form.streakThreshold
                val next = s.nextForm!!.streakThreshold
                val frac = ((s.sprig.streakDays - prev).toFloat() / (next - prev).toFloat()).coerceIn(0.02f, 1f)
                ThinProgress(frac, Modifier.fillMaxWidth(0.6f), color = if (allDone) c.teal600 else c.purple400, track = Color.White.copy(alpha = if (c.isDark) 0.08f else 0.6f), height = 4.dp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(
    h: HabitToday,
    onComplete: () -> Unit,
    onUndo: () -> Unit,
    onPhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KeptTheme.colors
    val ctx = LocalContext.current
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhoto
        if (ok && f != null) onPhoto(f.absolutePath)
        pendingPhoto = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = newProofFile(ctx)
            pendingPhoto = file
            takePicture.launch(uri)
        }
    }

    val onClick: () -> Unit = {
        when {
            h.isDone -> Unit
            h.habit.proofType == ProofType.MANUAL -> onComplete()
            h.habit.proofType == ProofType.PHOTO -> cameraPermission.launch(android.Manifest.permission.CAMERA)
        }
    }

    // A tick after the give-up time still shows as done, but not as a win.
    val counts = !h.late
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(if (h.isDone && counts) c.green50 else c.surface2)
            .then(if (h.isDone) Modifier else Modifier.background(Color.Transparent))
            .combinedClickable(onClick = onClick, onLongClick = { if (h.isDone) onUndo() })
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (h.isDone && counts) c.green400.copy(alpha = 0.25f) else c.purple50), contentAlignment = Alignment.Center) {
            Icon(if (h.isDone) Icons.Outlined.Check else HabitIcons.of(h.habit.iconKey), null, tint = if (h.isDone && counts) c.green600 else if (h.isDone) c.textMuted else c.purple600, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${h.habit.title} ${h.subtitle}", style = MaterialTheme.typography.titleSmall, color = if (h.isDone && counts) c.green600 else c.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    if (h.late) "Too late" else if (h.isDone) "Done" else h.progressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (h.isDone && counts) c.green600 else c.textMuted,
                )
            }
            if (!h.isDone) {
                Text(
                    when (h.habit.proofType) { ProofType.MANUAL -> "Tap when done"; ProofType.PHOTO -> "Tap to snap a photo" },
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
            } else {
                Text(
                    if (h.late) "Doesn't count today. Hold to undo." else "Hold to undo",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (counts) c.green600.copy(alpha = 0.7f) else c.textMuted,
                )
            }
        }
        if (!h.isDone) {
            Spacer(Modifier.width(8.dp))
            Icon(
                when (h.habit.proofType) { ProofType.PHOTO -> Icons.Outlined.CameraAlt; ProofType.MANUAL -> Icons.Outlined.Check },
                null, tint = c.textMuted, modifier = Modifier.size(20.dp),
            )
        }
    }
}

fun newProofFile(ctx: Context): Pair<File, Uri> {
    val dir = File(ctx.filesDir, "proof").apply { mkdirs() }
    val file = File(dir, "proof_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    return file to uri
}

@Composable
private fun LockStatusBox(s: HomeUiState) {
    val c = KeptTheme.colors
    val lock = s.lock ?: return
    val due = lock.settings.dueMinute.minuteOfDayLabel()
    val from = lock.settings.lockFromMinute.minuteOfDayLabel()
    val remaining = s.today.remaining
    val (icon, text) = when {
        s.today.total == 0 -> Icons.Outlined.LockOpen to "Nothing to lock for. Add a habit."
        s.today.allDoneOnTime -> Icons.Outlined.LockOpen to "Apps are open. You kept today."
        s.pastDue -> Icons.Outlined.LockOpen to "Apps are open. Today is missed — the lock is back at $from."
        lock.breakActiveUntil != null -> Icons.Outlined.LockOpen to "Lock paused. Apps re-lock soon. Sprig noticed."
        s.lockActive -> Icons.Outlined.Lock to "Everything but essentials is locked until ${if (remaining == 1) "this is" else "both are"} done or $due."
        else -> Icons.Outlined.Lock to "Apps lock at $from until today's habits are done."
    }
    InfoBox(text, icon = icon, background = if (s.today.allDone) c.teal50 else c.surface2, foreground = if (s.today.allDone) c.teal600 else c.textSecondary)
}

private fun dotFor(isToday: Boolean, r: com.example.kept.core.data.db.DayRecordEntity?, allDoneToday: Boolean): DayDot = when {
    isToday -> if (allDoneToday) DayDot.DONE else DayDot.TODAY
    r == null -> DayDot.MISSED
    r.unprotected && !r.writtenOff -> DayDot.UNPROTECTED
    r.countedForStreak -> DayDot.DONE
    r.shieldConsumed -> DayDot.SHIELDED
    else -> DayDot.MISSED
}
