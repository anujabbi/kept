package com.example.kept.feature.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.data.db.HabitEntity
import com.example.kept.core.data.db.HabitEntryEntity
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigState
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.shots.Shot
import com.example.kept.feature.home.HomeUiState
import com.example.kept.feature.home.SprigPanel
import com.example.kept.feature.lock.BreakLockScreen
import com.example.kept.feature.lock.LockScreen
import com.example.kept.feature.lock.LockUi
import com.example.kept.feature.recap.RecapContent
import com.example.kept.feature.recap.RecapUi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The existing screens with the new Sprig in place, to check nothing in their (not yet
 * redesigned) layouts breaks: Home's Sprig panel, Lock, Break, and Recap kept and slipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class SprigOnScreensScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { Shot { Box(Modifier.fillMaxSize().background(KeptTheme.colors.surface1)) { content() } } }
        rule.onRoot().captureRoboImage("src/test/screenshots/screen_$name.png")
    }

    private fun habit(id: Long, title: String, icon: String, done: Boolean) = HabitToday(
        HabitEntity(id = id, title = title, iconKey = icon, proofType = ProofType.MANUAL, targetValue = 1, unit = "", createdAt = 0),
        if (done) HabitEntryEntity(habitId = id, date = "2026-11-21", progressValue = 1, completedAt = 1) else null,
    )

    private fun today(done: Boolean) = TodaySummary(listOf(habit(1, "Exercise", "run", done), habit(2, "Read", "book", done)))

    private val lockUi = LockUi(
        loaded = true,
        lock = LockState(Settings(), today(false), breakActiveUntil = null, breaksUsedThisWeek = 0, userExceptions = emptySet(), unprotectedToday = false),
        sprig = SprigState(level = 2, streakDays = 9),
        clockLabel = "10:42",
    )

    @Test fun homeIdle() = shot("home_sprig_idle") {
        Box(Modifier.padding(20.dp)) { SprigPanel(HomeUiState(loaded = true, today = today(false), sprig = SprigState(streakDays = 9)), onOpenRoadmap = {}) }
    }

    @Test fun homeAllDone() = shot("home_sprig_cheer") {
        Box(Modifier.padding(20.dp)) { SprigPanel(HomeUiState(loaded = true, today = today(true), sprig = SprigState(streakDays = 30)), onOpenRoadmap = {}) }
    }

    @Test fun homeWilted() = shot("home_sprig_wilted") {
        Box(Modifier.padding(20.dp)) { SprigPanel(HomeUiState(loaded = true, today = today(false), sprig = SprigState(streakDays = 14, wilted = true)), onOpenRoadmap = {}) }
    }

    @Test fun lock() = shot("lock") {
        LockScreen(lockUi, blockedLabel = "Instagram", onComplete = {}, onBreak = {}, onEmergency = {}, onOpenKept = {})
    }

    @Test fun breakLock() = shot("break") {
        BreakLockScreen(lockUi, onCancel = {}, onConfirm = {}, countdownSeconds = 0)
    }

    @Test fun recapKept() = shot("recap_kept") {
        RecapContent(
            RecapUi(DayRecordEntity(date = "2026-11-20", habitsDone = 2, habitsTotal = 2, lockedMillis = 3_600_000, countedForStreak = true, formId = 3, streakEnd = 9), LocalDate.of(2026, 11, 20)),
            onClose = {},
        )
    }

    @Test fun recapSlipped() = shot("recap_slipped") {
        RecapContent(
            RecapUi(DayRecordEntity(date = "2026-11-20", habitsDone = 1, habitsTotal = 2, formId = 4, streakEnd = 0), LocalDate.of(2026, 11, 20), streakReset = true),
            onClose = {},
        )
    }
}
