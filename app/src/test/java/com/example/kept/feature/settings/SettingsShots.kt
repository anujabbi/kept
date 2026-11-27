package com.example.kept.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.data.account.AccountState
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.domain.HillEdge
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.SignInUi
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.lock.InstalledApp
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.ui.KeptTab
import com.example.kept.core.ui.KeptTabBar
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment

object SettingsFixtures {
    /** The design's values (`A7-Settings`): 7:00 am to 9:00 pm, 2 exceptions, 30 min; and Your promises' 4 habits and 2 weekly goals (#236). */
    val ALL_SET = SettingsPage(
        habits = 4, weeklyGoals = 2, lockFromMinute = 7 * 60, dueMinute = 21 * 60, exceptions = 2, breakMinutes = 30,
        permissionsOff = 0, reminders = true, analytics = false, bestStreak = 12, form = SprigForm.BLOOM,
    )

    /** Night lockdown switched on with nothing else changed (issue #164): 11:00 pm to 7:00 am. */
    val NIGHT_ON = ALL_SET.copy(night = NightSetting(enabled = true))

    /** Signed out after a sign-in that failed (#193): the Account group with its error line. */
    val SIGN_IN_FAILED = ALL_SET.copy(signIn = SignInUi.failed(cancelled = false, offline = true))

    /** Signed in (#194): the planner's mockup account, name over email, then Sign out. */
    val SIGNED_IN = ALL_SET.copy(account = AccountState.SignedIn("sam.rivera@gmail.com", "Sam Rivera"))

    /** Signed in with a long name and email (#194), which wrap and are never cut off. */
    val SIGNED_IN_LONG = ALL_SET.copy(account = AccountState.SignedIn("alexandra.featherstonehaugh@gmail.com", "Alexandra Featherstonehaugh"))

    /** Two permissions off, reminders off, one exception, a 15 minute break. */
    val MISSING = ALL_SET.copy(permissionsOff = 2, reminders = false, analytics = true, exceptions = 1, breakMinutes = 15)

    /** The design's 14 days (`A15-History` script), newest first: every outcome appears. */
    val HISTORY: List<DayRecordEntity> = listOf(
        "2026-09-29" to "kept", "2026-09-28" to "kept", "2026-09-27" to "kept", "2026-09-26" to "kept",
        "2026-09-25" to "shield", "2026-09-24" to "kept", "2026-09-23" to "kept", "2026-09-22" to "off",
        "2026-09-21" to "kept", "2026-09-20" to "late", "2026-09-19" to "missed", "2026-09-18" to "written",
        "2026-09-17" to "kept", "2026-09-16" to "kept",
    ).map { (d, k) -> record(d, k) }

    /**
     * Days a weekly goal's must-do session decided (story #176), newest first: missed for one
     * goal, saved by the shield with two goals of the longest names (28 characters), a must-do
     * day that was kept, and an ordinary kept day.
     */
    val HISTORY_WEEKLY: List<DayRecordEntity> = listOf(
        record("2026-11-25", "late").copy(weeklyDue = 1, weeklyMissed = "Exercise"),
        record("2026-11-24", "late").copy(shieldConsumed = true, weeklyDue = 2, weeklyMissed = "Practice guitar with friends\nRead one chapter of my novel"),
        record("2026-11-23", "kept").copy(weeklyDue = 1, weeklyDone = 1),
        record("2026-11-22", "kept"),
    )

    fun record(date: String, kind: String): DayRecordEntity {
        val r = DayRecordEntity(date, habitsDone = 2, habitsTotal = 2, finalized = true)
        return when (kind) {
            "kept" -> r.copy(countedForStreak = true)
            "shield" -> r.copy(habitsDone = 1, shieldConsumed = true)
            "off" -> r.copy(unprotected = true)
            "late" -> r
            "missed" -> r.copy(habitsDone = 0)
            "written" -> r.copy(habitsDone = 1, writtenOff = true, breaksUsed = 4)
            else -> error(kind)
        }
    }

    val apps: List<PickableApp> = listOf(
        "Spotify" to true, "Google Classroom" to true, "Chrome" to false, "Instagram" to false,
        "TikTok" to false, "YouTube" to false, "WhatsApp" to false,
    ).map { (label, on) -> PickableApp(InstalledApp("test.${label.lowercase().replace(' ', '.')}", label), on) }

