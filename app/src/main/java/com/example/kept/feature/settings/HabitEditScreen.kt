package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.HabitsCopy
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.KeptChip
import com.example.kept.core.ui.KeptSwitch
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.switchState

private val D = DaybreakColors

/**
 * Add a habit, or Edit habit when [habitId] is set (`A9-Habit-edit`). Saving or removing
 * returns to Your habits. Removing goes through the lock-window confirm (issue #1).
 */
@Composable
fun HabitEditScreen(habitId: Long?, onClose: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    // Close once: a second tap on Remove while the page is leaving must not remove or pop again.
    var closed by remember { mutableStateOf(false) }
    val close = { if (!closed) { closed = true; onClose() } }
    if (habitId == null) {
        HabitEditPage(HabitDraft(), onClose = close, onSave = { vm.addHabit(it); close() })
        return
    }
    // The habits load after the first frame; wait for this one rather than open an empty form.
    val habit = s.habits.firstOrNull { it.id == habitId }
    if (habit == null) {
        Box(Modifier.fillMaxSize().background(D.cream))
        return
    }
    HabitEditPage(
        HabitDraft.of(habit.title, habit.proofType, habit.targetValue, habit.unit),
        onClose = close,
        onSave = { vm.saveHabit(habit, it); close() },
        editing = true,
        remove = RemoveHabit(HabitLimits.canRemove(s.habits.size)) { if (!closed) vm.requestRemoveHabit(habit.id, onRemoved = close) },
    )
    LockChangeConfirmDialog(vm)
}

/** Edit habit's "Remove habit": [allowed] is false for the last habit, which shows why instead. */
data class RemoveHabit(val allowed: Boolean, val onRemove: () -> Unit)

private val DraftSaver = listSaver<HabitDraft, Any>(
    save = { listOf(it.title, it.target, it.unit, it.photo) },
    restore = { HabitDraft(it[0] as String, it[1] as Int, it[2] as String, it[3] as Boolean) },
)

/**
 * The three questions (`A9-Habit-edit`): what (a name field and four quick picks), how much (a
 * stepper, with unit chips for a name that is not a quick pick) and whether a photo proves it.
 * The rules live in [HabitDraft]. Under a close button the title and questions scroll; "Save
 * habit" (and in edit mode a red quiet "Remove habit") stay fixed at the bottom. Onboarding's
 * "Add your own" uses this page too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditPage(
    initial: HabitDraft,
    onClose: () -> Unit,
    onSave: (HabitDraft) -> Unit,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    remove: RemoveHabit? = null,
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    // Save and Close act once (review on #149): a second tap during the exit transition would
    // add the habit twice and pop past the list. Remove may be answered by the lock-window
    // confirm and cancelled, so it does not end the page here; the caller closes once removed.
    var done by remember { mutableStateOf(false) }
    fun finish(action: () -> Unit) { if (!done) { done = true; action() } }
    BoxWithConstraints(modifier.fillMaxSize().background(D.cream).imePadding()) {
        val width = maxWidth
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header: padding 24 12 4, a 44dp close button (48 to touch).
            Box(Modifier.padding(start = 10.dp, top = 22.dp, bottom = 2.dp)) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClick = { finish(onClose) })
                        .semantics { contentDescription = HabitsCopy.CANCEL }
                        .testTag("page_back"),
                    contentAlignment = Alignment.Center,
                ) { Icon(DaybreakIcons.Close, null, Modifier.size(22.dp), tint = D.ink) }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("page_body")
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                val title = if (editing) HabitsCopy.EDIT_TITLE else HabitsCopy.ADD_TITLE
                Text(title, Modifier.testTag("page_title"), style = fitLongestWord(title, TitleStyle, width - 48.dp), color = D.ink)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(HabitsCopy.NAME_LABEL, style = LabelStyle, color = D.ink)
                    NameField(draft.title) { draft = draft.withTitle(it) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HabitDraft.PRESETS.forEach { p -> QuickPick(p.title) { draft = draft.pick(p) } }
                    }
                }

                if (draft.showsAmount) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AmountRow(draft, onLess = { draft = draft.less() }, onMore = { draft = draft.more() })
                        if (draft.showsUnits) {
                            FlowRow(
                                Modifier.fillMaxWidth().semantics { contentDescription = HabitsCopy.UNIT }.testTag("habit_units"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            ) {
                                HabitDraft.UNITS.forEach { u ->
                                    KeptChip(u, selected = draft.unit == u, onClick = { draft = draft.withUnit(u) }, modifier = Modifier.testTag("habit_unit_$u"))
                                }
                            }
                        }
                    }
                }

                PhotoRow(draft.photo) { draft = draft.togglePhoto() }
            }
            Column(
                Modifier.fillMaxWidth().background(D.cream).navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton(HabitsCopy.SAVE, onClick = { finish { onSave(draft) } }, enabled = draft.canSave, modifier = Modifier.testTag("habit_save"))
                if (remove != null) {
                    if (remove.allowed) {
                        QuietButton(HabitsCopy.REMOVE, onClick = { if (!done) remove.onRemove() }, color = D.danger, modifier = Modifier.testTag("habit_remove"))
                    } else {
                        Text(
                            HabitsCopy.KEEP_ONE,
                            Modifier.fillMaxWidth().testTag("habit_keep_one"),
                            style = DaybreakType.caption, color = D.muted, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private val TitleStyle = DaybreakType.pageTitle.copy(fontSize = 36.sp, lineHeight = 36.sp)
private val LabelStyle = DaybreakType.body.copy(fontWeight = FontWeight.Bold)
private val AmountStyle = DaybreakType.button.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")
private val SignStyle = DaybreakType.button.copy(fontSize = 22.sp, lineHeight = 26.sp)

/** The name field: 60dp, radius 20, white, a 1.5dp `field` border (2dp purple when focused), 19/700. */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = HabitsCopy.NAME_LABEL }.testTag("habit_name"),
        singleLine = true,
        textStyle = DaybreakType.button.copy(fontSize = 19.sp, lineHeight = 24.sp, color = D.ink),
        cursorBrush = SolidColor(D.purple),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(D.white)
                    .border(if (focused) 2.dp else 1.5.dp, if (focused) D.purple else D.field, shape)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(HabitsCopy.NAME_PLACEHOLDER, style = DaybreakType.button.copy(fontSize = 19.sp, lineHeight = 24.sp), color = D.placeholder)
                inner()
            }
        },
    )
}

