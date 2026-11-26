package com.example.kept.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// The habit cards of onboarding's habit step (`A10-Onb-Habits`, #205), shared with Settings →
// Your promises (#236), which is built to look like it.

private val D = DaybreakColors

/**
 * A habit card: at least 64dp, radius 20, a 2dp ring; picked is purple pale with a purple ring.
 * A 40dp purple-tint icon tile, the [title] (16/700) over its [detail] (13/500 muted) and an
 * optional [chip] under them, then [trailing] (onboarding's check, Your promises' chevron).
 * [action] makes the card a toggle or a button; [tag] names it, and `{tag}_title` and
 * `{tag}_detail` its two lines.
 */
@Composable
fun PromiseCard(
    icon: ImageVector,
    title: String,
    detail: String,
    action: Modifier,
    tag: String,
    picked: Boolean = false,
    chip: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(if (picked) D.purplePale else D.white)
            .border(2.dp, if (picked) D.purple else D.lineSoft, shape)
            .then(action)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.purpleTint), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = D.purple)
        }
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.testTag("${tag}_title"), style = DaybreakType.rowTitle, color = D.ink)
            Text(detail, Modifier.testTag("${tag}_detail"), style = DaybreakType.caption, color = D.muted)
            chip?.invoke()
        }
        trailing()
    }
}

/**
 * The how-often chip (#205): "Every day" or "4 days a week", a white pill with a purple ring and
 * a chevron, in a 48dp tall box. With [onClick] it is its own button and TalkBack reads
 * [description] once; without, a tap reaches the card under it and TalkBack reads the label
 * with the card.
 */
@Composable
fun CadenceChip(label: String, tag: String, onClick: (() -> Unit)? = null, description: String? = null) {
    val action = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
            // One label, read once: the visible "4 days a week" would otherwise be read again after it.
            .clearAndSetSemantics {
                contentDescription = description ?: label
                role = Role.Button
                onClick { onClick(); true }
            }
    }
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag(tag).then(action),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.clip(CircleShape).background(D.white).border(1.5.dp, D.purpleDone, CircleShape)
                .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = DaybreakType.caption, fontWeight = FontWeight.Bold, color = D.purpleInk)
            Icon(DaybreakIcons.Chevron, null, Modifier.size(14.dp).rotate(90f), tint = D.purpleInk)
        }
    }
}

/** "Add your own" or "Add a habit": a 64dp dashed row with a white plus tile. */
@Composable
fun DashedAddRow(label: String, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).dashedBorder(2.dp, D.ring, 20.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.white), contentAlignment = Alignment.Center) {
            Icon(DaybreakIcons.Plus, null, Modifier.size(20.dp), tint = D.purple)
        }
        Text(label, style = DaybreakType.rowTitle, color = D.ink)
    }
}
