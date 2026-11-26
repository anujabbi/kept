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
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.NightSliders
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.PromiseDraft
import com.example.kept.core.domain.PromiseLimits
import com.example.kept.core.domain.PromiseRules
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
    const val LOCK_WINDOW = 2
    const val SHUTDOWN = 3
    const val EXCEPTIONS = 4
    const val PERMISSIONS = 5
    const val READY = 6
}

data class OnboardingState(
    /** [OnboardingStep.WELCOME], or the step number the progress bar shows (1 to 6). */
    val step: Int = OnboardingStep.WELCOME,
    val chosen: List<HabitTemplate> = listOf(HabitTemplates.list[0], HabitTemplates.list[1]),
    /**
     * Step 3's night window on its two sliders, 11:00 pm to 7:00 am to begin with (#218).
     * Continue turns it on and Skip off; Back keeps both times. "Start today" writes it
     * ([onboardingShutdown]).
     */
    val shutdown: NightSetting = NightSetting(enabled = true),
    /** Step 2's window: the stored one (brought inside the bar) until the person moves it. */
    val window: LockWindowRules.Window = LockWindowRules.normalize(Settings().lockFromMinute, Settings().dueMinute),
    /** True once the person moved the window; only then does "Start today" write it. */
    val windowTouched: Boolean = false,
    val saving: Boolean = false,
    /**
     * How often each ticked habit happens (#205): days a week by title for a some-days habit; a
     * title not here is every day. Only titles in [chosen] count ([daysOf]).
     */
    val weeklyDays: Map<String, Int> = mapOf(OnboardingCadence.EXERCISE to OnboardingCadence.EXERCISE_DAYS),
    /** The promise (#218), while it is open: for a ticked habit's chip, or for "Add your own". */
    val promise: OnboardingPromise? = null,
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

    /** The ticked habit [title] as its promise reads it. */
    fun promiseOf(title: String): PromiseDraft? = chosen.firstOrNull { it.title == title }?.let { t ->
        PromiseRules.ofHabit(t.title, t.proofType, t.targetValue, t.unit).withDays(daysOf(t.title))
    }

    /**
     * What the open promise's days list allows: the counts leave out the habit it is open for, so
     * at three weekly goals the weekly choices are off unless this habit is one of them.
     */
    val promiseLimits: PromiseLimits
        get() {
            val others = chosen.filter { it.title != promise?.title }
            return PromiseLimits.new(daily = others.count { daysOf(it.title) == null }, weekly = others.count { daysOf(it.title) != null })
        }

    /** Names Done refuses: every other ticked habit, since onboarding tells habits apart by name. */
    val promiseTaken: Set<String> get() = chosen.map { it.title }.filter { it != promise?.title }.toSet()
}

/** The open promise: for ticked habit [title], or a new habit ("Add your own") when null. */
data class OnboardingPromise(val title: String?, val draft: PromiseDraft)

/** Step 1's how-often rules (#205). */
object OnboardingCadence {
    /** The one preset that starts weekly, matched by title. */
    const val EXERCISE = "Exercise"
    const val EXERCISE_DAYS = 4

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

    /** A ticked habit's chip opens its promise, filled in. */
    fun openPromise(s: OnboardingState, title: String): OnboardingState {
        val draft = s.promiseOf(title) ?: return s
        return s.copy(promise = OnboardingPromise(title, draft))
    }

    /** "Add your own" opens an empty promise on "Something else…", every day. */
    fun openAddHabit(s: OnboardingState): OnboardingState =
        if (s.atLimit) s else s.copy(promise = OnboardingPromise(null, PromiseDraft("", days = null).somethingElse()))

    /**
     * Done on the promise: the habit it was open for becomes [d] (renamed, a new amount, every day
     * or some days a week), or [d] is added, ticked. Taken names and choices the limits turn off
     * are refused, as the screen refuses them. The promise stays open (as it opened) while
     * Sprig cheers; [closePromise] closes it.
     */
    fun promiseDone(s: OnboardingState, d: PromiseDraft): OnboardingState {
        val p = s.promise ?: return s
        if (!d.canSave || s.promiseTaken.any { it.equals(d.name, ignoreCase = true) } || !s.promiseLimits.allows(d.days)) return s
        val old = s.chosen.firstOrNull { it.title == p.title }
        val t = d.template(old)
        val chosen = if (old == null) s.chosen + t else s.chosen.map { if (it === old) t else it }
        val weekly = (s.weeklyDays - (p.title ?: "")).let { w -> if (d.days == null) w - t.title else w + (t.title to d.days) }
        return s.copy(chosen = chosen, weeklyDays = weekly)
    }

    /** Cancel, Back, or the end of Sprig's cheer. */
    fun closePromise(s: OnboardingState): OnboardingState = s.copy(promise = null)

