package com.example.kept.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kept.core.domain.PromiseBlank
import com.example.kept.core.domain.PromiseCopy
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseLimits
import com.example.kept.core.domain.PromisePart
import com.example.kept.core.domain.PromiseRules
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.LocalReduceMotion
import com.example.kept.core.ui.Motion
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.keptTween
import com.example.kept.core.ui.sprig.SprigView
import kotlinx.coroutines.delay

private val D = DaybreakColors

/** How long Sprig cheers after Done before the promise closes by itself (#218). */
const val PROMISE_CHEER_MS = 1_500L

/**
 * The edit page's way out under Done: "Remove habit" or "Delete goal" ([label]); when not
 * [allowed] (the last habit) the [note] shows instead.
 */
data class PromiseRemove(val label: String, val allowed: Boolean, val note: String? = null, val onRemove: () -> Unit)

private val DraftSaver = listSaver<PromiseDraft, Any?>(
    save = { listOf(it.activity, it.target, it.unit, it.days, it.photo, it.custom) },
    restore = { PromiseDraft(it[0] as String, it[1] as Int, it[2] as String, it[3] as Int?, it[4] as Boolean, it[5] as Boolean) },
)

/**
 * The promise (#218): "Your promise" and Cancel at the top, the sentence centred with a dropdown
 * behind every blank (the activity, the amount, the unit, how often), the line under it for a
 * weekly promise, and Done at the bottom. "Something else…" turns the activity into a text field.
 *
 * Done hands the promise to [onDone] with a callback the caller runs once it is saved; a save
 * held for the lock-change confirm and cancelled never runs it, and the page stays. Once saved,
 * the text stays as it is, Sprig rises from the bottom and cheers for [PROMISE_CHEER_MS], and the
 * page closes by itself ([onClose]). With reduce motion Sprig is simply there. Cancel and system
 * Back close it unsaved; Back first closes an open list.
 *
 * [taken] names Done refuses (onboarding keeps habits apart by name). [remove] adds the edit
 * page's "Remove habit" or "Delete goal" under Done.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromisePage(
    initial: PromiseDraft,
    limits: PromiseLimits,
    onDone: (PromiseDraft, saved: () -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    form: SprigForm = SprigForm.SPRIG,
    taken: Set<String> = emptySet(),
    remove: PromiseRemove? = null,
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(limits.fit(initial)) }
    var open by rememberSaveable { mutableStateOf<PromiseBlank?>(null) }
    var cheering by rememberSaveable { mutableStateOf(false) }
    // The activity is a text field once "Something else…" is picked, or for a new one with no name
    // yet; a saved activity of its own reads as a blank like the presets until then.
    var typing by rememberSaveable { mutableStateOf(initial.custom && initial.activity.isBlank()) }
    var closed by remember { mutableStateOf(false) }
    fun close() { if (!closed) { closed = true; onClose() } }
    val canDone = draft.canSave && taken.none { it.equals(draft.name, ignoreCase = true) }

    BackHandler(enabled = !closed) { if (open != null) open = null else close() }
    LaunchedEffect(cheering) {
        if (cheering) {
            delay(PROMISE_CHEER_MS)
            close()
        }
    }

    // Where the sentence ends on the page, so an open list hangs under it.
    var sentenceBottom by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // The page's own coordinates, so an open list hangs under the sentence as drawn (padding and
    // scroll included), and may reach down over Done, as the mockup's days list does.
    var page by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().background(D.cream).imePadding().onGloballyPositioned { page = it }.testTag("promise_page")) {
        val pageHeight = maxHeight
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    PromiseCopy.LABEL,
                    Modifier.weight(1f).testTag("promise_label"),
                    style = DaybreakType.caption.copy(letterSpacing = 0.04.em), fontWeight = FontWeight.ExtraBold, color = D.muted,
                )
                QuietButton(PromiseCopy.CANCEL, onClick = { close() }, enabled = !cheering, color = D.muted, modifier = Modifier.testTag("promise_cancel"))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val minHeight = maxHeight
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Sentence(
                        draft, open, typing,
                        onOpen = { b -> if (!cheering) open = if (open == b) null else b },
                        onType = { draft = draft.withActivity(it) },
                        modifier = Modifier.onGloballyPositioned { c ->
                            val b = page
                            sentenceBottom = if (b != null && b.isAttached) b.localBoundingBoxOf(c).bottom else c.boundsInParent().bottom
                        },
                    )
                    val line = draft.line
                    if (line != null) {
                        Text(
                            line,
                            Modifier.padding(top = 18.dp).testTag("promise_line"),
                            style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.muted, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // While Sprig cheers the footer keeps its place, invisible, so the sentence does not move.
                Column(
                    Modifier.fillMaxWidth().alpha(if (cheering) 0f else 1f).then(if (cheering) Modifier.clearAndSetSemantics { } else Modifier),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrimaryButton(
                        PromiseCopy.DONE,
                        onClick = { if (!cheering && !closed) { open = null; onDone(draft) { cheering = true } } },
                        enabled = canDone,
                        modifier = Modifier.testTag("promise_done"),
                    )
                    if (remove != null) {
                        if (remove.allowed) QuietButton(remove.label, onClick = { if (!cheering) remove.onRemove() }, color = D.danger, modifier = Modifier.testTag("promise_remove"))
                        else if (remove.note != null) {
                            Text(remove.note, Modifier.fillMaxWidth().padding(top = 4.dp).testTag("promise_keep_one"), style = DaybreakType.caption, color = D.muted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        val list = open
        if (list != null) {
            // A tap outside the list closes it, as a dropdown does. Not in the semantics tree:
            // TalkBack closes the list with Back or by picking.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { open = null } })
            val top = with(density) { sentenceBottom.toDp() } + 4.dp
            val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
            OptionsList(
                list, draft, limits,
                onPick = { d -> if (d.custom && list == PromiseBlank.ACTIVITY) typing = true; draft = d; open = null },
                modifier = Modifier.padding(horizontal = 24.dp).offset(y = top.coerceAtMost(pageHeight - 160.dp))
                    .heightIn(max = (pageHeight - top - bottomInset - 16.dp).coerceAtLeast(144.dp)),
            )
        }
        if (cheering) CheeringSprig(form, Modifier.align(Alignment.BottomCenter))
    }
}

/** Sprig rising from the bottom edge and cheering; with reduce motion it is simply there. */
@Composable
private fun CheeringSprig(form: SprigForm, modifier: Modifier = Modifier) {
    var risen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { risen = true }
    val reduce = LocalReduceMotion.current
    val y by animateDpAsState(if (risen || reduce) 40.dp else 240.dp, keptTween(Motion.RISE_MS, Motion.RiseEasing), label = "promise-sprig")
    SprigView(
        form, SprigPose.CHEER,
        modifier.navigationBarsPadding().offset(y = y).size(220.dp).testTag("promise_sprig"),
    )
}

