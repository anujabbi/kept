package com.example.kept.core.domain

import com.example.kept.core.domain.GapEvidence.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapEvidenceTest {

    private val own = "com.example.kept"
    private fun e(kind: Kind, at: Long, pkg: String? = null) = GapEvidence.Event(kind, at, pkg)

    /** Everything that is not KEPT or the dialer is lockable here. */
    private fun fold(vararg events: GapEvidence.Event) =
        GapEvidence.fold(events.toList(), ownPackage = own) { it != own && it != "com.android.dialer" }

    @Test fun `screen on with the keyguard up is not use`() {
        // Waking to check the time never unlocks anything, and the lock screen is not KEPT's fault.
        val use = fold(
            e(Kind.KEYGUARD_SHOWN, 100),
            e(Kind.SCREEN_ON, 200),
            e(Kind.SCREEN_OFF, 300),
        )
        assertEquals(emptyList<Long>(), use.inUseAtMillis)
        assertFalse(use.lockedAppResumed)
    }

    @Test fun `unlocking the phone is use`() {
        val use = fold(e(Kind.SCREEN_ON, 200), e(Kind.KEYGUARD_HIDDEN, 250))
        assertTrue(250L in use.inUseAtMillis)
    }

    @Test fun `screen on while already unlocked is use`() {
        val use = fold(e(Kind.KEYGUARD_HIDDEN, 100), e(Kind.SCREEN_ON, 200))
        assertTrue(200L in use.inUseAtMillis)
    }

    @Test fun `time spent in KEPT itself is not use`() {
        // Opening KEPT to turn the lock back on must not be what marks the day unprotected.
        val use = fold(
            e(Kind.APP_RESUMED, 100, own),
            e(Kind.SCREEN_ON, 150),
            e(Kind.KEYGUARD_HIDDEN, 200),
            e(Kind.APP_RESUMED, 250, own),
        )
        assertEquals(emptyList<Long>(), use.inUseAtMillis)
        assertFalse(use.lockedAppResumed)
    }

    @Test fun `resuming a locked app while unlocked is both use and a locked-app resume`() {
        val use = fold(
            e(Kind.SCREEN_ON, 100),
            e(Kind.KEYGUARD_HIDDEN, 150),
            e(Kind.APP_RESUMED, 200, "com.instagram.android"),
        )
        assertTrue(use.lockedAppResumed)
        assertTrue(200L in use.inUseAtMillis)
    }

    @Test fun `an allowlisted app is use but not a locked-app resume`() {
        val use = fold(
            e(Kind.SCREEN_ON, 100),
            e(Kind.KEYGUARD_HIDDEN, 150),
            e(Kind.APP_RESUMED, 200, "com.android.dialer"),
        )
        assertFalse(use.lockedAppResumed)
        assertTrue(200L in use.inUseAtMillis)
    }

    @Test fun `a locked app resumed behind the keyguard still counts as a locked-app resume`() {
        // Usage events can arrive out of the user's control; the app did come to the foreground.
        val use = fold(e(Kind.KEYGUARD_SHOWN, 100), e(Kind.APP_RESUMED, 200, "com.instagram.android"))
        assertTrue(use.lockedAppResumed)
        assertEquals(emptyList<Long>(), use.inUseAtMillis)
    }

    @Test fun `locking the phone again ends the use`() {
        val use = fold(
            e(Kind.KEYGUARD_HIDDEN, 100),
            e(Kind.SCREEN_OFF, 200),
            e(Kind.KEYGUARD_SHOWN, 210),
            e(Kind.SCREEN_ON, 300),
            e(Kind.APP_RESUMED, 320, "com.instagram.android"),
        )
        assertEquals(listOf(100L), use.inUseAtMillis)
    }

    @Test fun `use timestamps come back in order`() {
        val use = fold(
            e(Kind.KEYGUARD_HIDDEN, 400),
            e(Kind.SCREEN_ON, 100),
            e(Kind.KEYGUARD_HIDDEN, 200),
        )
        assertEquals(use.inUseAtMillis.sorted(), use.inUseAtMillis)
    }
}
