package com.example.kept.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.WeeklyGoalCopy
import com.example.kept.core.ui.HabitTemplate
import androidx.compose.ui.semantics.Role
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.OnboardingCopy
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

    /** Welcome and steps 1 to 3 wired as the screen wires them; later steps are a placeholder. */
    @Composable
    private fun Flow(initial: OnboardingState) {
        var s by remember { mutableStateOf(initial) }
        latest = s
        when {
            s.step == OnboardingStep.WELCOME -> WelcomeStep(onStart = { s = OnboardingFlow.next(s) })
            s.step == OnboardingStep.HABITS && s.addingHabit -> AddHabitPage(onBack = { s = OnboardingFlow.back(s) }, onSave = { s = OnboardingFlow.addCustom(s, it) })
            s.step == OnboardingStep.HABITS -> {
                HabitsStep(
                    s,
                    onToggle = { s = OnboardingFlow.toggle(s, it) },
                    onCadence = { s = OnboardingFlow.openCadence(s, it) },
                    onAddOwn = { s = OnboardingFlow.openAddHabit(s) },
                    onBack = { s = OnboardingFlow.back(s) },
                    onContinue = { s = OnboardingFlow.next(s) },
                )
                CadenceSheet(
                    s,
                    CadenceActions(
                        onEveryDay = { s = OnboardingFlow.sheetEveryDay(s) },
                        onSomeDays = { s = OnboardingFlow.sheetSomeDays(s) },
                        onDays = { s = OnboardingFlow.sheetDays(s, it) },
                        onDone = { s = OnboardingFlow.sheetDone(s) },
                        onDismiss = { s = OnboardingFlow.closeCadence(s) },
                    ),
                )
            }
            s.step == OnboardingStep.SHUTDOWN -> ShutdownStep(
                s.shutdownMinute,
                onPick = { s = OnboardingFlow.pickShutdown(s, it) },
                onBack = { s = OnboardingFlow.back(s) },
                onContinue = { s = OnboardingFlow.next(s) },
                onSkip = { s = OnboardingFlow.skipShutdown(s) },
            )
            s.step == OnboardingStep.LOCK_WINDOW -> LockWindowStep(s.window, { s = OnboardingFlow.setWindow(s, it) }, { s = OnboardingFlow.back(s) }, { s = OnboardingFlow.next(s) })
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
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 1 of 6")))
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

    @Test fun add_your_own_opens_add_a_habit_and_back_returns_with_picks_kept() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Study").tapRow()
        rule.onNodeWithTag("onboarding_add_own").performScrollTo().performClick()
        rule.onNodeWithTag("onboarding_add_habit").assertIsDisplayed()
        rule.onNodeWithText("Save habit").assertExists()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(listOf("Exercise", "Read", "Study"), latest.chosen.map { it.title })
    }

    @Test fun at_ten_habits_the_limit_line_replaces_add_your_own() {
        show(OnboardingFixtures.habitsAtLimit)
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("onboarding_habit_limit"))
        rule.onNodeWithTag("onboarding_habit_limit").assertTextEquals(HabitLimits.LIMIT_REACHED)
        rule.onNodeWithTag("onboarding_add_own").assertDoesNotExist()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("10 daily habits · 1 weekly goal")
    }

    // How often (#205, #207) -------------------------------------------------------------------

    private fun chip(title: String) = rule.onNodeWithTag("onboarding_cadence_$title")

    /** The chip reads "Read, 3 days a week. Change how often": its cadence with the habit name. */
    private fun cadence(title: String, shown: String) = SemanticsMatcher.expectValue(
        SemanticsProperties.ContentDescription,
        listOf("$title, ${if (shown == "Every day") "every day" else shown}. Change how often"),
    )

    /** Opens [title]'s sheet, picks [days] (null for every day) and taps Done. */
    private fun setCadence(title: String, days: Int?) {
        chip(title).performScrollTo().performClick()
        if (days == null) {
            rule.onNodeWithTag("cadence_every_day").performClick()
        } else {
            rule.onNodeWithTag("cadence_some_days").performClick()
            rule.onNodeWithTag("cadence_day_$days").performClick()
        }
        rule.onNodeWithTag("cadence_done").performClick()
        rule.waitForIdle()
    }

    @Test fun step_1_asks_what_habits_and_only_ticked_rows_have_a_chip() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("page_title").assertTextEquals("What habits will you keep?")
        chip("Read").assert(cadence("Read", "Every day"))
        chip("Exercise").assert(cadence("Exercise", "4 days a week"))
        listOf("Practice", "Study", "Tidy room").forEach { chip(it).assertDoesNotExist() }
        rule.onNodeWithTag("onboarding_habit_Practice").tapRow()
        chip("Practice").assert(cadence("Practice", "Every day"))
        rule.onNodeWithTag("onboarding_habit_Practice").tapRow()
        chip("Practice").assertDoesNotExist()
    }

    @Test fun the_chip_opens_the_sheet_and_done_sets_the_days() {
        show(OnboardingFixtures.habits)
        chip("Read").performClick()
        rule.onNodeWithTag("cadence_title").assertTextEquals("How often will you read?")
        rule.onNodeWithTag("cadence_every_day").assertIsSelected()
        rule.onNodeWithTag("cadence_summary").assertTextEquals("Read, 10 pages, every day")
        rule.onNodeWithTag("cadence_some_days").performClick().assertIsSelected()
        // The day picker offers 1 to 6; 7 is every day.
        (1..6).forEach { rule.onNodeWithTag("cadence_day_$it").assertExists() }
        rule.onNodeWithTag("cadence_day_7").assertDoesNotExist()
        rule.onNodeWithText(WeeklyGoalCopy.SUMMARY_BODY).assertExists()
        rule.onNodeWithTag("cadence_day_3").performClick().assertIsSelected()
        rule.onNodeWithTag("cadence_summary").assertTextEquals("Read, 10 pages, 3 days a week")
        rule.onNodeWithTag("cadence_done").assertTextEquals("Done").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cadence_sheet").assertDoesNotExist()
        chip("Read").assert(cadence("Read", "3 days a week"))
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("Set one habit to every day to continue.")
        setCadence("Read", null)
        chip("Read").assert(cadence("Read", "Every day"))
        assertEquals(null, latest.daysOf("Read"))
    }

    @Test fun the_exercise_sheet_summary_carries_the_target() {
        show(OnboardingFixtures.habits)
        chip("Exercise").performClick()
        rule.onNodeWithTag("cadence_title").assertTextEquals("How often will you exercise?")
        rule.onNodeWithTag("cadence_summary").assertTextEquals("Exercise, 30 min, 4 days a week")
        rule.onNodeWithTag("cadence_day_4").assertIsSelected()
    }

    @Test fun with_only_weekly_habits_continue_does_not_advance_and_the_footer_says_why() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Read").tapRow()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("Set one habit to every day to continue.")
        rule.onNodeWithTag("onboarding_next").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("onboarding_habits").assertIsDisplayed()
        assertEquals(OnboardingStep.HABITS, latest.step)
    }

    @Test fun a_fourth_habit_cannot_be_weekly_and_exercise_ticked_fourth_is_every_day() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Exercise").tapRow()
        listOf("Practice", "Study", "Tidy room").forEach { rule.onNodeWithTag("onboarding_habit_$it").performScrollTo().tapRow() }
        setCadence("Read", 2)
        setCadence("Practice", 2)
        setCadence("Study", 2)
        rule.onNodeWithTag("onboarding_habit_Exercise").performScrollTo().tapRow()
        chip("Exercise").assert(cadence("Exercise", "Every day"))
        chip("Tidy room").performScrollTo().performClick()
        rule.onNodeWithTag("cadence_some_days").assertIsNotEnabled()
        rule.onNodeWithTag("cadence_limit").assertTextEquals(WeeklyGoalCopy.LIMIT_REACHED)
        rule.onNodeWithTag("cadence_done").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("2 daily habits · 3 weekly goals")
    }

    @Test fun a_habit_added_with_add_your_own_gets_the_chip_and_sheet_every_day() {
        show(OnboardingFlow.addCustom(OnboardingFixtures.habits, HabitTemplate("Walk", "walk", ProofType.MANUAL, 20, "min")))
        chip("Walk").performScrollTo().assert(cadence("Walk", "Every day"))
        setCadence("Walk", 5)
        chip("Walk").assert(cadence("Walk", "5 days a week"))
        assertEquals(5, latest.daysOf("Walk"))
    }

    @Test fun back_from_a_later_step_keeps_the_chosen_cadences() {
        show(OnboardingFixtures.habits)
        setCadence("Read", 3)
        setCadence("Exercise", null)
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_skip").performClick()
        rule.onNodeWithTag("onboarding_lock_window").assertIsDisplayed()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        chip("Read").assert(cadence("Read", "3 days a week"))
        chip("Exercise").assert(cadence("Exercise", "Every day"))
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

    @Test fun back_from_the_lock_window_and_forward_again_keeps_every_choice() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().tapRow()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_shutdown_1320").performClick()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("give_up_earlier").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_shutdown_1320").assertIsSelected()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().assert(checked(true))
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("2 daily habits · 1 weekly goal")
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_shutdown_1320").assertIsSelected()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("give_up_value").assertTextEquals("8:30 pm")
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_welcome").assertIsDisplayed()
        assertEquals(3, latest.chosen.size)
    }

    // Shutdown time (#164) ---------------------------------------------------------------------

    private val shutdownRows = OnboardingCopy.SHUTDOWN_TIMES.map { "onboarding_shutdown_$it" }

    @Test fun the_step_after_habits_is_the_shutdown_page_with_nothing_picked() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_shutdown").assertIsDisplayed()
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 2 of 6")))
        rule.onNodeWithTag("page_title").assertTextEquals("Pick a shutdown time")
        rule.onNodeWithText("A shutdown time helps you stop doomscrolling at night.").assertIsDisplayed()
        listOf("10 PM", "11 PM", "Midnight").forEachIndexed { i, label ->
            rule.onNodeWithTag(shutdownRows[i]).assertIsDisplayed().assertIsNotSelected()
                .assertTextEquals(label, "Apps lock until 7:00 am. Phone, messages and maps still work.")
        }
        assertEquals(listOf("onboarding_shutdown_1320", "onboarding_shutdown_1380", "onboarding_shutdown_0"), shutdownRows)
        rule.onNodeWithTag("onboarding_next").assertTextEquals("Continue").assertIsNotEnabled()
        rule.onNodeWithTag("onboarding_skip").assertTextEquals("Skip").assertIsEnabled()
        assertEquals(null, latest.shutdownMinute)
    }

    @Test fun picking_a_row_enables_continue_and_only_one_row_is_picked() {
        show(OnboardingState(step = OnboardingStep.SHUTDOWN))
        rule.onNodeWithTag("onboarding_shutdown_1380").performClick().assertIsSelected()
        rule.onNodeWithTag("onboarding_next").assertIsEnabled()
        assertEquals(1380, latest.shutdownMinute)
        rule.onNodeWithTag("onboarding_shutdown_0").performClick().assertIsSelected()
        rule.onNodeWithTag("onboarding_shutdown_1380").assertIsNotSelected()
        rule.onNodeWithTag("onboarding_shutdown_1320").assertIsNotSelected()
        assertEquals(0, latest.shutdownMinute)
        // Tapping the picked row again keeps it: a radio button, not a checkbox.
        rule.onNodeWithTag("onboarding_shutdown_0").performClick().assertIsSelected()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_lock_window").assertIsDisplayed()
        assertEquals(0, latest.shutdownMinute)
    }

    @Test fun skip_goes_to_the_lock_window_with_no_shutdown_time() {
        show(OnboardingState(step = OnboardingStep.SHUTDOWN))
        rule.onNodeWithTag("onboarding_shutdown_1320").performClick()
        rule.onNodeWithTag("onboarding_skip").performClick()
        rule.onNodeWithTag("onboarding_lock_window").assertIsDisplayed()
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 3 of 6")))
        assertEquals(null, latest.shutdownMinute)
    }

    @Test fun back_from_the_lock_window_returns_to_the_shutdown_page_with_the_choice_kept() {
        show(OnboardingState(step = OnboardingStep.SHUTDOWN))
        rule.onNodeWithTag("onboarding_shutdown_1380").performClick()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_lock_window").assertIsDisplayed()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_shutdown").assertIsDisplayed()
        rule.onNodeWithTag("onboarding_shutdown_1380").assertIsSelected()
        rule.onNodeWithTag("onboarding_next").assertIsEnabled()
    }

    /** The existing accessibility check (`PartsBehaviourTest`): 48 x 48dp and something TalkBack reads. */
    @Test fun the_rows_continue_and_skip_are_accessible_at_font_1_and_2() {
        var scale by mutableStateOf(1f)
        var picked by mutableStateOf<Int?>(null)
        rule.setContent { Shot(scale) { ShutdownStep(picked, { picked = it }, {}, {}, {}) } }
        for (f in listOf(1f, 2f)) for (p in listOf<Int?>(null, 1380)) {
            scale = f
            picked = p
            rule.waitForIdle()
            val d = rule.density.density
            for (tag in shutdownRows + "onboarding_next" + "onboarding_skip" + "page_back") {
                if (tag in shutdownRows) rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag(tag))
                val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
                val at = "$tag at font $f, picked $p"
                assertTrue("$at is tappable", hasClickAction().matches(node))
                val label = listOfNotNull(
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                ).joinToString(" ").trim()
                assertTrue("$at has a TalkBack label", label.isNotEmpty())
                assertTrue("$at '$label' is ${node.size.width / d}dp wide", node.size.width / d >= 47.9f)
                assertTrue("$at '$label' is ${node.size.height / d}dp tall", node.size.height / d >= 47.9f)
            }
            shutdownRows.forEach { tag ->
                val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
                assertEquals("$tag is a radio button", androidx.compose.ui.semantics.Role.RadioButton, node.config[SemanticsProperties.Role])
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
