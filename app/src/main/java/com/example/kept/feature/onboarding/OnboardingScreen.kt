package com.example.kept.feature.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.analytics.Screens
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalRules
import com.example.kept.core.lock.ForegroundWatcherService
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.lock.Permissions
import com.example.kept.core.ui.HabitTemplate
import com.example.kept.core.ui.HabitTemplates
import com.example.kept.core.work.WorkScheduler
import com.example.kept.feature.settings.BodyMode
import com.example.kept.feature.settings.ExceptionsBody
import com.example.kept.feature.settings.PermissionsBody
import com.example.kept.feature.settings.hasCamera
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Onboarding's screens, in order: [WELCOME], then steps 1 to [ONBOARDING_STEPS]. */
object OnboardingStep {
    const val WELCOME = 0
    const val HABITS = 1
    const val SHUTDOWN = 2
    const val LOCK_WINDOW = 3
    const val EXCEPTIONS = 4
    const val PERMISSIONS = 5
    const val READY = 6
}

data class OnboardingState(
    /** [OnboardingStep.WELCOME], or the step number the progress bar shows (1 to 6). */
    val step: Int = OnboardingStep.WELCOME,
    val chosen: List<HabitTemplate> = listOf(HabitTemplates.list[0], HabitTemplates.list[1]),
    /**
     * Step 2's shutdown time as a minute of the day, or null while none is picked and after Skip.
     * Nothing is preselected; "Start today" writes it ([onboardingShutdown]).
     */
    val shutdownMinute: Int? = null,
    /** Step 3's window: the stored one (brought inside the bar) until the person moves it. */
    val window: LockWindowRules.Window = LockWindowRules.normalize(Settings().lockFromMinute, Settings().dueMinute),
    /** True once the person moved the window; only then does "Start today" write it. */
    val windowTouched: Boolean = false,
    /** Step 1's "Add your own" page is open. */
    val addingHabit: Boolean = false,
    val saving: Boolean = false,
    /**
     * How often each ticked habit happens (#205): days a week by title for a some-days habit; a
     * title not here is every day. Only titles in [chosen] count ([daysOf]).
     */
    val weeklyDays: Map<String, Int> = mapOf(OnboardingCadence.EXERCISE to OnboardingCadence.EXERCISE_DAYS),
    /** The how-often sheet, while it is open. */
    val sheet: CadenceSheetState? = null,
) {
    /** Every ticked habit: the camera permission step asks for any photo habit, weekly or not. */
    val hasPhotoHabit: Boolean get() = chosen.any { it.proofType == ProofType.PHOTO }

    /** Days a week for a ticked some-days habit; null for every day (or not ticked). */
    fun daysOf(title: String): Int? = if (chosen.any { it.title == title }) weeklyDays[title] else null

    val daily: List<HabitTemplate> get() = chosen.filter { daysOf(it.title) == null }
    val weekly: List<HabitTemplate> get() = chosen.filter { daysOf(it.title) != null }

    /** [HabitLimits] counts every-day habits only; weekly goals have their own limit, as in Settings. */
    val atLimit: Boolean get() = !HabitLimits.canAdd(daily.size)

    /** Continue needs one every-day habit: a day with none never counts toward the streak. */
    val canContinue: Boolean get() = daily.isNotEmpty()

    /** The habit the sheet is open for. */
    val sheetHabit: HabitTemplate? get() = sheet?.let { sh -> chosen.firstOrNull { it.title == sh.title } }

    /** "Some days a week" is off on an every-day habit's sheet once three weekly goals are chosen. */
    val sheetCanSomeDays: Boolean get() = sheet != null && (daysOf(sheet.title) != null || WeeklyGoalRules.canAdd(weekly.size))

    /** "Every day" is off on a weekly habit's sheet once ten every-day habits are chosen. */
    val sheetCanEveryDay: Boolean get() = sheet != null && (daysOf(sheet.title) == null || HabitLimits.canAdd(daily.size))
}

