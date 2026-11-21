package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings and its pages fit at the design's own 390 × 844 dp too (see [SettingsShots.everyStateFits]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class SettingsMidScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test fun everyStateFits() = SettingsShots(rule, "_390").everyStateFits()
}
