package com.example.kept.feature.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.BuddyRepository
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.Variants
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.KeyValueRow
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.SecondaryButton
import com.example.kept.core.ui.sprig.ShareCard
import com.example.kept.core.ui.sprig.SprigSpec
import com.example.kept.core.ui.sprig.SprigView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class RecapUi(val record: DayRecordEntity? = null, val buddyName: String? = null, val date: LocalDate = LocalDate.now())

@HiltViewModel
class RecapViewModel @Inject constructor(
    dayRepo: DayRepository,
    buddy: BuddyRepository,
    saved: SavedStateHandle,
) : ViewModel() {
    private val date: LocalDate = LocalDate.parse(saved.get<String>("date")!!)
    val state = combine(dayRepo.observe(date), buddy.observeBuddy()) { r, b -> RecapUi(r, b?.displayName, date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUi(date = date))
}

@Composable
fun RecapScreen(date: String, onClose: () -> Unit, vm: RecapViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    val ctx = LocalContext.current
    val r = s.record
    val kept = r?.countedForStreak == true
    val form = SprigForm.fromId(r?.formId ?: 1)
    val variant = Variants.forDate(s.date)
    val isToday = s.date == LocalDate.now()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(14.dp))
        Text(if (isToday) "Today, wrapped up" else s.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")) + ", wrapped up", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        Spacer(Modifier.height(14.dp))

        val bg = if (kept) c.teal50 else if (r?.shieldConsumed == true) c.blue50 else c.surface0
        val fg = if (kept) c.teal900 else if (r?.shieldConsumed == true) c.blue800 else c.textPrimary
        val sub = if (kept) c.teal600 else if (r?.shieldConsumed == true) c.blue400 else c.textSecondary
        KeptCard(Modifier.fillMaxWidth(), background = bg, border = null, shape = RoundedCornerShape(16.dp), padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SprigView(form, if (kept) SprigPose.CHEER else SprigPose.DROOP, Modifier.size(150.dp), variant = variant, wilted = !kept && r?.shieldConsumed != true)
                Spacer(Modifier.height(8.dp))
                val headline = when {
                    r == null -> "Nothing recorded"
                    kept -> "The promise was kept"
                    r.unprotected -> "The lock was off"
                    r.shieldConsumed -> "Shield used"
                    r.writtenOff -> "Day written off"
                    else -> "The promise slipped"
                }
                Text(headline, style = MaterialTheme.typography.titleMedium, color = fg)
                val detail = when {
                    r == null -> ""
                    kept -> "${form.displayName} · that's ${r.streakEnd} day${if (r.streakEnd == 1) "" else "s"} running."
                    r.unprotected -> "Days without protection don't count. Streak held at ${r.streakEnd}."
                    r.shieldConsumed -> "Your weekly shield kept the ${r.streakEnd}-day streak alive."
                    else -> "Streak reset. Sprig is back to Sprig. Today is a fresh start."
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = sub, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (r != null) {
            Column(Modifier.fillMaxWidth()) {
                KeyValueRow("Habits done", "${r.habitsDone} of ${r.habitsTotal}")
                KeyValueRow("Time off apps", formatMillis(r.lockedMillis))
                KeyValueRow("Points earned", "+${r.pointsEarned}")
                KeyValueRow("Locks broken", "${r.breaksUsed}", valueColor = if (r.breaksUsed > 0) c.danger else c.textPrimary, divider = false)
            }
        }
        Spacer(Modifier.height(18.dp))
        SecondaryButton(
            if (s.buddyName != null) "Share with ${s.buddyName}" else "Share",
            onClick = {
                ShareCard.share(
                    ctx,
                    ShareCard.Content(
                        SprigSpec(form, if (kept) SprigPose.CHEER else SprigPose.IDLE, variant),
                        headline = if (kept) "Day ${r?.streakEnd ?: 0} kept" else "Back at it tomorrow",
                        subline = "${r?.habitsDone ?: 0} of ${r?.habitsTotal ?: 0} habits · ${formatMillis(r?.lockedMillis ?: 0)} off apps",
                    ),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Done", onClick = onClose)
        Spacer(Modifier.height(20.dp))
    }
}

fun formatMillis(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
