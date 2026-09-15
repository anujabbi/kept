package com.example.kept.feature.settings

import com.example.kept.core.domain.Allowlist
import com.example.kept.core.lock.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of [pickableApps], the pure mapping behind the exceptions picker: which
 * installed apps may be offered, and which of them are flagged exempt.
 *
 * This covers the mapping only. It says nothing about where the exempt set comes from, which is
 * where the real issue-#4 defect lived (`SettingsViewModel` read it from a cold `WhileSubscribed`
 * StateFlow); that is covered on-device by `ExceptionPickerSourceTest`.
 */
class ExceptionPickerTest {

    private val hard = Allowlist.build(listOf("com.google.android.dialer"))

    private val installed = listOf(
        InstalledApp("com.instagram.android", "Instagram"),
        InstalledApp("com.spotify.music", "Spotify"),
        InstalledApp("com.google.android.dialer", "Phone"),
        InstalledApp(Allowlist.OWN_PACKAGE, "KEPT"),
    )

    @Test fun `apps on the hard allowlist are not offered`() {
        val packages = pickableApps(installed, hard, emptySet()).map { it.app.packageName }
        assertEquals(listOf("com.instagram.android", "com.spotify.music"), packages)
    }

    @Test fun `a stored exception renders as allowed`() {
        val picker = pickableApps(installed, hard, setOf("com.spotify.music"))
        assertTrue(picker.single { it.app.packageName == "com.spotify.music" }.allowed)
        assertFalse(picker.single { it.app.packageName == "com.instagram.android" }.allowed)
    }

    @Test fun `an empty exempt set leaves everything locked`() {
        assertTrue(pickableApps(installed, hard, emptySet()).none { it.allowed })
    }

    @Test fun `an app added after the list was built shows as allowed once it is exempt`() {
        val before = pickableApps(installed, hard, emptySet())
        assertFalse(before.single { it.app.packageName == "com.instagram.android" }.allowed)
        val after = pickableApps(installed, hard, setOf("com.instagram.android"))
        assertTrue(after.single { it.app.packageName == "com.instagram.android" }.allowed)
    }

    @Test fun `an exception for an app that is no longer installed is simply not shown`() {
        val picker = pickableApps(installed, hard, setOf("com.uninstalled.app"))
        assertTrue(picker.none { it.app.packageName == "com.uninstalled.app" })
        assertTrue(picker.none { it.allowed })
    }
}