/** The how-often sheet's unsaved choice for [title]: [days] a week, or null for every day. */
data class CadenceSheetState(val title: String, val days: Int?)

/** Step 1's how-often rules (#205). */
object OnboardingCadence {
    /** The one preset that starts weekly, matched by title. */
    const val EXERCISE = "Exercise"
    const val EXERCISE_DAYS = 4

    /** The day picker: 1 to 6. Seven days a week is every day, so it is not offered. */
    val DAY_CHOICES: List<Int> = (WeeklyGoalRules.MIN_DAYS until WeeklyGoalRules.MAX_DAYS).toList()

    /**
     * The cadence a habit gets when ticked: the Exercise preset 4 days a week while there is room
     * for another weekly goal; everything else, and Exercise at the limit, every day.
     */
    fun defaultFor(s: OnboardingState, t: HabitTemplate): Int? =
        if (t.title == EXERCISE && t in HabitTemplates.list && WeeklyGoalRules.canAdd(s.weekly.size)) EXERCISE_DAYS else null
}

/**
 * The state changes behind every onboarding tap, pure so they are unit tested. The ViewModel
 * applies them to its state flow.
 */
object OnboardingFlow {
    /** Ticking applies the default cadence ([OnboardingCadence.defaultFor]); unticking drops it. */
    fun toggle(s: OnboardingState, t: HabitTemplate): OnboardingState {
        if (s.chosen.any { it.title == t.title }) {
            return s.copy(chosen = s.chosen.filter { it.title != t.title }, weeklyDays = s.weeklyDays - t.title)
        }
        val days = OnboardingCadence.defaultFor(s, t)
        if (days == null && s.atLimit) return s
        val weekly = if (days == null) s.weeklyDays - t.title else s.weeklyDays + (t.title to days)
        return s.copy(chosen = s.chosen + t, weeklyDays = weekly)
    }

    /** A habit from "Add your own" is every day, like any habit but Exercise. */
    fun addCustom(s: OnboardingState, t: HabitTemplate): OnboardingState {
        val add = !s.atLimit && s.chosen.none { it.title == t.title }
        return if (add) s.copy(chosen = s.chosen + t, weeklyDays = s.weeklyDays - t.title, addingHabit = false) else s.copy(addingHabit = false)
    }

    /** A chip opens its habit's sheet on the habit's current cadence. */
    fun openCadence(s: OnboardingState, title: String): OnboardingState =
        if (s.chosen.none { it.title == title }) s else s.copy(sheet = CadenceSheetState(title, s.daysOf(title)))

    fun sheetEveryDay(s: OnboardingState): OnboardingState =
        if (s.sheet == null || !s.sheetCanEveryDay) s else s.copy(sheet = s.sheet.copy(days = null))

    /** "Some days a week" starts from the habit's own days, or [WeeklyGoalRules.DEFAULT_DAYS]. */
    fun sheetSomeDays(s: OnboardingState): OnboardingState {
        val sheet = s.sheet ?: return s
        if (sheet.days != null || !s.sheetCanSomeDays) return s
        return s.copy(sheet = sheet.copy(days = s.daysOf(sheet.title) ?: WeeklyGoalRules.DEFAULT_DAYS))
    }

    fun sheetDays(s: OnboardingState, days: Int): OnboardingState {
        val sheet = s.sheet ?: return s
        if (sheet.days == null || days !in OnboardingCadence.DAY_CHOICES) return s
        return s.copy(sheet = sheet.copy(days = days))
    }

    /** "Done" keeps the sheet's choice. */
    fun sheetDone(s: OnboardingState): OnboardingState {
        val sheet = s.sheet ?: return s
        val weekly = if (sheet.days == null) s.weeklyDays - sheet.title else s.weeklyDays + (sheet.title to sheet.days)
        return s.copy(weeklyDays = weekly, sheet = null)
    }

