package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.domain.WeeklyGoalDraft
import com.example.kept.core.domain.WeeklyGoalSetupLayout
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.KeptChip
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.SprigView

private val D = DaybreakColors

/**
 * The weekly goal setup screen (issue #165, mockup 1): a new goal, or an existing one when
 * [goalId] is set. Saving or deleting returns to the list. Deleting asks first.
 *
 * While the goal holds the lock (today is its must-do day and the daily lock is in force),
 * lowering its days a week or deleting it goes through the lock-change confirm instead
 * ([SettingsViewModel.requestWeeklyGoalChange], issue #165): Cancel keeps the goal as it is and
 * the page open.
 *
 * A goal removed elsewhere while this page is open closes the page: nothing is left to edit, and
 * a save would change nothing.
 */
@Composable
fun WeeklyGoalEditScreen(
    goalId: Long?,
    onClose: () -> Unit,
    vm: WeeklyGoalsViewModel = hiltViewModel(),
    settings: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    // Close once: a second tap while the page is leaving must not save, delete or pop again.
    var closed by remember { mutableStateOf(false) }
    val close = { if (!closed) { closed = true; onClose() } }
    val sprig = SetupSprig(s.form, s.wilted)
    // The goals load after the first frame; wait for this one rather than open an empty form.
    val goal = s.goals.firstOrNull { it.id == goalId }
    // Seen once, then gone: removed from somewhere else (or by this page, which is closing anyway).
    val seen = remember { booleanArrayOf(false) }
    if (goal != null) seen[0] = true
    val gone = WeeklyGoalEditGone.of(s.loaded, goalId, goal != null, seen[0])
    LaunchedEffect(gone) { if (gone) close() }
    when {
        !s.loaded || (goalId != null && goal == null) -> Box(Modifier.fillMaxSize().background(D.cream))
        goal == null -> WeeklyGoalEditPage(WeeklyGoalDraft(), onClose = close, onSave = { vm.add(it); close() }, sprig = sprig)
        else -> WeeklyGoalEditPage(
            WeeklyGoalDraft.of(goal.title, goal.daysPerWeek, goal.targetValue, goal.unit),
            onClose = close,
            onSave = { d -> settings.requestWeeklyGoalChange(goal.id, goal.daysPerWeek, d.days) { if (!closed) { vm.save(goal.id, d); close() } } },
            sprig = sprig,
            onDelete = { settings.requestWeeklyGoalChange(goal.id, goal.daysPerWeek, null) { if (!closed) { vm.remove(goal.id); close() } } },
            asksFirst = { d -> settings.weeklyGoalConfirm(goal.id, goal.daysPerWeek, d?.days) != null },
        )
    }
    LockChangeConfirmDialog(settings)
}

/**
 * Whether the edit page's goal has gone away under it: the goals are loaded, the page is for an
 * existing goal, that goal was there and no longer is. Before it has been seen the page is still
 * waiting for its first read, and an unknown id simply stays blank, as before.
 */
object WeeklyGoalEditGone {
    fun of(loaded: Boolean, goalId: Long?, present: Boolean, seenBefore: Boolean): Boolean =
        loaded && goalId != null && !present && seenBefore
}

/** Sprig beside the title, in the person's current form. */
data class SetupSprig(val form: SprigForm, val wilted: Boolean = false)

private val DraftSaver = listSaver<WeeklyGoalDraft, Any>(
    save = { listOf(it.title, it.days, it.target, it.unit) },
    restore = { WeeklyGoalDraft(it[0] as String, it[1] as Int, it[2] as Int, it[3] as String) },
)

