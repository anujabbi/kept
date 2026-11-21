package com.example.kept.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import com.example.kept.core.domain.HillEdge
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.ProofType
import com.example.kept.core.lock.InstalledApp
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.ui.HabitTemplate
import com.example.kept.core.ui.HabitTemplates
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.example.kept.feature.settings.BodyMode
import com.example.kept.feature.settings.ExceptionsContent
import com.example.kept.feature.settings.PickableApp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

object OnboardingFixtures {
    /** The design's list (`A12-Exceptions`): Spotify and Google Classroom stay open. */
    val apps: List<PickableApp> = listOf(
        "Spotify" to true, "Google Classroom" to true, "Chrome" to false, "Instagram" to false,
        "TikTok" to false, "YouTube" to false, "WhatsApp" to false,
    ).map { (label, on) -> PickableApp(InstalledApp("test.${label.lowercase().replace(' ', '.')}", label), on) }

    /** Step 1 as it opens: Exercise and Read picked. */
    val habits = OnboardingState(step = OnboardingStep.HABITS)

    /** Ten picked: every preset and four of their own. */
    val habitsAtLimit = OnboardingState(
        step = OnboardingStep.HABITS,
        chosen = HabitTemplates.list + (1..4).map { HabitTemplate("Own habit $it", "star", ProofType.MANUAL, 5, "min") },
    )

    val window = LockWindowRules.Window(7 * 60, 21 * 60)

    /** The design's permissions state: usage access and notifications on, overlay missing. */
    val oneRequiredMissing: (PermissionKind) -> Boolean = { it == PermissionKind.USAGE_ACCESS || it == PermissionKind.NOTIFICATIONS }
    val allGranted: (PermissionKind) -> Boolean = { true }
    val onboardingKinds = listOf(PermissionKind.USAGE_ACCESS, PermissionKind.OVERLAY, PermissionKind.NOTIFICATIONS, PermissionKind.BATTERY, PermissionKind.CAMERA)
}

/** Every screen the issue lists, by golden name. */
val onboardingScreens: List<Pair<String, @Composable () -> Unit>> = listOf(
    "welcome" to { WelcomeStep(onStart = {}) },
    "habits" to { HabitsStep(OnboardingFixtures.habits, {}, {}, {}, {}) },
    "habits_limit" to { HabitsStep(OnboardingFixtures.habitsAtLimit, {}, {}, {}, {}) },
    "add_habit" to { AddHabitPage(onBack = {}, onSave = {}) },
    "lock_window" to { LockWindowStep(OnboardingFixtures.window, {}, {}, {}) },
    "exceptions" to { ExceptionsContent(BodyMode.ONBOARDING, emptyList(), OnboardingFixtures.apps, "", {}, { _, _ -> }, {}, {}) },
    "permissions_missing" to { permissions(BodyMode.ONBOARDING, OnboardingFixtures.oneRequiredMissing) },
    "permissions_all" to { permissions(BodyMode.ONBOARDING, OnboardingFixtures.allGranted) },
    "ready" to { ReadyStep(saving = false, onBack = {}, onStart = {}) },
    "exceptions_settings" to { ExceptionsContent(BodyMode.SETTINGS, emptyList(), OnboardingFixtures.apps, "", {}, { _, _ -> }, {}, {}) },
    "permissions_settings" to { permissions(BodyMode.SETTINGS, OnboardingFixtures.oneRequiredMissing) },
)

@Composable
private fun permissions(mode: BodyMode, granted: (PermissionKind) -> Boolean) =
    com.example.kept.feature.settings.PermissionsContent(mode, OnboardingFixtures.onboardingKinds, granted, {}, {}, {})

/** Screenshots and fit checks of onboarding for one screen size; [prefix] names it in the golden. */
class OnboardingShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun shot(name: String, fontScale: Float) {
        val content = onboardingScreens.first { it.first == name }.second
        rule.setContent { Shot(fontScale) { content() } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/onboarding${prefix}_${name}_font$fontScale.png")
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

    private fun layout(tag: String): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
        return result.single()
    }

