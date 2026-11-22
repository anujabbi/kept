package com.example.kept.core.ui.shots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Daybreak parts sheet (`A18-Parts`, tab bar, switch, chip) at 412 × 915dp and font scale
 * 1.0 and 2.0. Goldens: `app/src/test/screenshots/`. The sky itself is shown by the Home goldens.
 * Record with `./gradlew recordRoborazziDebug`, check with `./gradlew verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class PartsScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, fontScale: Float, content: @Composable () -> Unit) {
        rule.setContent { Shot(fontScale, content) }
        rule.onRoot().captureRoboImage("src/test/screenshots/${name}_font${fontScale}.png")
    }

    @Test fun banners() = shot("parts_banners", 1f) { BannersSheet() }
    @Test fun banners_font2() = shot("parts_banners", 2f) { BannersSheet() }

    @Test fun habitRows() = shot("parts_habit_rows", 1f) { HabitRowsSheet() }
    @Test fun habitRows_font2() = shot("parts_habit_rows", 2f) { HabitRowsSheet() }

    @Test fun buttons() = shot("parts_buttons", 1f) { ButtonsSheet() }
    @Test fun buttons_font2() = shot("parts_buttons", 2f) { ButtonsSheet() }

    @Test fun controls() = shot("parts_switch_chip_tabbar", 1f) { ControlsSheet() }
    @Test fun controls_font2() = shot("parts_switch_chip_tabbar", 2f) { ControlsSheet() }
}
