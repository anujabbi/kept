package com.example.kept.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.kept.core.data.account.SignInResult
import com.example.kept.feature.settings.PROMISE_CHEER_MS
import com.example.kept.feature.settings.promiseSentence
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalCopy
import androidx.compose.ui.semantics.Role
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.NightSliders
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.OnboardingCopy
import com.example.kept.core.domain.SignInUi
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.ui.shots.Shot
import com.example.kept.feature.settings.BodyMode
import com.example.kept.feature.settings.ExceptionsContent
import com.example.kept.feature.settings.PermissionsContent
import com.example.kept.feature.settings.PickableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What onboarding does when touched (#132). The steps are driven through [OnboardingFlow], the
 * same transitions [OnboardingViewModel] applies.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class OnboardingBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private var latest = OnboardingState()

    /** What the fake account layer answers, and how often it was asked (#193). */
    private var signInAnswer: SignInResult = SignInResult.Success
    private var signInCalls = 0

    /** Welcome and steps 1 to 4 wired as the screen wires them; later steps are a placeholder. */
    @Composable
    private fun Flow(initial: OnboardingState) {
        var s by remember { mutableStateOf(initial) }
        latest = s
        when {
            s.step == OnboardingStep.WELCOME -> WelcomeStep(onStart = { s = OnboardingFlow.next(s) })
            s.step == OnboardingStep.ACCOUNT -> AccountStep(
                s.signIn,
                signedInAs = null,
                onSignIn = { signInCalls++; s = OnboardingFlow.signInFinished(OnboardingFlow.startSignIn(s), signInAnswer) },
                onBack = { s = OnboardingFlow.back(s) },
                onContinue = { s = OnboardingFlow.next(s) },
                onSkip = { s = OnboardingFlow.skipAccount(s) },
            )
            s.step == OnboardingStep.HABITS && s.promise != null -> androidx.compose.runtime.key(s.promise?.title ?: "new") {
                OnboardingPromisePage(s, onDone = { s = OnboardingFlow.promiseDone(s, it) }, onClose = { s = OnboardingFlow.closePromise(s) })
            }
            s.step == OnboardingStep.HABITS -> HabitsStep(
                s,
                onToggle = { s = OnboardingFlow.toggle(s, it) },
                onCadence = { s = OnboardingFlow.openPromise(s, it) },
                onAddOwn = { s = OnboardingFlow.openAddHabit(s) },
                onBack = { s = OnboardingFlow.back(s) },
                onContinue = { s = OnboardingFlow.next(s) },
            )
            s.step == OnboardingStep.LOCK_WINDOW -> LockWindowStep(s.window, { s = OnboardingFlow.setWindow(s, it) }, { s = OnboardingFlow.back(s) }, { s = OnboardingFlow.next(s) })
            s.step == OnboardingStep.SHUTDOWN -> ShutdownStep(
                s.shutdown,
                onFrom = { s = OnboardingFlow.pickShutdownFrom(s, it) },
                onUntil = { s = OnboardingFlow.pickShutdownUntil(s, it) },
                onBack = { s = OnboardingFlow.back(s) },
                onContinue = { s = OnboardingFlow.continueShutdown(s) },
                onSkip = { s = OnboardingFlow.skipShutdown(s) },
            )
            else -> Text("step ${s.step}")
        }
    }

    private fun show(initial: OnboardingState = OnboardingState()) {
        rule.setContent { Shot(1f) { Flow(initial) } }
        rule.waitForIdle()
    }

    /**
     * Taps a habit row on its icon tile. A ticked row's chip sits under its text, where the row's
     * centre is, and a tap there opens the sheet rather than unticking.
     */
    private fun SemanticsNodeInteraction.tapRow(): SemanticsNodeInteraction = performTouchInput { click(Offset(34.dp.toPx(), centerY)) }

    private fun checked(on: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState(on))

    @Test fun pick_my_habits_opens_step_1() {
        show()
        rule.onNodeWithText(OnboardingCopy.WELCOME_HEADLINE).assertIsDisplayed()
        rule.onNodeWithText(OnboardingCopy.WELCOME_CAPTION).assertIsDisplayed()
        rule.onNodeWithTag("welcome_start").assertTextEquals("Pick my habits").performClick()
        // Sign in with Google is step 1 (#193); Skip goes on to the habits, step 2.
        rule.onNodeWithTag("onboarding_account").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_skip").performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 2 of 7")))
    }

    @Test fun preset_rows_toggle_and_the_footer_counts_them() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("1 daily habit · 1 weekly goal")
        rule.onNodeWithTag("onboarding_habit_Practice").assert(checked(false)).tapRow().assert(checked(true))
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("2 daily habits · 1 weekly goal")
        listOf("Exercise", "Read", "Practice").forEach { rule.onNodeWithTag("onboarding_habit_$it").tapRow() }
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("Pick at least one")
        rule.onNodeWithTag("onboarding_next").assertIsNotEnabled()
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().tapRow()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("1 daily habit")
        rule.onNodeWithTag("onboarding_next").assertIsEnabled()
    }

    @Test fun add_your_own_opens_the_promise_on_something_else_and_cancel_keeps_the_picks() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Study").tapRow()
        rule.onNodeWithTag("onboarding_add_own").performScrollTo().performClick()
        rule.onNodeWithTag("onboarding_promise").assertIsDisplayed()
        rule.onNodeWithTag("promise_activity_field").assertExists()
        rule.onNodeWithTag("promise_done").assertIsNotEnabled()
        rule.onNodeWithTag("promise_cancel").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(listOf("Exercise", "Read", "Study"), latest.chosen.map { it.title })
    }

    @Test fun something_else_typed_in_add_your_own_is_added_ticked_with_that_name() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_add_own").performScrollTo().performClick()
        rule.onNodeWithTag("promise_activity_field").performTextInput("walk the dog")
        assertEquals("I will walk the dog for 20 minutes, every day.", promiseText())
        done()
        chip("Walk the dog").performScrollTo().assert(cadence("Walk the dog", "Every day"))
        val walk = latest.chosen.single { it.title == "Walk the dog" }
        assertEquals(20 to "min", walk.targetValue to walk.unit)
        assertEquals("run", walk.iconKey)
    }

    @Test fun at_ten_habits_the_limit_line_replaces_add_your_own() {
        show(OnboardingFixtures.habitsAtLimit)
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("onboarding_habit_limit"))
        rule.onNodeWithTag("onboarding_habit_limit").assertTextEquals(HabitLimits.LIMIT_REACHED)
        rule.onNodeWithTag("onboarding_add_own").assertDoesNotExist()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("10 daily habits · 1 weekly goal")
    }

    // How often (#205, #207), on the promise (#218) ---------------------------------------------

    private fun chip(title: String) = rule.onNodeWithTag("onboarding_cadence_$title")

    /** The chip reads "Read, 3 days a week. Change how often": its cadence with the habit name. */
    private fun cadence(title: String, shown: String) = SemanticsMatcher.expectValue(
        SemanticsProperties.ContentDescription,
        listOf("$title, ${if (shown == "Every day") "every day" else shown.replace(' ', '\u00A0')}. Change how often"),
    )

    /** The promise's sentence as it reads on screen. */
    private fun promiseText(): String = promiseSentence(rule)

    /** Done, then Sprig's cheer, after which the promise closes by itself. */
    private fun done() {
        rule.onNodeWithTag("promise_done").performClick()
        rule.mainClock.advanceTimeBy(PROMISE_CHEER_MS + 100)
        rule.waitForIdle()
    }

    /** Opens [title]'s promise, picks [days] (null for every day) and taps Done. */
    private fun setCadence(title: String, days: Int?) {
        chip(title).performScrollTo().performClick()
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_${days ?: 0}").performClick()
        done()
    }

    @Test fun step_1_asks_what_habits_and_only_ticked_rows_have_a_chip() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("page_title").assertTextEquals("What habits will you keep?")
        chip("Read").assert(cadence("Read", "Every day"))
        chip("Exercise").assert(cadence("Exercise", "4 days a week"))
        listOf("Practice", "Study", "Tidy room").forEach { chip(it).assertDoesNotExist() }
        rule.onNodeWithTag("onboarding_habit_Practice").tapRow()
        chip("Practice").assert(cadence("Practice", "Every day"))
        rule.onNodeWithTag("onboarding_habit_Practice").tapRow()
        chip("Practice").assertDoesNotExist()
    }

    @Test fun the_chip_opens_the_promise_filled_in_and_done_sets_the_days() {
        show(OnboardingFixtures.habits)
        chip("Exercise").performClick()
        assertEquals("I will exercise for 30 minutes, 4 days a week.", promiseText())
        rule.onNodeWithTag("promise_cancel").performClick()
        rule.waitForIdle()
        chip("Read").performClick()
        assertEquals("I will read 10 pages, every day.", promiseText())
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_3").performClick()
        assertEquals("I will read 10 pages, 3 days a week.", promiseText())
        done()
        rule.onNodeWithTag("onboarding_promise").assertDoesNotExist()
        chip("Read").assert(cadence("Read", "3 days a week"))
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("Set one habit to every day to continue.")
        setCadence("Read", null)
        chip("Read").assert(cadence("Read", "Every day"))
        assertEquals(null, latest.daysOf("Read"))
    }

    @Test fun a_new_amount_shows_on_the_row_and_is_what_is_saved() {
        show(OnboardingFixtures.habits)
        chip("Read").performClick()
        rule.onNodeWithTag("promise_blank_amount").performClick()
        rule.onNodeWithTag("promise_option_20").performClick()
        done()
        assertEquals(20, latest.chosen.single { it.title == "Read" }.targetValue)
        rule.onNodeWithTag("onboarding_habit_Read").assert(hasText("20 pages", substring = true))
        assertEquals(20, latest.dailyHabitRows(0).single { it.title == "Read" }.targetValue)
    }

    @Test fun with_only_weekly_habits_continue_does_not_advance_and_the_footer_says_why() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Read").tapRow()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("Set one habit to every day to continue.")
        rule.onNodeWithTag("onboarding_next").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(OnboardingStep.HABITS, latest.step)
    }

    @Test fun at_three_weekly_goals_the_weekly_choices_are_off_with_the_limit_line() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Exercise").tapRow()
        listOf("Practice", "Study", "Tidy room").forEach { rule.onNodeWithTag("onboarding_habit_$it").performScrollTo().tapRow() }
        setCadence("Read", 2)
        setCadence("Practice", 2)
        setCadence("Study", 2)
        rule.onNodeWithTag("onboarding_habit_Exercise").performScrollTo().tapRow()
        chip("Exercise").assert(cadence("Exercise", "Every day"))
        chip("Tidy room").performScrollTo().performClick()
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_0").assertIsEnabled().assertIsSelected()
        (1..6).forEach { rule.onNodeWithTag("promise_option_days_$it").assertIsNotEnabled() }
        rule.onNodeWithTag("promise_limit").assertTextEquals(WeeklyGoalCopy.LIMIT_REACHED)
        rule.onNodeWithTag("promise_option_days_2").performClick()
        assertEquals("I will tidy room, every day.", promiseText())
        // The list covers Done, as in the mockup; a tap on its blank closes it.
        rule.onNodeWithTag("promise_blank_days").performClick()
        done()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("2 daily habits · 3 weekly goals")
    }

    @Test fun a_weekly_habits_own_promise_keeps_its_weekly_choices_at_three() {
        show(OnboardingFixtures.habits)
        listOf("Practice", "Study").forEach { rule.onNodeWithTag("onboarding_habit_$it").performScrollTo().tapRow() }
        setCadence("Practice", 2)
        setCadence("Study", 2)
        chip("Exercise").performScrollTo().performClick()
        rule.onNodeWithTag("promise_blank_days").performClick()
        rule.onNodeWithTag("promise_option_days_6").assertIsEnabled()
        rule.onNodeWithTag("promise_limit").assertDoesNotExist()
    }

    @Test fun renaming_a_habit_on_its_promise_replaces_it_and_a_taken_name_cannot_be_saved() {
        show(OnboardingFixtures.habits)
        chip("Read").performClick()
        rule.onNodeWithTag("promise_blank_activity").performClick()
        rule.onNodeWithTag("promise_option_Exercise").performClick()
        rule.onNodeWithTag("promise_done").assertIsNotEnabled()
        rule.onNodeWithTag("promise_blank_activity").performClick()
        rule.onNodeWithTag("promise_option_Study").performClick()
        assertEquals("I will study for 45 minutes, every day.", promiseText())
        done()
        assertEquals(listOf("Exercise", "Study"), latest.chosen.map { it.title })
        rule.onNodeWithTag("onboarding_habit_Study").assert(checked(true))
        rule.onNodeWithTag("onboarding_habit_Read").assert(checked(false))
    }

    @Test fun back_from_a_later_step_keeps_every_promise() {
        show(OnboardingFixtures.habits)
        setCadence("Read", 3)
        setCadence("Exercise", null)
        chip("Read").performClick()
        rule.onNodeWithTag("promise_blank_amount").performClick()
        rule.onNodeWithTag("promise_option_30").performClick()
        done()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_lock_window").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_shutdown").assertIsDisplayed()
        repeat(2) { rule.onNodeWithTag("page_back").performClick() }
        chip("Read").assert(cadence("Read", "3 days a week"))
        chip("Exercise").assert(cadence("Exercise", "Every day"))
        chip("Read").performClick()
        assertEquals("I will read 30 pages, 3 days a week.", promiseText())
    }

    /** TalkBack reads the chip's cadence with the habit name, and it is a 48dp button, at font 1 and 2. */
    @Test fun the_chip_reads_its_cadence_with_the_habit_name_at_font_1_and_2() {
        var scale by mutableStateOf(1f)
        rule.setContent { Shot(scale) { HabitsStep(OnboardingFixtures.habits, {}, {}, {}, {}, {}) } }
        for (f in listOf(1f, 2f)) {
            scale = f
            rule.waitForIdle()
            val d = rule.density.density
            for ((title, said) in listOf("Exercise" to "Exercise, 4 days a week. Change how often", "Read" to "Read, every day. Change how often")) {
                rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("onboarding_cadence_$title"))
                val node = chip(title).fetchSemanticsNode()
                assertEquals(listOf(said), node.config[SemanticsProperties.ContentDescription])
                assertEquals(Role.Button, node.config[SemanticsProperties.Role])
                assertTrue("$title chip at font $f is ${node.size.height / d}dp tall", node.size.height / d >= 47.9f)
                assertTrue("$title chip at font $f is ${node.size.width / d}dp wide", node.size.width / d >= 47.9f)
            }
        }
    }

    @Test fun the_last_page_has_the_weekly_row_only_with_a_weekly_goal() {
        var weekly by mutableStateOf(true)
        rule.setContent { Shot(1f) { ReadyStep(saving = false, hasWeeklyGoal = weekly, onBack = {}, onStart = {}) } }
        rule.onNodeWithTag("ready_weekly").assertTextEquals("Weekly goals lock only when you'd fall behind. On other days you can skip them.")
        weekly = false
        rule.waitForIdle()
        rule.onNodeWithTag("ready_weekly").assertDoesNotExist()
    }

    @Test fun the_steppers_move_in_30_minute_steps_and_the_sentence_follows() {
        show(OnboardingState(step = OnboardingStep.LOCK_WINDOW))
        rule.onNodeWithTag("lock_from_value").assertTextEquals("7:00 am")
        rule.onNodeWithTag("lock_from_later").performClick()
        rule.onNodeWithTag("lock_from_value").assertTextEquals("7:30 am")
        rule.onNodeWithTag("lock_sentence").assert(hasText("From 7:30 am,", substring = true))
        rule.onNodeWithTag("give_up_later").performClick()
        rule.onNodeWithTag("give_up_value").assertTextEquals("9:30 pm")
        rule.onNodeWithTag("lock_sentence").assert(hasText("not done by 9:30 pm,", substring = true))
        assertEquals(LockWindowRules.Window(7 * 60 + 30, 21 * 60 + 30), latest.window)
    }

    @Test fun the_band_moves_with_the_window() {
        show(OnboardingState(step = OnboardingStep.LOCK_WINDOW))
        fun band() = rule.onNodeWithTag("lock_band").fetchSemanticsNode().let { it.positionInRoot.x to it.size.width }
        val (x0, w0) = band()
        rule.onNodeWithTag("lock_from_later").performClick()
        rule.waitForIdle()
        val (x1, w1) = band()
        assertTrue("the band's start moved right", x1 > x0)
        assertTrue("the band got shorter", w1 < w0)
    }

    @Test fun give_up_stops_two_hours_after_the_start() {
        show(OnboardingState(step = OnboardingStep.LOCK_WINDOW, window = LockWindowRules.Window(19 * 60, 22 * 60)))
        rule.onNodeWithTag("give_up_earlier").performClick().performClick()
        rule.onNodeWithTag("give_up_value").assertTextEquals("9:00 pm")
        rule.onNodeWithTag("give_up_earlier").assertIsNotEnabled()
        rule.onNodeWithTag("lock_from_later").assertIsNotEnabled()
        rule.onNodeWithTag("lock_from_earlier").assertIsEnabled()
    }

    @Test fun the_start_stops_at_6_am_and_give_up_at_11_30_pm() {
        show(OnboardingState(step = OnboardingStep.LOCK_WINDOW, window = LockWindowRules.Window(6 * 60 + 30, 23 * 60)))
        rule.onNodeWithTag("lock_from_earlier").performClick()
        rule.onNodeWithTag("lock_from_value").assertTextEquals("6:00 am")
        rule.onNodeWithTag("lock_from_earlier").assertIsNotEnabled()
        rule.onNodeWithTag("give_up_later").performClick()
        rule.onNodeWithTag("give_up_value").assertTextEquals("11:30 pm")
        rule.onNodeWithTag("give_up_later").assertIsNotEnabled()
    }

    @Test fun back_from_the_shutdown_step_and_forward_again_keeps_every_choice_and_both_times() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().tapRow()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("give_up_earlier").performClick()
        rule.onNodeWithTag("onboarding_next").performClick()
        slide("shutdown_from_slider", 16)
        slide("shutdown_until_slider", 12)
        rule.onNodeWithTag("page_back").performClick()
        assertEquals("8:30 pm", time("give_up_value"))
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().assert(checked(true))
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("2 daily habits · 1 weekly goal")
        rule.onNodeWithTag("onboarding_next").performClick()
        assertEquals("8:30 pm", time("give_up_value"))
        rule.onNodeWithTag("onboarding_next").performClick()
        assertEquals("Midnight", time("shutdown_from_time"))
        assertEquals("8:00 am", time("shutdown_until_time"))
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_account").assertIsDisplayed()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
        assertEquals(3, latest.chosen.size)
    }

    // Shutdown time (#164, #218) ---------------------------------------------------------------

    /** Moves a slider to step [index], as dragging it or TalkBack's adjust would. */
    private fun slide(tag: String, index: Int) {
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(index.toFloat()) }
        rule.waitForIdle()
    }

    private fun plain(s: String) = s.replace(' ', ' ')

    private fun sentence() = plain(rule.onNodeWithTag("shutdown_sentence").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""))

    private fun time(tag: String) = plain(rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""))

    @Test fun the_order_is_habits_then_the_lock_then_shutdown_with_sprigs_line() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_lock_window").assertIsDisplayed()
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 3 of 7")))
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_shutdown").assertIsDisplayed()
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 4 of 7")))
        rule.onNodeWithTag("page_title").assertTextEquals("Pick a shutdown time")
        rule.onNodeWithTag("shutdown_line").assertTextEquals("Sprig highly recommends a shutdown time, so your phone doesn't eat into your sleep.")
        rule.onAllNodesWithText("A shutdown time helps you stop doomscrolling at night.").assertCountEquals(0)
    }

    @Test fun the_sliders_start_at_11_pm_and_7_am_and_the_sentence_follows_them() {
        show(OnboardingState(step = OnboardingStep.SHUTDOWN))
        assertEquals("11:00 pm", time("shutdown_from_time"))
        assertEquals("7:00 am", time("shutdown_until_time"))
        assertEquals("From 11:00 pm to 7:00 am, apps stay locked. Phone, messages and maps still work.", sentence())
        slide("shutdown_from_slider", 13)
        assertEquals("11:15 pm", time("shutdown_from_time"))
        slide("shutdown_until_slider", 9)
        assertEquals("7:15 am", time("shutdown_until_time"))
        assertEquals("From 11:15 pm to 7:15 am, apps stay locked. Phone, messages and maps still work.", sentence())
        slide("shutdown_from_slider", 0)
        assertEquals("8:00 pm", time("shutdown_from_time"))
        slide("shutdown_from_slider", NightSliders.FROM_STEPS)
        assertEquals("2:00 am", time("shutdown_from_time"))
        slide("shutdown_until_slider", 0)
        assertEquals("5:00 am", time("shutdown_until_time"))
        slide("shutdown_until_slider", NightSliders.UNTIL_STEPS)
        assertEquals("10:00 am", time("shutdown_until_time"))
    }

    @Test fun continue_saves_the_slider_times_and_skip_saves_no_night_lockdown() {
        show(OnboardingState(step = OnboardingStep.SHUTDOWN))
        slide("shutdown_from_slider", 8)
        rule.onNodeWithTag("onboarding_next").assertTextEquals("Continue").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(OnboardingStep.SHUTDOWN + 1, latest.step)
        assertEquals(NightSetting(enabled = true, fromMinute = 22 * 60, untilMinute = 7 * 60), latest.shutdown)
        val on = Settings().onboardingShutdown(latest.shutdown)
        assertEquals(Triple(true, 22 * 60, 7 * 60), Triple(on.nightLockEnabled, on.nightFromMinute, on.nightUntilMinute))
    }

    @Test fun skip_saves_no_night_lockdown_and_keeps_the_times_for_back() {
        show(OnboardingState(step = OnboardingStep.SHUTDOWN))
        slide("shutdown_from_slider", 8)
        rule.onNodeWithTag("onboarding_skip").assertTextEquals("Skip").performClick()
        rule.waitForIdle()
        assertEquals(false, latest.shutdown.enabled)
        assertEquals(22 * 60, latest.shutdown.fromMinute)
        assertEquals(false, Settings().onboardingShutdown(latest.shutdown).nightLockEnabled)
    }

    /** The existing accessibility check (`PartsBehaviourTest`): 48 x 48dp and something TalkBack reads. */
    @Test fun the_sliders_continue_and_skip_are_accessible_at_font_1_and_2() {
        var scale by mutableStateOf(1f)
        rule.setContent { Shot(scale) { ShutdownStep(NightSetting(enabled = true), {}, {}, {}, {}, {}) } }
        for (f in listOf(1f, 2f)) {
            scale = f
            rule.waitForIdle()
            val d = rule.density.density
            for (tag in listOf("shutdown_from_slider", "shutdown_until_slider", "onboarding_next", "onboarding_skip", "page_back")) {
                if (tag.endsWith("slider")) rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag(tag))
                val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
                val label = listOfNotNull(
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                ).joinToString(" ").trim()
                assertTrue("$tag at font $f has a TalkBack label", label.isNotEmpty())
                val size = node.size.toSize()
                assertTrue("$tag '$label' is ${size.width / d}dp wide", size.width / d >= 47.9f)
                assertTrue("$tag '$label' is ${size.height / d}dp tall", size.height / d >= 47.9f)
            }
            val from = rule.onNodeWithTag("shutdown_from_slider").fetchSemanticsNode().config
            assertEquals(listOf("Shutdown at"), from[SemanticsProperties.ContentDescription])
            assertEquals("11:00 pm", plain(from[SemanticsProperties.StateDescription]))
            val until = rule.onNodeWithTag("shutdown_until_slider").fetchSemanticsNode().config
            assertEquals("7:00 am", plain(until[SemanticsProperties.StateDescription]))
        }
    }

    // Sign in with Google (#193) -----------------------------------------------------------------

    /** Owner decision 2026-11-25: the first screen after the welcome. */
    @Test fun the_sign_in_step_is_step_1_right_after_the_welcome() {
        show()
        rule.onNodeWithTag("welcome_start").performClick()
        rule.onNodeWithTag("onboarding_account").assertIsDisplayed()
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 1 of 7")))
        rule.onNodeWithTag("page_title").assertTextEquals("Sign in with Google")
        rule.onNodeWithTag("account_sign_in").assertIsDisplayed().assertIsEnabled().assertTextEquals("Sign in with Google")
        rule.onNodeWithTag("onboarding_skip").assertIsDisplayed().assertTextEquals("Skip")
        rule.onNodeWithTag("account_error").assertDoesNotExist()
    }

    @Test fun skip_goes_to_the_habits_and_never_asks_the_account_layer() {
        show(OnboardingState(step = OnboardingStep.ACCOUNT))
        rule.onNodeWithTag("onboarding_skip").performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(OnboardingStep.HABITS, latest.step)
        assertEquals(0, signInCalls)
    }

    @Test fun a_successful_sign_in_goes_on_to_the_habits() {
        signInAnswer = SignInResult.Success
        show(OnboardingState(step = OnboardingStep.ACCOUNT))
        rule.onNodeWithTag("account_sign_in").performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(1, signInCalls)
        assertEquals(SignInUi.Idle, latest.signIn)
    }

    @Test fun cancelling_the_sheet_stays_on_the_step_with_a_short_line_and_skip() {
        signInAnswer = SignInResult.Cancelled
        show(OnboardingState(step = OnboardingStep.ACCOUNT))
        rule.onNodeWithTag("account_sign_in").performClick()
        rule.onNodeWithTag("onboarding_account").assertIsDisplayed()
        rule.onNodeWithTag("account_error").assertTextEquals("Sign-in cancelled.")
        rule.onNodeWithTag("account_sign_in").assertIsEnabled()
        rule.onNodeWithTag("onboarding_skip").assertIsEnabled().performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(SignInUi.Idle, latest.signIn)
    }

    @Test fun no_connection_says_check_your_connection_and_can_be_tried_again() {
        signInAnswer = SignInResult.Failed(java.io.IOException("offline"))
        show(OnboardingState(step = OnboardingStep.ACCOUNT))
        rule.onNodeWithTag("account_sign_in").performClick()
        rule.onNodeWithTag("account_error").assertTextEquals("Couldn't sign in. Check your connection and try again.")
        signInAnswer = SignInResult.Success
        rule.onNodeWithTag("account_sign_in").performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(2, signInCalls)
    }

    /** A server or setup failure is not blamed on the connection (#193, owner-reported). */
    @Test fun any_other_failure_does_not_blame_the_connection() {
        signInAnswer = SignInResult.Failed(IllegalStateException("401 invalid api key"))
        show(OnboardingState(step = OnboardingStep.ACCOUNT))
        rule.onNodeWithTag("account_sign_in").performClick()
        rule.onNodeWithTag("account_error").assertTextEquals("Couldn't sign in. Try again in a moment.")
        rule.onNodeWithTag("onboarding_skip").assertIsEnabled()
    }

    @Test fun while_signing_in_the_button_waits() {
        rule.setContent { Shot(1f) { AccountStep(SignInUi.Busy, null, {}, {}, {}, {}) } }
        rule.onNodeWithTag("account_sign_in").assertIsNotEnabled().assertTextEquals("Signing in…")
    }

    @Test fun signed_in_already_the_step_says_who_and_continues() {
        var continued = 0
        rule.setContent { Shot(1f) { AccountStep(SignInUi.Idle, "sam@example.com", {}, {}, { continued++ }, {}) } }
        rule.onNodeWithTag("account_signed_in").assertTextEquals("Signed in as sam@example.com")
        rule.onNodeWithTag("account_sign_in").assertDoesNotExist()
        rule.onNodeWithTag("onboarding_next").assertTextEquals("Continue").performClick()
        assertEquals(1, continued)
    }

    /** The existing accessibility check: 48 x 48dp and something TalkBack reads, at font 1 and 2. */
    @Test fun sign_in_and_skip_are_accessible_at_font_1_and_2() {
        var scale by mutableStateOf(1f)
        rule.setContent { Shot(scale) { AccountStep(SignInUi.Idle, null, {}, {}, {}, {}) } }
        for (f in listOf(1f, 2f)) {
            scale = f
            rule.waitForIdle()
            val d = rule.density.density
            for (tag in listOf("account_sign_in", "onboarding_skip", "page_back")) {
                val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
                assertTrue("$tag at font $f is tappable", hasClickAction().matches(node))
                assertTrue("$tag at font $f is ${node.size.height / d}dp tall", node.size.height / d >= 47.9f)
                assertTrue("$tag at font $f is ${node.size.width / d}dp wide", node.size.width / d >= 47.9f)
            }
        }
    }

    // Permissions -----------------------------------------------------------------------------

    private fun showPermissions(mode: BodyMode, granted: (PermissionKind) -> Boolean, onGrant: (PermissionKind) -> Unit = {}, onContinue: () -> Unit = {}) {
        rule.setContent { Shot(1f) { PermissionsContent(mode, OnboardingFixtures.onboardingKinds, granted, onGrant, {}, onContinue) } }
        rule.waitForIdle()
    }

    @Test fun a_missing_required_permission_changes_continue_and_shows_the_red_line() {
        var continued = 0
        showPermissions(BodyMode.ONBOARDING, OnboardingFixtures.oneRequiredMissing, onContinue = { continued++ })
        rule.onNodeWithTag("onboarding_next").assertTextEquals("Continue without the lock").performClick()
        assertEquals(1, continued)
        rule.onNodeWithTag("onboarding_permissions_warning")
            .assertTextEquals("Without display over other apps nothing locks and days won't count. You can grant it later in Settings.")
        rule.onNodeWithTag("permission_tag_overlay").assertTextEquals("Required")
        rule.onNodeWithTag("permission_tag_usage_access").assertTextEquals("On")
        rule.onNodeWithTag("permission_tag_battery").assertTextEquals("Recommended")
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("permission_tag_camera"))
        rule.onNodeWithTag("permission_tag_camera").assertTextEquals("For photo habits")
    }

    @Test fun all_granted_reads_continue_with_no_red_line_and_no_grant_buttons() {
        showPermissions(BodyMode.ONBOARDING, OnboardingFixtures.allGranted)
        rule.onNodeWithTag("onboarding_next").assertTextEquals("Continue")
        rule.onNodeWithTag("onboarding_permissions_warning").assertDoesNotExist()
        listOf("usage_access", "overlay", "notifications", "battery", "camera").forEach { rule.onNodeWithTag("permission_grant_$it").assertDoesNotExist() }
    }

    @Test fun grant_asks_for_that_permission_and_the_card_updates_on_return() {
        val asked = mutableListOf<PermissionKind>()
        val granted = mutableStateOf(setOf(PermissionKind.USAGE_ACCESS, PermissionKind.NOTIFICATIONS))
        rule.setContent { Shot(1f) { PermissionsContent(BodyMode.ONBOARDING, OnboardingFixtures.onboardingKinds, { it in granted.value }, { asked += it }, {}, {}) } }
        rule.onNodeWithTag("permission_grant_overlay").performClick()
        assertEquals(listOf(PermissionKind.OVERLAY), asked)
        // Coming back from the system screen re-reads the grants.
        granted.value = granted.value + PermissionKind.OVERLAY
        rule.waitForIdle()
        rule.onNodeWithTag("permission_grant_overlay").assertDoesNotExist()
        rule.onNodeWithTag("permission_tag_overlay").assertTextEquals("On")
        rule.onNodeWithTag("onboarding_next").assertTextEquals("Continue")
    }

    // Exceptions ------------------------------------------------------------------------------

    @Test fun exception_rows_toggle_and_the_count_follows() {
        val apps = mutableStateOf(OnboardingFixtures.apps)
        val toggled = mutableListOf<Pair<String, Boolean>>()
        rule.setContent {
            Shot(1f) {
                ExceptionsContent(BodyMode.ONBOARDING, emptyList(), apps.value, "", {}, { p, on ->
                    toggled += p.app.label to on
                    apps.value = apps.value.map { if (it.app == p.app) PickableApp(it.app, on) else it }
                }, {}, {})
            }
        }
        rule.onNodeWithTag("exceptions_count").assertTextEquals("2 apps stay open")
        rule.onNodeWithTag("exception_test.chrome").performScrollTo().performClick()
        rule.onNodeWithTag("exceptions_count").assertTextEquals("3 apps stay open")
        rule.onNodeWithTag("exception_test.spotify").performScrollTo().performClick()
        assertEquals(listOf("Chrome" to true, "Spotify" to false), toggled)
        rule.onNodeWithText("Phone").assertIsDisplayed()
        rule.onNodeWithText("Search apps").assertExists()
    }

    @Test fun search_narrows_the_list() {
        rule.setContent { Shot(1f) { ExceptionsContent(BodyMode.ONBOARDING, emptyList(), OnboardingFixtures.apps, "tik", {}, { _, _ -> }, {}, {}) } }
        rule.onNodeWithTag("exception_test.tiktok").assertExists()
        rule.onNodeWithTag("exception_test.chrome").assertDoesNotExist()
    }
}
