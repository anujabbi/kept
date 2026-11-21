package com.example.kept.core.ui.shots

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.example.kept.core.ui.DaybreakHabitRow
import com.example.kept.core.ui.HabitRowState
import com.example.kept.core.ui.KeptTab
import com.example.kept.core.ui.KeptTabBar
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
class PartsBehaviourTest {

    @get:Rule val rule = createComposeRule()

    /** Every tappable node is at least 48 × 48dp to touch and has something TalkBack reads. */
    private fun assertAccessible(content: @Composable () -> Unit) {
        rule.setContent { Shot(1f, content) }
        val density = rule.density.density
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("found tappable parts", nodes.isNotEmpty())
        nodes.forEach { node ->
            val c = node.config
            val label = listOfNotNull(
                c.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
            ).joinToString(" ").trim()
            assertTrue("node ${node.id} has a TalkBack label", label.isNotEmpty())
            val touch = node.touchBoundsInRoot
            assertTrue("'$label' is ${touch.width / density}dp wide", touch.width / density >= 47.9f)
            assertTrue("'$label' is ${touch.height / density}dp tall", touch.height / density >= 47.9f)
        }
    }

    @Test fun bannersAreAccessible() = assertAccessible { BannersSheet() }
    @Test fun habitRowsAreAccessible() = assertAccessible { HabitRowsSheet() }
    @Test fun buttonsAreAccessible() = assertAccessible { ButtonsSheet() }
    @Test fun switchChipAndTabBarAreAccessible() = assertAccessible { ControlsSheet() }

    @Test fun switchAndChipReportTheirState() {
        rule.setContent { Shot { ControlsSheet() } }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Reminders"))).assertIsOn()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Send anonymous usage data"))).assertIsOff()
        rule.onNodeWithText("min").assertIsSelected()
        rule.onNodeWithText("Today").assertIsSelected()
    }

    @Test fun toDoRowTicksAndDoneRowUndoesOnHold() {
        var ticks = 0
        var undos = 0
        rule.setContent {
            Shot {
                androidx.compose.foundation.layout.Column {
                    DaybreakHabitRow("Exercise", "30 min", HabitRowState.ToDo, onTick = { ticks++ }, onUndo = { undos++ })
                    DaybreakHabitRow("Read", "10 pages", HabitRowState.Done, onTick = { ticks++ }, onUndo = { undos++ })
                }
            }
        }
        rule.onNodeWithText("Exercise").performClick()
        assertEquals(1, ticks)
        rule.onNodeWithText("Read").performClick()
        assertEquals("tapping a done row does nothing", 1, ticks)
        rule.onNodeWithText("Read").performTouchInput { longClick() }
        assertEquals(1, undos)
        rule.onNodeWithText("Exercise").assertIsOff()
        rule.onNodeWithText("Read").assertIsOn()
    }

    @Test fun tabBarReportsTheTappedTab() {
        val picked = mutableListOf<KeptTab>()
        rule.setContent { Shot { KeptTabBar(KeptTab.Today, onSelect = { picked += it }) } }
        rule.onNodeWithText("Sprig").performClick()
        rule.onNodeWithText("Today").performClick()
        assertEquals("re-tapping the current tab does nothing", listOf(KeptTab.Sprig), picked)
    }

}