    /** Overlay off, the rest on. */
    val oneMissing: (PermissionKind) -> Boolean = { it != PermissionKind.OVERLAY }
    val allGranted: (PermissionKind) -> Boolean = { true }
}

/** The Settings tab as the app shows it: the page, then the fixed tab bar. */
@Composable
internal fun SettingsFrame(page: SettingsPage, actions: SettingsActions = SettingsActions()) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { SettingsContent(page, actions) }
        KeptTabBar(KeptTab.Settings, onSelect = {})
    }
}

/** Every page the issue lists, by golden name. */
internal val settingsScreens: List<Pair<String, @Composable () -> Unit>> = listOf(
    "settings" to { SettingsFrame(SettingsFixtures.ALL_SET) },
    "settings_missing" to { SettingsFrame(SettingsFixtures.MISSING) },
    "settings_night" to { SettingsFrame(SettingsFixtures.NIGHT_ON) },
    "settings_sign_in_error" to { SettingsFrame(SettingsFixtures.SIGN_IN_FAILED) },
    "settings_account_signed_in" to { SettingsFrame(SettingsFixtures.SIGNED_IN_LONG) },
    "history" to { HistoryContent(SettingsFixtures.HISTORY, 12, {}, {}) },
    "history_empty" to { HistoryContent(emptyList(), 0, {}, {}) },
    "history_weekly" to { HistoryContent(SettingsFixtures.HISTORY_WEEKLY, 12, {}, {}) },
    "exceptions" to { ExceptionsContent(BodyMode.SETTINGS, emptyList(), SettingsFixtures.apps, "", {}, { _, _ -> }, {}, {}) },
    "permissions_all" to { PermissionsContent(BodyMode.SETTINGS, PermissionKind.entries, SettingsFixtures.allGranted, {}, {}, {}) },
    "permissions_missing" to { PermissionsContent(BodyMode.SETTINGS, PermissionKind.entries, SettingsFixtures.oneMissing, {}, {}, {}) },
)

/** The two rows the Night lockdown group shows only while its switch is on (issue #164). */
internal val nightRows = listOf("settings_night_start", "settings_night_end")

/** Every row tag on the Settings tab, top to bottom. */
internal val settingsRows = listOf(
    "settings_promises", "settings_lock_from", "settings_give_up", "settings_exceptions_row",
    "settings_break", "settings_permissions_row", "settings_history",
)

/** Screenshots and fit checks of Settings and its pages for one screen size; [prefix] names it. */
class SettingsShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun shot(name: String, fontScale: Float) {
        val content = settingsScreens.first { it.first == name }.second
        rule.setContent { Shot(fontScale) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/settings${prefix}_${name}_font$fontScale.png")
    }

