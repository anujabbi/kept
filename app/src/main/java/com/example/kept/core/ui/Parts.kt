package com.example.kept.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The Daybreak shared parts, ported from `docs/design/daybreak/A18-Parts.dc.html` (banners,
// habit rows, the empty row), `Main.dc.html` (tab bar), `A7-Settings.dc.html` (switch) and
// `A9-Habit-edit.dc.html` (chip). Buttons are in Components.kt. Every part is at least 48dp
// to touch and carries a TalkBack label.

private val D = DaybreakColors

/**
 * The smallest a tappable node may be. Set on the clickable node itself (`sizeIn` before
 * `clickable`), not through `minimumInteractiveComponentSize()`, which only pads the touch area
 * around a smaller node.
 */
private val MIN_TOUCH = 48.dp

/** A CSS `border: <width> dashed <color>` with rounded corners. */
fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp, dash: Dp = 6.dp, gap: Dp = 5.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx() - w / 2),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
    )
}

// ---------------------------------------------------------------------------------------------
// Banners

@Composable
private fun Banner(
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = textColor,
    title: String? = null,
    titleColor: Color = textColor,
    body: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, Modifier.padding(top = 1.dp).size(20.dp), tint = iconTint)
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, style = DaybreakType.bodySmall, fontWeight = FontWeight.Bold, color = titleColor)
            Text(body, style = DaybreakType.bodySmall, color = textColor)
        }
        trailing?.invoke()
    }
}

/** Red: a required permission is missing. The whole banner opens Permissions. */
@Composable
fun LockOffBanner(message: String, onFix: () -> Unit, modifier: Modifier = Modifier) {
    Banner(
        background = D.dangerTint,
        textColor = D.dangerInk,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = "Fix", role = Role.Button, onClick = onFix),
        icon = DaybreakIcons.Warning,
        iconTint = D.danger,
        title = "Lock is off",
        titleColor = D.danger,
        body = message,
        trailing = { Text("Fix", style = DaybreakType.bodySmall, fontWeight = FontWeight.ExtraBold, color = D.danger) },
    )
}

/** The first-landing body when reminders can reach the user (`A18-Parts`). */
const val FIRST_LANDING_BODY = "You can close the app now. Sprig keeps watch and reminds you when it matters."

/**
 * The first-landing body when notifications are off: the reminder promise would be false, so
 * it says how to turn them on instead (issue #41).
 */
const val FIRST_LANDING_BODY_NO_NOTIFICATIONS =
    "You can close the app now. Sprig keeps watch, but reminders are off until notifications are turned on in Settings."

/** Green: once, right after onboarding. "Got it" dismisses it. */
@Composable
fun FirstLandingBanner(onGotIt: () -> Unit, modifier: Modifier = Modifier, body: String = FIRST_LANDING_BODY) {
    Banner(
        background = D.greenTint,
        textColor = D.greenText,
        modifier = modifier,
        title = "That's it, you're all set.",
        titleColor = D.greenInk,
        body = body,
        trailing = {
            // 32dp to see, 48dp to touch.
            Box(
                Modifier.offset(y = (-8).dp).sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
                    .clickable(role = Role.Button, onClick = onGotIt),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.height(32.dp).clip(CircleShape).background(D.white).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Got it", style = DaybreakType.caption, fontWeight = FontWeight.Bold, color = D.greenInk) }
            }
        },
    )
}

/** Amber: past the give-up time with habits left. Information only. */
@Composable
fun TooLateBanner(modifier: Modifier = Modifier) {
    Banner(
        background = D.amberTint,
        textColor = D.amberInk,
        modifier = modifier.semantics(mergeDescendants = true) {},
        icon = DaybreakIcons.Clock,
        iconTint = D.amber,
        body = "Too late for today. You can still tick these off, but today won't count.",
    )
}

// ---------------------------------------------------------------------------------------------
// Habit rows

enum class HabitRowState { ToDo, Done, DoneLate }