    /** Sprig tagged [sprigTag] stands behind the hill whose crest is tagged [crestTag]. */
    private fun assertBehindHill(at: String, sprigTag: String, crestTag: String) {
        val sprig = bounds(sprigTag)
        val crest = bounds(crestTag, unmerged = true).top
        val w = rootDp().width
        for (x in listOf(sprig.left, sprig.right)) {
            val hill = HillEdge.topDp(crest, w, x)
            assertTrue("$at: Sprig's bottom ${sprig.bottom} is not behind the hill ($hill) at x=$x", sprig.bottom >= hill + 1f)
        }
        assertTrue("$at: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
    }

    private fun assertClear(at: String, sprigTag: String, vararg tags: String) {
        val sprig = bounds(sprigTag)
        for (t in tags) {
            val b = bounds(t)
            assertFalse("$at: Sprig $sprig overlaps $t $b", sprig.overlaps(b))
        }
    }

    /**
     * At font 1.0, 1.3 and 2.0: headlines never split inside a word; on Welcome and step 5 Sprig
     * touches no text and its lower body is behind the hill; the welcome text ends above the
     * crest; each step's footer button is on screen, not under the bottom edge.
     */
    fun everyStateFits() {
        val scale = mutableFloatStateOf(1f)
        val current = mutableStateOf(onboardingScreens.first())
        rule.setContent { Shot(scale.floatValue) { current.value.second() } }
        for (f in listOf(1f, 1.3f, 2f)) for (screen in onboardingScreens) {
            scale.floatValue = f
            current.value = screen
            rule.waitForIdle()
            val name = screen.first
            val at = "$name$prefix at font $f"
            when (name) {
                "welcome" -> {
                    assertNoWordSplit(rule, "welcome_headline", at)
                    assertClear(at, "welcome_sprig", "welcome_headline", "welcome_line", "welcome_start", "welcome_caption")
                    assertBehindHill(at, "welcome_sprig", "welcome_crest")
                    val crest = bounds("welcome_crest", unmerged = true).top
                    assertTrue("$at: the line runs into the hill", bounds("welcome_line").bottom <= crest)
                    assertTrue("$at: the button is above the hill's crest", bounds("welcome_start").top > crest)
                }
                "ready" -> {
                    assertNoWordSplit(rule, "ready_title", at)
                    assertClear(at, "ready_sprig", "ready_title", "step_progress")
                    assertBehindHill(at, "ready_sprig", "ready_crest")
                    assertTrue("$at: the title runs into the hill", bounds("ready_title").bottom <= bounds("ready_crest", unmerged = true).top)
                    assertOnScreen(at, "onboarding_finish")
                }
                else -> {
                    assertNoWordSplit(rule, "page_title", at)
                    if (name != "add_habit" && !name.endsWith("_settings")) assertOnScreen(at, "onboarding_next")
                }
            }
            if (name == "lock_window") {
                val d = rule.density.density
                val labels = rule.onNodeWithTag("lock_bar_labels").onChildren().fetchSemanticsNodes().map { it.positionInRoot.x / d to (it.positionInRoot.x + it.size.width) / d }
                assertTrue("$at: four bar labels", labels.size == 4)
                labels.zipWithNext().forEach { (a, b) -> assertTrue("$at: bar labels touch ($a, $b)", b.first - a.second >= 7.5f) }
                for (row in listOf("lock_from", "give_up")) {
                    rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag("${row}_label"))
                    rule.waitForIdle()
                    assertTrue("$at: the $row label wraps beside its stepper", layout("${row}_label").lineCount == 1)
                }
                for (v in listOf("lock_from_value", "give_up_value")) {
                    rule.onNodeWithTag("page_body").performScrollToNode(hasTestTag(v))
                    rule.waitForIdle()
                    val l = layout(v)
                    assertTrue("$at: $v wraps", l.lineCount == 1)
                    assertTrue("$at: $v is cut off", l.multiParagraph.intrinsics.maxIntrinsicWidth <= l.size.width + 0.5f)
                }
            }
        }
    }

    private fun assertOnScreen(at: String, tag: String) {
        val b = bounds(tag)
        assertTrue("$at: $tag ($b) runs off the bottom (${rootDp().height})", b.bottom <= rootDp().height + 0.5f && b.top >= 0f)
    }
}
