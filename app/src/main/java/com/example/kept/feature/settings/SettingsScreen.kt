package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.domain.LockWindowField
import com.example.kept.core.domain.NightEdit
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.NightWindow
import com.example.kept.core.domain.SettingsCopy
import com.example.kept.core.domain.SettingsSky
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.KeptChip
import com.example.kept.core.ui.RowGroup
import com.example.kept.core.ui.RowValue
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.SwitchRow
import com.example.kept.core.ui.ValueRow
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.SprigView
import kotlin.math.ceil
import com.example.kept.core.ui.DaybreakColors as D

/** Everything the Settings tab shows, read from the real stores (`A7-Settings`). */
data class SettingsPage(
    val habits: Int = 0,
    val lockFromMinute: Int = 7 * 60,
    val dueMinute: Int = 21 * 60,
    val exceptions: Int = 0,
    val breakMinutes: Int = 30,
    /** Permissions on the Permissions page that are not granted. */
    val permissionsOff: Int = 0,
    val reminders: Boolean = true,
    val analytics: Boolean = true,
    val bestStreak: Int = 0,
    val form: SprigForm = SprigForm.SPRIG,
    val wilted: Boolean = false,
    /** Night lockdown (issue #164): off, 11:00 pm to 7:00 am, until changed. */
    val night: NightSetting = NightSetting(),
) {
    companion object {
        fun of(s: SettingsUi, permissionsOff: Int) = SettingsPage(
            habits = s.habits.size,
            lockFromMinute = s.settings.lockFromMinute,
            dueMinute = s.settings.dueMinute,
            exceptions = s.exceptions.size,
            breakMinutes = s.settings.breakDurationMin,
            permissionsOff = permissionsOff,
            reminders = s.settings.remindersEnabled,
            analytics = s.settings.analyticsEnabled,
            bestStreak = s.sprig.bestStreak,
            form = s.sprig.form,
            wilted = s.sprig.wilted,
            night = NightSetting(s.settings.nightLockEnabled, s.settings.nightFromMinute, s.settings.nightUntilMinute),
        )
    }
}

/** What a row on the Settings tab does when tapped or switched. */
data class SettingsActions(
    val onOpenHabits: () -> Unit = {},
    val onEditLockFrom: () -> Unit = {},
    val onEditGiveUp: () -> Unit = {},
    val onOpenExceptions: () -> Unit = {},
    val onEditBreak: () -> Unit = {},
    val onOpenPermissions: () -> Unit = {},
    val onNightLock: (Boolean) -> Unit = {},
    val onEditNightStart: () -> Unit = {},
    val onEditNightEnd: () -> Unit = {},
    val onReminders: (Boolean) -> Unit = {},
    val onAnalytics: (Boolean) -> Unit = {},
    val onOpenHistory: () -> Unit = {},
)

private enum class Editor { LOCK_FROM, GIVE_UP, BREAK, NIGHT_START, NIGHT_START_CUSTOM, NIGHT_END }