/**
 * The sentence: words and blanks flowing centred, 40/800. A blank is purple with a 3dp underline
 * and a small chevron, and is a button that TalkBack reads with its value. "Something else…"
 * makes the activity a text field (with the chevron beside it to open the list again).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sentence(draft: PromiseDraft, open: PromiseBlank?, typing: Boolean, onOpen: (PromiseBlank) -> Unit, onType: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().testTag("promise_sentence"),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        draft.parts.forEach { part ->
            when (part) {
                is PromisePart.Words -> part.text.split(' ').forEach { w -> Text(w, style = SentenceStyle, color = D.ink) }
                is PromisePart.Blank -> Row(verticalAlignment = Alignment.Bottom) {
                    if (part.blank == PromiseBlank.ACTIVITY && typing) ActivityField(draft, open == PromiseBlank.ACTIVITY, onType, onOpen = { onOpen(PromiseBlank.ACTIVITY) })
                    else BlankButton(part, open == part.blank, onClick = { onOpen(part.blank) })
                    if (part.after.isNotEmpty()) Text(part.after, style = SentenceStyle, color = D.ink)
                }
            }
        }
    }
}

private val SentenceStyle = DaybreakType.headline.copy(fontSize = 40.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em)

/** The purple underline under a blank. */
private fun Modifier.underline(): Modifier = drawBehind {
    val w = 3.dp.toPx()
    drawLine(D.purple, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

@Composable
private fun BlankButton(part: PromisePart.Blank, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clip(RoundedCornerShape(6.dp))
            .background(if (open) D.purpleTint else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = "Change ${part.blank.label.lowercase()}", onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = part.blank.label }
            .underline()
            .padding(horizontal = 2.dp)
            .testTag("promise_blank_${part.blank.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(part.value, style = SentenceStyle, color = D.purple)
        Icon(DaybreakIcons.Chevron, null, Modifier.padding(start = 2.dp, top = 8.dp).size(14.dp).rotate(90f), tint = D.purple)
    }
}

/** "Something else…": the activity typed in place, with the chevron beside it to go back to the list. */
@Composable
private fun ActivityField(draft: PromiseDraft, open: Boolean, onType: (String) -> Unit, onOpen: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(D.purpleTint).underline(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft.activity,
            onValueChange = onType,
            modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 60.dp).heightIn(min = 48.dp).padding(horizontal = 4.dp)
                .focusRequester(focus)
                .semantics { contentDescription = PromiseCopy.ACTIVITY }
                .testTag("promise_activity_field"),
            singleLine = true,
            textStyle = SentenceStyle.copy(color = D.purple),
            cursorBrush = SolidColor(D.purple),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.activity.isEmpty()) Text(PromiseCopy.PLACEHOLDER, style = SentenceStyle, color = D.purpleDone, maxLines = 1, softWrap = false)
                    inner()
                }
            },
        )
        Box(
            Modifier.size(48.dp).background(if (open) D.purpleTint else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = "${PromiseCopy.ACTIVITY} list" }
                .testTag("promise_blank_activity"),
            contentAlignment = Alignment.Center,
        ) { Icon(DaybreakIcons.Chevron, null, Modifier.size(16.dp).rotate(90f), tint = D.purple) }
    }
}

