package com.example.kept.feature.buddy

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.Buddy
import com.example.kept.core.data.BuddyRepository
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.Variants
import com.example.kept.core.ui.Avatar
import com.example.kept.core.ui.DayDot
import com.example.kept.core.ui.InfoBox
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.MutedText
import com.example.kept.core.ui.Pill
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.ScreenTitle
import com.example.kept.core.ui.SecondaryButton
import com.example.kept.core.ui.SectionLabel
import com.example.kept.core.ui.WeekDots
import com.example.kept.core.ui.sprig.SprigView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BuddyUi(
    val buddy: Buddy? = null,
    val myCode: String = "",
    val me: SprigState = SprigState(),
    val myDoneToday: Boolean = false,
    val myWeek: List<Pair<LocalDate, DayRecordEntity?>> = emptyList(),
    val error: String? = null,
    val sentToast: String? = null,
)

@HiltViewModel
class BuddyViewModel @Inject constructor(
    private val repo: BuddyRepository,
    sprig: SprigRepository,
    habits: HabitRepository,
    days: DayRepository,
) : ViewModel() {
    private val code = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)
    private val toast = MutableStateFlow<String?>(null)

    init { viewModelScope.launch { code.value = repo.myInviteCode() } }

    val state = combine(repo.observeBuddy(), code, sprig.state, habits.observeToday(), days.observeLastSeven()) { b, c, s, t, w ->
        BuddyUi(b, c, s, t.allDone, w)
    }.combine(error) { s, e -> s.copy(error = e) }.combine(toast) { s, t -> s.copy(sentToast = t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuddyUi())

    fun pair(input: String) = viewModelScope.launch {
        val ok = repo.pair(input)
        error.value = if (ok) null else "That code doesn't look right. It's two groups of three, like K4W-92B."
    }
    fun unpair() = viewModelScope.launch { repo.unpair() }
    fun nudge() = viewModelScope.launch { repo.nudge(); toast.value = "Nudge sent" }
    fun cheer() = viewModelScope.launch { repo.cheer(); toast.value = "Cheer sent" }
    fun clearToast() { toast.value = null }
}

