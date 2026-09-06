package com.example.kept.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.domain.ProofType
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.InfoBox
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.SecondaryButton
import com.example.kept.core.ui.SectionLabel
import com.example.kept.core.ui.Selectable

@Composable
fun HabitsEditScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.textSecondary) }
            Text("Daily habits", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text("Two or three is plenty. Apps stay locked until every one is done.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(16.dp))

        s.habits.forEach { h ->
            KeptCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HabitIcons.of(h.iconKey), null, tint = c.purple600, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(
                            when (h.proofType) { ProofType.TIMER -> "${h.targetValue} min timer"; ProofType.MANUAL -> "Tap to complete · ${h.targetValue} ${h.unit}"; ProofType.PHOTO -> "Photo check-in" },
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
                    IconButton(onClick = { vm.removeHabit(h.id) }, enabled = s.habits.size > 1) { Icon(Icons.Outlined.Delete, "Remove", tint = if (s.habits.size > 1) c.textMuted else c.border) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (s.habits.size == 1) InfoBox("Keep at least one habit. Remove this one after adding another.")
        Spacer(Modifier.height(8.dp))

        if (!adding && s.habits.size < 4) SecondaryButton("Add a habit", onClick = { adding = true })
        if (adding) {
            HabitEditor(onCancel = { adding = false }) { title, icon, proof, target, unit ->
                vm.addHabit(title, icon, proof, target, unit); adding = false
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditor(onCancel: () -> Unit, initial: HabitEntity? = null, onSave: (String, String, ProofType, Int, String) -> Unit) {
    val c = KeptTheme.colors
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var icon by remember { mutableStateOf(initial?.iconKey ?: "star") }
    var proof by remember { mutableStateOf(initial?.proofType ?: ProofType.TIMER) }
    var target by remember { mutableStateOf((initial?.targetValue ?: 20).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "min") }

    KeptCard(Modifier.fillMaxWidth(), background = c.surface1, border = null, shape = RoundedCornerShape(14.dp)) {
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(28) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("What will you do?") }, placeholder = { Text("e.g. Piano") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue400, unfocusedBorderColor = c.borderStrong),
        )
        Spacer(Modifier.height(12.dp))
        SectionLabel("Icon")
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HabitIcons.all.forEach { (key, vec) ->
                Selectable(selected = icon == key, onClick = { icon = key }, modifier = Modifier.width(44.dp), shape = RoundedCornerShape(10.dp), padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                    Icon(vec, key, tint = c.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("How do you prove it?")
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProofOption("In-app timer", "Runs while you do it. Apps stay locked.", proof == ProofType.TIMER) { proof = ProofType.TIMER; unit = "min" }
            ProofOption("Tap when done", "Honour system. Hold to undo within the day.", proof == ProofType.MANUAL) { proof = ProofType.MANUAL; if (unit == "min") unit = "times" }
            ProofOption("Photo check-in", "Snap a photo as proof. Stays on your phone.", proof == ProofType.PHOTO) { proof = ProofType.PHOTO; unit = "photo"; target = "1" }
        }
        if (proof != ProofType.PHOTO) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = target, onValueChange = { target = it.filter(Char::isDigit).take(3) }, modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text(if (proof == ProofType.TIMER) "Minutes" else "Target") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue400, unfocusedBorderColor = c.borderStrong),
                )
                if (proof == ProofType.MANUAL) OutlinedTextField(
                    value = unit, onValueChange = { unit = it.take(12) }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Unit") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue400, unfocusedBorderColor = c.borderStrong),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val t = target.toIntOrNull() ?: 0
        PrimaryButton("Save habit", enabled = title.isNotBlank() && (proof == ProofType.PHOTO || t > 0), onClick = { onSave(title, icon, proof, if (proof == ProofType.PHOTO) 1 else t, unit) })
        Spacer(Modifier.height(4.dp))
        SecondaryButton("Cancel", onClick = onCancel)
    }
}

@Composable
fun ProofOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = KeptTheme.colors
    Selectable(selected, onClick, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}