/**
 * The questions (mockup 1): what (the Add a habit name field, five quick picks and "+ Custom"),
 * how much each time once there is an activity (Add a habit's stepper and unit chips; story
 * #205) and how many days a week (chips 1 to 7), then the summary card that follows them all.
 * The rules live in [WeeklyGoalDraft]. Under a close button the questions scroll; "Save goal"
 * (and, for an existing goal, a red quiet "Delete goal", [onDelete]) stay fixed at the bottom.
 *
 * [asksFirst] says whether saving a draft (or deleting, for null) will be held for the
 * lock-change confirm rather than done at once. Such a tap does not close the page or latch it:
 * the caller's confirm may be cancelled, and the page has to go on working. A held delete also
 * skips this page's own "Delete this weekly goal?", so the person is asked once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyGoalEditPage(
    initial: WeeklyGoalDraft,
    onClose: () -> Unit,
    onSave: (WeeklyGoalDraft) -> Unit,
    modifier: Modifier = Modifier,
    sprig: SetupSprig? = null,
    onDelete: (() -> Unit)? = null,
    asksFirst: (WeeklyGoalDraft?) -> Boolean = { false },
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Save, Close and a confirmed Delete act once, as on Add a habit (review on #149).
    var done by remember { mutableStateOf(false) }
    fun finish(action: () -> Unit) { if (!done) { done = true; action() } }
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val side = WeeklyGoalSetupLayout.SIDE_DP.dp

    BoxWithConstraints(modifier.fillMaxSize().background(D.cream).imePadding()) {
        val width = maxWidth
        val showSprig = sprig != null && WeeklyGoalSetupLayout.showsSprig(LocalDensity.current.fontScale)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.padding(start = 10.dp, top = 22.dp, bottom = 2.dp)) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = { finish(onClose) })
                        .semantics { contentDescription = WeeklyGoalCopy.CANCEL }
                        .testTag("page_back"),
                    contentAlignment = Alignment.Center,
                ) { Icon(DaybreakIcons.Close, null, Modifier.size(22.dp), tint = D.ink) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("page_body")
                    .padding(top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val titleWidth = width - side * 2 - if (showSprig) (WeeklyGoalSetupLayout.SPRIG_DP + 8).dp else 0.dp
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            WeeklyGoalCopy.TITLE,
                            Modifier.semantics { heading() }.testTag("page_title"),
                            style = fitLongestWord(WeeklyGoalCopy.TITLE, TitleStyle, titleWidth), color = D.ink,
                        )
                        Text(WeeklyGoalCopy.SUBTITLE, Modifier.testTag("goal_subtitle"), style = DaybreakType.body, color = D.muted)
                    }
                    if (showSprig && sprig != null) {
                        SprigView(sprig.form, SprigPose.WAVE, Modifier.size(WeeklyGoalSetupLayout.SPRIG_DP.dp).testTag("goal_sprig"), wilted = sprig.wilted)
                    }
                }

                Column(Modifier.padding(horizontal = side), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(WeeklyGoalCopy.NAME_LABEL, style = LabelStyle, color = D.ink)
                    NameField(
                        draft.title, { draft = draft.withTitle(it) },
                        Modifier.focusRequester(nameFocus), label = WeeklyGoalCopy.NAME_LABEL, tag = "goal_name",
                    )
                    FlowRow(Modifier.testTag("goal_picks"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeeklyGoalDraft.QUICK_PICKS.forEach { p ->
                            KeptChip(p, selected = draft.isPicked(p), onClick = { draft = draft.pick(p) }, modifier = Modifier.testTag("goal_pick_$p"))
                        }
                        CustomChip { nameFocus.requestFocus(); keyboard?.show() }
                    }
                }

                // How much: shown once there is an activity, as the target goes with it.
                if (draft.name.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = side), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmountRow(draft.amount, draft.canLess, draft.canMore, onLess = { draft = draft.less() }, onMore = { draft = draft.more() }, tag = "goal")
                        if (draft.showsUnits) UnitChips(draft.unit, onPick = { draft = draft.withUnit(it) }, tag = "goal")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = side),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(WeeklyGoalCopy.DAYS_LABEL, Modifier.weight(1f).testTag("goal_days_label"), style = LabelStyle, color = D.ink)
                        Text(
                            draft.daysCount, Modifier.testTag("goal_days_count"),
                            style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.muted, maxLines = 1, softWrap = false,
                        )
                    }
                    DayChips(draft.days, width, onPick = { draft = draft.withDays(it) })
                    if (draft.showsSevenHint) {
                        Text(
                            WeeklyGoalCopy.SEVEN_HINT,
                            Modifier.padding(horizontal = side).semantics { liveRegion = LiveRegionMode.Polite }.testTag("goal_seven_hint"),
                            style = DaybreakType.caption, color = D.muted,
                        )
                    }
                }

                Column(
                    Modifier.padding(horizontal = side).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(D.purpleTint)
                        .padding(horizontal = 18.dp, vertical = 16.dp).testTag("goal_summary"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(draft.summary, Modifier.testTag("goal_summary_title"), style = SummaryStyle, color = D.ink)
                    Text(WeeklyGoalCopy.SUMMARY_BODY, Modifier.testTag("goal_summary_body"), style = SummaryBodyStyle, color = D.inkSoft)
                }
            }
            Column(
                Modifier.fillMaxWidth().background(D.cream).navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(
                    WeeklyGoalCopy.SAVE,
                    onClick = { if (asksFirst(draft)) { if (!done) onSave(draft) } else finish { onSave(draft) } },
                    enabled = draft.canSave, modifier = Modifier.testTag("goal_save"),
                )
                if (onDelete != null) {
                    QuietButton(
                        WeeklyGoalCopy.DELETE,
                        onClick = { if (!done) { if (asksFirst(null)) onDelete() else confirmDelete = true } },
                        color = D.danger, modifier = Modifier.testTag("goal_delete"),
                    )
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        DeleteGoalConfirm(
            // The goal as it is saved, not what the form holds unsaved.
            WeeklyGoalCopy.deleteBody(initial.name, initial.days),
            onConfirm = { confirmDelete = false; finish(onDelete) },
            onCancel = { confirmDelete = false },
        )
    }
}

private val TitleStyle = DaybreakType.pageTitle.copy(fontSize = 36.sp, lineHeight = 36.sp)
private val LabelStyle = DaybreakType.body.copy(fontWeight = FontWeight.Bold)
private val DayStyle = DaybreakType.button.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")
private val SummaryStyle = DaybreakType.button.copy(fontWeight = FontWeight.ExtraBold)
private val SummaryBodyStyle = DaybreakType.caption.copy(fontSize = 13.5.sp, lineHeight = (13.5 * 1.4).sp)

/** "+ Custom": a quick pick's size with a dashed outline; it puts the cursor in the field. 48dp to touch. */
@Composable
private fun CustomChip(onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick).testTag("goal_custom"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 36.dp)
                .drawBehind {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        D.purpleDone,
                        topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius((size.height - w) / 2),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(WeeklyGoalCopy.CUSTOM, style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.purpleInk, maxLines = 1) }
    }
}

