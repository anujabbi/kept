package com.example.kept.feature.home

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

/** Home at the design's own width, 390 × 844dp, where compact Sprig is 168dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w390dp-h844dp-420dpi")
class HomeMidScreenTest {

    @get:Rule val rule = createComposeRule()

    private val current = mutableStateOf(HomeFixtures.state(2, 0))
    private val scale = mutableFloatStateOf(1f)

    @Test fun sprigSitsBehindTheHill() {
        rule.setContent { Shot(scale.floatValue) { HomeFrame(current.value) } }
        assertSprigSitsBehindHill(rule, current, scale)
    }

    @Test fun sprigNeverOverlapsTheHeadlineBlock() {
        rule.setContent { Shot(scale.floatValue) { HomeFrame(current.value) } }
        assertSprigClearsText(rule, current, scale)
    }
}