@Composable
fun SettingsScreen(
    onOpenExceptions: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val perms = rememberPermissionStates(vm.permissions, PermissionKind.entries)
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    val page = SettingsPage.of(s, perms.count { !it.value })

    SettingsContent(
        page,
        SettingsActions(
            onOpenHabits = onOpenHabits,
            onEditLockFrom = { editor = Editor.LOCK_FROM },
            onEditGiveUp = { editor = Editor.GIVE_UP },
            onOpenExceptions = onOpenExceptions,
            onEditBreak = { editor = Editor.BREAK },
            onOpenPermissions = onOpenPermissions,
            onNightLock = { vm.requestNightEdit(NightEdit.switch(page.night, it)) },
            onEditNightStart = { editor = Editor.NIGHT_START },
            onEditNightEnd = { editor = Editor.NIGHT_END },
            onReminders = { vm.setReminders(it) },
            onAnalytics = { vm.setAnalyticsEnabled(it) },
            onOpenHistory = onOpenHistory,
        ),
    )

    when (editor) {
        Editor.LOCK_FROM -> TimeEditor(SettingsCopy.LOCK_FROM, page.lockFromMinute, onDismiss = { editor = null }) {
            vm.requestSetLockWindow(LockWindowField.LOCK_FROM, it, page.lockFromMinute, page.dueMinute)
        }
        Editor.GIVE_UP -> TimeEditor(SettingsCopy.GIVE_UP, page.dueMinute, onDismiss = { editor = null }) {
            vm.requestSetLockWindow(LockWindowField.GIVE_UP, it, page.lockFromMinute, page.dueMinute)
        }
        Editor.BREAK -> BreakLengthEditor(page.breakMinutes, onDismiss = { editor = null }) { vm.setBreakDuration(it) }
        Editor.NIGHT_START -> NightStartEditor(
            page.night,
            onDismiss = { editor = null },
            onCustom = { editor = Editor.NIGHT_START_CUSTOM },
        ) { vm.requestNightEdit(NightEdit.start(page.night, it)) }
        Editor.NIGHT_START_CUSTOM -> TimeEditor(
            SettingsCopy.NIGHT_START, page.night.fromMinute, onDismiss = { editor = null },
            error = { NightWindow.error(it, page.night.untilMinute) },
        ) { vm.requestNightEdit(NightEdit.start(page.night, it)) }
        Editor.NIGHT_END -> TimeEditor(
            SettingsCopy.NIGHT_END, page.night.untilMinute, onDismiss = { editor = null },
            error = { NightWindow.error(page.night.fromMinute, it) },
        ) { vm.requestNightEdit(NightEdit.end(page.night, it)) }
        null -> Unit
    }

    LockChangeConfirmDialog(vm)
}

/**
 * The Settings tab in Daybreak (`A7-Settings`), without the ViewModel so tests can render it:
 * a lilac sky with "Settings" and a small Sprig over the hill, then "Your promise", "Lock" and
 * "Reminders and privacy", the History row and the privacy line. The whole page scrolls; the
 * tab bar is outside it.
 */
@Composable
internal fun SettingsContent(page: SettingsPage, actions: SettingsActions, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    // The title's height, measured; a taller title (large font) moves the hill down.
    var titlePx by remember { mutableIntStateOf(0) }
    val textBottom = SettingsSky.TEXT_TOP_DP + ceil(with(density) { titlePx.toDp() }.value).toInt()
    val crest = SettingsSky.crestDp(textBottom)
    val skyHeight = inset + crest.dp + Hill.RISE

    BoxWithConstraints(modifier.fillMaxSize().background(D.cream).testTag("settings_screen")) {
        val screen = maxWidth.value.toInt()
        val titleWidth = SettingsSky.titleWidthDp(screen).dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("settings_page")) {
            SkyScaffold(
                sky = Sky.lilac,
                skyHeight = skyHeight,
                modifier = Modifier.fillMaxWidth().height(skyHeight),
                skyContent = {
                    // Where the hill's crest is, for tests; takes no space and draws nothing.
                    Spacer(Modifier.offset(y = inset + crest.dp).size(0.dp).testTag("settings_crest"))
                    SprigView(
                        page.form,
                        SprigPose.IDLE,
                        Modifier.align(Alignment.TopEnd).padding(end = SettingsSky.SPRIG_END_DP.dp)
                            .offset(y = inset + SettingsSky.sprigTopDp(crest, screen).dp)
                            .size(SettingsSky.SPRIG_DP.dp).testTag("settings_sprig"),
                        wilted = page.wilted,
                    )
                    Text(
                        SettingsCopy.TITLE,
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                            .padding(top = inset + SettingsSky.TEXT_TOP_DP.dp, start = SettingsSky.TEXT_SIDE_DP.dp)
                            .width(titleWidth)
                            .onSizeChanged { titlePx = it.height }
                            .semantics { heading() }
                            .testTag("settings_title"),
                        style = fitLongestWord(SettingsCopy.TITLE, DaybreakType.headline, titleWidth),
                        color = D.ink,
                    )
                },
            )
            Groups(page, actions)
        }
    }
}

