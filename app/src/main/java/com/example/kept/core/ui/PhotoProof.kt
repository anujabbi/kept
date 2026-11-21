package com.example.kept.core.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** A new file for a photo proof under `files/proof`, and its `FileProvider` URI for the camera. */
fun newProofFile(ctx: Context): Pair<File, Uri> {
    val dir = File(ctx.filesDir, "proof").apply { mkdirs() }
    val file = File(dir, "proof_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    return file to uri
}

/**
 * Ticking a photo habit, shared by Home and the lock screen so a proof is saved the same way
 * from both: ask for the camera permission, take the picture into [newProofFile], and when the
 * camera says the picture was taken, play the tick haptic and hand [onSaved] the file's path.
 * Returns the function the row calls on tap.
 */
@Composable
fun rememberPhotoTick(onSaved: (path: String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    // Saveable: the camera can take long enough for the activity to be recreated.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath
        if (ok && path != null) {
            haptics.habitTicked()
            onSaved(path)
        }
        pendingPath = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (file, uri) = newProofFile(ctx)
            pendingPath = file.absolutePath
            takePicture.launch(uri)
        }
    }
    return remember(cameraPermission) { { cameraPermission.launch(Manifest.permission.CAMERA) } }
}
