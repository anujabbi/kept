package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.domain.minuteOfDayLabel
import com.example.kept.core.ui.DayDot
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.ScreenTitle
import com.example.kept.core.ui.SectionLabel
import com.example.kept.core.ui.Selectable
import com.example.kept.core.ui.WeekDots
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onOpenExceptions: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenRecap: (String) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    val perms = rememberPermissionStates(vm.permissions, com.example.kept.core.lock.PermissionKind.entries)
    val missing = perms.count { !it.value }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        ScreenTitle("Settings")
        Spacer(Modifier.height(16.dp))

        SectionLabel("The rule")
        Spacer(Modifier.height(8.dp))
        KeptCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            NavRow("Habits", "${s.habits.size} daily", onOpenHabits)
            Divider()
            NavRow("Exceptions", if (s.exceptions.isEmpty()) "Everything locks" else "${s.exceptions.size} app${if (s.exceptions.size == 1) "" else "s"} stay open", onOpenExceptions)
            Divider()
            NavRow("Permissions", if (missing == 0) "All set" else "$missing to grant", onOpenPermissions, warn = missing > 0)
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel("Lock window")
        Spacer(Modifier.height(8.dp))
        KeptCard {
            TimeRow("Lock apps from", s.settings.lockFromMinute) { vm.setLockWindow(it, s.settings.dueMinute) }
            Spacer(Modifier.height(10.dp))
            TimeRow("Give-up time", s.settings.dueMinute) { vm.setLockWindow(s.settings.lockFromMinute, it) }
            Spacer(Modifier.height(6.dp))
            Text("Apps lock between these times until today's habits are done. After the give-up time they open, and the day counts as missed.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel("Break lock", trailing = "${s.breaksThisWeek} of 3 used this week")
        Spacer(Modifier.height(8.dp))
        KeptCard {
            Text("Unlock length after a break", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    Selectable(selected = s.settings.breakDurationMin == m, onClick = { vm.setBreakDuration(m) }, modifier = Modifier.weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                        Text("$m min", style = MaterialTheme.typography.labelLarge, color = c.textPrimary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("A break always works. It costs a level, wilts Sprig, and you get three a week before a day is written off.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel("Reminders")
        Spacer(Modifier.height(8.dp))
        KeptCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Two hours before give-up", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    Text("Only if something is still undone.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
                Switch(checked = s.settings.remindersEnabled, onCheckedChange = vm::setReminders, colors = SwitchDefaults.colors(checkedTrackColor = c.purple600))
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionLabel("History", trailing = "Best streak ${s.sprig.bestStreak}")
        Spacer(Modifier.height(8.dp))
        if (s.history.isEmpty()) {
            KeptCard { Text("Your first recap lands after tonight.", style = MaterialTheme.typography.bodySmall, color = c.textMuted) }
        } else {
            KeptCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                s.history.take(14).forEachIndexed { i, r ->
                    val d = LocalDate.parse(r.date)
                    Row(Modifier.fillMaxWidth().clickable { onOpenRecap(r.date) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        WeekDots(listOf(when { r.unprotected -> DayDot.UNPROTECTED; r.countedForStreak -> DayDot.DONE; r.shieldConsumed -> DayDot.SHIELDED; else -> DayDot.MISSED }), c.purple400, size = 14.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(d.format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text("${r.habitsDone}/${r.habitsTotal}" + if (r.breaksUsed > 0) " · ${r.breaksUsed} break" else "", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                    }
                    if (i < minOf(s.history.size, 14) - 1) Divider()
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("KEPT never shares your app usage, screen time, or location with anyone. Your buddy sees only your streak, today's done flag and Sprig's form.", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Divider() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(KeptTheme.colors.border))
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit, warn: Boolean = false) {
    val c = KeptTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = if (warn) c.danger else c.textSecondary)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun TimeRow(label: String, minute: Int, onChange: (Int) -> Unit) {
    val c = KeptTheme.colors
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(minute.minuteOfDayLabel(), style = MaterialTheme.typography.bodyMedium, color = c.purple600)
    }
    if (open) {
        val state = androidx.compose.material3.rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = false)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onChange(state.hour * 60 + state.minute); open = false }) { Text("Set") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { androidx.compose.material3.TimePicker(state = state) },
            containerColor = c.surface2,
        )
    }
}