/**
 * One habit (`A18-Parts`, 56dp rows). A tap on a to-do row ticks it, with the CONFIRM haptic;
 * a photo habit's tap is the camera's to handle, so it fires [onTick] without the haptic and
 * the caller confirms once the photo is saved. Holding a done row undoes it.
 *
 * [roomy] is Home's 1 to 3 habit row (`Main`): 68dp, radius 22, a 34dp dot, a 17sp title with
 * the detail (or state) on a second line instead of on the right.
 *
 * [night] is the lock screen's dark row (`A3-Lock`) on [Sky.lockGround]: a clear row with a
 * `#4A4590` border, and when done a `#3E3895` fill with a gold dot and an ink check. Done late
 * stays a clear row, like Home's grey late row: a `#A9A3DC` dot, soft title, "Too late" in gold.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DaybreakHabitRow(
    title: String,
    detail: String,
    state: HabitRowState,
    onTick: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    photo: Boolean = false,
    roomy: Boolean = false,
    night: Boolean = false,
) {
    val haptics = rememberHaptics()
    val done = state != HabitRowState.ToDo
    val shape = RoundedCornerShape(if (roomy) 22.dp else 18.dp)
    val dot = if (roomy) 34.dp else 28.dp
    val (ground, border) = when {
        night -> if (state == HabitRowState.Done) LockPalette.rowDone to LockPalette.rowDoneLine else Color.Transparent to LockPalette.rowLine
        state == HabitRowState.ToDo -> D.white to D.rowLine
        state == HabitRowState.Done -> D.purpleTint to D.purpleDone
        else -> D.lateRow to D.rowLine
    }
    val ink = if (night) D.skyTextLight else D.ink
    val soft = if (night) LockPalette.soft else D.muted
    Row(
        modifier.fillMaxWidth().heightIn(min = if (roomy) 68.dp else 56.dp).clip(shape).background(ground).border(2.dp, border, shape)
            .combinedClickable(
                onClickLabel = if (done) null else if (photo) "Snap photo" else "Mark done",
                onLongClickLabel = if (done) "Undo" else null,
                onClick = {
                    if (!done) {
                        if (!photo) haptics.habitTicked()
                        onTick()
                    }
                },
                onLongClick = if (done) onUndo else null,
            )
            .semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(done)
            }
            .padding(horizontal = if (roomy) 16.dp else 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (roomy) 14.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            !done -> Box(Modifier.size(dot).border(2.dp, if (night) LockPalette.ring else D.faint, CircleShape))
            else -> Box(
                Modifier.size(dot).clip(CircleShape).background(
                    when {
                        night -> if (state == HabitRowState.Done) Sky.gold else LockPalette.ring
                        state == HabitRowState.Done -> D.purple
                        else -> D.disabled
                    },
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(DaybreakIcons.Check, null, Modifier.size(if (roomy) 18.dp else 15.dp), tint = if (night) D.ink else D.white) }
        }
        val titleText: @Composable (Modifier) -> Unit = { m ->
            Text(
                title,
                m,
                style = if (roomy) DaybreakType.rowTitle.copy(fontSize = 17.sp, lineHeight = 22.sp) else DaybreakType.rowTitle,
                color = if (state == HabitRowState.DoneLate) soft else ink,
                textDecoration = if (state == HabitRowState.Done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val trailing: @Composable () -> Unit = {
            when {
                state == HabitRowState.Done -> Text(if (roomy) "Done. Hold to undo" else "Done", style = DaybreakType.caption, color = soft)
                state == HabitRowState.DoneLate -> Text("Too late", style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = if (night) Sky.gold else D.amberLabel)
                photo -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val accent = if (night) LockPalette.soft else D.purple
                    Icon(DaybreakIcons.Camera, null, Modifier.size(16.dp), tint = accent)
                    Text("Snap photo", style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = accent)
                }
                else -> Text(detail, style = DaybreakType.caption, color = soft)
            }
        }
        if (roomy) {
            Column(Modifier.weight(1f)) {
                titleText(Modifier)
                trailing()
            }
        } else {
            titleText(Modifier.weight(1f))
            trailing()
        }
    }
}

/** No habits yet (`A18-Parts`): a dashed row that opens Add a habit. */
@Composable
fun AddFirstHabitRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).dashedBorder(2.dp, D.ring, 20.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.purpleTint), contentAlignment = Alignment.Center) {
            Icon(DaybreakIcons.Plus, null, Modifier.size(20.dp), tint = D.purple)
        }
        Column {
            Text("Add your first habit", style = DaybreakType.rowTitle, color = D.ink)
            Text("Apps lock until it is done each day.", style = DaybreakType.caption, color = D.muted)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Switch and chip

/**
 * The Daybreak switch (`A7-Settings`): a 48 × 28 track, purple on and `#CFC8E6` off, with a
 * 22dp white knob. Pass [onCheckedChange] = null when the whole row is the toggle and carries
 * the label; otherwise give it a [label] for TalkBack.
 */
@Composable
fun KeptSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val track by animateColorAsState(if (checked) D.purple else D.ring, keptTween(200), label = "switch-track")
    val knob by animateDpAsState(if (checked) 23.dp else 3.dp, keptTween(200), label = "switch-knob")
    val toggle = if (onCheckedChange != null) {
        Modifier.sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
    } else Modifier
    Box(modifier.then(toggle), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 48.dp, height = 28.dp).clip(CircleShape).background(track)) {
            Box(Modifier.offset(x = knob, y = 3.dp).size(22.dp).clip(CircleShape).background(D.white))
        }
    }
}

/**
 * A chip (`A9-Habit-edit` unit chips): 36dp, fully round, purple when selected and purple
 * tint when not. 48dp to touch.
 */
@Composable
fun KeptChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    Box(
        modifier.sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH).selectable(selected = selected, role = role, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (selected) D.purple else D.purpleTint)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = DaybreakType.bodySmall, fontWeight = FontWeight.Bold, color = if (selected) D.white else D.purpleInk, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tab bar

enum class KeptTab(val label: String, val icon: ImageVector) {
    Today("Today", DaybreakIcons.Today),
    Sprig("Sprig", DaybreakIcons.Sprig),
    Settings("Settings", DaybreakIcons.Settings),
}

/**
 * The bottom tab bar (`Main`): 74dp on cream under a 1.5dp `line` border; the current tab is
 * purple, the others muted. It pads itself above the navigation bar.
 */
@Composable
fun KeptTabBar(selected: KeptTab?, onSelect: (KeptTab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(D.cream).drawBehind {
            drawRect(D.line, size = Size(size.width, 1.5.dp.toPx()))
        }.padding(top = 1.5.dp).navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 74.dp)) {
            KeptTab.entries.forEach { tab ->
                val on = tab == selected
                val ink = if (on) D.purple else D.muted
                Column(
                    Modifier.weight(1f).heightIn(min = 74.dp)
                        .selectable(selected = on, role = Role.Tab, onClick = { if (!on) onSelect(tab) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, Modifier.size(24.dp), tint = ink)
                    Spacer(Modifier.height(4.dp))
                    Text(tab.label, style = DaybreakType.tab, color = ink, maxLines = 1)
                }
            }
        }
    }
}
