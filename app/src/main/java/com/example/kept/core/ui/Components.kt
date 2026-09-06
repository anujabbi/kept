package com.example.kept.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun KeptCard(
    modifier: Modifier = Modifier,
    background: Color = KeptTheme.colors.surface2,
    border: Color? = KeptTheme.colors.border,
    shape: Shape = RoundedCornerShape(12.dp),
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.clip(shape).background(background, shape)
    if (border != null) m = m.border(1.dp, border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = KeptTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.ink, contentColor = c.onInk,
            disabledContainerColor = c.surface0, disabledContentColor = c.textMuted,
        ),
        elevation = null,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = KeptTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, c.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary, disabledContentColor = c.textMuted),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = KeptTheme.colors.textSecondary, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = KeptTheme.colors.textMuted)) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, background: Color = KeptTheme.colors.amber50, foreground: Color = KeptTheme.colors.amber800, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(13.dp), tint = foreground); Spacer(Modifier.width(5.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = KeptTheme.colors.textSecondary)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = KeptTheme.colors.textMuted)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = KeptTheme.colors.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = KeptTheme.colors.textSecondary)
        }
    }
}

@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = KeptTheme.colors.purple400, track: Color = KeptTheme.colors.surface0, height: Dp = 5.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(RoundedCornerShape(999.dp)).background(color))
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = KeptTheme.colors.textPrimary, divider: Boolean = true) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(key, style = MaterialTheme.typography.bodyMedium, color = KeptTheme.colors.textSecondary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(KeptTheme.colors.border))
    }
}

@Composable
fun InfoBox(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, background: Color = KeptTheme.colors.surface1, foreground: Color = KeptTheme.colors.textSecondary) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(background).padding(12.dp), verticalAlignment = Alignment.Top) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp).padding(top = 1.dp), tint = foreground); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.bodySmall, color = foreground)
    }
}

/** A row of seven squares, oldest first. */
@Composable
fun WeekDots(days: List<DayDot>, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            val bg = when (d) {
                DayDot.DONE -> color
                DayDot.SHIELDED -> color.copy(alpha = 0.35f)
                DayDot.MISSED -> KeptTheme.colors.surface0
                DayDot.UNPROTECTED -> Color.Transparent
                DayDot.FUTURE -> KeptTheme.colors.surface0.copy(alpha = 0.5f)
                DayDot.TODAY -> Color.Transparent
            }
            var m = Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(bg)
            if (d == DayDot.UNPROTECTED) m = m.border(1.5.dp, KeptTheme.colors.danger, RoundedCornerShape(6.dp))
            if (d == DayDot.TODAY) m = m.border(1.5.dp, color, RoundedCornerShape(6.dp))
            Box(m)
        }
    }
}

enum class DayDot { DONE, SHIELDED, MISSED, UNPROTECTED, FUTURE, TODAY }

@Composable
fun Avatar(initials: String, modifier: Modifier = Modifier, size: Dp = 40.dp, background: Color = KeptTheme.colors.blue50, foreground: Color = KeptTheme.colors.blue800) {
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.labelLarge, color = foreground, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Selectable(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val c = KeptTheme.colors
    val border by animateColorAsState(if (selected) c.blue400 else c.border, label = "sel")
    Row(
        modifier.fillMaxWidth().clip(shape).background(if (selected) c.blue50.copy(alpha = if (c.isDark) 0.4f else 0.35f) else c.surface2)
            .border(if (selected) 2.dp else 1.dp, border, shape).clickable(onClick = onClick).padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center, content = content)

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = KeptTheme.colors.textMuted, textAlign = align)
