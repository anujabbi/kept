package com.example.kept.feature.onboarding

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.Variants
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.lock.Permissions
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.HabitTemplate
import com.example.kept.core.ui.HabitTemplates
import com.example.kept.core.ui.InfoBox
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.MutedText
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.ScreenTitle
import com.example.kept.core.ui.SecondaryButton
import com.example.kept.core.ui.SectionLabel
import com.example.kept.core.ui.Selectable
import com.example.kept.core.ui.sprig.SprigView
import com.example.kept.core.work.WorkScheduler
import com.example.kept.feature.settings.ExceptionsScreen
import com.example.kept.feature.settings.HabitEditor
import com.example.kept.feature.settings.PermissionCards
import com.example.kept.feature.settings.TimeRow
import com.example.kept.feature.settings.hasCamera
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val chosen: List<HabitTemplate> = listOf(HabitTemplates.list[0], HabitTemplates.list[1]),
    val lockFrom: Int = 7 * 60,
    val due: Int = 21 * 60,
    val breakMin: Int = 30,
    val saving: Boolean = false,
) {
    val hasPhotoHabit: Boolean get() = chosen.any { it.proofType == ProofType.PHOTO }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: KeptPreferences,
    private val habits: HabitRepository,
    private val scheduler: WorkScheduler,
    private val time: TimeSource,
    val permissions: Permissions,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    fun toggle(t: HabitTemplate) = _state.update { s ->
        val list = if (s.chosen.any { it.title == t.title }) s.chosen.filter { it.title != t.title } else if (s.chosen.size < 4) s.chosen + t else s.chosen
        s.copy(chosen = list)
    }
    fun addCustom(t: HabitTemplate) = _state.update { s -> if (s.chosen.size < 4) s.copy(chosen = s.chosen + t) else s }
    fun setWindow(from: Int, due: Int) = _state.update { it.copy(lockFrom = from, due = due) }
    fun setBreak(min: Int) = _state.update { it.copy(breakMin = min) }
    fun next() = _state.update { it.copy(step = it.step + 1) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    /** Persists habits and settings when leaving step 1 so the exceptions/permission steps can already use them. */
    fun persistDraft() = viewModelScope.launch {
        val s = _state.value
        habits.replaceAll(s.chosen.map { HabitEntity(title = it.title, iconKey = it.iconKey, proofType = it.proofType, targetValue = it.targetValue, unit = it.unit, createdAt = time.nowMillis()) })
        prefs.updateSettings { it.copy(lockFromMinute = s.lockFrom, dueMinute = s.due, breakDurationMin = s.breakMin, firstUseDate = it.firstUseDate ?: time.today()) }
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(saving = true) }
        persistDraft().join()
        prefs.updateSettings { it.copy(onboardingDone = true, onboardingStep = 5) }
        ForegroundWatcherService.start(ctx)
        scheduler.scheduleAll()
        onDone()
    }
}

private const val STEPS = 5

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    Column(Modifier.fillMaxSize().background(c.surface1).statusBarsPadding().navigationBarsPadding()) {
        // Header: back + progress + counter
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::back, enabled = s.step > 0) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = if (s.step > 0) c.textSecondary else c.border) }
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.surface0)) {
                Box(Modifier.fillMaxWidth((s.step + 1f) / STEPS).height(4.dp).background(c.purple400))
            }
            Spacer(Modifier.width(12.dp))
            Text("${s.step + 1} of $STEPS", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            Spacer(Modifier.width(8.dp))
        }
        AnimatedContent(
            targetState = s.step,
            transitionSpec = {
                if (targetState > initialState) slideInHorizontally { it / 3 } togetherWith slideOutHorizontally { -it / 3 }
                else slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it / 3 }
            },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { step ->
            when (step) {
                0 -> StepHabits(s, vm)
                1 -> StepRule(s, vm)
                2 -> StepExceptions(vm)
                3 -> StepPermissions(s, vm)
                else -> StepMeetSprig(s, vm, onDone)
            }
        }
    }
}

