package com.example.kept.feature.celebration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.PromiseKeptMoment
import com.example.kept.core.ui.shots.Shot
import com.example.kept.core.ui.shots.assertNoWordSplit
import com.github.takahirom.roborazzi.captureRoboImage

/** Every form Sprig can evolve into. */
val EVOLVED_FORMS = SprigForm.entries.filter { it.streakThreshold > 0 }

/** The streak at which [form] is reached, as the gallery entry records it. */
fun reachedAt(form: SprigForm) = form.streakThreshold

/**
 * Screenshots of both moments for one screen size ([prefix] names it in the golden), shared by the
 * 412 × 915 and 360 × 640 test classes.
 */
class MomentShots(private val rule: ComposeContentTestRule, private val prefix: String) {

    fun celebrate(fontScale: Float) {
        rule.setContent { Shot(fontScale) { PromiseKeptMoment(SprigForm.BLOOM, streakDays = 12, onCarryOn = {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/celebrate${prefix}_font$fontScale.png")
    }

    fun evolve(form: SprigForm, fontScale: Float) {
        rule.setContent { Shot(fontScale) { SprigEvolvesContent(form, reachedAt(form), onShare = {}, onCarryOn = {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/evolve${prefix}_${form.name.lowercase()}_font$fontScale.png")
    }

    /** One frame of the motion (reduce motion off): Sprig mid-pop, the ring growing, petals falling. */
    fun celebrateMidMoment() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Moving { PromiseKeptMoment(SprigForm.BLOOM, streakDays = 12, onCarryOn = {}, frozenAtMs = 1_000) } }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().captureRoboImage("src/test/screenshots/celebrate${prefix}_moving_font1.0.png")
    }

    fun evolveMidMoment() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Moving { SprigEvolvesContent(SprigForm.THICKET, 14, onShare = {}, onCarryOn = {}, frozenAtMs = 1_000) } }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().captureRoboImage("src/test/screenshots/evolve${prefix}_moving_font1.0.png")
    }

    /** No headline splits inside a word, at font 1.0, 1.3 and 2.0, for both moments and every form. */
    fun headlinesKeepWordsWhole() {
        val scale = mutableFloatStateOf(1f)
        val screen = mutableStateOf<(@Composable () -> Unit)>({})
        rule.setContent { Shot(scale.floatValue) { screen.value() } }
        val cases = listOf<Pair<String, @Composable () -> Unit>>("celebrate" to { PromiseKeptMoment(SprigForm.BLOOM, 12, onCarryOn = {}) }) +
            EVOLVED_FORMS.map { f -> "evolve ${f.name}" to @Composable { SprigEvolvesContent(f, reachedAt(f), onShare = {}, onCarryOn = {}) } }
        for (s in listOf(1f, 1.3f, 2f)) {
            scale.floatValue = s
            for ((name, content) in cases) {
                screen.value = content
                rule.waitForIdle()
                val tag = if (name == "celebrate") "celebration_headline" else "evolve_headline"
                assertNoWordSplit(rule, tag, "$name$prefix at font $s")
            }
        }
    }
}

/** Like [Shot] at font 1.0, but with motion on. */
@Composable
private fun Moving(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        KeptTheme(reduceMotion = false, content = content)
    }
}
