package com.example.kept.core.ui.shots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kept.core.ui.AddFirstHabitRow
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakHabitRow
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.FirstLandingBanner
import com.example.kept.core.ui.HabitRowState
import com.example.kept.core.ui.KeptChip
import com.example.kept.core.ui.KeptSwitch
import com.example.kept.core.ui.KeptTab
import com.example.kept.core.ui.KeptTabBar
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.LockOffBanner
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.SecondaryButton
import com.example.kept.core.ui.TooLateBanner

/** Theme, cream page and a font scale, the way every screenshot is taken. */
@Composable
fun Shot(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        KeptTheme(reduceMotion = true, content = content)
    }
}

/** A section of the parts sheet, laid out like `A18-Parts`: 20dp sides, 8dp between parts. */
@Composable
private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Note(text: String) =
    Text(text, Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp), style = DaybreakType.smallLabel, color = DaybreakColors.faint)

@Composable
fun BannersSheet() = Sheet {
    Note("Lock is off (a required permission is missing). Tapping opens Permissions.")
    LockOffBanner("Usage access is off. Today won't count until it's back on.", onFix = {})
    Note("First landing, once, right after onboarding.")
    FirstLandingBanner(onGotIt = {})
    Note("Past the give-up time with habits left.")
    TooLateBanner()
}

@Composable
fun HabitRowsSheet() = Sheet {
    Note("Habit row states: to do, done, done late, photo.")
    DaybreakHabitRow("Exercise", "30 min", HabitRowState.ToDo, onTick = {}, onUndo = {})
    DaybreakHabitRow("Read", "10 pages", HabitRowState.Done, onTick = {}, onUndo = {})
    DaybreakHabitRow("Practice", "20 min", HabitRowState.DoneLate, onTick = {}, onUndo = {})
    DaybreakHabitRow("Tidy room", "", HabitRowState.ToDo, onTick = {}, onUndo = {}, photo = true)
    Note("No habits yet. Tapping opens Add a habit.")
    AddFirstHabitRow(onClick = {})
}

@Composable
fun ButtonsSheet() = Sheet {
    Note("Buttons: primary, secondary, quiet, disabled.")
    PrimaryButton("Primary action", onClick = {})
    SecondaryButton("Secondary action", onClick = {})
    QuietButton("Quiet action", onClick = {}, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
    PrimaryButton("Disabled", onClick = {}, enabled = false)
}

@Composable
fun ControlsSheet() = Box(Modifier.fillMaxSize()) {
    Sheet {
        Note("Switch: on, off.")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KeptSwitch(checked = true, onCheckedChange = {}, label = "Reminders")
            KeptSwitch(checked = false, onCheckedChange = {}, label = "Send anonymous usage data")
        }
        Note("Chips: selected, not selected.")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeptChip("min", selected = true, onClick = {})
            KeptChip("pages", selected = false, onClick = {})
            KeptChip("times", selected = false, onClick = {})
            KeptChip("glasses", selected = false, onClick = {})
        }
    }
    KeptTabBar(KeptTab.Today, onSelect = {}, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
}
