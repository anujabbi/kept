package com.example.kept.feature.home

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import com.example.kept.core.domain.SprigState
import com.example.kept.core.ui.shots.Shot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's Sprig sits inside a clickable card that opens the roadmap. In the merged tree TalkBack
 * uses, Sprig must still be its own button, so a double tap hops it rather than opening the card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class SprigPanelSemanticsTest {

    @get:Rule val rule = createComposeRule()

    @Test fun sprigIsItsOwnButtonInsideTheRoadmapCard() {
        var roadmapOpened = 0
        rule.setContent {
            Shot { SprigPanel(HomeUiState(loaded = true, sprig = SprigState(streakDays = 7)), onOpenRoadmap = { roadmapOpened++ }) }
        }
        // Default (merged) tree, the one TalkBack reads.
        val sprig = rule.onNodeWithContentDescription("Sprig, Bloom, happy")
        val node = sprig.fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertTrue(hasClickAction().matches(node))

        // The card is a separate node and has not swallowed Sprig's description.
        val others = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.id != node.id }
        assertEquals("only the roadmap card besides Sprig", 1, others.size)
        val cardLabel = others.single().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertFalse(cardLabel.any { it.startsWith("Sprig,") })

        sprig.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("double tap on Sprig does not open the roadmap", 0, roadmapOpened)
    }
}