    /** Dismissing the sheet (swipe, scrim, back) keeps the cadence it opened with. */
    fun closeCadence(s: OnboardingState): OnboardingState = s.copy(sheet = null)

    fun openAddHabit(s: OnboardingState): OnboardingState = if (s.atLimit) s else s.copy(addingHabit = true)

    fun pickShutdown(s: OnboardingState, minute: Int): OnboardingState = s.copy(shutdownMinute = minute)

    /** "Skip" on the shutdown step: no shutdown time, and on to the lock window. */
    fun skipShutdown(s: OnboardingState): OnboardingState = next(s.copy(shutdownMinute = null))

    fun setWindow(s: OnboardingState, w: LockWindowRules.Window): OnboardingState = s.copy(window = w, windowTouched = true)

    /** Step 1 holds until one habit is every day. */
    fun next(s: OnboardingState): OnboardingState =
        if (s.step == OnboardingStep.HABITS && !s.canContinue) s
        else s.copy(step = (s.step + 1).coerceAtMost(ONBOARDING_STEPS), addingHabit = false, sheet = null)

    /**
     * Back from any step keeps every choice, cadences included: only the step moves (or the
     * sheet or the Add a habit page closes).
     */
    fun back(s: OnboardingState): OnboardingState = when {
        s.sheet != null -> s.copy(sheet = null)
        s.addingHabit -> s.copy(addingHabit = false)
        else -> s.copy(step = (s.step - 1).coerceAtLeast(OnboardingStep.WELCOME))
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: KeptPreferences,
    private val habits: HabitRepository,
    private val weeklyGoals: WeeklyGoalRepository,
    private val scheduler: WorkScheduler,
    private val time: TimeSource,
    val permissions: Permissions,
    private val analytics: Analytics,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        // Step 3 starts from the stored window, which a re-run of onboarding may have changed.
        viewModelScope.launch {
            val s = prefs.currentSettings()
            _state.update { if (it.windowTouched) it else it.copy(window = LockWindowRules.normalize(s.lockFromMinute, s.dueMinute)) }
        }
    }

    fun toggle(t: HabitTemplate) = _state.update { OnboardingFlow.toggle(it, t) }
    fun addCustom(t: HabitTemplate) = _state.update { OnboardingFlow.addCustom(it, t) }
    fun openAddHabit() = _state.update(OnboardingFlow::openAddHabit)
    fun closeAddHabit() = _state.update { it.copy(addingHabit = false) }
    fun pickShutdown(minute: Int) = _state.update { OnboardingFlow.pickShutdown(it, minute) }
    fun skipShutdown() = _state.update(OnboardingFlow::skipShutdown)
    fun setWindow(w: LockWindowRules.Window) = _state.update { OnboardingFlow.setWindow(it, w) }
    fun openCadence(title: String) = _state.update { OnboardingFlow.openCadence(it, title) }
    val cadenceActions = CadenceActions(
        onEveryDay = { _state.update(OnboardingFlow::sheetEveryDay) },
        onSomeDays = { _state.update(OnboardingFlow::sheetSomeDays) },
        onDays = { n -> _state.update { OnboardingFlow.sheetDays(it, n) } },
        onDone = { _state.update(OnboardingFlow::sheetDone) },
        onDismiss = { _state.update(OnboardingFlow::closeCadence) },
    )
    fun next() = _state.update(OnboardingFlow::next)
    fun back() = _state.update(OnboardingFlow::back)

    /**
     * Onboarding is one nav destination but six screens to the person walking it, so the step —
     * not the route — is what a funnel needs (issue #10). Steps are numbered as the progress bar
     * shows them, from 1; the welcome screen before them is not a step.
     */
    fun reportStepViewed(step: Int) {
        analytics.screen(Screens.ONBOARDING, mapOf("onboarding_step" to step))
        analytics.capture("onboarding_step_viewed", mapOf("step" to step))
    }