/**
 * The seven day chips (mockup 1): white 48dp rounds with a `field` border, the chosen one purple,
 * 18/800 tabular. Each chip's touch target is its whole cell; [WeeklyGoalSetupLayout.dayRowSideDp]
 * keeps the cells at 48dp or more. TalkBack hears "4 days a week", selected or not.
 */
@Composable
private fun DayChips(selected: Int, screenWidth: Dp, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = WeeklyGoalSetupLayout.dayRowSideDp(screenWidth.value.toInt()).dp)
            .selectableGroup().testTag("goal_days"),
    ) {
        WeeklyGoalDraft.DAY_CHOICES.forEach { n ->
            val on = n == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(n) })
                    .semantics { contentDescription = WeeklyGoalCopy.days(n) }
                    .testTag("goal_day_$n"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.padding(horizontal = (WeeklyGoalSetupLayout.GAP_DP / 2).dp).fillMaxWidth().heightIn(min = 48.dp)
                        .clip(CircleShape).background(if (on) D.purple else D.white)
                        .then(if (on) Modifier else Modifier.border(1.5.dp, D.field, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { Text(n.toString(), style = DayStyle, color = if (on) D.white else D.ink, maxLines = 1) }
            }
        }
    }
}

/** "Delete this weekly goal?": the settings confirm's cream card, a red "Delete goal" and "Cancel". */
@Composable
internal fun DeleteGoalConfirm(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(WeeklyGoalCopy.DELETE_TITLE, style = DaybreakType.section, color = D.ink) },
        text = { Text(message, Modifier.testTag("goal_delete_message"), style = DaybreakType.body, color = D.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.heightIn(min = 48.dp).testTag("goal_delete_confirm")) {
                Text(WeeklyGoalCopy.DELETE, style = DaybreakType.button, fontWeight = FontWeight.Bold, color = D.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, Modifier.heightIn(min = 48.dp).testTag("goal_delete_cancel")) { Text(WeeklyGoalCopy.CANCEL, style = DaybreakType.button, color = D.ink) }
        },
        containerColor = D.cream,
    )
}
