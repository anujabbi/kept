package com.example.kept.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Your habits and Add a habit in Daybreak (`A8-Habits`, `A9-Habit-edit`) at 360 × 640 dp, font
 * scale 1.0 and 2.0: the list with 6 habits and at 10, Add a habit empty, with a custom name and
 * its unit chips, with the photo on, and Edit habit (and the last habit, which cannot be
 * removed). Goldens: `app/src/test/screenshots/habits_360_*`. Also checks every state fits
 * (see [HabitsShots.everyStateFits]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h640dp-420dpi")
class HabitsNarrowScreenTest {

    @get:Rule val rule = createComposeRule()
    private val shots by lazy { HabitsShots(rule, "_360") }

    @Test fun list() = shots.shot("list", 1f)
    @Test fun listLimit() = shots.shot("list_limit", 1f)
    @Test fun listLimit_font2() = shots.shot("list_limit", 2f)
    @Test fun add() = shots.shot("add", 1f)
    @Test fun addCustom() = shots.shot("add_custom", 1f)
    @Test fun addCustom_font2() = shots.shot("add_custom", 2f)
    @Test fun addPhoto() = shots.shot("add_photo", 1f)
    @Test fun addPhoto_font2() = shots.shot("add_photo", 2f)
    @Test fun edit() = shots.shot("edit", 1f)
    @Test fun edit_font2() = shots.shot("edit", 2f)
    @Test fun editLast() = shots.shot("edit_last", 1f)
    @Test fun editLast_font2() = shots.shot("edit_last", 2f)

    @Test fun everyStateFits() = shots.everyStateFits()
}
