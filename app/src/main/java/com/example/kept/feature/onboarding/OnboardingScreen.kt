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
import com.example.kept.core.data.account.AccountRepository
import com.example.kept.core.data.account.AccountState
import com.example.kept.core.data.account.SignInResult
import com.example.kept.core.data.account.toUi
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SignInUi
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
    /** Sign in with Google, optional (#193): the first step, owner decision 2026-11-25. */
    const val ACCOUNT = 1
    const val HABITS = 2
    const val SHUTDOWN = 3
    const val LOCK_WINDOW = 4
    const val EXCEPTIONS = 5
    const val PERMISSIONS = 6
    const val READY = 7
}

data class OnboardingState(
    /** [OnboardingStep.WELCOME], or the step number the progress bar shows (1 to 7). */
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
    /** The sign-in step's button and its error line (#193). */
    val signIn: SignInUi = SignInUi.Idle,
) {
    val hasPhotoHabit: Boolean get() = chosen.any { it.proofType == ProofType.PHOTO }
    val atLimit: Boolean get() = !HabitLimits.canAdd(chosen.size)
}

/**
 * The state changes behind every onboarding tap, pure so they are unit tested. The ViewModel
 * applies them to its state flow.
 */
object OnboardingFlow {
    fun toggle(s: OnboardingState, t: HabitTemplate): OnboardingState {
        val list = when {
            s.chosen.any { it.title == t.title } -> s.chosen.filter { it.title != t.title }
            HabitLimits.canAdd(s.chosen.size) -> s.chosen + t
            else -> s.chosen
        }
        return s.copy(chosen = list)
    }

    fun addCustom(s: OnboardingState, t: HabitTemplate): OnboardingState {
        val chosen = if (HabitLimits.canAdd(s.chosen.size) && s.chosen.none { it.title == t.title }) s.chosen + t else s.chosen
        return s.copy(chosen = chosen, addingHabit = false)
    }

    fun openAddHabit(s: OnboardingState): OnboardingState = if (s.atLimit) s else s.copy(addingHabit = true)

    fun pickShutdown(s: OnboardingState, minute: Int): OnboardingState = s.copy(shutdownMinute = minute)

    /** "Skip" on the shutdown step: no shutdown time, and on to the lock window. */
    fun skipShutdown(s: OnboardingState): OnboardingState = next(s.copy(shutdownMinute = null))

    fun setWindow(s: OnboardingState, w: LockWindowRules.Window): OnboardingState = s.copy(window = w, windowTouched = true)

    /** "Sign in with Google" tapped: the button waits until the account layer answers (#193). */
    fun startSignIn(s: OnboardingState): OnboardingState = s.copy(signIn = SignInUi.Busy)

    /**
     * Signed in goes on to the habits step; anything else stays, with one short line and Skip.
     * An answer that lands after the person went back a step only clears the button.
     */
    fun signInFinished(s: OnboardingState, r: SignInResult): OnboardingState = when {
        s.step != OnboardingStep.ACCOUNT -> s.copy(signIn = SignInUi.Idle)
        else -> signInAnswered(s, r)
    }

    private fun signInAnswered(s: OnboardingState, r: SignInResult): OnboardingState =
        if (r == SignInResult.Success) next(s.copy(signIn = SignInUi.Idle)) else s.copy(signIn = r.toUi())

    /** "Skip" on the sign-in step: nothing is asked of the account layer. */
    fun skipAccount(s: OnboardingState): OnboardingState = next(s.copy(signIn = SignInUi.Idle))

    fun next(s: OnboardingState): OnboardingState = s.copy(step = (s.step + 1).coerceAtMost(ONBOARDING_STEPS), addingHabit = false)

    /** Back from any step keeps every choice: only the step moves (or the Add a habit page closes). */
    fun back(s: OnboardingState): OnboardingState =
        if (s.addingHabit) s.copy(addingHabit = false) else s.copy(step = (s.step - 1).coerceAtLeast(OnboardingStep.WELCOME))
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: KeptPreferences,
    private val habits: HabitRepository,
    private val scheduler: WorkScheduler,
    private val time: TimeSource,
    val permissions: Permissions,
    private val analytics: Analytics,
    private val accounts: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    /** Who is signed in, for the sign-in step (#193). */
    val account: StateFlow<AccountState> = accounts.account

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
    fun next() = _state.update(OnboardingFlow::next)
    fun skipAccount() = _state.update(OnboardingFlow::skipAccount)
    fun back() = _state.update(OnboardingFlow::back)

    /** Runs the Google sheet from [activityContext]; a second tap while it is open does nothing. */
    fun signIn(activityContext: Context) {
        if (_state.value.signIn == SignInUi.Busy) return
        _state.update(OnboardingFlow::startSignIn)
        viewModelScope.launch {
            val r = accounts.signInWithGoogle(activityContext)
            _state.update { OnboardingFlow.signInFinished(it, r) }
        }
    }

    /**
     * Onboarding is one nav destination but seven screens to the person walking it, so the step —
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
     * Persists habits and the first-use date when leaving step 1 so the exceptions/permission
     * steps can already use them. The shutdown time and the lock window are written on "Start
     * today" (the window only if it was moved): see [onboardingShutdown] and [onboardingLockWindow].
     */
    fun persistDraft() = viewModelScope.launch {
        val s = _state.value
        habits.replaceAll(s.chosen.map { HabitEntity(title = it.title, iconKey = it.iconKey, proofType = it.proofType, targetValue = it.targetValue, unit = it.unit, createdAt = time.nowMillis()) })
        prefs.updateSettings { it.onboardingDraft(time.today()) }
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(saving = true) }
        persistDraft().join()
        val s = _state.value
        prefs.updateSettings {
            it.onboardingShutdown(s.shutdownMinute).onboardingLockWindow(s.window.takeIf { s.windowTouched }).onboardingFinished(time.today())
        }
        ForegroundWatcherService.start(ctx)
        scheduler.scheduleAll()
        analytics.capture("onboarding_completed", mapOf("habit_count" to s.chosen.size))
        onDone()
    }
}

/**
 * Onboarding in Daybreak (spec, Welcome and Onboarding): the welcome screen, then seven steps with
 * a progress bar and a back arrow. System back goes back a step (closing Add a habit first) and
 * keeps every choice; on the welcome screen it leaves the app as before.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(s.step) { if (s.step > OnboardingStep.WELCOME) vm.reportStepViewed(s.step) }
    BackHandler(enabled = s.step > OnboardingStep.WELCOME || s.addingHabit) { vm.back() }

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
                else HabitsStep(s, onToggle = vm::toggle, onAddOwn = vm::openAddHabit, onBack = vm::back, onContinue = { vm.persistDraft(); vm.next() })
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
            OnboardingStep.ACCOUNT -> {
                val account by vm.account.collectAsStateWithLifecycle()
                AccountStep(
                    s.signIn,
                    signedInAs = (account as? AccountState.SignedIn)?.let { it.email ?: it.displayName ?: "" },
                    onSignIn = { vm.signIn(ctx) },
                    onBack = vm::back,
                    onContinue = vm::next,
                    onSkip = vm::skipAccount,
                )
            }
            else -> ReadyStep(saving = s.saving, onBack = vm::back, onStart = { vm.finish(onDone) })
        }
    }
}
