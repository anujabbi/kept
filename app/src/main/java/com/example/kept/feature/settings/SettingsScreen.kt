package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val onReminders: (Boolean) -> Unit = {},
    val onAnalytics: (Boolean) -> Unit = {},
    val onOpenHistory: () -> Unit = {},
)

private enum class Editor { LOCK_FROM, GIVE_UP, BREAK }

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
            onReminders = { vm.setReminders(it) },
            onAnalytics = { vm.setAnalyticsEnabled(it) },
            onOpenHistory = onOpenHistory,
        ),
    )

    when (editor) {
        Editor.LOCK_FROM -> TimeEditor(SettingsCopy.LOCK_FROM, page.lockFromMinute, onDismiss = { editor = null }) {
            vm.requestSetLockWindow(it, page.dueMinute)
        }
        Editor.GIVE_UP -> TimeEditor(SettingsCopy.GIVE_UP, page.dueMinute, onDismiss = { editor = null }) {
            vm.requestSetLockWindow(page.lockFromMinute, it)
        }
        Editor.BREAK -> BreakLengthEditor(page.breakMinutes, onDismiss = { editor = null }) { vm.setBreakDuration(it) }
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

/** The existing time picker for a lock window row, on Daybreak colours. "Set" saves. */
@Composable
private fun TimeEditor(title: String, minute: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = DaybreakType.section, color = D.ink) },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = { onSet(state.hour * 60 + state.minute); onDismiss() }, Modifier.testTag("time_set")) {
                Text("Set", style = DaybreakType.button, color = D.purple)
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
 * Shown on any settings screen that guards a change with [SettingsViewModel.pendingLockChange]
 * (issue #1). Also keeps [SettingsViewModel.lockActive] subscribed while such a screen is on
 * screen, since its `WhileSubscribed` StateFlow would otherwise sit on its stale initial value.
 */
@Composable
fun LockChangeConfirmDialog(vm: SettingsViewModel) {
    vm.lockActive.collectAsStateWithLifecycle()
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
