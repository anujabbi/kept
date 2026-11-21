package com.example.kept.feature.lock

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kept.core.ui.shots.Shot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The lock screen at the design's own width, 390 × 844dp, where Sprig is 168dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class LockMidScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test fun everyStateFits() {
        val current = mutableStateOf(LockFixtures.state(2, 0))
        val scale = mutableFloatStateOf(1f)
        val label = mutableStateOf("Chrome")
        rule.setContent { Shot(scale.floatValue) { LockScreen(current.value, label.value, LockActions()) } }
        assertLockFits(rule, current, scale, label)
    }
}
