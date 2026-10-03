package com.example.kept

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.analytics.NoOpAnalytics
import com.example.kept.core.data.DayRepository
import com.example.kept.core.data.HabitRepository
import com.example.kept.core.data.LockRepository
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.Allowlist
import com.example.kept.core.lock.AllowlistResolver
import com.example.kept.core.lock.InstalledApp
import com.example.kept.core.lock.InstalledAppsSource
import com.example.kept.core.lock.Permissions
import com.example.kept.core.notify.ReminderPoster
import com.example.kept.core.work.WorkScheduler
import com.example.kept.feature.settings.PickableApp
import com.example.kept.feature.settings.SettingsViewModel
import com.example.kept.feature.settings.orderForPicker
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Where the exceptions picker gets its exempt set from (issue #4).
 *
 * `SettingsViewModel.loadApps` used to read it from `state.value` — a `WhileSubscribed` StateFlow.
 * On the standalone Exceptions route nothing collects `state`, so that value is still the empty
 * initial `SettingsUi()` and every app rendered as un-exempt however many exceptions were stored.
 * These tests deliberately never touch `vm.state`, which is exactly the condition that used to
 * break, and drive the picker only through the repository.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExceptionPickerSourceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var ctx: Context
    @Inject lateinit var db: KeptDatabase
    @Inject lateinit var prefs: KeptPreferences
    @Inject lateinit var lockRepo: LockRepository
    @Inject lateinit var habitsRepo: HabitRepository
    @Inject lateinit var sprigRepo: SprigRepository
    @Inject lateinit var dayRepo: DayRepository
    @Inject lateinit var appsSource: InstalledAppsSource
    @Inject lateinit var allowlist: AllowlistResolver
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var reminders: ReminderPoster
    @Inject lateinit var time: TimeSource
    @Inject lateinit var permissions: Permissions

    @Before fun setUp() {
        hilt.inject()
        resetAppState(db, prefs)
    }

    private fun viewModel() = SettingsViewModel(
        ctx, prefs, habitsRepo, lockRepo, sprigRepo, dayRepo, appsSource, allowlist, scheduler, reminders, time, permissions,
        // Nothing reported: this test only cares where the picker reads its exempt set from.
        NoOpAnalytics(),
    )

    /** Any launchable app on this device that the user is actually allowed to exempt. */
    private suspend fun someExemptableApp(): InstalledApp? =
        Allowlist.pickable(appsSource.listLaunchable(), { it.packageName }, allowlist.hardAllowlist()).firstOrNull()

    private suspend fun SettingsViewModel.awaitPicker(predicate: (List<PickableApp>) -> Boolean) =
        withTimeout(10_000) { apps.filterNotNull().first(predicate) }

    @Test fun an_exception_already_in_the_repository_shows_as_allowed_without_state_being_collected(): Unit = runBlocking {
        val target = someExemptableApp()
        assumeTrue("no exemptable app installed on this device", target != null)
        lockRepo.addException(target!!.packageName, target.label)

        val vm = viewModel()
        vm.loadApps().join()

        // Note: vm.state is never read here. That is the whole point.
        val picker = vm.awaitPicker { list -> list.any { it.app.packageName == target.packageName } }
        val row = picker.single { it.app.packageName == target.packageName }
        org.junit.Assert.assertTrue("a stored exception must render as allowed", row.allowed)
        org.junit.Assert.assertTrue("other apps stay locked", picker.filter { it.app.packageName != target.packageName }.none { it.allowed })
    }

    @Test fun with_no_exceptions_stored_nothing_shows_as_allowed(): Unit = runBlocking {
        val target = someExemptableApp()
        assumeTrue("no exemptable app installed on this device", target != null)

        val vm = viewModel()
        vm.loadApps().join()

        val picker = vm.awaitPicker { it.isNotEmpty() }
        org.junit.Assert.assertTrue(picker.none { it.allowed })
    }

    /**
     * The picker follows the repository rather than a local optimistic edit, so a reload landing
     * after a write cannot flip a toggle back.
     */
    @Test fun toggling_through_the_view_model_is_reflected_from_the_repository(): Unit = runBlocking {
        val target = someExemptableApp()
        assumeTrue("no exemptable app installed on this device", target != null)

        val vm = viewModel()
        vm.loadApps().join()
        vm.awaitPicker { list -> list.any { it.app.packageName == target!!.packageName } }

        vm.toggleException(target!!, true).join()
        vm.awaitPicker { list -> list.single { it.app.packageName == target.packageName }.allowed }

        // A reload racing the write must not revert it: the exempt set is never held locally.
        vm.loadApps().join()
        vm.awaitPicker { list -> list.single { it.app.packageName == target.packageName }.allowed }

        vm.toggleException(target, false).join()
        vm.awaitPicker { list -> !list.single { it.app.packageName == target.packageName }.allowed }
    }

    /** An exception written straight to the repository reaches an already-built picker. */
    @Test fun an_exception_written_while_the_picker_is_open_reaches_it(): Unit = runBlocking {
        val target = someExemptableApp()
        assumeTrue("no exemptable app installed on this device", target != null)

        val vm = viewModel()
        vm.loadApps().join()
        vm.awaitPicker { list -> list.any { it.app.packageName == target!!.packageName } }

        lockRepo.addException(target!!.packageName, target.label)
        vm.awaitPicker { list -> list.single { it.app.packageName == target.packageName }.allowed }
    }

    /**
     * Switched-on rows stay pinned at the top (issue #39): what the screen shows is
     * [orderForPicker] over the picker the view model emits, so after a toggle through the
     * view model the toggled rows come first, alphabetical among themselves, and a search query
     * keeps them first within its matches.
     */
    @Test fun toggled_on_rows_come_first_in_picker_order_and_stay_there_under_a_query(): Unit = runBlocking {
        val pickable = Allowlist.pickable(appsSource.listLaunchable(), { it.packageName }, allowlist.hardAllowlist())
            .sortedBy { it.label.lowercase() }
        assumeTrue("need at least three exemptable apps on this device", pickable.size >= 3)
        // The last two alphabetically, so pinning them is visible: unsorted they would be at the bottom.
        val (a, b) = pickable.takeLast(2)

        val vm = viewModel()
        vm.loadApps().join()
        vm.awaitPicker { list -> list.any { it.app.packageName == a.packageName } }
        org.junit.Assert.assertEquals(
            "before any toggle the order is alphabetical",
            pickable.map { it.packageName },
            orderForPicker(vm.apps.value!!, "").map { it.app.packageName },
        )

        vm.toggleException(b, true).join()
        var ordered = orderForPicker(vm.awaitPicker { list -> list.single { it.app.packageName == b.packageName }.allowed }, "")
        org.junit.Assert.assertEquals("the toggled row is first", b.packageName, ordered.first().app.packageName)

        vm.toggleException(a, true).join()
        ordered = orderForPicker(vm.awaitPicker { list -> list.single { it.app.packageName == a.packageName }.allowed }, "")
        org.junit.Assert.assertEquals("both on rows are first, alphabetical among themselves", listOf(a.packageName, b.packageName), ordered.take(2).map { it.app.packageName })
        org.junit.Assert.assertTrue("the locked rows after them are alphabetical", ordered.drop(2).map { it.app.label.lowercase() }.let { it == it.sorted() })

        // A query that matches the on row and others keeps the on row first within the matches.
        val q = b.label.first().toString()
        val filtered = orderForPicker(vm.apps.value!!, q)
        org.junit.Assert.assertTrue(filtered.all { it.app.label.contains(q, ignoreCase = true) })
        org.junit.Assert.assertTrue(filtered.takeWhile { it.allowed }.map { it.app.packageName }.containsAll(listOf(a, b).filter { it.label.contains(q, ignoreCase = true) }.map { it.packageName }))

        vm.toggleException(b, false).join()
        ordered = orderForPicker(vm.awaitPicker { list -> !list.single { it.app.packageName == b.packageName }.allowed }, "")
        org.junit.Assert.assertEquals("switched off, it is back at its alphabetical place", b.packageName, ordered.last().app.packageName)
    }
}
