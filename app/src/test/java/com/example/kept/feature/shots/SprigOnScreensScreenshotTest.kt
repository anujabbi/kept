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
import com.example.kept.core.data.db.DayRecordEntity
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.shots.Shot
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
 * redesigned) layouts breaks: Recap kept and slipped. The lock and break screens have their own
 * Daybreak screenshots (`LockScreenshotTest`, `BreakScreenshotTest`).
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