    /** One Grant tap, answered (issue #10). */
    fun reportPermissionAnswer(kind: PermissionKind, granted: Boolean) = analytics.capture(
        if (granted) "permission_granted" else "permission_denied",
        mapOf("permission" to kind.eventValue),
    )

    /**
     * Persists the every-day habits and the first-use date when leaving step 1 so the
     * exceptions/permission steps can already use them. Weekly goals, the shutdown time and the
     * lock window are written on "Start today" (the window only if it was moved): see
     * [addOnboardingGoals], [onboardingShutdown] and [onboardingLockWindow].
     */
    fun persistDraft() = viewModelScope.launch {
        val s = _state.value
        habits.replaceAll(s.dailyHabitRows(time.nowMillis()))
        prefs.updateSettings { it.onboardingDraft(time.today()) }
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(saving = true) }
        persistDraft().join()
        val s = _state.value
        weeklyGoals.addOnboardingGoals(s.weeklyGoalPlans)
        prefs.updateSettings {
            it.onboardingShutdown(s.shutdownMinute).onboardingLockWindow(s.window.takeIf { s.windowTouched }).onboardingFinished(time.today())
        }
        ForegroundWatcherService.start(ctx)
        scheduler.scheduleAll()
        // Every ticked row, daily or weekly, as before #205: no new analytics.
        analytics.capture("onboarding_completed", mapOf("habit_count" to s.chosen.size))
        onDone()
    }
}

/**
 * Onboarding in Daybreak (spec, Welcome and Onboarding): the welcome screen, then six steps with
 * a progress bar and a back arrow. System back goes back a step (closing Add a habit first) and
 * keeps every choice; on the welcome screen it leaves the app as before.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(s.step) { if (s.step >= OnboardingStep.HABITS) vm.reportStepViewed(s.step) }
    BackHandler(enabled = s.step > OnboardingStep.WELCOME || s.addingHabit || s.sheet != null) { vm.back() }

    AnimatedContent(
        targetState = s.step,
        transitionSpec = {
            if (targetState > initialState) slideInHorizontally { it / 3 } togetherWith slideOutHorizontally { -it / 3 }
            else slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it / 3 }
        },
        label = "step",
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(onStart = vm::next)
            OnboardingStep.HABITS ->
                if (s.addingHabit) AddHabitPage(onBack = vm::closeAddHabit, onSave = vm::addCustom)
                else {
                    HabitsStep(
                        s, onToggle = vm::toggle, onCadence = vm::openCadence, onAddOwn = vm::openAddHabit, onBack = vm::back,
                        onContinue = { if (s.canContinue) { vm.persistDraft(); vm.next() } },
                    )
                    CadenceSheet(s, vm.cadenceActions)
                }
            OnboardingStep.SHUTDOWN -> ShutdownStep(s.shutdownMinute, onPick = vm::pickShutdown, onBack = vm::back, onContinue = vm::next, onSkip = vm::skipShutdown)
            OnboardingStep.LOCK_WINDOW -> LockWindowStep(s.window, onChange = vm::setWindow, onBack = vm::back, onContinue = vm::next)
            OnboardingStep.EXCEPTIONS -> ExceptionsBody(BodyMode.ONBOARDING, onBack = vm::back, onContinue = vm::next)
            OnboardingStep.PERMISSIONS -> {
                val kinds = buildList {
                    add(PermissionKind.USAGE_ACCESS); add(PermissionKind.OVERLAY); add(PermissionKind.NOTIFICATIONS); add(PermissionKind.BATTERY)
                    if (s.hasPhotoHabit && ctx.hasCamera()) add(PermissionKind.CAMERA)
                }
                PermissionsBody(BodyMode.ONBOARDING, vm.permissions, kinds, vm::reportPermissionAnswer, onBack = vm::back, onContinue = vm::next)
            }
            else -> ReadyStep(saving = s.saving, hasWeeklyGoal = s.weekly.isNotEmpty(), onBack = vm::back, onStart = { vm.finish(onDone) })
        }
    }
}
