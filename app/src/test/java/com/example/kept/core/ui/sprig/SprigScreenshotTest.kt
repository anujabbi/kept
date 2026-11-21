package com.example.kept.core.ui.sprig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.shots.Shot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the Daybreak Sprig, to compare against `docs/design/daybreak/SprigA.dc.html`:
 * every form idle, every pose at Bloom, droop with a tear, each form's locked silhouette, and a
 * wilted Sprig. Record with `./gradlew recordRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class SprigScreenshotTest(private val name: String, private val spec: SprigSpec) {

    @get:Rule val rule = createComposeRule()

    @Test fun shot() {
        rule.setContent { Shot { Frame(name, spec) } }
        rule.onRoot().captureRoboImage("src/test/screenshots/sprig_$name.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            SprigForm.entries.forEach { add(arrayOf("form_${it.name.lowercase()}_idle", SprigSpec(it, SprigPose.IDLE))) }
            SprigPose.entries.forEach { add(arrayOf("pose_${it.name.lowercase()}_bloom", SprigSpec(SprigForm.BLOOM, it))) }
            add(arrayOf("pose_droop_tear_bloom", SprigSpec(SprigForm.BLOOM, SprigPose.DROOP, tear = true)))
            add(arrayOf("wilted_thicket", SprigSpec(SprigForm.THICKET, SprigPose.DROOP, wilted = true)))
            SprigForm.entries.forEach { add(arrayOf("silhouette_${it.name.lowercase()}", SprigSpec(it, SprigPose.IDLE, silhouetteAlpha = 0.2f))) }
        }
    }
}

/** One Sprig at 240dp on cream, with 40dp of headroom above for the sprout and halo. */
@Composable
private fun Frame(name: String, spec: SprigSpec) {
    Column(Modifier.width(300.dp).background(DaybreakColors.cream).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = DaybreakType.smallLabel, color = DaybreakColors.faint)
        Box(Modifier.padding(top = 20.dp).size(240.dp).background(DaybreakColors.white)) {
            SprigView(spec.form, spec.pose, Modifier.size(240.dp), wilted = spec.wilted, tear = spec.tear, silhouetteAlpha = spec.silhouetteAlpha)
        }
    }
}
