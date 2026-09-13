package com.example.kept

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kept.core.lock.InstalledAppsSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Issue #4: the launchable set was cached for five minutes and nothing ever called
 * [InstalledAppsSource.invalidate], so a newly installed app stayed invisible to the lock — it
 * could not be intercepted, and the exceptions picker and the lock disagreed about which apps even
 * exist. The watcher service now invalidates on PACKAGE_ADDED/REPLACED/REMOVED/CHANGED; this pins
 * what invalidating has to do.
 *
 * Identity (`assertSame` / `assertNotSame`) is the check that matters: `launchablePackages()`
 * builds a fresh Set on every real query, so the same instance coming back proves the cache was
 * served and a different instance proves the device was queried again.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InstalledAppsInvalidationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @Inject lateinit var apps: InstalledAppsSource

    @Before fun setUp() = hilt.inject()

    @Test fun repeated_reads_are_served_from_the_cache() {
        val first = apps.launchablePackages()
        assertTrue(first.isNotEmpty())
        assertSame(first, apps.launchablePackages())
    }

    @Test fun invalidating_forces_the_next_read_to_query_the_device_again() {
        val before = apps.launchablePackages()
        apps.invalidate()
        val after = apps.launchablePackages()
        assertNotSame("invalidate() must drop the cache, not just mark it", before, after)
        // Nothing was installed or removed in between, so the content is the same; only the
        // caching changed.
        assertEquals(before, after)
        // and the re-read is cached again from there
        assertSame(after, apps.launchablePackages())
    }

    @Test fun invalidating_bumps_the_revision_screens_listen_to() = runBlocking {
        val before = apps.revision.first()
        apps.invalidate()
        assertEquals(before + 1, apps.revision.first())
        apps.invalidate()
        assertEquals(before + 2, apps.revision.first())
    }
}
