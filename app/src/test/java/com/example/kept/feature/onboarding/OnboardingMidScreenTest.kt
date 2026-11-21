package com.example.kept.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Onboarding at the design's own 390 × 844 dp: every screen fits at font 1.0, 1.3 and 2.0. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class OnboardingMidScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test fun everyStateFits() = OnboardingShots(rule, "_390").everyStateFits()
}
