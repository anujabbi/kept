package com.example.kept.core.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kept.core.data.ForegroundSignal
import com.example.kept.core.data.LockState
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.TodaySummary
import com.example.kept.core.data.WeeklyGoalRepository
import com.example.kept.core.data.WeeklyGoalWeek
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.WeeklyGoalEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.WeeklyGoalRules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The must-do notification as it is really posted (issue #165, story #175): the real
 * [KeptNotifications] and [KeptPreferences] under Robolectric. "Once per goal per day" has to
 * hold when the process is restarted in the middle of the day, so a second notifier with fresh
 * objects stands in for the restart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MustDoNotifierTest {

    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val sunday = LocalDate.of(2026, 11, 22)
    private val wednesday = sunday.plusDays(3)
    private val nine = LocalTime.of(9, 0)

    @Before fun setUp() = runBlocking {
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        KeptPreferences(ctx).clearAll()
        nm.cancelAll()
    }

    /** A notifier as a newly started process builds it: nothing carried over in memory. */
    private fun freshNotifier() = MustDoNotifier(KeptPreferences(ctx), KeptNotifications(ctx))

    private fun goal(id: Long, title: String, days: Int, today: LocalDate, vararg sessions: LocalDate) = WeeklyGoalWeek(
        WeeklyGoalEntity(id = id, title = title, iconKey = "run", daysPerWeek = days, createdAt = 0, effectiveFrom = "2026-11-01"),
        WeeklyGoalRules.week(WeeklyGoalRules.Goal(days, LocalDate.of(2026, 11, 1)), sessions.toSet(), today),
    )

    /** 7:00 am to 9:00 pm, no habits left, and [goals]. */
    private fun state(vararg goals: WeeklyGoalWeek) = LockState(
        settings = Settings(onboardingDone = true), today = TodaySummary(emptyList()), breakActiveUntil = null,
        breaksUsedThisWeek = 0, userExceptions = emptySet(), unprotectedToday = false, weekly = goals.toList(),
    )

    private fun mustDo() = nm.activeNotifications.filter { it.id >= KeptNotifications.ID_MUST_DO_BASE }
    private fun title(n: android.service.notification.StatusBarNotification) = n.notification.extras.getCharSequence("android.title").toString()
    private fun text(n: android.service.notification.StatusBarNotification) = n.notification.extras.getCharSequence("android.text").toString()

    @Test fun `example A posts its notification once, with the issue's words`() = runBlocking {
        val notifier = freshNotifier()
        val s = state(goal(1, "Exercise", 4, wednesday))
        notifier.check(s, nine)
        val n = mustDo().single()
        assertEquals("No exercise yet this week", title(n))
        assertEquals("Only 4 days left and you need 4. Your apps are locked until today's session is done.", text(n))
        assertEquals(KeptNotifications.CH_REMINDER, n.notification.channelId)
        assertEquals(mapOf(1L to "2026-11-25"), KeptPreferences(ctx).mustDoNotified())
        // Tick after tick, minute after minute: nothing new. Swiped away, it stays away.
        nm.cancelAll()
        repeat(3) { notifier.check(s, nine.plusMinutes(it.toLong())) }
        assertTrue(mustDo().isEmpty())
    }

    @Test fun `it is not posted again after the process is restarted`() = runBlocking {
        val s = state(goal(1, "Exercise", 4, wednesday))
        freshNotifier().check(s, nine)
        assertEquals(1, mustDo().size)
        nm.cancelAll()
        // A new process: new objects, the same stored mark.
        freshNotifier().check(s, LocalTime.of(13, 0))
        assertTrue("posted a second time after a restart", mustDo().isEmpty())
    }

    @Test fun `it waits for the lock start and is not posted after the give-up time`() = runBlocking {
        val s = state(goal(1, "Exercise", 4, wednesday))
        freshNotifier().check(s, LocalTime.of(6, 59))
        assertTrue(mustDo().isEmpty())
        freshNotifier().check(s, LocalTime.of(21, 0))
        assertTrue(mustDo().isEmpty())
        freshNotifier().check(s, LocalTime.of(7, 0))
        assertEquals(1, mustDo().size)
    }

    @Test fun `logging the session takes it down and an undo does not bring it back`() = runBlocking {
        val notifier = freshNotifier()
        notifier.check(state(goal(1, "Exercise", 4, wednesday)), nine)
        assertEquals(1, mustDo().size)
        notifier.check(state(goal(1, "Exercise", 4, wednesday, wednesday)), nine)
        assertTrue("still up after the session was logged", mustDo().isEmpty())
        notifier.check(state(goal(1, "Exercise", 4, wednesday)), nine)
        assertTrue("posted again after an undo", mustDo().isEmpty())
    }

    @Test fun `the give-up time takes it down`() = runBlocking {
        val notifier = freshNotifier()
        val s = state(goal(1, "Exercise", 4, wednesday))
        notifier.check(s, LocalTime.of(20, 59))
        assertEquals(1, mustDo().size)
        notifier.check(s, LocalTime.of(21, 0))
        assertTrue(mustDo().isEmpty())
    }

    @Test fun `the next must-do day gets its own, progress-aware`() = runBlocking {
        val notifier = freshNotifier()
        notifier.check(state(goal(1, "Exercise", 4, wednesday)), nine)
        notifier.check(state(goal(1, "Exercise", 4, wednesday, wednesday)), nine)
        val thursday = wednesday.plusDays(1)
        notifier.check(state(goal(1, "Exercise", 4, thursday, wednesday)), nine)
        val n = mustDo().single()
        assertEquals("1 of 4 exercise so far this week", title(n))
        assertEquals("Only 3 days left and you need 3. Your apps are locked until today's session is done.", text(n))
        assertEquals(mapOf(1L to "2026-11-26"), KeptPreferences(ctx).mustDoNotified())
    }

    @Test fun `two goals each get their own notification, once`() = runBlocking {
        val notifier = freshNotifier()
        val s = state(goal(1, "Exercise", 4, wednesday), goal(2, "Practice", 7, wednesday, sunday, sunday.plusDays(1), sunday.plusDays(2)))
        notifier.check(s, nine)
        assertEquals(setOf("No exercise yet this week", "3 of 7 practice so far this week"), mustDo().map(::title).toSet())
        assertEquals(mapOf(1L to "2026-11-25", 2L to "2026-11-25"), KeptPreferences(ctx).mustDoNotified())
        // One logged: its notification goes, the other stays.
        notifier.check(state(goal(1, "Exercise", 4, wednesday, wednesday), goal(2, "Practice", 7, wednesday, sunday, sunday.plusDays(1), sunday.plusDays(2))), nine)
        assertEquals(listOf("3 of 7 practice so far this week"), mustDo().map(::title))
    }

    @Test fun `no notification on a day that is not must-do`() = runBlocking {
        freshNotifier().check(state(goal(1, "Exercise", 4, sunday.plusDays(1), sunday)), nine)
        assertTrue(mustDo().isEmpty())
        assertTrue(KeptPreferences(ctx).mustDoNotified().isEmpty())
    }

    @Test fun `with notifications not allowed nothing is marked, so allowing them later still posts today's`() = runBlocking {
        shadowOf(ctx).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val s = state(goal(1, "Exercise", 4, wednesday))
        freshNotifier().check(s, nine)
        assertTrue(KeptPreferences(ctx).mustDoNotified().isEmpty())
        shadowOf(ctx).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        freshNotifier().check(s, LocalTime.of(10, 0))
        assertEquals(1, mustDo().size)
    }

    // --- "All habits done. Apps are open." must not be posted while a session holds the lock ---

    private val db = Room.inMemoryDatabaseBuilder(ctx, KeptDatabase::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    private fun dayCompletion(at: String): NotificationDayCompletion {
        val time = TimeSource(Clock.fixed(Instant.parse(at), ZoneId.of("UTC")))
        return NotificationDayCompletion(
            KeptNotifications(ctx),
            object : ForegroundSignal { override val isForeground = false },
            WeeklyGoalRepository(db.weeklyGoalDao(), db.weeklyGoalEntryDao(), time),
            KeptPreferences(ctx),
            time,
        )
    }

    private fun dayDone() = nm.activeNotifications.any { it.id == KeptNotifications.ID_DAY_DONE }

    @Test fun `the habits finished from the shade on a must-do day do not announce open apps`() = runBlocking {
        db.weeklyGoalDao().insert(WeeklyGoalEntity(title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 0, effectiveFrom = "2026-11-01"))
        dayCompletion("2026-11-25T09:00:00Z").onDayCompleted()
        assertTrue("\"Apps are open\" was posted while the weekly session holds the lock", !dayDone())
    }

    @Test fun `with no must-do session left it is announced as before`() = runBlocking {
        db.weeklyGoalDao().insert(WeeklyGoalEntity(title = "Exercise", iconKey = "run", daysPerWeek = 4, createdAt = 0, effectiveFrom = "2026-11-01"))
        // Monday: 6 days for 4, not a must-do day.
        dayCompletion("2026-11-23T09:00:00Z").onDayCompleted()
        assertTrue(dayDone())
    }
}
