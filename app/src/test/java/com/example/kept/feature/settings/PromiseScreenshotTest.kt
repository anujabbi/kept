package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The promise (#218) at 412 × 915 dp, font scale 1.0 and 2.0: Exercise 4 days a week, Read every day, each
 * list open, the weekly choices off at three goals, "Something else…", a photo habit, the last
 * habit, and Sprig's cheer. Goldens: `app/src/test/screenshots/promise_*`. Also checks every
 * state fits ([PromiseShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class PromiseScreenshotTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { PromiseShots(rule, "") }

    @Test fun exercise() = shots.shot("exercise", 1f)
    @Test fun exercise_font2() = shots.shot("exercise", 2f)
    @Test fun readDaily() = shots.shot("read_daily", 1f)
    @Test fun readDaily_font2() = shots.shot("read_daily", 2f)
    @Test fun activityList() = shots.shot("activity_list", 1f)
    @Test fun daysList() = shots.shot("days_list", 1f)
    @Test fun daysList_font2() = shots.shot("days_list", 2f)
    @Test fun unitList() = shots.shot("unit_list", 1f)
    @Test fun limit() = shots.shot("limit", 1f)
    @Test fun limit_font2() = shots.shot("limit", 2f)
    @Test fun custom() = shots.shot("custom", 1f)
    @Test fun custom_font2() = shots.shot("custom", 2f)
    @Test fun typing() = shots.shot("typing", 1f)
    @Test fun photo() = shots.shot("photo", 1f)
    @Test fun editLast() = shots.shot("edit_last", 1f)
    @Test fun editLast_font2() = shots.shot("edit_last", 2f)
    @Test fun cheer() = shots.shot("cheer", 1f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