/** One list's options for [blank]: a white card, radius 22, the current one ticked on purple tint. */
@Composable
private fun OptionsList(blank: PromiseBlank, draft: PromiseDraft, limits: PromiseLimits, onPick: (PromiseDraft) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    // The options scroll when the list is taller than the room under the sentence; the limit line
    // stays in view under them.
    Column(modifier.fillMaxWidth().clip(shape).background(D.white).border(2.dp, D.purpleDone, shape).testTag("promise_list_${blank.name.lowercase()}")) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(8.dp).selectableGroup()) {
            when (blank) {
                PromiseBlank.ACTIVITY -> {
                    PromiseRules.PRESETS.forEach { p -> Option(p.title, draft.preset == p, true, "promise_option_${p.title}") { onPick(draft.pick(p)) } }
                    Option(PromiseCopy.SOMETHING_ELSE, draft.custom, true, "promise_option_something_else", hint = PromiseCopy.TYPE_YOUR_OWN, accent = true) {
                        onPick(if (draft.custom) draft else draft.somethingElse())
                    }
                }
                PromiseBlank.AMOUNT -> draft.amounts.forEach { n -> Option(n.toString(), n == draft.target, true, "promise_option_$n") { onPick(draft.withTarget(n)) } }
                PromiseBlank.UNIT -> PromiseRules.UNITS.forEach { u -> Option(PromiseCopy.unitOption(u), u == draft.unit, true, "promise_option_$u") { onPick(draft.withUnit(u)) } }
                PromiseBlank.DAYS -> draft.dayChoices.forEach { d ->
                    Option(PromiseCopy.daysOption(d), d == draft.days, limits.allows(d), "promise_option_days_${d ?: 0}") { onPick(draft.withDays(d)) }
                }
            }
        }
        if (blank == PromiseBlank.DAYS && limits.line != null) {
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height1().background(D.line))
            Text(
                limits.line,
                Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("promise_limit"),
                style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted,
            )
        }
    }
}

private fun Modifier.height1(): Modifier = this.then(Modifier.heightIn(min = 1.dp, max = 1.dp))

@Composable
private fun Option(label: String, selected: Boolean, enabled: Boolean, tag: String, hint: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) D.purpleTint else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val ink = when {
            !enabled -> D.disabled
            selected || accent -> D.purple
            else -> D.ink
        }
        Text(label, Modifier.weight(1f), style = DaybreakType.rowTitle, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, color = ink)
        if (hint != null) Text(hint, style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted, maxLines = 1)
        if (selected) Icon(DaybreakIcons.Check, null, Modifier.size(14.dp), tint = D.purple)
    }
}
