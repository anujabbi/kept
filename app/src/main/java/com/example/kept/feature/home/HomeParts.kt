package com.example.kept.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.HomeCopy
import com.example.kept.core.ui.DayDot
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.dashedBorder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Home-only parts, ported from `Main.dc.html` and `A2-Home-6.dc.html`: the streak pill, the
// week row and the "Shield ready" button with its card.

private val D = DaybreakColors

/** "{n} day streak" on the sky (`Main`): 32dp, white at 28%, never wraps. */
@Composable
internal fun StreakPill(days: Int, ink: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(D.white.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp).testTag("home_streak"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(DaybreakIcons.Flame, null, Modifier.size(14.dp), tint = ink)
        Text(
            HomeCopy.streak(days),
            style = DaybreakType.bodySmall.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What a day in the week row shows (spec, Home): kept, shielded, today (not yet kept), missed,
 * or a day the lock was off. Today turns gold once it is finished in time.
 */
internal fun dayDot(isToday: Boolean, record: DayRecordEntity?, todayKept: Boolean): DayDot = when {
    isToday -> if (todayKept) DayDot.DONE else DayDot.TODAY
    record == null -> DayDot.MISSED
    // Hollow, not grey: a day the lock was off is KEPT's failure, and it costs nothing (issue #2).
    record.unprotected && !record.writtenOff -> DayDot.UNPROTECTED
    record.countedForStreak -> DayDot.DONE
    // The shield cannot save a written-off day (issue #143), whatever an older build recorded.
    record.writtenOff -> DayDot.MISSED
    record.shieldConsumed -> DayDot.SHIELDED
    else -> DayDot.MISSED
}

private fun dayWord(d: DayDot): String = when (d) {
    DayDot.DONE -> "kept"
    DayDot.SHIELDED -> "saved by the shield"
    DayDot.TODAY -> "today"
    DayDot.UNPROTECTED -> "lock was off, doesn't count"
    DayDot.MISSED, DayDot.FUTURE -> "missed"
}

/** One day of the week row: a 30dp circle over its weekday initial. */
@Composable
private fun WeekDay(date: LocalDate, dot: DayDot, modifier: Modifier = Modifier) {
    val label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    val name = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Column(
        modifier.clearAndSetSemantics { contentDescription = "$name, ${dayWord(dot)}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val circle = Modifier.size(30.dp)
        val drawn = when (dot) {
            DayDot.DONE -> circle.clip(CircleShape).background(D.streakGold)
            DayDot.SHIELDED -> circle.clip(CircleShape).background(D.shieldTint).border(2.dp, D.shieldBlue, CircleShape)
            DayDot.TODAY -> circle.dashedBorder(2.dp, D.purple, 15.dp, dash = 4.dp, gap = 3.dp)
            DayDot.UNPROTECTED -> circle.dashedBorder(2.dp, D.faint, 15.dp, dash = 4.dp, gap = 3.dp)
            DayDot.MISSED, DayDot.FUTURE -> circle.clip(CircleShape).background(D.missedDay)
        }
        Box(drawn.testTag("home_day_${dot.name.lowercase()}"))
        Text(label, style = DaybreakType.smallLabel, fontWeight = FontWeight.SemiBold, color = D.muted, maxLines = 1)
    }
}

/** "Last 7 days" with the best streak, then seven days oldest first. */
@Composable
internal fun WeekSection(days: List<Pair<LocalDate, DayDot>>, best: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Last 7 days", Modifier.weight(1f, fill = false), style = DaybreakType.section, color = D.ink)
            Text(HomeCopy.bestStreak(best), style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.muted, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { (date, dot) -> WeekDay(date, dot, Modifier.weight(1f)) }
        }
    }
}

/**
 * "Shield ready" (13sp, blue, shield icon; 28dp to see, 48dp to touch). Tapping toggles a dark
 * card just above it. Used this week it reads "Shield used this week" in muted grey, and the
 * card says when the next one comes. The card does not take space: it is drawn over whatever is
 * above the button.
 */
@Composable
internal fun ShieldButton(ready: Boolean, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (ready) D.shieldBlue else D.muted
    val label = if (ready) HomeCopy.SHIELD_READY else HomeCopy.SHIELD_USED
    Box(modifier) {
        Row(
            Modifier.sizeIn(minHeight = 48.dp, minWidth = 48.dp)
                .clickable(onClickLabel = if (open) "Close" else "Explain", role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .testTag("home_shield"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(DaybreakIcons.Shield, null, Modifier.size(14.dp), tint = color)
            Text(label, style = DaybreakType.caption, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
        if (open) {
            ShieldCard(
                ready,
                // Zero-size in layout; drawn so its bottom sits 8dp above the button's 28dp visual.
                Modifier.layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, minHeight = 0, maxWidth = minOf(c.maxWidth, 290.dp.roundToPx())))
                    layout(0, 0) { p.place(0, (10.dp - 8.dp).roundToPx() - p.height) }
                },
            )
        }
    }
}

@Composable
private fun ShieldCard(ready: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.shadow(12.dp, shape, ambientColor = D.ink.copy(alpha = 0.25f), spotColor = D.ink.copy(alpha = 0.25f))
            .clip(shape).background(D.ink).padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("home_shield_card"),
    ) {
        Text(HomeCopy.SHIELD_CARD_TITLE, style = DaybreakType.bodySmall, fontWeight = FontWeight.Bold, color = D.cream)
        Spacer(Modifier.height(4.dp))
        Text(if (ready) HomeCopy.SHIELD_CARD_READY else HomeCopy.SHIELD_CARD_USED, style = DaybreakType.bodySmall, color = D.cream)
    }
}
