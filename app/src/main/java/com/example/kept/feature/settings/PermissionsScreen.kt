package com.example.kept.feature.settings

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.lock.Permissions
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.Pill
import com.example.kept.core.ui.SecondaryButton

data class PermissionCardInfo(val kind: PermissionKind, val title: String, val why: String, val required: Boolean)

val permissionCards = listOf(
    PermissionCardInfo(PermissionKind.USAGE_ACCESS, "Usage access", "Lets KEPT see which app is in front so it can lock it. Nothing is stored or shared.", true),
    PermissionCardInfo(PermissionKind.OVERLAY, "Display over other apps", "Lets the lock screen step in front of a locked app. Without it Android blocks KEPT from appearing.", true),
    PermissionCardInfo(PermissionKind.NOTIFICATIONS, "Notifications", "The lock needs a quiet ongoing notification to stay alive. Also the daily recap.", true),
    PermissionCardInfo(PermissionKind.BATTERY, "Battery exemption", "Stops Android from killing the lock in the background.", false),
    PermissionCardInfo(PermissionKind.CAMERA, "Camera", "Only for photo check-in habits.", false),
)

/** Re-evaluates permission state every time the screen comes back to the foreground. */
@Composable
fun rememberPermissionStates(perms: Permissions, kinds: List<PermissionKind>): Map<PermissionKind, Boolean> {
    var states by remember { mutableStateOf(kinds.associateWith { perms.granted(it) }) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) states = kinds.associateWith { perms.granted(it) } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return states
}

@Composable
fun PermissionCards(perms: Permissions, kinds: List<PermissionKind>, onChange: (Map<PermissionKind, Boolean>) -> Unit = {}) {
    val ctx = LocalContext.current
    var states by remember { mutableStateOf(kinds.associateWith { perms.granted(it) }) }
    val refresh = { states = kinds.associateWith { perms.granted(it) }; onChange(states) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val requestNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        permissionCards.filter { it.kind in kinds }.forEach { card ->
            val granted = states[card.kind] == true
            PermissionCard(card, granted) {
                when (card.kind) {
                    PermissionKind.USAGE_ACCESS -> ctx.startActivity(perms.usageAccessIntent())
                    PermissionKind.OVERLAY -> ctx.startActivity(perms.overlayIntent())
                    PermissionKind.NOTIFICATIONS -> if (android.os.Build.VERSION.SDK_INT >= 33) requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS) else ctx.startActivity(perms.appSettingsIntent())
                    PermissionKind.BATTERY -> ctx.startActivity(perms.batteryIntent())
                    PermissionKind.CAMERA -> requestCamera.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(card: PermissionCardInfo, granted: Boolean, onGrant: () -> Unit) {
    val c = KeptTheme.colors
    KeptCard(Modifier.fillMaxWidth(), background = if (granted) c.green50 else c.surface2, border = if (granted) null else c.border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(card.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    if (granted) Pill("Granted", background = c.green400.copy(alpha = 0.25f), foreground = c.green600)
                    else if (card.required) Pill("Required", background = c.amber50, foreground = c.amber800)
                    else Pill("Recommended", background = c.surface0, foreground = c.textMuted)
                }
                Spacer(Modifier.height(4.dp))
                Text(card.why, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            if (granted) { Spacer(Modifier.width(10.dp)); Icon(Icons.Outlined.CheckCircle, null, tint = c.green600, modifier = Modifier.size(22.dp)) }
        }
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Grant", onClick = onGrant)
        }
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit, vm: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val c = KeptTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.textSecondary) }
            Text("Permissions", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text("The lock only works while these stay on. If usage access is turned off during a lock window, that day is marked unprotected and doesn't count.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(16.dp))
        PermissionCards(vm.permissions, PermissionKind.entries)
        Spacer(Modifier.height(24.dp))
    }
}

fun Context.hasCamera(): Boolean = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
