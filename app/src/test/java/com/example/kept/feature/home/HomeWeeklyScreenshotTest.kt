package com.example.kept.feature.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.example.kept.core.data.WeeklyGoalWeek
import com.example.kept.core.ui.shots.Shot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's weekly card (weekly goals mockup 4; issue #165, story #174) at 412 × 915dp, font scale
 * 1.0 and 2.0: on track, today counts, a complete week, and the "Add a weekly goal" row. Each
 * shot is scrolled so the part it is about is on screen. Goldens:
 * `app/src/test/screenshots/home_weekly_*`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class HomeWeeklyScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, fontScale: Float, s: HomeUiState, scrollTo: String?) {
        rule.setContent { Shot(fontScale) { HomeFrame(s) } }
        rule.waitForIdle()
        if (scrollTo != null) rule.onNodeWithTag(scrollTo).performScrollTo()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/home_weekly_${name}_font${fontScale}.png")
    }

    private fun card(name: String, fontScale: Float, goal: WeeklyGoalWeek) =
        shot(name, fontScale, HomeFixtures.state(2, 1, weekly = listOf(goal)), scrollTo = "home_weekly_card_1")

    @Test fun onTrack() = card("on_track", 1f, HomeFixtures.onTrack())
    @Test fun onTrack_font2() = card("on_track", 2f, HomeFixtures.onTrack())
    @Test fun todayCounts() = card("today_counts", 1f, HomeFixtures.todayCounts())
    @Test fun todayCounts_font2() = card("today_counts", 2f, HomeFixtures.todayCounts())
    @Test fun completeWeek() = card("complete", 1f, HomeFixtures.completeWeek())
    @Test fun completeWeek_font2() = card("complete", 2f, HomeFixtures.completeWeek())

    @Test fun addCard() = shot("add", 1f, HomeFixtures.state(1, 0), scrollTo = "home_weekly_add")
    @Test fun addCard_font2() = shot("add", 2f, HomeFixtures.state(1, 0), scrollTo = "home_weekly_add")

    /**
     * The first partial week: 4 a week, set today (Wednesday), so this week asks for 3 (Thursday to
     * Saturday). The card reads "0 of 3" on three segments, Su to Tu are not counted, no lock today.
     */
    @Test fun firstWeek() = card("first_week", 1f, HomeFixtures.goal(1, "Practice", 4, from = HomeFixtures.today))

    /** Three goals under two habits, as the page opens and scrolled to the last card. */
    @Test fun threeGoals() = shot("three", 1f, HomeFixtures.state(2, 1, weekly = HomeFixtures.threeGoals()), scrollTo = null)
    @Test fun threeGoalsEnd() = shot("three_end", 1f, HomeFixtures.state(2, 1, weekly = HomeFixtures.threeGoals()), scrollTo = "home_shield")

    /** Six habits: the cards end the habit list, which is the part that scrolls. */
    @Test fun compact() = shot("compact", 1f, HomeFixtures.state(6, 2, weekly = listOf(HomeFixtures.onTrack())), scrollTo = "home_weekly_card_1")
    @Test fun compact_font2() = shot("compact", 2f, HomeFixtures.state(6, 2, weekly = listOf(HomeFixtures.onTrack())), scrollTo = "home_weekly_card_1")
    @Test fun compactAdd() = shot("compact_add", 1f, HomeFixtures.state(6, 2), scrollTo = "home_weekly_add")

    /** No habits yet, one goal. */
    @Test fun noHabits() = shot("no_habits", 1f, HomeFixtures.state(0, 0, weekly = listOf(HomeFixtures.todayCounts())), scrollTo = null)
}
