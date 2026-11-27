package com.example.kept.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.example.kept.core.data.account.AccountState
import com.example.kept.core.domain.SettingsCopy
import com.example.kept.core.domain.SignInUi
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What Settings and its pages do when touched, and what TalkBack hears (issue #133). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class SettingsBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private fun text(tag: String): String =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }.replace(SettingsCopy.NBSP, ' ')

    @Test fun every_row_opens_its_editor_or_page() {
        val opened = mutableListOf<String>()
        val actions = SettingsActions(
            onOpenPromises = { opened += "promises" },
            onEditLockFrom = { opened += "lock_from" },
            onEditGiveUp = { opened += "give_up" },
            onOpenExceptions = { opened += "exceptions" },
            onEditBreak = { opened += "break" },
            onOpenPermissions = { opened += "permissions" },
            onOpenHistory = { opened += "history" },
        )
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.ALL_SET, actions) } }
        for (row in settingsRows) rule.onNodeWithTag(row).performScrollTo().assertHasClickAction().performClick()
        assertEquals(listOf("promises", "lock_from", "give_up", "exceptions", "break", "permissions", "history"), opened)
    }

    @Test fun every_value_is_live() {
        var page by mutableStateOf(SettingsFixtures.ALL_SET)
        rule.setContent { Shot(1f) { SettingsFrame(page) } }
        assertEquals("Your promises", text("settings_promises_title"))
        assertEquals("6", text("settings_promises_value"))
        rule.onNodeWithText("4 every day, 2 weekly").assertExists()
        rule.onNodeWithText("Habits").assertDoesNotExist()
        rule.onNodeWithText("Weekly goals").assertDoesNotExist()
        assertEquals("7:00 am", text("settings_lock_from_value"))
        assertEquals("9:00 pm", text("settings_give_up_value"))
        assertEquals("2 apps", text("settings_exceptions_row_value"))
        assertEquals("30 min", text("settings_break_value"))
        assertEquals("All set", text("settings_permissions_row_value"))
        assertEquals("Best streak 12", text("settings_history_value"))

        page = page.copy(habits = 1, weeklyGoals = 3, lockFromMinute = 6 * 60 + 30, dueMinute = 23 * 60 + 30, exceptions = 1, breakMinutes = 60, permissionsOff = 3, bestStreak = 40)
        rule.waitForIdle()
        assertEquals("4", text("settings_promises_value"))
        rule.onNodeWithText("1 every day, 3 weekly").assertExists()
        assertEquals("6:30 am", text("settings_lock_from_value"))
        assertEquals("11:30 pm", text("settings_give_up_value"))
        assertEquals("1 app", text("settings_exceptions_row_value"))
        assertEquals("60 min", text("settings_break_value"))
        assertEquals("3 off", text("settings_permissions_row_value"))
        assertEquals("Best streak 40", text("settings_history_value"))
    }

    @Test fun switches_toggle_and_tell_talkback_on_or_off() {
        var page by mutableStateOf(SettingsFixtures.ALL_SET)
        val actions = SettingsActions(
            onReminders = { page = page.copy(reminders = it) },
            onAnalytics = { page = page.copy(analytics = it) },
        )
        rule.setContent { Shot(1f) { SettingsFrame(page, actions) } }
        fun check(tag: String, on: Boolean) {
            val c = rule.onNodeWithTag(tag).fetchSemanticsNode().config
            assertEquals("$tag role", Role.Switch, c.getOrNull(SemanticsProperties.Role))
            assertEquals("$tag state", if (on) "On" else "Off", c.getOrNull(SemanticsProperties.StateDescription))
            assertEquals("$tag toggle", if (on) ToggleableState.On else ToggleableState.Off, c.getOrNull(SemanticsProperties.ToggleableState))
        }
        check("reminders_toggle", true)
        check("analytics_toggle", false)
        rule.onNodeWithTag("reminders_toggle").performScrollTo().performClick()
        rule.onNodeWithTag("analytics_toggle").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(false, page.reminders)
        assertEquals(true, page.analytics)
        check("reminders_toggle", false)
        check("analytics_toggle", true)
    }

    @Test fun the_switches_read_their_title_and_hint() {
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.ALL_SET) } }
        val t = rule.onNodeWithTag("reminders_toggle").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Reminders", "Three nudges a day"), t)
    }

    @Test fun exception_rows_tell_talkback_on_or_off() {
        rule.setContent { Shot(1f) { ExceptionsContent(BodyMode.SETTINGS, emptyList(), SettingsFixtures.apps, "", {}, { _, _ -> }, {}, {}) } }
        val c = rule.onNodeWithTag("exception_test.spotify").fetchSemanticsNode().config
        assertEquals("On", c.getOrNull(SemanticsProperties.StateDescription))
        val off = rule.onNodeWithTag("exception_test.chrome").fetchSemanticsNode().config
        assertEquals("Off", off.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun settings_pages_have_a_back_arrow_and_no_step_bar_or_continue() {
        var backs = 0
        val pages = listOf<@androidx.compose.runtime.Composable () -> Unit>(
            { ExceptionsContent(BodyMode.SETTINGS, emptyList(), SettingsFixtures.apps, "", {}, { _, _ -> }, { backs++ }, {}) },
            { PermissionsContent(BodyMode.SETTINGS, PermissionKind.entries, SettingsFixtures.oneMissing, {}, { backs++ }, {}) },
            { HistoryContent(SettingsFixtures.HISTORY, 12, { backs++ }, {}) },
        )
        var i by mutableStateOf(0)
        rule.setContent { Shot(1f) { pages[i]() } }
        for (n in pages.indices) {
            i = n
            rule.waitForIdle()
            rule.onNodeWithTag("page_back").performClick()
            rule.onNodeWithTag("step_progress").assertDoesNotExist()
            rule.onNodeWithTag("onboarding_next").assertDoesNotExist()
        }
        assertEquals(3, backs)
    }

    @Test fun permissions_in_settings_show_the_hollow_day_note() {
        rule.setContent { Shot(1f) { PermissionsContent(BodyMode.SETTINGS, PermissionKind.entries, SettingsFixtures.oneMissing, {}, {}, {}) } }
        rule.onNodeWithTag("page_body").performScrollToNode(androidx.compose.ui.test.hasText("hollow", substring = true))
        rule.onNodeWithText("If usage access goes off during a lock window, that day shows as hollow: it does not count, and it does not break your streak.").assertExists()
        rule.onNodeWithTag("permission_grant_overlay").assertExists()
        rule.onNodeWithTag("permission_grant_usage_access").assertDoesNotExist()
    }

    @Test fun history_shows_each_day_and_opens_its_recap() {
        val opened = mutableListOf<String>()
        rule.setContent { Shot(1f) { HistoryContent(SettingsFixtures.HISTORY, 12, {}, { opened += it }) } }
        rule.onNodeWithText("Your last 14 days. Best streak: 12 days.").assertExists()
        val expected = mapOf(
            "2026-09-29" to "Kept", "2026-09-25" to "Shield used", "2026-09-22" to "Lock was off",
            "2026-09-20" to "Too late", "2026-09-19" to "Missed", "2026-09-18" to "Written off",
        )
        for ((date, label) in expected) {
            rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("history_$date"))
            assertEquals(label, text("history_label_$date"))
        }
        assertEquals("Tue 29 Sep", text("history_date_2026-09-29"))
        val read = rule.onNodeWithTag("history_2026-09-25").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text.replace(SettingsCopy.NBSP, ' ') }
        assertEquals(listOf("Fri 25 Sep", "1 of 2", "Shield used"), read)
        rule.onNodeWithTag("history_2026-09-25").performClick()
        assertEquals(listOf("2026-09-25"), opened)
    }

    @Test fun history_names_the_weekly_goal_a_day_was_missed_for() {
        val days = listOf(
            // Both habits done in time, the must-do session not: missed, and it says why.
            SettingsFixtures.record("2026-11-25", "late").copy(weeklyDue = 1, weeklyMissed = "Exercise"),
            // The same, saved by the shield, with two goals due.
            SettingsFixtures.record("2026-11-24", "late").copy(shieldConsumed = true, weeklyDue = 2, weeklyMissed = "Exercise\nRead Dune"),
            // A kept must-do day and an ordinary late day name nothing.
            SettingsFixtures.record("2026-11-23", "kept").copy(weeklyDue = 1, weeklyDone = 1),
            SettingsFixtures.record("2026-11-22", "late"),
        )
        rule.setContent { Shot(1f) { HistoryContent(days, 12, {}, {}) } }
        assertEquals("Missed", text("history_label_2026-11-25"))
        assertEquals("Weekly goal missed: Exercise", text("history_weekly_2026-11-25"))
        assertEquals("Shield used", text("history_label_2026-11-24"))
        assertEquals("Weekly goals missed: Exercise, Read Dune", text("history_weekly_2026-11-24"))
        assertEquals("Kept", text("history_label_2026-11-23"))
        rule.onNodeWithTag("history_weekly_2026-11-23", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("Too late", text("history_label_2026-11-22"))
        rule.onNodeWithTag("history_weekly_2026-11-22", useUnmergedTree = true).assertDoesNotExist()
        // TalkBack reads the row as one button, the reason included.
        val read = rule.onNodeWithTag("history_2026-11-25").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text.replace(SettingsCopy.NBSP, ' ') }
        assertEquals(listOf("Wed 25 Nov", "2 of 2", "Missed", "Weekly goal missed: Exercise"), read)
    }

    @Test fun history_shows_at_most_fourteen_days() {
        val many = (1..20).map { SettingsFixtures.record("2026-10-%02d".format(21 - it), "kept") }
        rule.setContent { Shot(1f) { HistoryContent(many, 20, {}, {}) } }
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("history_2026-10-07"))
        rule.onNodeWithTag("history_2026-10-06").assertDoesNotExist()
    }

    @Test fun empty_history_says_when_the_first_recap_lands() {
        rule.setContent { Shot(1f) { HistoryContent(emptyList(), 0, {}, {}) } }
        rule.onNodeWithText("Your first recap lands after tonight.").assertExists()
    }

    @Test fun picking_a_break_length_saves_it_and_closes() {
        val picked = mutableListOf<Int>()
        var open by mutableStateOf(true)
        rule.setContent { Shot(1f) { if (open) BreakLengthEditor(30, onDismiss = { open = false }) { picked += it } } }
        val selected = rule.onNodeWithTag("break_30").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        assertEquals(true, selected)
        rule.onNodeWithTag("break_60").performClick()
        rule.waitForIdle()
        assertEquals(listOf(60), picked)
        assertTrue(!open)
    }

    @Test fun changing_the_window_while_locked_asks_change_it_anyway() {
        var confirmed = 0
        var cancelled = 0
        rule.setContent { Shot(1f) { LockChangeConfirm(SettingsCopy.LOCK_FROM_WHILE_LOCKED, { confirmed++ }, { cancelled++ }) } }
        rule.onNodeWithText("Apps are locked right now").assertExists()
        rule.onNodeWithText(SettingsCopy.LOCK_FROM_WHILE_LOCKED).assertExists()
        rule.onNodeWithText("Change it anyway").assertExists()
        rule.onNodeWithTag("change_cancel").performClick()
        rule.onNodeWithTag("change_anyway").performClick()
        assertEquals(1, confirmed)
        assertEquals(1, cancelled)
    }

    // Sign in with Google (#193) ---------------------------------------------------------------

    @Test fun signed_out_settings_offers_sign_in_with_google_and_tapping_it_signs_in() {
        var asked = 0
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.ALL_SET, SettingsActions(onSignIn = { asked++ })) } }
        rule.onNodeWithTag("settings_sign_in").performScrollTo().assertHasClickAction()
        assertEquals("Sign in with Google", text("settings_sign_in_title"))
        assertEquals("Optional. Your habits stay on this phone.", text("settings_sign_in_hint"))
        rule.onNodeWithTag("settings_sign_in").performClick()
        assertEquals(1, asked)
        rule.onNodeWithTag("settings_sign_in_error").assertDoesNotExist()
    }

    @Test fun signed_in_or_still_restoring_the_row_is_gone() {
        var page by mutableStateOf(SettingsFixtures.SIGNED_IN)
        rule.setContent { Shot(1f) { SettingsFrame(page) } }
        rule.onNodeWithTag("settings_sign_in").assertDoesNotExist()
        rule.onNodeWithTag("settings_history").assertExists()
        page = page.copy(account = AccountState.Restoring)
        rule.waitForIdle()
        rule.onNodeWithTag("settings_sign_in").assertDoesNotExist()
        rule.onNodeWithTag("settings_account").assertDoesNotExist()
        rule.onNodeWithTag("settings_sign_out").assertDoesNotExist()
        page = page.copy(account = AccountState.SignedOut)
        rule.waitForIdle()
        rule.onNodeWithTag("settings_sign_in").assertExists()
        rule.onNodeWithTag("settings_account").assertDoesNotExist()
        rule.onNodeWithTag("settings_sign_out").assertDoesNotExist()
    }

    @Test fun a_failed_sign_in_leaves_one_line_and_the_row_usable() {
        var asked = 0
        var page by mutableStateOf(SettingsFixtures.ALL_SET.copy(signIn = SignInUi.Busy))
        rule.setContent { Shot(1f) { SettingsFrame(page, SettingsActions(onSignIn = { asked++ })) } }
        rule.onNodeWithTag("settings_sign_in").performScrollTo()
        assertEquals("Signing in…", text("settings_sign_in_title"))
        rule.onNodeWithTag("settings_sign_in").performClick()
        assertEquals("a second tap while the sheet is open does nothing", 0, asked)

        page = page.copy(signIn = SignInUi.failed(cancelled = true))
        rule.waitForIdle()
        rule.onNodeWithTag("settings_sign_in_error").performScrollTo()
        assertEquals("Sign-in cancelled.", text("settings_sign_in_error"))
        page = page.copy(signIn = SignInUi.failed(cancelled = false, offline = true))
        rule.waitForIdle()
        assertEquals("Couldn't sign in. Check your connection and try again.", text("settings_sign_in_error"))
        page = page.copy(signIn = SignInUi.failed(cancelled = false))
        rule.waitForIdle()
        assertEquals("Couldn't sign in. Try again in a moment.", text("settings_sign_in_error"))
        rule.onNodeWithTag("settings_sign_in").performScrollTo().performClick()
        assertEquals(1, asked)
    }

    @Test fun the_settings_page_reads_the_account_state() {
        val ui = SettingsUi()
        assertEquals(AccountState.SignedOut, SettingsPage.of(ui, 0, account = AccountState.SignedOut).account)
        assertEquals(AccountState.SignedIn("a@b.c", null), SettingsPage.of(ui, 0, account = AccountState.SignedIn("a@b.c", null)).account)
        assertEquals(AccountState.Restoring, SettingsPage.of(ui, 0, account = AccountState.Restoring).account)
        assertTrue(SettingsPage.of(ui, 0, account = AccountState.SignedIn("a@b.c", null), signingOut = true).signingOut)
    }

    // Signed in, and sign out (#194) -----------------------------------------------------------

    /** Item 1: the account row shows the name with the email under it, and is not a button. */
    @Test fun signed_in_settings_shows_the_account_and_sign_out() {
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.SIGNED_IN) } }
        rule.onNodeWithTag("settings_account").performScrollTo()
        assertEquals("Sam Rivera", text("settings_account_title"))
        assertEquals("sam.rivera@gmail.com", text("settings_account_email"))
        val row = rule.onNodeWithTag("settings_account").fetchSemanticsNode().config
        assertEquals("the account row is not tappable", null, row.getOrNull(SemanticsActions.OnClick))
        rule.onNodeWithTag("settings_sign_out").performScrollTo().assertHasClickAction()
        assertEquals("Sign out", text("settings_sign_out_title"))
        assertEquals("Your habits stay on this phone.", text("settings_sign_out_hint"))
        rule.onNodeWithTag("settings_sign_in").assertDoesNotExist()
    }

    /** Item 1: no display name shows the email alone; neither shows "Signed in". */
    @Test fun the_account_row_falls_back_to_the_email_then_signed_in() {
        var page by mutableStateOf(SettingsFixtures.SIGNED_IN.copy(account = AccountState.SignedIn("sam.rivera@gmail.com", null)))
        rule.setContent { Shot(1f) { SettingsFrame(page) } }
        rule.onNodeWithTag("settings_account").performScrollTo()
        assertEquals("sam.rivera@gmail.com", text("settings_account_title"))
        rule.onNodeWithTag("settings_account_email", useUnmergedTree = true).assertDoesNotExist()
        page = page.copy(account = AccountState.SignedIn(null, null))
        rule.waitForIdle()
        assertEquals("Signed in", text("settings_account_title"))
        rule.onNodeWithTag("settings_account_email", useUnmergedTree = true).assertDoesNotExist()
        page = page.copy(account = AccountState.SignedIn(null, "Sam Rivera"))
        rule.waitForIdle()
        assertEquals("Sam Rivera", text("settings_account_title"))
        rule.onNodeWithTag("settings_account_email", useUnmergedTree = true).assertDoesNotExist()
    }

    /** Owner decision 2026-11-27: Sign out asks first; Cancel keeps the session, confirming signs out. */
    @Test fun sign_out_asks_first_and_cancel_keeps_the_user_signed_in() {
        var signedOut = 0
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.SIGNED_IN, SettingsActions(onSignOut = { signedOut++ })) } }
        rule.onNodeWithTag("settings_sign_out").performScrollTo().performClick()
        rule.onNodeWithText("Sign out?").assertExists()
        rule.onNodeWithText("Your habits, check-ins and progress stay on this phone.").assertExists()
        rule.onNodeWithTag("sign_out_cancel").performClick()
        rule.onNodeWithText("Sign out?").assertDoesNotExist()
        assertEquals("Cancel signs nothing out", 0, signedOut)

        rule.onNodeWithTag("settings_sign_out").performScrollTo().performClick()
        rule.onNodeWithTag("sign_out_confirm").performClick()
        rule.onNodeWithText("Sign out?").assertDoesNotExist()
        assertEquals(1, signedOut)
    }

    /** Item 2: while signing out the title reads "Signing out…" and a second tap does nothing. */
    @Test fun while_signing_out_a_second_tap_does_nothing() {
        var signedOut = 0
        rule.setContent { Shot(1f) { SettingsFrame(SettingsFixtures.SIGNED_IN.copy(signingOut = true), SettingsActions(onSignOut = { signedOut++ })) } }
        rule.onNodeWithTag("settings_sign_out").performScrollTo()
        assertEquals("Signing out…", text("settings_sign_out_title"))
        rule.onNodeWithTag("settings_sign_out").performClick()
        rule.onNodeWithText("Sign out?").assertDoesNotExist()
        assertEquals(0, signedOut)
    }

    @Test fun the_sign_out_confirm_buttons_are_full_size() {
        rule.setContent { Shot(1f) { SignOutConfirm({}, {}) } }
        val d = rule.density.density
        for (tag in listOf("sign_out_cancel", "sign_out_confirm")) {
            val n = rule.onNodeWithTag(tag).assertHasClickAction().fetchSemanticsNode()
            assertTrue("$tag is ${n.size.height / d}dp tall", n.size.height / d >= 47.9f)
            assertTrue("$tag is ${n.size.width / d}dp wide", n.size.width / d >= 47.9f)
        }
    }
}
