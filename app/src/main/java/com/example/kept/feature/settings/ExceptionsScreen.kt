package com.example.kept.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.ui.InfoBox
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.Pill
import com.example.kept.core.ui.SectionLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExceptionsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel(), embedded: Boolean = false) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val hard by vm.hardAllowlistLabels.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadApps() }

    val list = apps ?: emptyList()
    val allowedCount = list.count { it.allowed }
    val filtered = list.filter { query.isBlank() || it.app.label.contains(query, ignoreCase = true) }.sortedWith(compareByDescending<PickableApp> { it.allowed }.thenBy { it.app.label.lowercase() })

    LazyColumn(Modifier.fillMaxSize().then(if (embedded) Modifier else Modifier.statusBarsPadding()), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item {
            if (!embedded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.textSecondary) }
                    Column {
                        Text("What stays open", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        Text(if (allowedCount == 0) "Everything locks until habits are done" else "$allowedCount exception${if (allowedCount == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            SectionLabel("Always available")
            Spacer(Modifier.height(8.dp))
            KeptCard(background = c.surface1, border = null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val labels = if (hard.isEmpty()) listOf("Phone", "Messages", "Maps", "Camera", "Settings", "Clock") else hard
                    labels.take(14).forEach { Pill(it, background = c.surface2, foreground = c.textPrimary) }
                    if (labels.size > 14) Pill("+${labels.size - 14} more", background = c.surface2, foreground = c.textMuted)
                }
                Spacer(Modifier.height(8.dp))
                Text("These can't be locked, by design. Emergency calls always work.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            Spacer(Modifier.height(18.dp))
            SectionLabel("Your exceptions", trailing = "Keep this list short")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search apps") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue400, unfocusedBorderColor = c.border),
            )
            Spacer(Modifier.height(8.dp))
            if (apps == null) InfoBox("Loading your apps…")
        }
        items(filtered, key = { it.app.packageName }) { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(p.app.packageName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.app.label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    Text(if (p.allowed) "Stays open" else "Locked", style = MaterialTheme.typography.bodySmall, color = if (p.allowed) c.teal600 else c.textMuted)
                }
                Switch(checked = p.allowed, onCheckedChange = { vm.toggleException(p.app, it) }, colors = SwitchDefaults.colors(checkedTrackColor = c.teal600))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp: androidx.compose.ui.graphics.ImageBitmap? = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()?.let { d ->
            val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            d.setBounds(0, 0, 96, 96)
            d.draw(cv)
            b.asImageBitmap()
        }
    }
    if (bmp != null) Image(bmp, null, Modifier.size(36.dp)) else Box(Modifier.size(36.dp))
}
