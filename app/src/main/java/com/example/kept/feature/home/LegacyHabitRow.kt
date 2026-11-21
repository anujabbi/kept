package com.example.kept.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.kept.core.data.HabitToday
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.rememberHaptics
import java.io.File

// The pre-Daybreak habit row. Home no longer uses it (it uses DaybreakHabitRow); the lock
// screen still does until it is redesigned in #127, which removes this file.

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
    val haptics = rememberHaptics()
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingPhoto
        if (ok && f != null) {
            haptics.habitTicked()
            onPhoto(f.absolutePath)
        }
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
            h.habit.proofType == ProofType.MANUAL -> {
                haptics.habitTicked()
                onComplete()
            }
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
