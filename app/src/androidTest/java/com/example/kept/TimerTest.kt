package com.example.kept

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.data.HabitActions
import com.example.kept.core.data.HabitEvent
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimerController
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.PointsRules
import com.example.kept.core.domain.ProofType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TimerTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var db: KeptDatabase
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var actions: HabitActions
    @Inject lateinit var sprig: SprigRepository
    @Inject lateinit var timer: TimerController

    @Before fun setUp() { hilt.inject(); resetAppState(db, prefs) }

    @Test fun timer_advances_and_awards_finish_bonus() = runBlocking {
        prefs.updateSettings { it.copy(onboardingDone = true) }
        val id = habits.addHabit("Practice", "music", ProofType.TIMER, 1, "min")

        timer.start(id)
        delay(2_300)
        assertTrue(timer.state.value.unflushedSeconds >= 2)
        timer.pause()
        delay(500)
        val afterPause = habits.observeHabit(id).let { habits.today().habits.first { h -> h.id == id } }
        assertTrue(afterPause.progress >= 2)

        val before = sprig.current().points
        val event = actions.addTimerSeconds(id, 60)
        assertTrue(event is HabitEvent.Completed)
        assertEquals(before + PointsRules.HABIT_BONUS, sprig.current().points)
        assertTrue(habits.today().allDone)
        // Level up because all habits are done.
        assertEquals(2, sprig.current().level)
    }
}
