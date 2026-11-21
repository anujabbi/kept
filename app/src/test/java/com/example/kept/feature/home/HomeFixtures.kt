package com.example.kept.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import androidx.compose.ui.Modifier
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.ui.KeptTab
import com.example.kept.core.ui.KeptTabBar
import java.time.LocalDate

/** Home states shaped like the design files: a Wednesday, a 12 day streak, Bloom. */
internal object HomeFixtures {
    /** A Wednesday, so the week row reads T F S S M T W like `Main`. */
    val today: LocalDate = LocalDate.of(2026, 11, 25)

    private const val DUE = 1_000L

    private data class Spec(val title: String, val target: Int, val unit: String, val photo: Boolean = false)

    private val specs = listOf(
        Spec("Exercise", 30, "min"),
        Spec("Read", 10, "pages"),
        Spec("Practice guitar", 20, "min"),
        Spec("Study", 45, "min"),
        Spec("Tidy room", 1, "", photo = true),
        Spec("Sleep by 11", 1, ""),
        Spec("Stretch", 10, "min"),
        Spec("Journal", 1, ""),
        Spec("Water", 8, "glasses"),
        Spec("Walk the dog", 20, "min"),
    )

    /** [total] habits, the first [done] ticked; the first [late] of those ticked after the give-up time. */
    fun habits(total: Int, done: Int, late: Int = 0): TodaySummary = TodaySummary(
        specs.take(total).mapIndexed { i, sp ->
            val id = i + 1L
            val entry = if (i < done) {
                HabitEntryEntity(habitId = id, date = today.toString(), progressValue = sp.target, completedAt = if (i < late) DUE + 1 else DUE - 1)
            } else null
            HabitToday(
                HabitEntity(id = id, title = sp.title, iconKey = "run", proofType = if (sp.photo) ProofType.PHOTO else ProofType.MANUAL, targetValue = sp.target, unit = sp.unit, createdAt = 0),
                entry,
                dueMillis = DUE,
            )
        },
    )

    /** `Main`'s week: kept, shielded, kept × 4, then today. */
    private fun lastSeven(): List<Pair<LocalDate, DayRecordEntity?>> = (6 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        val r = when (back) {
            0 -> null
            5 -> DayRecordEntity(date = d.toString(), shieldConsumed = true, finalized = true)
            else -> DayRecordEntity(date = d.toString(), countedForStreak = true, finalized = true)
        }
        d to r
    }

    fun state(
        total: Int,
        done: Int,
        late: Int = 0,
        pastDue: Boolean = false,
        lockOff: Boolean = false,
        firstLanding: Boolean = false,
        shieldReady: Boolean = true,
        week: List<Pair<LocalDate, DayRecordEntity?>> = lastSeven(),
    ): HomeUiState {
        val t = habits(total, done, late)
        return HomeUiState(
            loaded = true,
            today = t,
            sprig = SprigState(level = 4, streakDays = 12, bestStreak = 12, shieldAvailable = shieldReady),
            lock = LockState(Settings(), t, breakActiveUntil = null, breaksUsedThisWeek = 0, userExceptions = emptySet(), unprotectedToday = false),
            pastDue = pastDue,
            date = today,
            missingLockPermissions = if (lockOff) listOf(PermissionKind.USAGE_ACCESS) else emptyList(),
            lastSeven = week,
            firstLanding = firstLanding,
        )
    }

    /** Every day kind once: missed, lock off, shielded, kept, kept, kept, today. */
    fun everyDayKind(): List<Pair<LocalDate, DayRecordEntity?>> = (6 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        d to when (back) {
            6 -> DayRecordEntity(date = d.toString(), finalized = true)
            5 -> DayRecordEntity(date = d.toString(), unprotected = true, finalized = true)
            4 -> DayRecordEntity(date = d.toString(), shieldConsumed = true, finalized = true)
            0 -> null
            else -> DayRecordEntity(date = d.toString(), countedForStreak = true, finalized = true)
        }
    }
}

/** Home as the app shows it: the page above the tab bar. */
@Composable
internal fun HomeFrame(s: HomeUiState, actions: HomeActions = HomeActions()) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { HomeContent(s, actions) }
        KeptTabBar(KeptTab.Today, onSelect = {})
    }
}

/** The issue's Sprig states: 0/1, 0/3, 3/3, 0/6, 6/6, 0/10. */
internal val SPRIG_STATES = listOf(1 to 0, 3 to 0, 3 to 3, 6 to 0, 6 to 6, 10 to 0)

/**
 * In every Sprig state at font 1.0 and 2.0, Sprig's box (its drawing can reach every edge of it)
 * touches neither the headline, the line under it, nor the streak pill. [current] and [scale]
 * drive a composition the caller has already set.
 */
internal fun assertSprigClearsText(rule: ComposeContentTestRule, current: MutableState<HomeUiState>, scale: MutableFloatState) {
    for (f in listOf(1f, 2f)) for ((total, done) in SPRIG_STATES) {
        scale.floatValue = f
        current.value = HomeFixtures.state(total, done)
        rule.waitForIdle()
        val sprig = rule.onNodeWithTag("home_sprig").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("home_headline", "home_line", "home_streak")) {
            val text = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$done/$total at $f: Sprig $sprig overlaps $tag $text", !sprig.overlaps(text))
        }
    }
}
