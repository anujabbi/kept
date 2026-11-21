package com.example.kept.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.RecapOutcome
import com.example.kept.core.domain.SettingsCopy
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.PageHeader
import com.example.kept.core.ui.StepPage
import com.example.kept.core.ui.cardSegment
import com.example.kept.feature.recap.RecapRules
import java.time.LocalDate
import com.example.kept.core.ui.DaybreakColors as D

/** Settings → History (`A15-History`). Tapping a day opens its recap, as the old list did. */
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenRecap: (String) -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    HistoryContent(s.history, s.sprig.bestStreak, onBack = onBack, onOpenDay = onOpenRecap)
}

/**
 * How one outcome's dot and label are drawn (`A15` script, `kinds`): the dot's fill, its 2dp
 * ring and whether that ring is dashed, then the label's colour.
 */
internal data class HistoryLook(val fill: Color, val ring: Color, val dashed: Boolean, val ink: Color)

internal fun historyLook(o: RecapOutcome): HistoryLook = when (o) {
    RecapOutcome.KEPT -> HistoryLook(D.streakGold, D.streakGold, false, D.amberLabel)
    RecapOutcome.SHIELD -> HistoryLook(D.shieldTint, D.shieldBlue, false, D.shieldInk)
    RecapOutcome.SLIPPED -> HistoryLook(D.missedDay, D.disabled, false, D.muted)
    RecapOutcome.TOO_LATE -> HistoryLook(D.lateTint, D.lateRing, false, D.amberLabel)
    RecapOutcome.LOCK_OFF -> HistoryLook(Color.Transparent, D.faint, true, D.muted)
    RecapOutcome.WRITTEN_OFF -> HistoryLook(D.dangerTint, D.danger, false, D.danger)
}

/**
 * The history page without the ViewModel: back arrow, "History", "Your last 14 days. Best
 * streak: {n} days.", then the last 14 recorded days, newest first, in one card; or, before the
 * first recap, a card saying when it lands. [records] is newest first.
 */
@Composable
internal fun HistoryContent(records: List<DayRecordEntity>, bestStreak: Int, onBack: () -> Unit, onOpenDay: (String) -> Unit) {
    val days = records.take(SettingsCopy.HISTORY_DAYS)
    StepPage(
        header = PageHeader.Back(onBack),
        title = SettingsCopy.HISTORY,
        line = SettingsCopy.historyLine(bestStreak),
        modifier = Modifier.testTag("settings_history_page"),
        bodySpacing = 0.dp,
    ) {
        if (days.isEmpty()) {
            item(key = "empty") {
                val shape = RoundedCornerShape(22.dp)
                Box(
                    Modifier.fillMaxWidth().clip(shape).background(D.white).border(1.5.dp, D.lineSoft, shape)
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) { Text(SettingsCopy.HISTORY_EMPTY, Modifier.testTag("history_empty"), style = DaybreakType.body, color = D.muted) }
            }
        } else {
            itemsIndexed(days, key = { _, r -> r.date }) { i, r ->
                HistoryRow(r, first = i == 0, last = i == days.lastIndex) { onOpenDay(r.date) }
            }
        }
    }
}

/**
 * One day (`A15`): at least 54dp, 16dp sides; a 14dp dot, the date 15/700, "{done} of {total}"
 * 13/600 muted, and the outcome label 13/700 right-aligned in at least 92dp. When the date and
 * the two values do not fit on one line (a narrow screen at a large font) the values go under
 * the date, so nothing is split or cut off. TalkBack reads the row as one button: date, count
 * and outcome.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(r: DayRecordEntity, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val date = LocalDate.parse(r.date)
    val outcome = RecapRules.outcome(r)
    val look = historyLook(outcome)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).cardSegment(first, last)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("history_${r.date}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(look)
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                SettingsCopy.date(date),
                Modifier.align(Alignment.CenterVertically).padding(end = 12.dp).testTag("history_date_${r.date}"),
                style = DaybreakType.body.copy(fontWeight = FontWeight.Bold),
                color = D.ink, maxLines = 1, softWrap = false,
            )
            Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SettingsCopy.habitsDone(r.habitsDone, r.habitsTotal),
                    style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted, maxLines = 1, softWrap = false,
                )
                Text(
                    SettingsCopy.outcome(outcome),
                    Modifier.widthIn(min = 92.dp).testTag("history_label_${r.date}"),
                    style = DaybreakType.caption, fontWeight = FontWeight.Bold, color = look.ink,
                    textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** 14dp, its fill and a 2dp ring, dashed for a day the lock was off. */
@Composable
private fun Dot(look: HistoryLook) {
    Box(
        Modifier.size(14.dp).clip(CircleShape).background(look.fill).drawBehind {
            val w = 2.dp.toPx()
            drawRoundRect(
                look.ring,
                topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(size.width / 2),
                style = Stroke(w, pathEffect = if (look.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null),
            )
        },
    )
}