/** `A7`: groups 22dp apart, 20dp sides; the first group starts 6dp under the sky's edge. */
@Composable
private fun Groups(page: SettingsPage, a: SettingsActions) {
    Column(
        // The scaffold's sky ends 4dp under the design's (the hill's seam overlap), so 2dp are
        // left of the design's 6.
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        RowGroup(SettingsCopy.GROUP_PROMISE) {
            ValueRow(SettingsCopy.HABITS, RowValue(SettingsCopy.habits(page.habits)), a.onOpenHabits, first = true, tag = "settings_habits")
            ValueRow(SettingsCopy.LOCK_FROM, RowValue(SettingsCopy.time(page.lockFromMinute)), a.onEditLockFrom, tag = "settings_lock_from")
            ValueRow(SettingsCopy.GIVE_UP, RowValue(SettingsCopy.time(page.dueMinute)), a.onEditGiveUp, hint = SettingsCopy.GIVE_UP_HINT, tag = "settings_give_up")
        }
        RowGroup(SettingsCopy.GROUP_LOCK) {
            ValueRow(
                SettingsCopy.EXCEPTIONS, RowValue(SettingsCopy.exceptions(page.exceptions)), a.onOpenExceptions,
                hint = SettingsCopy.EXCEPTIONS_HINT, first = true, tag = "settings_exceptions_row",
            )
            ValueRow(SettingsCopy.BREAK_LENGTH, RowValue(SettingsCopy.minutes(page.breakMinutes)), a.onEditBreak, tag = "settings_break")
            val ok = page.permissionsOff == 0
            ValueRow(
                SettingsCopy.PERMISSIONS,
                RowValue(SettingsCopy.permissions(page.permissionsOff), color = if (ok) D.green else D.danger, dot = if (ok) D.greenBright else D.danger),
                a.onOpenPermissions,
                tag = "settings_permissions_row",
            )
        }
        RowGroup(SettingsCopy.GROUP_NIGHT) {
            SwitchRow(SettingsCopy.NIGHT, SettingsCopy.NIGHT_HINT, page.night.enabled, a.onNightLock, first = true, tag = "night_toggle")
            // The two ends of the window only mean something while night lockdown is on.
            if (page.night.enabled) {
                ValueRow(SettingsCopy.NIGHT_START, RowValue(SettingsCopy.nightTime(page.night.fromMinute)), a.onEditNightStart, tag = "settings_night_start")
                ValueRow(SettingsCopy.NIGHT_END, RowValue(SettingsCopy.nightTime(page.night.untilMinute)), a.onEditNightEnd, tag = "settings_night_end")
            }
        }
        RowGroup(SettingsCopy.GROUP_REMINDERS) {
            SwitchRow(SettingsCopy.REMINDERS, SettingsCopy.REMINDERS_HINT, page.reminders, a.onReminders, first = true, tag = "reminders_toggle")
            SwitchRow(SettingsCopy.ANALYTICS, SettingsCopy.ANALYTICS_HINT, page.analytics, a.onAnalytics, tag = "analytics_toggle")
        }
        RowGroup(null) {
            ValueRow(SettingsCopy.HISTORY, RowValue(SettingsCopy.bestStreak(page.bestStreak)), a.onOpenHistory, first = true, tag = "settings_history")
        }
        Text(
            SettingsCopy.PRIVACY,
            Modifier.padding(horizontal = 4.dp).testTag("settings_privacy"),
            style = DaybreakType.caption.copy(lineHeight = (13 * 1.4).sp),
            color = D.muted,
        )
    }
}

/**
 * The existing time picker for a lock window row, on Daybreak colours. "Set" saves. [error] is
 * what is wrong with the time picked so far, if anything: it shows under the picker and "Set"
 * stays disabled until the pick may be saved (night lockdown must be 1 to 12 hours long).
 */
@Composable
internal fun TimeEditor(
    title: String,
    minute: Int,
    onDismiss: () -> Unit,
    error: (Int) -> String? = { null },
    onSet: (Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = false)
    val problem = error(state.hour * 60 + state.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = DaybreakType.section, color = D.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = D.purplePale,
                        selectorColor = D.purple,
                        periodSelectorBorderColor = D.field,
                        periodSelectorSelectedContainerColor = D.purpleTint,
                        periodSelectorSelectedContentColor = D.ink,
                        periodSelectorUnselectedContentColor = D.muted,
                        timeSelectorSelectedContainerColor = D.purpleTint,
                        timeSelectorSelectedContentColor = D.ink,
                        timeSelectorUnselectedContainerColor = D.white,
                        timeSelectorUnselectedContentColor = D.ink,
                    ),
                )
                if (problem != null) {
                    Text(
                        problem,
                        Modifier.testTag("time_error").semantics { liveRegion = LiveRegionMode.Polite },
                        style = DaybreakType.bodySmall,
                        color = D.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(state.hour * 60 + state.minute); onDismiss() },
                modifier = Modifier.testTag("time_set"),
                enabled = problem == null,
            ) {
                Text("Set", style = DaybreakType.button, color = if (problem == null) D.purple else D.faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = DaybreakType.button, color = D.muted) } },
        containerColor = D.cream,
    )
}