/** A quick pick: a 36dp purple-tint chip, 14/600, 48dp to touch. */
@Composable
private fun QuickPick(label: String, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick).testTag("habit_preset_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 36.dp).clip(CircleShape).background(D.purpleTint).padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.purpleInk, maxLines = 1) }
    }
}

/**
 * "How much?" and its stepper (52dp, radius 26, white, a 1.5dp `field` border; − and + 48dp
 * wide, 22/700 purple; the amount 17/800 tabular, at least 92dp). Side by side only when the
 * whole label fits on one line beside the stepper; otherwise the stepper goes under it.
 */
@Composable
private fun AmountRow(draft: HabitDraft, onLess: () -> Unit, onMore: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("habit_amount_row")) {
        val labelW = with(density) { measurer.measure(HabitsCopy.HOW_MUCH, LabelStyle, softWrap = false, maxLines = 1).size.width.toDp() }
        val valueW = with(density) { measurer.measure(draft.amount, AmountStyle, softWrap = false, maxLines = 1).size.width.toDp() }
        val stepperW = 48.dp * 2 + maxOf(92.dp, valueW + 8.dp) + 3.dp
        val side = labelW + 12.dp + stepperW <= maxWidth
        val label: @Composable () -> Unit = { Text(HabitsCopy.HOW_MUCH, Modifier.testTag("habit_how_much"), style = LabelStyle, color = D.ink) }
        val stepper: @Composable () -> Unit = {
            val shape = RoundedCornerShape(26.dp)
            Row(
                Modifier.heightIn(min = 52.dp).clip(shape).background(D.white).border(1.5.dp, D.field, shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton("−", HabitsCopy.LESS, draft.canLess, onLess, "habit_less")
                Text(
                    draft.amount,
                    Modifier.widthIn(min = 92.dp).padding(horizontal = 4.dp).testTag("habit_amount"),
                    style = AmountStyle, color = D.ink, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                )
                StepButton("+", HabitsCopy.MORE, draft.canMore, onMore, "habit_more")
            }
        }
        if (side) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                label()
                stepper()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                label()
                stepper()
            }
        }
    }
}

@Composable
private fun StepButton(sign: String, description: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(sign, style = SignStyle, color = if (enabled) D.purple else D.disabled) }
}

/** "Prove it with a photo": the whole row is the switch; its hint under the title. */
@Composable
private fun PhotoRow(on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { stateDescription = switchState(on) }
            .testTag("habit_photo"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(HabitsCopy.PHOTO, style = LabelStyle, color = D.ink)
            Text(HabitsCopy.PHOTO_HINT, style = DaybreakType.caption.copy(lineHeight = (13 * 1.35).sp), color = D.muted)
        }
        KeptSwitch(on, onCheckedChange = null)
    }
}
