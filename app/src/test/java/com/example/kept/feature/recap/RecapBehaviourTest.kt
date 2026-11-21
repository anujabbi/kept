package com.example.kept.feature.recap

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.example.kept.core.data.DayRepository
import com.example.kept.core.domain.RecapMotion
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.shots.Shot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.db.KeptDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
 * What the recap does: the kept-day moment (bar grows, knob, petals once), reduce motion, the
 * buttons for each outcome, the copy with real numbers, no "Time off apps", and every state
 * fitting at 390 × 844.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class RecapBehaviourTest {

    @get:Rule val rule = createComposeRule()

    private var introStarts = 0

    private fun show(ui: RecapUi, reduceMotion: Boolean, playIntro: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KeptTheme(reduceMotion = reduceMotion) {
                RecapContent(ui, onClose = {}, playIntro = playIntro, onIntroStarted = { introStarts++ })
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun fillWidth() = rule.recapRect("recap_bar_fill").width
    private fun barWidth() = rule.recapRect("recap_bar").width
    private fun petals() = rule.onAllNodesWithTag("recap_petals").fetchSemanticsNodes().size

    @Test fun onAKeptDayTheBarGrowsFromYesterdayToToday() {
        show(RecapFixtures.KEPT, reduceMotion = false, playIntro = true)
        val w = barWidth()
        assertEquals("starts at yesterday, 4 of 7", w * 4 / 7, fillWidth(), 1f)
        rule.mainClock.advanceTimeBy(RecapMotion.GROW_DELAY_MS - 100L)
        assertEquals("waits 0.9s", w * 4 / 7, fillWidth(), 1f)
        rule.mainClock.advanceTimeBy(100L + RecapMotion.GROW_MS / 2)
        assertTrue("growing at 1.5s: ${fillWidth()}", fillWidth() > w * 4 / 7 + 2f)
        rule.mainClock.advanceTimeBy(RecapMotion.GROW_MS.toLong())
        assertEquals("today, 5 of 7, by 2.1s", w * 5 / 7, fillWidth(), 1f)
        assertEquals("played once", 1, introStarts)
    }

    @Test fun petalsFallOnceOnAKeptDay() {
        show(RecapFixtures.KEPT, reduceMotion = false, playIntro = true)
        assertEquals(1, petals())
        rule.mainClock.advanceTimeBy(RecapMotion.PETALS_END_MS - 200L)
        assertEquals("still falling", 1, petals())
        rule.mainClock.advanceTimeBy(400)
        assertEquals("gone once fallen", 0, petals())
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals("never again", 0, petals())
    }

    @Test fun reduceMotionShowsTodayAtOnceWithNoPetals() {
        show(RecapFixtures.KEPT, reduceMotion = true, playIntro = true)
        assertEquals(barWidth() * 5 / 7, fillWidth(), 1f)
        assertEquals(0, petals())
        rule.mainClock.advanceTimeBy(3_000)
        assertEquals(barWidth() * 5 / 7, fillWidth(), 1f)
        assertEquals(0, petals())
        assertEquals("nothing played", 0, introStarts)
    }

    @Test fun otherOutcomesHaveNoMoment() {
        val current = mutableStateOf(RecapFixtures.SHIELD)
        rule.mainClock.autoAdvance = false
        rule.setContent { KeptTheme(reduceMotion = false) { RecapContent(current.value, onClose = {}, playIntro = true, onIntroStarted = { introStarts++ }) } }
        for (ui in listOf(RecapFixtures.SHIELD, RecapFixtures.LOCK_OFF, RecapFixtures.SLIPPED, RecapFixtures.TOO_LATE, RecapFixtures.WRITTEN_OFF)) {
            current.value = ui
            rule.mainClock.advanceTimeByFrame()
            assertEquals(0, petals())
            assertEquals(maxOf(16f, barWidth() * RecapFixtures.fraction(ui)), fillWidth(), 1f)
        }
        assertEquals(0, introStarts)
    }

    @Test fun aRecreatedScreenDoesNotReplay() {
        show(RecapFixtures.KEPT, reduceMotion = false, playIntro = false)
        assertEquals(0, petals())
        assertEquals(barWidth() * 5 / 7, fillWidth(), 1f)
        assertEquals(0, introStarts)
    }

    @Test fun theMomentWaitsForTheRecordToLoad() {
        val current = mutableStateOf(RecapUi())
        rule.mainClock.autoAdvance = false
        rule.setContent { KeptTheme(reduceMotion = false) { RecapContent(current.value, onClose = {}, playIntro = true, onIntroStarted = { introStarts++ }) } }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0, introStarts)
        current.value = RecapFixtures.KEPT
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(1, introStarts)
        assertEquals(1, petals())
    }

    @Test fun theViewModelPlaysTheMomentOncePerShowing() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KeptDatabase::class.java).allowMainThreadQueries().build()
        val repo = DayRepository(db.dayRecordDao(), TimeSource(Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneId.of("UTC"))))
        val saved = SavedStateHandle(mapOf("date" to "2026-11-20"))
        val vm = RecapViewModel(repo, saved)
        assertTrue(vm.introPending)
        vm.introStarted()
        assertFalse(vm.introPending)
        assertFalse("a recreated ViewModel on the same saved state", RecapViewModel(repo, saved).introPending)
        db.close()
    }

    @Test fun keptSharesTheLookAndOthersOpenTheSprigTab() {
        val current = mutableStateOf(RecapFixtures.KEPT)
        var sprigs = 0
        var done = 0
        rule.setContent { Shot { RecapContent(current.value, onClose = { done++ }, onOpenSprigs = { sprigs++ }) } }
        rule.onNodeWithTag("recap_second", useUnmergedTree = false).assertTextEquals("Share this look")
        for (ui in listOf(RecapFixtures.SHIELD, RecapFixtures.LOCK_OFF, RecapFixtures.SLIPPED, RecapFixtures.TOO_LATE, RecapFixtures.WRITTEN_OFF)) {
            current.value = ui
            rule.waitForIdle()
            rule.onNodeWithTag("recap_second").assertTextEquals("See my Sprigs").performClick()
        }
        assertEquals(5, sprigs)
        rule.onNodeWithTag("recap_done_button").assertTextEquals("Done").performClick()
        assertEquals(1, done)
    }

    @Test fun copyAndStatsUseTheRealNumbers() {
        val current = mutableStateOf(RecapFixtures.KEPT)
        rule.setContent { Shot { RecapContent(current.value, onClose = {}) } }
        fun check(ui: RecapUi, headline: String, line: String, done: String, broken: String, brokenLabel: String, next: String, count: String) {
            current.value = ui
            rule.waitForIdle()
            rule.onNodeWithTag("recap_headline").assertTextEquals(headline)
            rule.onNodeWithTag("recap_line").assertTextEquals(line)
            rule.onNodeWithTag("recap_done").assertTextEquals(done)
            rule.onNodeWithTag("recap_done_label").assertTextEquals("habits done")
            rule.onNodeWithTag("recap_broken").assertTextEquals(broken)
            rule.onNodeWithTag("recap_broken_label").assertTextEquals(brokenLabel)
            rule.onNodeWithTag("recap_next").assertTextEquals(next)
            rule.onNodeWithTag("recap_count").assertTextEquals(count)
            rule.onAllNodesWithText("Time off apps", substring = true).assertCountEquals(0)
            rule.onAllNodesWithText("off apps", substring = true).assertCountEquals(0)
        }
        val nb = ' '
        check(RecapFixtures.KEPT, "Promise kept.", "12 days running.", "2${nb}of${nb}2", "0", "locks broken", "Thicket in 2 days", "Day 12 of 14")
        check(RecapFixtures.SHIELD, "Shield saved it.", "Your weekly shield kept the 12-day streak alive.", "1${nb}of${nb}2", "0", "locks broken", "Thicket in 2 days", "Day 12 of 14")
        check(
            RecapFixtures.LOCK_OFF, "The lock was off.", "That one is on us. Today does not count, but your 12-day streak is safe.",
            "2${nb}of${nb}2", "0", "locks broken", "Thicket in 2 days", "Day 12 of 14",
        )
        check(RecapFixtures.SLIPPED, "The promise slipped.", "Streak reset. Tomorrow is fresh.", "1${nb}of${nb}2", "1", "lock broken", "Bud in 3 days", "Day 0 of 3")
        check(RecapFixtures.TOO_LATE, "Too late to count.", "Everything got ticked after the give-up time. Streak reset.", "2${nb}of${nb}2", "0", "locks broken", "Bud in 3 days", "Day 0 of 3")
        check(
            RecapFixtures.WRITTEN_OFF, "Day written off.", "A fourth break this week. Streak reset, and the shield could not save it.",
            "1${nb}of${nb}2", "4", "locks broken", "Bud in 3 days", "Day 0 of 3",
        )
    }

    @Test fun theFinalFormHasNoBar() {
        rule.setContent { Shot { RecapContent(RecapFixtures.FINAL, onClose = {}) } }
        rule.onNodeWithTag("recap_final").assertTextEquals("Sprig is Celestial, the final form.")
        rule.onAllNodesWithTag("recap_bar").assertCountEquals(0)
        rule.onAllNodesWithTag("recap_to_sprig").assertCountEquals(0)
        rule.onAllNodesWithTag("recap_from_sprig").assertCountEquals(1)
    }

    @Test fun theProgressCardShowsBothForms() {
        rule.setContent { Shot { RecapContent(RecapFixtures.KEPT, onClose = {}) } }
        val from = rule.onNodeWithTag("recap_from_sprig").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        val to = rule.onNodeWithTag("recap_to_sprig").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(from, from.startsWith("Sprig, Bloom"))
        assertTrue(to, to.startsWith("Sprig, Thicket"))
        assertTrue("mini Sprigs are under 100dp", rule.recapRect("recap_from_sprig").width < 100f)
    }

    @Test fun everyStateFits() {
        val current = mutableStateOf(RecapFixtures.KEPT)
        val scale = mutableFloatStateOf(1f)
        rule.setContent { Shot(scale.floatValue) { RecapContent(current.value, onClose = {}) } }
        assertRecapFits(rule, current, scale, 390f)
    }
}
