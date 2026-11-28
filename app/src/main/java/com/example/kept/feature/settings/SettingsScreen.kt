package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.account.AccountState
import com.example.kept.core.domain.AccountCopy
import com.example.kept.core.domain.DeleteUi
import com.example.kept.core.domain.LockWindowField
import com.example.kept.core.domain.NightCopy
import com.example.kept.core.domain.NightEdit
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.NightSliders
import com.example.kept.core.domain.NightWindow
import com.example.kept.core.domain.SettingsCopy
import com.example.kept.core.domain.SettingsSky
import com.example.kept.core.domain.SignInUi
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.PromiseListCopy
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.ui.BigTimeStyle
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.GoogleMark
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.KeptChip
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.RowGroup
import com.example.kept.core.ui.RowValue
import com.example.kept.core.ui.ruleAbove
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.SwitchRow
import com.example.kept.core.ui.TimeSlider
import com.example.kept.core.ui.boldParts
import com.example.kept.core.ui.ValueRow
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.SprigView
import kotlin.math.ceil
import com.example.kept.core.ui.DaybreakColors as D

/** Everything the Settings tab shows, read from the real stores (`A7-Settings`). */
data class SettingsPage(
    val habits: Int = 0,
    /** Active weekly goals (issue #165). */
    val weeklyGoals: Int = 0,
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
    /**
     * Signed out shows the "Sign in with Google" row (#193); signed in, the account and Sign out
     * (#194); while a stored session loads ([AccountState.Restoring]), neither.
     */
    val account: AccountState = AccountState.SignedOut,
    /** The sign-in row's state and its error line. */
    val signIn: SignInUi = SignInUi.Idle,
    /** True while a sign out runs: the row reads "Signing out…" and ignores taps (#194). */
    val signingOut: Boolean = false,
    /** Delete account (#195): busy, or the red or green line under the Account group. */
    val delete: DeleteUi = DeleteUi.Idle,
) {
    companion object {
        fun of(
            s: SettingsUi,
            permissionsOff: Int,
            weeklyGoals: Int = 0,
            account: AccountState = AccountState.SignedOut,
            signIn: SignInUi = SignInUi.Idle,
            signingOut: Boolean = false,
            delete: DeleteUi = DeleteUi.Idle,
        ) = SettingsPage(
            habits = s.habits.size,
            weeklyGoals = weeklyGoals,
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
            account = account,
            signIn = signIn,
            signingOut = signingOut,
            delete = delete,
        )
    }
}

/** What a row on the Settings tab does when tapped or switched. */
data class SettingsActions(
    /** Your promises (#236): every daily habit and weekly goal. */
    val onOpenPromises: () -> Unit = {},
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
    val onSignIn: () -> Unit = {},
    /** Runs the sign out, once the user has confirmed it (#194). */
    val onSignOut: () -> Unit = {},
    /** Runs the account delete, once the user has confirmed it (#195). */
    val onDeleteAccount: () -> Unit = {},
)

private enum class Editor { LOCK_FROM, GIVE_UP, BREAK, NIGHT_START, NIGHT_START_CUSTOM, NIGHT_END }