/** "Unlock length after a break": the existing three lengths, as chips. Picking one saves it. */
@Composable
internal fun BreakLengthEditor(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettingsCopy.BREAK_LENGTH, style = DaybreakType.section, color = D.ink) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsCopy.BREAK_LENGTHS.forEach { m ->
                    KeptChip(
                        SettingsCopy.minutes(m),
                        selected = m == current,
                        onClick = { onPick(m); onDismiss() },
                        modifier = Modifier.testTag("break_$m"),
                        role = Role.RadioButton,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = DaybreakType.button, color = D.muted) } },
        containerColor = D.cream,
    )
}

/**
 * "Starts at" (issue #164): 10:00 pm, 11:00 pm and Midnight as chips, and "Custom…", which opens
 * the time picker. Picking a chip saves it and closes. A preset that would make the window longer
 * than 12 hours with the saved end cannot be picked; the line under the chips says why.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NightStartEditor(current: NightSetting, onDismiss: () -> Unit, onCustom: () -> Unit, onPick: (Int) -> Unit) {
    val blocked = NightWindow.PRESETS.filterNot { NightWindow.isValid(it, current.untilMinute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettingsCopy.NIGHT_START, style = DaybreakType.section, color = D.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    NightWindow.PRESETS.forEach { m ->
                        KeptChip(
                            SettingsCopy.nightTime(m),
                            selected = m == current.fromMinute,
                            onClick = { onPick(m); onDismiss() },
                            modifier = Modifier.testTag("night_start_$m"),
                            role = Role.RadioButton,
                            enabled = m !in blocked,
                        )
                    }
                    KeptChip(
                        SettingsCopy.NIGHT_CUSTOM,
                        selected = current.fromMinute !in NightWindow.PRESETS,
                        onClick = onCustom,
                        modifier = Modifier.testTag("night_start_custom"),
                        role = Role.RadioButton,
                    )
                }
                if (blocked.isNotEmpty()) {
                    Text(SettingsCopy.NIGHT_WINDOW_ERROR, Modifier.testTag("night_start_error"), style = DaybreakType.bodySmall, color = D.muted)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = DaybreakType.button, color = D.muted) } },
        containerColor = D.cream,
    )
}

/**
 * Shown on any settings screen that guards a change with [SettingsViewModel.pendingLockChange]
 * (issue #1). Also keeps [SettingsViewModel.lockHold] subscribed while such a screen is on
 * screen, since its `WhileSubscribed` StateFlow would otherwise sit on its stale initial value.
 */
@Composable
fun LockChangeConfirmDialog(vm: SettingsViewModel) {
    vm.lockHold.collectAsStateWithLifecycle()
    val pending by vm.pendingLockChange.collectAsStateWithLifecycle()
    val current = pending ?: return
    LockChangeConfirm(current.message, onConfirm = vm::confirmPendingLockChange, onCancel = vm::cancelPendingLockChange)
}

/** "Apps are locked right now" in Daybreak: a cream card, ink title, a red "Change it anyway". */
@Composable
internal fun LockChangeConfirm(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(LOCKED_NOW, style = DaybreakType.section, color = D.ink) },
        text = { Text(message, style = DaybreakType.body, color = D.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.testTag("change_anyway")) {
                Text(CHANGE_ANYWAY, style = DaybreakType.button, fontWeight = FontWeight.Bold, color = D.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, Modifier.testTag("change_cancel")) { Text("Cancel", style = DaybreakType.button, color = D.ink) }
        },
        containerColor = D.cream,
    )
}

internal const val LOCKED_NOW = "Apps are locked right now"
internal const val CHANGE_ANYWAY = "Change it anyway"