@Composable
private fun StepHabits(s: OnboardingState, vm: OnboardingViewModel) {
    val c = KeptTheme.colors
    var custom by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("onboarding_habits")) {
        Spacer(Modifier.height(8.dp))
        ScreenTitle("What do you want to do daily?", "Two or three is plenty. You can add more later.")
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitTemplates.list.forEach { t ->
                val selected = s.chosen.any { it.title == t.title }
                Selectable(selected, onClick = { vm.toggle(t) }) {
                    Icon(HabitIcons.of(t.iconKey), null, tint = if (selected) c.blue800 else c.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    if (selected) Icon(Icons.Outlined.Check, null, tint = c.blue400, modifier = Modifier.size(20.dp))
                }
            }
            s.chosen.filter { t -> HabitTemplates.list.none { it.title == t.title } }.forEach { t ->
                Selectable(true, onClick = { vm.toggle(t) }) {
                    Icon(HabitIcons.of(t.iconKey), null, tint = c.blue800, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Icon(Icons.Outlined.Check, null, tint = c.blue400, modifier = Modifier.size(20.dp))
                }
            }
            if (!custom && s.chosen.size < 4) {
                Selectable(false, onClick = { custom = true }) {
                    Icon(Icons.Outlined.Add, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Custom", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                }
            }
            if (custom) HabitEditor(onCancel = { custom = false }) { title, icon, proof, target, unit ->
                vm.addCustom(HabitTemplate(title, icon, proof, target, unit)); custom = false
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue", enabled = s.chosen.isNotEmpty(), onClick = { vm.persistDraft(); vm.next() }, modifier = Modifier.testTag("onboarding_next"))
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StepRule(s: OnboardingState, vm: OnboardingViewModel) {
    val c = KeptTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("onboarding_rule")) {
        Spacer(Modifier.height(8.dp))
        ScreenTitle("When does the lock run?", "Between these times, every app locks until your habits are done. Pick something you can't argue with later.")
        Spacer(Modifier.height(18.dp))
        KeptCard {
            TimeRow("Lock apps from", s.lockFrom) { vm.setWindow(it, s.due) }
            Spacer(Modifier.height(12.dp))
            TimeRow("Give-up time", s.due) { vm.setWindow(s.lockFrom, it) }
        }
        Spacer(Modifier.height(8.dp))
        MutedText("After the give-up time apps open again, and that day counts as missed.")
        Spacer(Modifier.height(20.dp))
        SectionLabel("If you break the lock, apps open for")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60).forEach { m ->
                Selectable(s.breakMin == m, onClick = { vm.setBreak(m) }, modifier = Modifier.weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                    Text("$m min", style = MaterialTheme.typography.labelLarge, color = c.textPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoBox("Breaking the lock always works. It takes a 60-second countdown, costs Sprig a level, and you get three a week before a day is written off.")
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Continue", onClick = { vm.persistDraft(); vm.next() }, modifier = Modifier.testTag("onboarding_next"))
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StepExceptions(vm: OnboardingViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 0.dp).testTag("onboarding_exceptions")) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            ScreenTitle("Everything locks, except…", "Phone, messages, maps and camera always work. Add a few apps you genuinely need, like music or school. Keep it short: every exception is a loophole.")
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.weight(1f)) { ExceptionsScreen(onBack = {}, embedded = true) }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Continue", onClick = vm::next, modifier = Modifier.testTag("onboarding_next"))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StepPermissions(s: OnboardingState, vm: OnboardingViewModel) {
    val ctx = LocalContext.current
    var states by remember { mutableStateOf<Map<PermissionKind, Boolean>>(emptyMap()) }
    val kinds = buildList {
        add(PermissionKind.USAGE_ACCESS); add(PermissionKind.OVERLAY); add(PermissionKind.NOTIFICATIONS); add(PermissionKind.BATTERY)
        if (s.hasPhotoHabit && ctx.hasCamera()) add(PermissionKind.CAMERA)
    }
    val requiredOk = (states[PermissionKind.USAGE_ACCESS] ?: vm.permissions.usageAccessGranted()) && (states[PermissionKind.OVERLAY] ?: vm.permissions.overlayGranted())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("onboarding_permissions")) {
        Spacer(Modifier.height(8.dp))
        ScreenTitle("Let KEPT do its job", "The lock needs to see which app is in front, and to step in front of it. KEPT never stores or shares what you use.")
        Spacer(Modifier.height(18.dp))
        PermissionCards(vm.permissions, kinds) { states = it }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (requiredOk) "Continue" else "Continue without the lock", onClick = vm::next, modifier = Modifier.testTag("onboarding_next"))
        if (!requiredOk) {
            Spacer(Modifier.height(8.dp))
            MutedText("Without usage access and display over other apps nothing locks and days won't count. You can grant them later in Settings.", Modifier.fillMaxWidth(), TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StepMeetSprig(s: OnboardingState, vm: OnboardingViewModel, onDone: () -> Unit) {
    val c = KeptTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("onboarding_sprig"), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        KeptCard(Modifier.fillMaxWidth(), background = c.purple50, border = null, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SprigView(SprigForm.SPRIG, SprigPose.WAVE, Modifier.size(160.dp), variant = Variants.forDate(java.time.LocalDate.now()))
                Text("This is Sprig", style = MaterialTheme.typography.headlineSmall, color = c.purple900)
                Text("Sprig grows every day you keep your promise, and evolves at streak milestones. Break a lock and Sprig wilts. Miss a day and Sprig starts over.", style = MaterialTheme.typography.bodyMedium, color = c.purple600, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(SprigForm.SPRIG, SprigForm.BUD, SprigForm.BLOOM, SprigForm.THICKET, SprigForm.GROVE).forEach { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SprigView(f, SprigPose.IDLE, Modifier.size(56.dp), animate = false, showAccessory = false)
                    Text(if (f.streakThreshold == 0) "Day 1" else "Day ${f.streakThreshold}", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MutedText("Each form you reach stays in your collection, in this week's look only. Share them anywhere.", Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        InfoBox("One weekly shield forgives a missed day. A buddy can cheer Sprig back to health. Everything else is on you.")
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (s.saving) "Starting…" else "Start today", enabled = !s.saving, onClick = { vm.finish(onDone) }, modifier = Modifier.testTag("onboarding_finish"))
        Spacer(Modifier.height(20.dp))
    }
}