@Composable
fun SettingsScreen(
    onOpenExceptions: () -> Unit,
    onOpenPromises: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    goalsVm: WeeklyGoalsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val goals by goalsVm.state.collectAsStateWithLifecycle()
    val perms = rememberPermissionStates(vm.permissions, PermissionKind.entries)
    var editor by rememberSaveable { mutableStateOf<Editor?>(null) }
    val account by vm.account.collectAsStateWithLifecycle()
    val signIn by vm.signIn.collectAsStateWithLifecycle()
    val signingOut by vm.signingOut.collectAsStateWithLifecycle()
    val delete by vm.delete.collectAsStateWithLifecycle()
    val activity = LocalContext.current
    val page = SettingsPage.of(s, perms.count { !it.value }, goals.goals.size, account, signIn, signingOut, delete)
    // "Your account was deleted." stays only until the user leaves the Settings tab (#195, item 4).
    DisposableEffect(vm) { onDispose { vm.clearDeleted() } }

    SettingsContent(
        page,
        SettingsActions(
            onOpenPromises = onOpenPromises,
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
            onSignIn = { vm.signIn(activity) },
            onSignOut = vm::signOut,
            onDeleteAccount = vm::deleteAccount,
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
            ValueRow(
                PromiseListCopy.TITLE, RowValue(PromiseListCopy.rowValue(page.habits, page.weeklyGoals)), a.onOpenPromises,
                hint = PromiseListCopy.rowHint(page.habits, page.weeklyGoals), first = true, tag = "settings_promises",
            )
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
        val account = page.account
        if (account is AccountState.SignedIn) {
            SignedInGroup(account, page.signingOut, page.delete, a.onSignOut, a.onDeleteAccount)
            if (page.delete is DeleteUi.Error) GroupNote(page.delete.message, D.danger, "settings_delete_error")
        }
        if (account == AccountState.SignedOut) {
            RowGroup(AccountCopy.GROUP) { SignInRow(page.signIn, a.onSignIn) }
            if (page.signIn is SignInUi.Error) GroupNote(page.signIn.message, D.danger, "settings_sign_in_error")
            else if (page.delete == DeleteUi.Deleted) GroupNote(AccountCopy.DELETED, D.green, "settings_deleted")
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

/** One short line under the Account group (#193, #195): an error in red, or "deleted" in green. Read out when it appears. */
@Composable
private fun GroupNote(message: String, color: Color, tag: String) {
    Text(
        message,
        Modifier.padding(horizontal = 4.dp).offset(y = (-12).dp).testTag(tag)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = DaybreakType.bodySmall,
        color = color,
    )
}

/**
 * "Sign in with Google" (#193): the row's shape (`A7`, 56dp, 16dp sides) with Google's G in
 * front, the title, the hint under it and the chevron. While [signIn] is busy the title reads
 * "Signing in…" and a tap does nothing.
 */
@Composable
private fun SignInRow(signIn: SignInUi, onSignIn: () -> Unit) {
    val busy = signIn == SignInUi.Busy
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button, onClick = onSignIn)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp).testTag("settings_sign_in"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(GoogleMark, null, Modifier.size(20.dp), tint = Color.Unspecified)
        Column(Modifier.weight(1f)) {
            Text(
                if (busy) AccountCopy.SIGNING_IN else AccountCopy.SIGN_IN,
                Modifier.testTag("settings_sign_in_title"),
                style = DaybreakType.rowTitle,
                color = if (busy) D.muted else D.ink,
            )
            Text(AccountCopy.ROW_HINT, Modifier.testTag("settings_sign_in_hint"), style = DaybreakType.caption, color = D.muted)
        }
        Icon(DaybreakIcons.Chevron, null, Modifier.size(18.dp), tint = D.faint)
    }
}

/**
 * The Account group signed in (#194): who is signed in, then Sign out and Delete account (#195),
 * each of which asks first. While either runs, both rows ignore taps.
 */
@Composable
private fun SignedInGroup(
    who: AccountState.SignedIn,
    signingOut: Boolean,
    delete: DeleteUi,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf<AccountAsk?>(null) }
    val deleting = delete == DeleteUi.Busy
    val busy = signingOut || deleting
    RowGroup(AccountCopy.GROUP) {
        AccountRow(who)
        SignOutRow(signingOut, enabled = !busy, onClick = { asking = AccountAsk.SIGN_OUT })
        DeleteAccountRow(deleting, enabled = !busy, onClick = { asking = AccountAsk.DELETE })
    }
    if (!busy) {
        when (asking) {
            AccountAsk.SIGN_OUT -> SignOutConfirm(onConfirm = { asking = null; onSignOut() }, onCancel = { asking = null })
            AccountAsk.DELETE -> DeleteAccountConfirm(onConfirm = { asking = null; onDelete() }, onCancel = { asking = null })
            null -> Unit
        }
    }
}

private enum class AccountAsk { SIGN_OUT, DELETE }

/**
 * The signed-in account (#194): Google's G, the name with the email under it (or the email alone,
 * or "Signed in"). Not a button. Both lines wrap rather than cut off; when they do, the G stays
 * level with the first line instead of the row's middle.
 */
@Composable
private fun AccountRow(who: AccountState.SignedIn) {
    val title = AccountCopy.accountTitle(who.displayName, who.email)
    val line = AccountCopy.accountLine(who.displayName, who.email)
    var titleLines by remember(title) { mutableIntStateOf(1) }
    var lineLines by remember(line) { mutableIntStateOf(1) }
    val wraps = titleLines > 1 || lineLines > 1
    val markTop = with(LocalDensity.current) { ((DaybreakType.rowTitle.lineHeight.toDp() - 20.dp) / 2).coerceAtLeast(0.dp) }
    Row(
        Modifier.fillMaxWidth().testTag("settings_account").semantics(mergeDescendants = true) {}
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = if (wraps) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(GoogleMark, null, Modifier.padding(top = if (wraps) markTop else 0.dp).size(20.dp), tint = Color.Unspecified)
        Column(Modifier.weight(1f)) {
            Text(
                title, Modifier.testTag("settings_account_title"), style = DaybreakType.rowTitle, color = D.ink,
                onTextLayout = { titleLines = it.lineCount },
            )
            if (line != null) {
                Text(
                    line, Modifier.testTag("settings_account_email"), style = DaybreakType.caption, color = D.muted,
                    onTextLayout = { lineLines = it.lineCount },
                )
            }
        }
    }
}

/** "Sign out" (#194): a purple action title, the hint under it, no chevron. "Signing out…" ignores taps. */
@Composable
private fun SignOutRow(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("settings_sign_out").ruleAbove(true)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (busy) AccountCopy.SIGNING_OUT else AccountCopy.SIGN_OUT,
            Modifier.testTag("settings_sign_out_title"),
            style = DaybreakType.rowTitle,
            color = if (busy) D.muted else D.purple,
        )
        Text(AccountCopy.SIGN_OUT_HINT, Modifier.testTag("settings_sign_out_hint"), style = DaybreakType.caption, color = D.muted)
    }
}