    /** The tab scrolled to its end: the privacy line sits above the tab bar. */
    fun scrolledShot(fontScale: Float) {
        rule.setContent { Shot(fontScale) { SettingsFrame(SettingsFixtures.ALL_SET) } }
        rule.onNodeWithTag("settings_page").performTouchInput { repeat(3) { swipeUp() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/settings${prefix}_settings_end_font$fontScale.png")
    }

    /**
     * The Night lockdown group (issue #164), scrolled into view: [on] false is the switch alone,
     * true is the switch with "Starts at" and "Ends at" under it.
     */
    fun nightShot(on: Boolean, fontScale: Float) {
        val page = if (on) SettingsFixtures.NIGHT_ON else SettingsFixtures.ALL_SET
        rule.setContent { Shot(fontScale) { SettingsFrame(page) } }
        // The row after the group first, so the whole group is on screen above it when it fits.
        rule.onNodeWithTag("reminders_toggle").performScrollTo()
        rule.onNodeWithTag(if (on) "settings_night_end" else "night_toggle").performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/settings${prefix}_night_${if (on) "on" else "off"}_font$fontScale.png")
    }

    /** The Account group (#193) scrolled into view, signed out, after a failed sign-in. */
    fun signInShot(fontScale: Float) {
        rule.setContent { Shot(fontScale) { SettingsFrame(SettingsFixtures.SIGN_IN_FAILED) } }
        rule.onNodeWithTag("settings_history").performScrollTo()
        rule.onNodeWithTag("settings_sign_in_error").performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/settings${prefix}_sign_in_error_font$fontScale.png")
    }

    /**
     * The Account group signed in (#194), scrolled into view: the mockup's account at font 1.0,
     * the long name and email at 2.0, as in the planner's two mockups.
     */
    fun accountShot(fontScale: Float) {
        val page = if (fontScale > 1f) SettingsFixtures.SIGNED_IN_LONG else SettingsFixtures.SIGNED_IN
        rule.setContent { Shot(fontScale) { SettingsFrame(page) } }
        rule.onNodeWithTag("settings_history").performScrollTo()
        rule.onNodeWithTag("settings_sign_out").performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/settings${prefix}_account_signed_in_font$fontScale.png")
    }

    /**
     * "Sign out?" (owner decision 2026-11-27) over the signed-in tab, dialog window included.
     * Robolectric draws the dialog window full width without its scrim or rounded card; the
     * emulator screenshots on the PR show the real look.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    fun signOutConfirmShot(fontScale: Float) {
        // The dialog is its own window with the configuration's density, which [Shot] does not reach.
        RuntimeEnvironment.setFontScale(fontScale)
        rule.setContent { Shot(fontScale) { SettingsFrame(SettingsFixtures.SIGNED_IN) } }
        rule.onNodeWithTag("settings_sign_out").performScrollTo().performClick()
        rule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/settings${prefix}_sign_out_confirm_font$fontScale.png")
    }

    /** History scrolled to its last day. */
    fun historyEndShot(fontScale: Float) {
        rule.setContent { Shot(fontScale) { HistoryContent(SettingsFixtures.HISTORY, 12, {}, {}) } }
        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("history_2026-09-16"))
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/settings${prefix}_history_end_font$fontScale.png")
    }

    private fun bounds(tag: String, unmerged: Boolean = false): Rect {
        val d = rule.density.density
        val n = rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
        return Rect(n.positionInRoot / d, Size(n.size.width / d, n.size.height / d))
    }

    private fun rootDp(): Size {
        val d = rule.density.density
        val n = rule.onRoot().fetchSemanticsNode()
        return Size(n.size.width / d, n.size.height / d)
    }

    private fun layout(tag: String, unmerged: Boolean = false): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        return result.single()
    }

    /** [tag]'s text is on one line and not cut off, and lies inside the screen's width. */
    private fun assertOneLine(at: String, tag: String, unmerged: Boolean = false) {
        val l = layout(tag, unmerged)
        assertTrue("$at: $tag wraps", l.lineCount == 1)
        assertTrue("$at: $tag is cut off (${l.multiParagraph.intrinsics.maxIntrinsicWidth} > ${l.size.width})", l.multiParagraph.intrinsics.maxIntrinsicWidth <= l.size.width + 0.5f)
        val b = bounds(tag, unmerged)
        assertTrue("$at: $tag $b runs off the screen (${rootDp().width})", b.left >= 0f && b.right <= rootDp().width + 0.5f)
    }

