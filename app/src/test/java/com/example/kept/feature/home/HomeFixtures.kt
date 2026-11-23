package com.example.kept.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import androidx.compose.ui.Modifier
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.WeeklyGoalWeek
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.domain.WeeklyGoalRules
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

    /** Today's give-up time in these fixtures: a tick or a session before it is in time. */
    const val DUE = 1_000L

    private data class Spec(val title: String, val target: Int, val unit: String, val photo: Boolean = false)

    private val specs = listOf(
        Spec("Exercise", 30, "min"),
        Spec("Read", 10, "pages"),
        Spec("Practice guitar", 20, "min"),
        Spec("Study", 45, "min"),
        Spec("Tidy room", 1, "", photo = true),
        Spec("Drink water", 8, "glasses"),
        Spec("Stretch", 10, "min"),
        Spec("Journal", 1, ""),
        Spec("Meditate", 10, "min"),
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
        weekly: List<WeeklyGoalWeek> = emptyList(),
    ): HomeUiState {
        val t = habits(total, done, late)
        return HomeUiState(
            loaded = true,
            today = t,
            sprig = SprigState(level = 4, streakDays = 12, bestStreak = 12, shieldAvailable = shieldReady),
            lock = LockState(Settings(), t, breakActiveUntil = null, breaksUsedThisWeek = 0, userExceptions = emptySet(), unprotectedToday = false),
            pastDue = pastDue,
            dueMillis = DUE,
            date = today,
            missingLockPermissions = if (lockOff) listOf(PermissionKind.USAGE_ACCESS) else emptyList(),
            lastSeven = week,
            firstLanding = firstLanding,
            weeklyGoals = weekly,
        )
    }

    /** The Sunday of [today]'s week: 22 November 2026. */
    val sunday: LocalDate = WeeklyGoalRules.weekStart(today)

    /**
     * A weekly goal's week as [on] (Wednesday unless said), with a session on each of [sessions],
     * counted as days after Sunday (0 = Sunday). Set long ago unless [from] says when.
     */
    fun goal(
        id: Long,
        title: String,
        daysPerWeek: Int,
        vararg sessions: Int,
        on: LocalDate = today,
        from: LocalDate = LocalDate.of(2026, 11, 1),
    ): WeeklyGoalWeek {
        val start = WeeklyGoalRules.weekStart(on)
        val dates = sessions.map { start.plusDays(it.toLong()) }.toSet()
        return WeeklyGoalWeek(
            WeeklyGoalEntity(id = id, title = title, iconKey = "run", daysPerWeek = daysPerWeek, createdAt = 0, effectiveFrom = from.toString()),
            WeeklyGoalRules.week(WeeklyGoalRules.Goal(daysPerWeek, from), dates, on),
            // A session logged today was logged in time, unless a test says otherwise.
            loggedTodayAt = if (on in dates) DUE - 1 else null,
        )
    }

    /** Wednesday, sessions on Sunday and Tuesday, 4 a week: on track, with a skipped Monday. */
    fun onTrack(): WeeklyGoalWeek = goal(1, "Exercise", 4, 0, 2)

    /** Worked example A: 4 a week, nothing logged, Wednesday. */
    fun todayCounts(): WeeklyGoalWeek = goal(1, "Exercise", 4)

    /** 3 a week, done on Sunday, Monday and today. */
    fun completeWeek(): WeeklyGoalWeek = goal(1, "Exercise", 3, 0, 1, 3)

    /** Worked example B: 4 a week, one session on Sunday, the clock on Monday. */
    fun exampleB(): WeeklyGoalWeek = goal(1, "Exercise", 4, 0, on = sunday.plusDays(1))

    /** Three goals, the limit: on track, today counts, and one set this Tuesday. */
    fun threeGoals(): List<WeeklyGoalWeek> = listOf(
        onTrack(),
        goal(2, "Read", 5, 0),
        goal(3, "Practice", 2, from = sunday.plusDays(2)),
    )

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

/** Every state where Sprig stands highest or lowest, both layouts. */
internal val HILL_STATES = SPRIG_STATES + listOf(1 to 1, 2 to 2, 4 to 4, 9 to 10, 10 to 10, 4 to 0, 2 to 0)

/**
 * Sprig's box bottom (its drawing is clipped there) is below the hill at both of its lower
 * corners, so its lower body is always behind the hill and never floats above it. The hill is
 * the foundation's ellipse: `(-60, crest, width + 60, crest + 96)`.
 */
internal fun assertSprigSitsBehindHill(rule: ComposeContentTestRule, current: MutableState<HomeUiState>, scale: MutableFloatState) {
    val d = rule.density.density
    for (f in listOf(1f, 2f)) for ((total, done) in HILL_STATES) {
        scale.floatValue = f
        current.value = HomeFixtures.state(total, done)
        rule.waitForIdle()
        val width = rule.onRoot().fetchSemanticsNode().size.width / d
        val crest = rule.onNodeWithTag("home_crest", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y / d
        val sprig = rule.onNodeWithTag("home_sprig").fetchSemanticsNode().let {
            androidx.compose.ui.geometry.Rect(it.positionInRoot / d, androidx.compose.ui.geometry.Size(it.size.width / d, it.size.height / d))
        }
        val a = width / 2 + 60f
        fun hillAt(x: Float): Float {
            val k = ((x - width / 2) / a).coerceIn(-1f, 1f)
            return crest + 48f - 48f * kotlin.math.sqrt(1f - k * k)
        }
        for (x in listOf(sprig.left, sprig.right)) {
            assertTrue(
                "$done/$total at $f, ${width}dp: Sprig's bottom ${sprig.bottom} is not below the hill (${hillAt(x)}) at x=$x",
                sprig.bottom >= hillAt(x) + 1f,
            )
        }
        assertTrue("$done/$total at $f: Sprig's top ${sprig.top} is under the crest $crest", sprig.top < crest)
    }
}

/** Home's headline ("Seven to go.", "Promise kept.") never breaks inside a word, at font 1.0, 1.3 and 2.0, in both layouts. */
internal fun assertHomeHeadlineWhole(rule: ComposeContentTestRule, current: MutableState<HomeUiState>, scale: MutableFloatState) {
    for (f in listOf(1f, 1.3f, 2f)) for (total in 0..10) for (done in setOf(0, total)) {
        scale.floatValue = f
        current.value = HomeFixtures.state(total, done)
        rule.waitForIdle()
        com.example.kept.core.ui.shots.assertNoWordSplit(rule, "home_headline", "$done/$total at $f")
    }
}
