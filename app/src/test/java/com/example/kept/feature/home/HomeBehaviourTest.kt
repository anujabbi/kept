package com.example.kept.feature.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HomeBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private class Calls {
        val completed = mutableListOf<Long>()
        val undone = mutableListOf<Long>()
        var permissions = 0
        var habits = 0
        var firstLanding = 0
        fun actions() = HomeActions(
            onComplete = { completed += it },
            onUndo = { undone += it },
            onOpenPermissions = { permissions++ },
            onOpenHabits = { habits++ },
            onFirstLandingSeen = { firstLanding++ },
        )
        val nothing get() = completed.isEmpty() && undone.isEmpty() && permissions == 0 && habits == 0 && firstLanding == 0
    }

    private fun show(s: HomeUiState, calls: Calls = Calls(), fontScale: Float = 1f): Calls {
        rule.setContent { Shot(fontScale) { HomeFrame(s, calls.actions()) } }
        return calls
    }

    @Test fun tapTicksAndHoldUndoes() {
        val calls = show(HomeFixtures.state(2, 1))
        rule.onNodeWithTag("home_habit_2").performClick()
        assertEquals(listOf(2L), calls.completed)
        rule.onNodeWithTag("home_habit_1").performClick()
        assertEquals("tapping a done row does not tick it again", listOf(2L), calls.completed)
        rule.onNodeWithTag("home_habit_1").performTouchInput { longClick() }
        assertEquals(listOf(1L), calls.undone)
    }

    @Test fun compactRowsTickAndUndoToo() {
        val calls = show(HomeFixtures.state(6, 2))
        rule.onNodeWithTag("home_habit_3").performClick()
        rule.onNodeWithTag("home_habit_2").performTouchInput { longClick() }
        assertEquals(listOf(3L), calls.completed)
        assertEquals(listOf(2L), calls.undone)
    }

    @Test fun shieldCardOpensAndClosesOnSecondTap() {
        show(HomeFixtures.state(2, 0))
        rule.onNodeWithTag("home_shield_card").assertDoesNotExist()
        rule.onNodeWithTag("home_shield").performClick()
        rule.onNodeWithText("Your weekly free pass").assertIsDisplayed()
        rule.onNodeWithText("Miss a day and the shield saves your streak. You get a new one every week.").assertIsDisplayed()
        rule.onNodeWithTag("home_shield").performClick()
        rule.onNodeWithTag("home_shield_card").assertDoesNotExist()
    }

    @Test fun shieldCardClosesOnATapElsewhereWithoutTickingAnything() {
        val calls = show(HomeFixtures.state(2, 0))
        rule.onNodeWithTag("home_shield").performClick()
        rule.onNodeWithTag("home_shield_card").assertExists()
        // A tap on a habit row while the card is open only closes the card.
        val row = rule.onNodeWithTag("home_habit_1").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput { click(Offset(row.x, row.y)) }
        rule.onNodeWithTag("home_shield_card").assertDoesNotExist()
        assertTrue(calls.completed.isEmpty())
    }

    @Test fun shieldButtonIs48dpToTouch() {
        show(HomeFixtures.state(2, 0))
        val node = rule.onNodeWithTag("home_shield").fetchSemanticsNode()
        val d = rule.density.density
        assertTrue("height ${node.size.height / d}", node.size.height / d >= 47.9f)
        assertTrue("width ${node.size.width / d}", node.size.width / d >= 47.9f)
        assertEquals("Collapsed", node.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test fun usedShieldSaysSoAndExplainsTheNextOne() {
        show(HomeFixtures.state(2, 0, shieldReady = false))
        rule.onNodeWithText("Shield used this week").assertIsDisplayed()
        rule.onNodeWithTag("home_shield").performClick()
        rule.onNodeWithText("You used this week's shield. A new one comes on Monday.").assertIsDisplayed()
    }

    /** One composition whose state and font scale the test changes. */
    private val current = androidx.compose.runtime.mutableStateOf(HomeFixtures.state(2, 0))
    private val scale = androidx.compose.runtime.mutableFloatStateOf(1f)

    private fun showLive(calls: Calls = Calls()) =
        rule.setContent { Shot(scale.floatValue) { HomeFrame(current.value, calls.actions()) } }

    @Test fun tappingSprigHopsAndOpensNothing() {
        val calls = Calls()
        showLive(calls)
        for (s in listOf(HomeFixtures.state(2, 0), HomeFixtures.state(6, 6))) {
            current.value = s
            rule.waitForIdle()
            val sprig = rule.onNodeWithTag("home_sprig")
            sprig.performTouchInput { click(center) }
            sprig.performSemanticsAction(SemanticsActions.OnClick)
            rule.waitForIdle()
            assertTrue("Sprig navigates nowhere", calls.nothing)
            // Still on Home.
            rule.onNodeWithTag("home_headline").assertIsDisplayed()
        }
    }

    @Test fun bannersShowInOrderAndAct() {
        val calls = show(HomeFixtures.state(2, 1, late = 0, pastDue = true, lockOff = true, firstLanding = true))
        val lockOff = rule.onNodeWithTag("home_lock_off").fetchSemanticsNode().boundsInRoot.top
        val first = rule.onNodeWithTag("home_first_landing").fetchSemanticsNode().boundsInRoot.top
        val late = rule.onNodeWithTag("home_too_late").fetchSemanticsNode().boundsInRoot.top
        assertTrue("lock is off, first landing, too late", lockOff < first && first < late)
        rule.onNodeWithTag("home_lock_off").performClick()
        assertEquals(1, calls.permissions)
        rule.onNodeWithText("Got it").performClick()
        assertEquals(1, calls.firstLanding)
    }

    @Test fun noHabitsShowsTheAddRow() {
        val calls = show(HomeFixtures.state(0, 0))
        rule.onNodeWithText("Add your first habit").performClick()
        assertEquals(1, calls.habits)
    }

    @Test fun headlineAndLineFollowProgress() {
        show(HomeFixtures.state(2, 1))
        rule.onNodeWithText("One to go.").assertIsDisplayed()
        rule.onNodeWithText("Apps unlock when you finish, or at 9:00 pm.").assertIsDisplayed()
        rule.onNodeWithText("12 day streak").assertIsDisplayed()
    }

    @Test fun streakPillStaysOnOneLineAtLargeFonts() {
        current.value = HomeFixtures.state(6, 0)
        showLive()
        for (f in listOf(1f, 1.3f, 2f)) {
            scale.floatValue = f
            rule.waitForIdle()
            val text = rule.onNodeWithText("12 day streak")
            val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            text.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
            assertEquals("one line at $f", 1, result.single().lineCount)
            val r = result.single()
            // softWrap = false lays the paragraph out at the max width; what counts is the line.
            assertTrue("not cut at $f", !r.isLineEllipsized(0) && r.getLineRight(0) <= r.size.width + 0.5f)
        }
    }

    @Test fun sprigNeverOverlapsTheHeadlineBlock() {
        val states = listOf(HomeFixtures.state(1, 0), HomeFixtures.state(3, 0), HomeFixtures.state(3, 3), HomeFixtures.state(6, 0), HomeFixtures.state(6, 6), HomeFixtures.state(10, 0))
        showLive()
        for (f in listOf(1f, 2f)) for (s in states) {
            scale.floatValue = f
            current.value = s
            rule.waitForIdle()
            val sprig = rule.onNodeWithTag("home_sprig").fetchSemanticsNode().boundsInRoot
            for (tag in listOf("home_headline", "home_line")) {
                val text = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("${s.today.done}/${s.today.total} at $f: Sprig $sprig overlaps $tag $text", !sprig.overlaps(text))
            }
        }
    }
}