    /**
     * At font 1.0, 1.3 and 2.0 on every page: no title splits inside a word; on the tab, Sprig
     * touches no text, its lower body is behind the hill, the title ends above the crest, every
     * row's value is on one line and on screen, and the privacy line, scrolled to, ends above the
     * tab bar; on History, every date and label is on one line and on screen.
     */
    fun everyStateFits() {
        val scale = mutableFloatStateOf(1f)
        val current = mutableStateOf(settingsScreens.first())
        rule.setContent { Shot(scale.floatValue) { current.value.second() } }
        for (f in listOf(1f, 1.3f, 2f)) for (screen in settingsScreens) {
            scale.floatValue = f
            current.value = screen
            rule.waitForIdle()
            val name = screen.first
            val at = "$name$prefix at font $f"
            when {
                name.startsWith("settings") -> settingsFit(at)
                name == "history" -> {
                    assertNoWordSplit(rule, "page_title", at)
                    for (r in SettingsFixtures.HISTORY) {
                        rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("history_${r.date}"))
                        rule.waitForIdle()
                        assertOneLine(at, "history_date_${r.date}", unmerged = true)
                        assertOneLine(at, "history_label_${r.date}", unmerged = true)
                    }
                }
                name == "history_weekly" -> for (r in SettingsFixtures.HISTORY_WEEKLY) {
                    rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("history_${r.date}"))
                    rule.waitForIdle()
                    assertOneLine(at, "history_date_${r.date}", unmerged = true)
                    assertOneLine(at, "history_label_${r.date}", unmerged = true)
                    // The line naming the goal may wrap; it never splits a word or leaves the screen.
                    if (r.weeklyMissed.isNotEmpty()) {
                        assertNoWordSplit(rule, "history_weekly_${r.date}", at, unmerged = true)
                        val b = bounds("history_weekly_${r.date}", unmerged = true)
                        assertTrue("$at: the weekly line $b runs off the screen", b.left >= 0f && b.right <= rootDp().width + 0.5f)
                    }
                }
                else -> assertNoWordSplit(rule, "page_title", at)
            }
        }
    }

    private fun settingsFit(at: String) {
        rule.onNodeWithTag("settings_title").performScrollTo()
        rule.waitForIdle()
        assertNoWordSplit(rule, "settings_title", at)
        val sprig = bounds("settings_sprig")
        val title = bounds("settings_title")
        assertFalse("$at: Sprig $sprig overlaps the title $title", sprig.overlaps(title))
        val crest = bounds("settings_crest", unmerged = true).top
        val w = rootDp().width
        for (x in listOf(sprig.left, sprig.right)) {
            val hill = HillEdge.topDp(crest, w, x)
            assertTrue("$at: Sprig's bottom ${sprig.bottom} is not behind the hill ($hill) at x=$x", sprig.bottom >= hill + 1f)
        }
        assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
        assertTrue("$at: the title runs into the hill", title.bottom <= crest)
        val night = rule.onAllNodesWithTag("settings_night_start").fetchSemanticsNodes().isNotEmpty()
        rule.onNodeWithTag("night_toggle").performScrollTo()
        rule.waitForIdle()
        val toggle = bounds("night_toggle")
        assertTrue("$at: the night switch $toggle is off the screen or too small", toggle.left >= 0f && toggle.right <= w + 0.5f && toggle.height >= 47.9f)
        for (row in settingsRows + if (night) nightRows else emptyList()) {
            rule.onNodeWithTag(row).performScrollTo()
            rule.waitForIdle()
            assertNoWordSplit(rule, "${row}_title", at, unmerged = true)
            assertOneLine(at, "${row}_value", unmerged = true)
        }
        if (rule.onAllNodesWithTag("settings_account").fetchSemanticsNodes().isNotEmpty()) {
            // Signed in (#194): the name and the email are the user's own, so they may break inside
            // a word (a 17-letter surname at 360dp and font 2.0 does); they wrap but are never cut
            // off or run off the card, and both rows are full rows.
            rule.onNodeWithTag("settings_sign_out").performScrollTo()
            rule.onNodeWithTag("settings_account").performScrollTo()
            rule.waitForIdle()
            val card = bounds("settings_account")
            for (tag in listOf("settings_account_title", "settings_account_email")) {
                val l = layout(tag, unmerged = true)
                assertFalse("$at: $tag is cut off", l.hasVisualOverflow)
                val b = bounds(tag, unmerged = true)
                assertTrue("$at: $tag $b runs off the card $card", b.left >= card.left && b.right <= card.right + 0.5f)
            }
            assertTrue("$at: the account row $card is too small or off the screen", card.left >= 0f && card.right <= w + 0.5f && card.height >= 55.9f)
            rule.onNodeWithTag("settings_sign_out").performScrollTo()
            rule.waitForIdle()
            assertNoWordSplit(rule, "settings_sign_out_title", at, unmerged = true)
            val out = bounds("settings_sign_out")
            assertTrue("$at: the sign-out row $out is off the screen or too small", out.left >= 0f && out.right <= w + 0.5f && out.height >= 55.9f)
        } else {
            // The sign-in row (#193): its title never splits a word and it is a full row tall.
            rule.onNodeWithTag("settings_sign_in").performScrollTo()
            rule.waitForIdle()
            assertNoWordSplit(rule, "settings_sign_in_title", at, unmerged = true)
            val signIn = bounds("settings_sign_in")
            assertTrue("$at: the sign-in row $signIn is off the screen or too small", signIn.left >= 0f && signIn.right <= w + 0.5f && signIn.height >= 55.9f)
        }
        if (rule.onAllNodesWithTag("settings_sign_in_error").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("settings_sign_in_error").performScrollTo()
            rule.waitForIdle()
            assertNoWordSplit(rule, "settings_sign_in_error", at)
        }
        rule.onNodeWithTag("settings_privacy").performScrollTo()
        rule.waitForIdle()
        val page = bounds("settings_screen")
        val privacy = bounds("settings_privacy")
        assertTrue("$at: the privacy line $privacy runs under the tab bar (${page.bottom})", privacy.bottom <= page.bottom + 0.5f)
    }
}