@Composable
fun BuddyScreen(vm: BuddyViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        ScreenTitle("Your buddy")
        Spacer(Modifier.height(16.dp))
        val b = s.buddy
        if (b == null) EmptyState(s, vm) else Paired(b, s, vm)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Paired(b: Buddy, s: BuddyUi, vm: BuddyViewModel) {
    val c = KeptTheme.colors
    LaunchedEffect(s.sentToast) { if (s.sentToast != null) { kotlinx.coroutines.delay(2000); vm.clearToast() } }

    KeptCard(Modifier.fillMaxWidth().testTag("buddy_paired")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(b.initials)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.displayName, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text("${b.streakDays} day streak · ${b.form.displayName}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            if (b.doneToday) Pill("Done today", background = c.green50, foreground = c.green600)
            else Pill("Not yet", background = c.surface0, foreground = c.textMuted)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(if (s.sentToast == "Nudge sent") "Sent" else "Nudge", onClick = vm::nudge, Modifier.weight(1f), enabled = !b.doneToday)
            SecondaryButton(if (s.sentToast == "Cheer sent") "Sent" else "Cheer", onClick = vm::cheer, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(14.dp))

    // Side by side Sprigs: the flaunt moment.
    KeptCard(Modifier.fillMaxWidth(), background = c.purple50, border = null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            SprigColumn("You", s.me.form, s.me.streakDays, s.me.wilted, s.myDoneToday)
            SprigColumn(b.displayName, b.form, b.streakDays, false, b.doneToday)
        }
        Spacer(Modifier.height(10.dp))
        val pair = s.me.pairStreak
        Text(
            when {
                pair >= SprigForm.DUO_PAIR_STREAK -> "Pair streak $pair. Duo form unlocked."
                pair > 0 -> "Pair streak $pair · ${SprigForm.DUO_PAIR_STREAK - pair} more days together unlocks the Duo form"
                else -> "Both keep a day and the pair streak starts. Seven together unlocks the Duo form."
            },
            style = MaterialTheme.typography.bodySmall, color = c.purple600, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(14.dp))

    InfoBox("${b.displayName} sees your streak, whether today is done, and Sprig's form. Never your apps, screen time, or location.", icon = Icons.Outlined.VisibilityOff)
    Spacer(Modifier.height(18.dp))

    SectionLabel("Side by side", trailing = "This week")
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("You", Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            WeekDots(s.myWeek.map { (d, r) -> myDot(d, r, s.myDoneToday) }, c.purple400)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.displayName.take(6), Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            WeekDots(b.lastSevenDays.map { ch -> when (ch) { '1' -> DayDot.DONE; 'u' -> DayDot.UNPROTECTED; '0' -> DayDot.MISSED; else -> DayDot.FUTURE } }.let { it.dropLast(1) + (if (b.doneToday) DayDot.DONE else DayDot.TODAY) }, c.blue400)
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { QuietButton("Unpair", onClick = vm::unpair) }
}

@Composable
private fun SprigColumn(name: String, form: SprigForm, streak: Int, wilted: Boolean, done: Boolean) {
    val c = KeptTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SprigView(form, if (done) SprigPose.CHEER else SprigPose.IDLE, Modifier.size(110.dp), variant = Variants.forDate(LocalDate.now()), wilted = wilted, showAccessory = false)
        Text(name, style = MaterialTheme.typography.labelLarge, color = c.purple900)
        Text("${form.displayName} · $streak", style = MaterialTheme.typography.labelMedium, color = c.purple600)
    }
}

private fun myDot(d: LocalDate, r: DayRecordEntity?, doneToday: Boolean): DayDot = when {
    d == LocalDate.now() -> if (doneToday) DayDot.DONE else DayDot.TODAY
    r == null -> DayDot.MISSED
    r.unprotected -> DayDot.UNPROTECTED
    r.countedForStreak -> DayDot.DONE
    r.shieldConsumed -> DayDot.SHIELDED
    else -> DayDot.MISSED
}

@Composable
private fun EmptyState(s: BuddyUi, vm: BuddyViewModel) {
    val c = KeptTheme.colors
    val ctx = LocalContext.current
    var entering by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().testTag("buddy_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
        SprigView(s.me.form, SprigPose.WAVE, Modifier.size(150.dp), variant = Variants.forDate(LocalDate.now()), wilted = s.me.wilted)
        Spacer(Modifier.height(10.dp))
        Text("Streaks hold better in pairs", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text("Pick one person who'll notice if you disappear. Seven kept days together unlock a form nobody gets alone.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(20.dp))
        KeptCard(Modifier.fillMaxWidth(), background = c.surface1, border = null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your invite code", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text(s.myCode, fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = c.textPrimary, modifier = Modifier.testTag("invite_code"))
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Share code", onClick = {
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Be my KEPT buddy. My code is ${s.myCode}") }
            ctx.startActivity(Intent.createChooser(send, "Share your code"))
        })
        Spacer(Modifier.height(8.dp))
        if (!entering) {
            SecondaryButton("Enter a code", onClick = { entering = true }, modifier = Modifier.testTag("enter_code"))
        } else {
            OutlinedTextField(
                value = input, onValueChange = { input = it.uppercase().take(7) },
                modifier = Modifier.fillMaxWidth().testTag("code_input"),
                placeholder = { Text("K4W-92B") },
                singleLine = true,
                isError = s.error != null,
                supportingText = s.error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.blue400, unfocusedBorderColor = c.borderStrong),
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Pair", onClick = { vm.pair(input) }, enabled = input.length >= 6, modifier = Modifier.testTag("pair_button"))
        }
        Spacer(Modifier.height(14.dp))
        MutedText("Invite only. No profiles, no search, no messaging.", Modifier.fillMaxWidth(), TextAlign.Center)
    }
}
