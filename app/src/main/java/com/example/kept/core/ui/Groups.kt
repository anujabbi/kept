package com.example.kept.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.domain.SettingsRowFit

// Grouped rows (`A7-Settings`, `A15-History`): a section label, then a white radius-22 card with
// a 1.5dp `lineSoft` border whose rows are split by 1.5dp rules.

private val D = DaybreakColors
private val CARD_RADIUS = 22.dp
private val RULE = 1.5.dp

/**
 * One row of a card that a lazy list draws row by row (`A12` app rows, `A15` history rows):
 * the row clips to its part of the radius-22 card, fills it white, and draws its part of the
 * border plus the rule above it when it is not the [first].
 */
fun Modifier.cardSegment(first: Boolean, last: Boolean): Modifier {
    val top = if (first) CARD_RADIUS else 0.dp
    val bottom = if (last) CARD_RADIUS else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    return clip(shape).background(D.white).drawWithContent {
        drawContent()
        val w = RULE.toPx()
        val r = CARD_RADIUS.toPx()
        // The card's outline, extended past this row's ends where the card goes on, then
        // clipped to the row.
        val extTop = if (first) 0f else r * 2
        val extBottom = if (last) 0f else r * 2
        clipRect {
            drawRoundRect(
                D.lineSoft,
                topLeft = Offset(w / 2, w / 2 - extTop),
                size = Size(size.width - w, size.height - w + extTop + extBottom),
                cornerRadius = CornerRadius(r - w / 2),
                style = Stroke(w),
            )
            if (!first) drawLine(D.lineSoft, Offset(0f, w / 2), Offset(size.width, w / 2), w)
        }
    }
}

/** A group: its [title] (15/700, muted, 4dp in) and the card, 8dp apart. No title, no label. */
@Composable
fun RowGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(title, Modifier.padding(horizontal = 4.dp), style = DaybreakType.body, fontWeight = FontWeight.Bold, color = D.muted)
        }
        val shape = RoundedCornerShape(CARD_RADIUS)
        Column(Modifier.fillMaxWidth().clip(shape).background(D.white).border(RULE, D.lineSoft, shape), content = content)
    }
}

/** Draws the 1.5dp rule above a row that is not the first in its card. */
private fun Modifier.ruleAbove(show: Boolean): Modifier =
    if (!show) this else drawBehind { drawRect(D.lineSoft, size = Size(size.width, RULE.toPx())) }

private val titleStyle = DaybreakType.rowTitle
private val hintStyle = DaybreakType.caption.copy(lineHeight = (13 * 1.35).sp)
private val valueStyle = DaybreakType.body.copy(fontWeight = FontWeight.SemiBold)

/** How a row's value is drawn: muted text, or a coloured status with a dot ("All set"). */
data class RowValue(val text: String, val color: Color = D.muted, val dot: Color? = null)

/**
 * A row that opens something (`A7-Settings`): at least 56dp, 16dp sides; the title 16/700 with
 * an optional hint 13/500 under it, the value 15/600 on the right, then an 18dp chevron. The
 * value sits beside the title only when [SettingsRowFit] says it fits; otherwise under it.
 */
@Composable
fun ValueRow(
    title: String,
    value: RowValue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    first: Boolean = false,
    tag: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = if (value.dot != null) valueStyle.copy(fontWeight = FontWeight.Bold) else valueStyle
    BoxWithConstraints(
        modifier.fillMaxWidth().ruleAbove(!first)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        fun width(text: String, s: TextStyle): Float = with(density) {
            measurer.measure(text, s, softWrap = false, maxLines = 1).size.width.toDp().value
        }
        val valueW = width(value.text, style) + (if (value.dot != null) 14f else 0f)
        val trailing = valueW + 12f + 18f
        val hintWord = hint?.split(' ')?.maxOf { width(it, hintStyle) } ?: 0f
        val side = SettingsRowFit.sideBySide(width(title, titleStyle), hintWord, trailing, maxWidth.value)
        val valueText: @Composable () -> Unit = { ValueText(value, style, tag) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, if (tag != null) Modifier.testTag("${tag}_title") else Modifier, style = titleStyle, color = D.ink)
                if (hint != null) Text(hint, style = hintStyle, color = D.muted)
                if (!side) Box(Modifier.padding(top = 2.dp)) { valueText() }
            }
            if (side) valueText()
            Icon(DaybreakIcons.Chevron, null, Modifier.size(18.dp), tint = D.faint)
        }
    }
}

@Composable
private fun ValueText(value: RowValue, style: TextStyle, tag: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (value.dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(value.dot))
        Text(
            value.text,
            if (tag != null) Modifier.testTag("${tag}_value") else Modifier,
            style = style, color = value.color, maxLines = 1, softWrap = false,
        )
    }
}

/**
 * A row that is a switch (`A7-Settings`): the whole row toggles, TalkBack reads it as a switch
 * with its title, hint and "On" / "Off". At least 56dp; the text column has 10dp above and
 * below so a two-line hint keeps clear of the rules.
 */
@Composable
fun SwitchRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    tag: String? = null,
) {
    Row(
        modifier.fillMaxWidth().ruleAbove(!first)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { stateDescription = switchState(checked) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, style = titleStyle, color = D.ink)
            if (hint != null) Text(hint, style = hintStyle, color = D.muted)
        }
        KeptSwitch(checked, onCheckedChange = null)
    }
}
