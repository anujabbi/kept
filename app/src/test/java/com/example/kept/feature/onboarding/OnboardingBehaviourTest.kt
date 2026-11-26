package com.example.kept.feature.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.example.kept.core.data.account.SignInResult
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
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
            s.step == OnboardingStep.HABITS && s.addingHabit -> AddHabitPage(onBack = { s = OnboardingFlow.back(s) }, onSave = { s = OnboardingFlow.addCustom(s, it) })
            s.step == OnboardingStep.HABITS -> HabitsStep(
                s,
                onToggle = { s = OnboardingFlow.toggle(s, it) },
                onAddOwn = { s = OnboardingFlow.openAddHabit(s) },
                onBack = { s = OnboardingFlow.back(s) },
                onContinue = { s = OnboardingFlow.next(s) },
            )
            s.step == OnboardingStep.SHUTDOWN -> ShutdownStep(
                s.shutdownMinute,
                onPick = { s = OnboardingFlow.pickShutdown(s, it) },
                onBack = { s = OnboardingFlow.back(s) },
                onContinue = { s = OnboardingFlow.next(s) },
                onSkip = { s = OnboardingFlow.skipShutdown(s) },
            )
            s.step == OnboardingStep.LOCK_WINDOW -> LockWindowStep(s.window, { s = OnboardingFlow.setWindow(s, it) }, { s = OnboardingFlow.back(s) }, { s = OnboardingFlow.next(s) })
            s.step == OnboardingStep.ACCOUNT -> AccountStep(
                s.signIn,
                signedInAs = null,
                onSignIn = { signInCalls++; s = OnboardingFlow.signInFinished(OnboardingFlow.startSignIn(s), signInAnswer) },
                onBack = { s = OnboardingFlow.back(s) },
                onContinue = { s = OnboardingFlow.next(s) },
                onSkip = { s = OnboardingFlow.skipAccount(s) },
            )
            else -> Text("step ${s.step}", Modifier.testTag("later_placeholder"))
        }
    }

    private fun show(initial: OnboardingState = OnboardingState()) {
        rule.setContent { Shot(1f) { Flow(initial) } }
        rule.waitForIdle()
    }

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
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("2 habits picked")
        rule.onNodeWithTag("onboarding_habit_Practice").assert(checked(false)).performClick().assert(checked(true))
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("3 habits picked")
        listOf("Exercise", "Read", "Practice").forEach { rule.onNodeWithTag("onboarding_habit_$it").performClick() }
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("Pick at least one")
        rule.onNodeWithTag("onboarding_next").assertIsNotEnabled()
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().performClick()
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("1 habit picked")
        rule.onNodeWithTag("onboarding_next").assertIsEnabled()
    }

    @Test fun add_your_own_opens_add_a_habit_and_back_returns_with_picks_kept() {
        show(OnboardingFixtures.habits)
        rule.onNodeWithTag("onboarding_habit_Study").performClick()
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
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("10 habits picked")
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
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().performClick()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_shutdown_1320").performClick()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("give_up_earlier").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_shutdown_1320").assertIsSelected()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_habit_Tidy room").performScrollTo().assert(checked(true))
        rule.onNodeWithTag("onboarding_habit_count").assertTextEquals("3 habits picked")
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("onboarding_shutdown_1320").assertIsSelected()
        rule.onNodeWithTag("onboarding_next").performClick()
        rule.onNodeWithTag("give_up_value").assertTextEquals("8:30 pm")
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("page_back").performClick()
        rule.onNodeWithTag("onboarding_account").assertIsDisplayed()
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
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 3 of 7")))
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
        rule.onNodeWithTag("step_progress").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Step 4 of 7")))
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
