package com.example.kept.feature.celebration

import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kept.core.domain.SprigForm
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two moments (`A16-Celebrate`, `A17-Evolve`) at 360 × 640dp, font scale 1.0 and 2.0: "Promise
 * kept", the evolution for every form, and one frame of each with motion on. Goldens:
 * `app/src/test/screenshots/celebrate_360_*`, `evolve_360_*`. The lock-screen path is shot in
 * `LockMomentActivityTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class MomentsNarrowScreenTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { MomentShots(rule, "_360") }

    @Test fun celebrate() = shots.celebrate(1f)
    @Test fun celebrate_font2() = shots.celebrate(2f)
    @Test fun celebrate_moving() = shots.celebrateMidMoment()

    @Test fun evolveBud() = shots.evolve(SprigForm.BUD, 1f)
    @Test fun evolveBud_font2() = shots.evolve(SprigForm.BUD, 2f)
    @Test fun evolveBloom() = shots.evolve(SprigForm.BLOOM, 1f)
    @Test fun evolveBloom_font2() = shots.evolve(SprigForm.BLOOM, 2f)
    @Test fun evolveThicket() = shots.evolve(SprigForm.THICKET, 1f)
    @Test fun evolveThicket_font2() = shots.evolve(SprigForm.THICKET, 2f)
    @Test fun evolveGrove() = shots.evolve(SprigForm.GROVE, 1f)
    @Test fun evolveGrove_font2() = shots.evolve(SprigForm.GROVE, 2f)
    @Test fun evolveAncient() = shots.evolve(SprigForm.ANCIENT, 1f)
    @Test fun evolveAncient_font2() = shots.evolve(SprigForm.ANCIENT, 2f)
    @Test fun evolveCelestial() = shots.evolve(SprigForm.CELESTIAL, 1f)
    @Test fun evolveCelestial_font2() = shots.evolve(SprigForm.CELESTIAL, 2f)
    @Test fun evolve_moving() = shots.evolveMidMoment()

    @Test fun headlinesKeepWordsWhole() = shots.headlinesKeepWordsWhole()
}