/** "Delete account" (#195): Sign out's shape with a red title, the hint under it, no chevron. "Deleting account…" ignores taps. */
@Composable
private fun DeleteAccountRow(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("settings_delete").ruleAbove(true)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (busy) AccountCopy.DELETING else AccountCopy.DELETE,
            Modifier.testTag("settings_delete_title"),
            style = DaybreakType.rowTitle,
            color = if (busy) D.muted else D.danger,
        )
        Text(AccountCopy.DELETE_HINT, Modifier.testTag("settings_delete_hint"), style = DaybreakType.caption, color = D.muted)
    }
}

/**
 * "Delete your account?" (#195): the settings confirm's cream card, like [SignOutConfirm] and
 * [DeleteGoalConfirm]. What goes, then what stays, its first sentence in bold so it is not
 * missed. The delete is red; Cancel or a tap outside does nothing.
 */
@Composable
internal fun DeleteAccountConfirm(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(AccountCopy.DELETE_TITLE, style = DaybreakType.section, color = D.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(AccountCopy.DELETE_BODY, Modifier.testTag("delete_account_body"), style = DaybreakType.body, color = D.muted)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = D.ink)) { append(AccountCopy.DELETE_KEEP_BOLD) }
                        append(AccountCopy.DELETE_KEEP)
                    },
                    Modifier.testTag("delete_account_keep"),
                    style = DaybreakType.body,
                    color = D.muted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.heightIn(min = 48.dp).testTag("delete_account_confirm")) {
                Text(AccountCopy.DELETE, style = DaybreakType.button, fontWeight = FontWeight.Bold, color = D.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, Modifier.heightIn(min = 48.dp).testTag("delete_account_cancel")) {
                Text(AccountCopy.CANCEL, style = DaybreakType.button, color = D.ink)
            }
        },
        containerColor = D.cream,
    )
}

/**
 * "Sign out?" (owner decision 2026-11-27): the settings confirm's cream card. Nothing is lost, so
 * "Sign out" is purple, not the red of a costly change; Cancel keeps the user signed in.
 */
@Composable
internal fun SignOutConfirm(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(AccountCopy.SIGN_OUT_TITLE, style = DaybreakType.section, color = D.ink) },
        text = { Text(AccountCopy.SIGN_OUT_BODY, style = DaybreakType.body, color = D.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.heightIn(min = 48.dp).testTag("sign_out_confirm")) {
                Text(AccountCopy.SIGN_OUT, style = DaybreakType.button, fontWeight = FontWeight.Bold, color = D.purple)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, Modifier.heightIn(min = 48.dp).testTag("sign_out_cancel")) {
                Text(AccountCopy.CANCEL, style = DaybreakType.button, color = D.ink)
            }
        },
        containerColor = D.cream,
    )
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
 * "Starts at" (issues #164, #218): the time large, the shutdown slider (8 pm to 2 am in 15 minute
 * steps, from the saved start), the sentence it makes with the saved end, then "Custom…" under
 * it, which opens the time picker for a time outside the slider. "Set" saves the slider's time;
 * it is disabled, with the reason, where the night would be over 12 hours with the saved end.
 */
@Composable
internal fun NightStartEditor(current: NightSetting, onDismiss: () -> Unit, onCustom: () -> Unit, onPick: (Int) -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(NightSliders.fromIndex(current.fromMinute)) }
    val from = NightSliders.fromAt(index)
    val problem = NightWindow.error(from, current.untilMinute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettingsCopy.NIGHT_START, style = DaybreakType.section, color = D.ink) },
        text = {
            Column(Modifier.testTag("night_start_editor"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(NightCopy.big(from), Modifier.testTag("night_start_time"), style = BigTimeStyle, color = D.ink)
                TimeSlider(
                    index, NightSliders.FROM_STEPS, SettingsCopy.NIGHT_START, NightCopy.big(from), NightSliders.FROM_LABELS,
                    onChange = { index = it }, tag = "night_start_slider",
                )
                Text(
                    boldParts(NightCopy.startSentence(from, current.untilMinute)),
                    Modifier.padding(top = 4.dp).testTag("night_start_sentence"),
                    style = DaybreakType.bodySmall, color = D.inkSoft,
                )
                if (problem != null) {
                    Text(
                        problem,
                        Modifier.testTag("night_start_error").semantics { liveRegion = LiveRegionMode.Polite },
                        style = DaybreakType.bodySmall, color = D.danger,
                    )
                }
                QuietButton(SettingsCopy.NIGHT_CUSTOM, onClick = onCustom, modifier = Modifier.testTag("night_start_custom"))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(from); onDismiss() },
                modifier = Modifier.heightIn(min = 48.dp).testTag("night_start_set"),
                enabled = problem == null,
            ) { Text("Set", style = DaybreakType.button, color = if (problem == null) D.purple else D.faint) }
        },
        dismissButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Cancel", style = DaybreakType.button, color = D.muted) } },
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
