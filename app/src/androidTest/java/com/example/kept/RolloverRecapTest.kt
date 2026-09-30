package com.example.kept

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.RolloverRunner
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.StreakRules
import com.example.kept.core.ui.KeptTheme
import com.example.kept.feature.recap.RecapScreen
import com.example.kept.feature.recap.RecapViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import javax.inject.Inject

/**
 * Issue #16, end to end through the real Room database and the real [RolloverRunner]: after a gap
 * of several days the recap must be about the day the streak was actually lost, and the recap
 * screen must only claim "Streak reset" on that day.
 *
 * Three pieces of wiring are proved together:
 * 1. the runner picks the reset day (not the last day) as `pendingRecapDate`;
 * 2. [RecapViewModel] derives `streakReset` from the day's record and the record of the day
 *    before it, both read back from Room as the runner persisted them;
 * 3. [RecapScreen] renders the reset copy only when that flag is true, and a plain "fresh start"
 *    for a slipped day whose streak was already at 0.
 *
 * Setup: a 20-day streak, last rolled over four days ago, one MANUAL habit and no entries, so the
 * three pending days (today-3, today-2, today-1) are each 0 of 1. The day before the gap (today-4)
 * gets a finalized, kept record with `streakEnd = 20`. That mirrors reality: the runner persists
 * every day it rolls, so a streak above 0 always has a finalized previous row, and that row is the
 * "previous day" [com.example.kept.feature.recap.RecapRules.streakReset] compares against. Without
 * it the view model could not tell a reset from a fresh start. Today is not pending: the runner
 * only rolls up to yesterday, and its `insertIfAbsent` for today is a separate, unfinalized row
 * that none of these assertions look at.
 *
 * Shield handling: [StreakRules.refillShield] refills the weekly shield whenever the state's
 * `shieldWeekKey` differs from the ISO week key of the day being rolled. Setting the key to the
 * week of day 1 and `shieldAvailable = false` means day 1 has no shield, so it is a plain reset of
 * the 20-day streak. Days 2 and 3 may well refill the shield if they fall in a new ISO week, but
 * that cannot change the outcome: a refilled shield is only consumed when the streak is above 0,
 * and after day 1 it is 0, so days 2 and 3 neither consume a shield nor reset. The reset therefore
 * lands on day 1 deterministically, whatever weekday the suite runs on.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RolloverRecapTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()

    @Inject lateinit var db: KeptDatabase
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var dayRepo: DayRepository
    @Inject lateinit var runner: RolloverRunner
    @Inject lateinit var time: TimeSource

    private lateinit var today: LocalDate
    private val day1: LocalDate get() = today.minusDays(3)
    private val day2: LocalDate get() = today.minusDays(2)
    private val day3: LocalDate get() = today.minusDays(1)

    private val resetCopy = "Streak reset. Sprig is back to Sprig. Today is a fresh start."
    private val freshStartCopy = "Today is a fresh start."
    private val slippedHeadline = "The promise slipped"

    @Before fun setUp() {
        hilt.inject()
        resetAppState(db, prefs)
        today = time.today()
        runBlocking {
            prefs.updateSettings {
                it.copy(
                    onboardingDone = true,
                    firstUseDate = today.minusDays(10),
                    lockFromMinute = 0,
                    dueMinute = 24 * 60 - 1,
                )
            }
            habits.addHabit("Read", "book", ProofType.MANUAL, 1, "")
            // The last day of the streak, as the runner would have persisted it.
            db.dayRecordDao().upsert(
                DayRecordEntity(
                    date = today.minusDays(4).toString(),
                    habitsDone = 1,
                    habitsTotal = 1,
                    countedForStreak = true,
                    levelEnd = 4,
                    formId = SprigForm.forStreak(20).id,
                    streakEnd = 20,
                    finalized = true,
                ),
            )
            prefs.updateSprig {
                it.copy(
                    streakDays = 20,
                    level = 4,
                    lastRolloverDate = today.minusDays(4),
                    shieldAvailable = false,
                    // Matches day 1's ISO week so no refill happens before the reset; see the KDoc.
                    shieldWeekKey = StreakRules.weekKey(day1),
                )
            }
        }
    }

    @Test fun the_recap_after_a_gap_is_the_day_the_streak_reset() = runBlocking {
        val outputs = runner.runPending()

        assertEquals(listOf(day1, day2, day3), outputs.map { it.summary.date })
        assertTrue("day 1 should reset the 20-day streak", outputs[0].summary.streakReset)
        assertFalse(outputs[0].summary.shieldConsumed)
        assertFalse("day 2 was already at 0", outputs[1].summary.streakReset)
        assertFalse("day 3 was already at 0", outputs[2].summary.streakReset)
        assertEquals(0, outputs.last().state.streakDays)

        assertEquals(day1.toString(), prefs.currentSettings().pendingRecapDate)

        // What the recap view model will read back: day 1 ended at 0, the day before it at 20.
        val day1Record = dayRepo.get(day1)!!
        assertTrue(day1Record.finalized)
        assertEquals(0, day1Record.streakEnd)
        assertEquals(20, dayRepo.get(today.minusDays(4))!!.streakEnd)
    }

    @Test fun the_recap_screen_only_claims_a_reset_on_the_day_that_reset() {
        runBlocking { runner.runPending(notify = false) }
        showRecap(day1)

        compose.waitUntil(10_000) { compose.onAllNodes(hasText(resetCopy)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(slippedHeadline).assertIsDisplayed()
        compose.onNodeWithText(resetCopy).assertIsDisplayed()
    }

    @Test fun the_recap_screen_calls_a_later_slipped_day_a_fresh_start_not_a_reset() {
        runBlocking { runner.runPending(notify = false) }
        showRecap(day3)

        compose.waitUntil(10_000) { compose.onAllNodes(hasText(freshStartCopy)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(slippedHeadline).assertIsDisplayed()
        compose.onNodeWithText(freshStartCopy).assertIsDisplayed()
        compose.onAllNodesWithText("Streak reset", substring = true).assertCountEquals(0)
    }

    /** Builds the real view model by hand so [RecapScreen]'s `hiltViewModel()` default is never evaluated. */
    private fun showRecap(date: LocalDate) {
        val vm = RecapViewModel(dayRepo, SavedStateHandle(mapOf("date" to date.toString())))
        compose.setContent { KeptTheme { RecapScreen(date.toString(), onClose = {}, vm = vm) } }
    }
}