    fun pickShutdownFrom(s: OnboardingState, minute: Int): OnboardingState = s.copy(shutdown = NightSliders.pickFrom(s.shutdown, minute))
    fun pickShutdownUntil(s: OnboardingState, minute: Int): OnboardingState = s.copy(shutdown = NightSliders.pickUntil(s.shutdown, minute))

    /** "Continue" on the shutdown step: night lockdown on at the slider times. */
    fun continueShutdown(s: OnboardingState): OnboardingState = next(s.copy(shutdown = s.shutdown.copy(enabled = true)))

    /** "Skip" on the shutdown step: no night lockdown; the times stay for Back. */
    fun skipShutdown(s: OnboardingState): OnboardingState = next(s.copy(shutdown = s.shutdown.copy(enabled = false)))

    fun setWindow(s: OnboardingState, w: LockWindowRules.Window): OnboardingState = s.copy(window = w, windowTouched = true)

    /** Step 1 holds until one habit is every day. */
    fun next(s: OnboardingState): OnboardingState =
        if (s.step == OnboardingStep.HABITS && !s.canContinue) s
        else s.copy(step = (s.step + 1).coerceAtMost(ONBOARDING_STEPS), promise = null)

    /** Back from any step keeps every choice, promises included: only the step moves (or the promise closes). */
    fun back(s: OnboardingState): OnboardingState = when {
        s.promise != null -> s.copy(promise = null)
        else -> s.copy(step = (s.step - 1).coerceAtLeast(OnboardingStep.WELCOME))
    }
}

/**
 * The habit a promise makes in onboarding. A preset keeps its icon while its name stays;
 * anything else gets the icon for its name. A photo promise is the photo habit it was.
 */
fun PromiseDraft.template(old: HabitTemplate?): HabitTemplate {
    val h = habit()
    val icon = when {
        old != null && old.title == name -> old.iconKey
        else -> HabitTemplates.list.firstOrNull { it.title == name }?.iconKey ?: iconKey
    }
    return HabitTemplate(name, icon, h.proof, h.savedTarget, h.savedUnit)
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
        // Step 2 starts from the stored window, which a re-run of onboarding may have changed.
        viewModelScope.launch {
            val s = prefs.currentSettings()
            _state.update { if (it.windowTouched) it else it.copy(window = LockWindowRules.normalize(s.lockFromMinute, s.dueMinute)) }
        }
    }

    fun toggle(t: HabitTemplate) = _state.update { OnboardingFlow.toggle(it, t) }
    fun openAddHabit() = _state.update(OnboardingFlow::openAddHabit)
    fun openPromise(title: String) = _state.update { OnboardingFlow.openPromise(it, title) }
    fun promiseDone(d: PromiseDraft) = _state.update { OnboardingFlow.promiseDone(it, d) }
    fun closePromise() = _state.update(OnboardingFlow::closePromise)
    fun pickShutdownFrom(minute: Int) = _state.update { OnboardingFlow.pickShutdownFrom(it, minute) }
    fun pickShutdownUntil(minute: Int) = _state.update { OnboardingFlow.pickShutdownUntil(it, minute) }
    fun continueShutdown() = _state.update(OnboardingFlow::continueShutdown)
    fun skipShutdown() = _state.update(OnboardingFlow::skipShutdown)
    fun setWindow(w: LockWindowRules.Window) = _state.update { OnboardingFlow.setWindow(it, w) }
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
     * [weeklyGoalPlans], [onboardingShutdown] and [onboardingLockWindow].
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
        // Replaces rather than adds: a "Start today" retried after the process died between this
        // and onboardingFinished must not add the goals twice (review on #210).
        weeklyGoals.replaceAll(s.weeklyGoalPlans)
        prefs.updateSettings {
            it.onboardingShutdown(s.shutdown).onboardingLockWindow(s.window.takeIf { s.windowTouched }).onboardingFinished(time.today())
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
    BackHandler(enabled = s.step > OnboardingStep.WELCOME) { vm.back() }

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
            OnboardingStep.HABITS -> {
                val promise = s.promise
                if (promise != null) {
                    // Keyed by the habit it opened for, so another habit's promise starts fresh.
                    androidx.compose.runtime.key(promise.title ?: "new") {
                        OnboardingPromisePage(s, onDone = vm::promiseDone, onClose = vm::closePromise)
                    }
                } else {
                    HabitsStep(
                        s, onToggle = vm::toggle, onCadence = vm::openPromise, onAddOwn = vm::openAddHabit, onBack = vm::back,
                        onContinue = { if (s.canContinue) { vm.persistDraft(); vm.next() } },
                    )
                }
            }
            OnboardingStep.LOCK_WINDOW -> LockWindowStep(s.window, onChange = vm::setWindow, onBack = vm::back, onContinue = vm::next)
            OnboardingStep.SHUTDOWN -> ShutdownStep(
                s.shutdown, onFrom = vm::pickShutdownFrom, onUntil = vm::pickShutdownUntil,
                onBack = vm::back, onContinue = vm::continueShutdown, onSkip = vm::skipShutdown,
            )
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
