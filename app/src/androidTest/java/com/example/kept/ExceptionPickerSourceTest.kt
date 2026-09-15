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
}
