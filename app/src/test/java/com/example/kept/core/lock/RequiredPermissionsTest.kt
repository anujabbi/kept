package com.example.kept.core.lock

import com.example.kept.core.lock.PermissionKind.NOTIFICATIONS
import com.example.kept.core.lock.PermissionKind.OVERLAY
import com.example.kept.core.lock.PermissionKind.USAGE_ACCESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The onboarding Continue gate and its warning line as a pure function of the three required
 * permissions (issue #40). `Permissions` is a concrete Android class, so this is the only place
 * the three-permission matrix can be checked deterministically.
 */
class RequiredPermissionsTest {

    private fun missing(usage: Boolean, overlay: Boolean, notifications: Boolean) =
        RequiredPermissions.missing(RequiredPermissions.onboardingGate) {
            when (it) {
                USAGE_ACCESS -> usage
                OVERLAY -> overlay
                NOTIFICATIONS -> notifications
                else -> error("not part of the gate: $it")
            }
        }

    @Test fun `all three granted reads Continue with no warning`() {
        val m = missing(usage = true, overlay = true, notifications = true)
        assertTrue(m.isEmpty())
        assertEquals("Continue", RequiredPermissions.continueLabel(m))
        assertNull(RequiredPermissions.onboardingWarning(m))
    }

    @Test fun `notifications alone denied gates the button and names notifications only`() {
        val m = missing(usage = true, overlay = true, notifications = false)
        assertEquals(listOf(NOTIFICATIONS), m)
        assertEquals("Continue without the lock", RequiredPermissions.continueLabel(m))
        val warning = RequiredPermissions.onboardingWarning(m)!!
        assertTrue(warning, warning.contains("notifications"))
        assertFalse(warning, warning.contains("usage access"))
        assertFalse(warning, warning.contains("display over other apps"))
        // The lock itself still runs without this one, so the line must not claim nothing locks.
        assertFalse(warning, warning.contains("nothing locks"))
    }

    @Test fun `usage access and notifications denied names both`() {
        val m = missing(usage = false, overlay = true, notifications = false)
        assertEquals(listOf(USAGE_ACCESS, NOTIFICATIONS), m)
        assertEquals("Continue without the lock", RequiredPermissions.continueLabel(m))
        val warning = RequiredPermissions.onboardingWarning(m)!!
        assertTrue(warning, warning.contains("usage access and notifications"))
        assertTrue(warning, warning.contains("nothing locks"))
        assertFalse(warning, warning.contains("display over other apps"))
    }

    @Test fun `all three denied lists them in display order`() {
        val m = missing(usage = false, overlay = false, notifications = false)
        assertEquals(listOf(USAGE_ACCESS, OVERLAY, NOTIFICATIONS), m)
        assertEquals("usage access, display over other apps and notifications", RequiredPermissions.names(m))
    }

    @Test fun `the lock gate is usage access and overlay only`() {
        // Decision (issue #40): a denied notification permission is not a protection gap, the
        // foreground service still enforces the lock without its visible notification.
        assertEquals(listOf(USAGE_ACCESS, OVERLAY), RequiredPermissions.lockGate)
        assertFalse(NOTIFICATIONS in RequiredPermissions.lockGate)
    }

    @Test fun `home lock-off copy names the missing lock permissions`() {
        assertEquals(
            "Usage access is off. Today won't count until it's back on.",
            RequiredPermissions.lockOffBody(listOf(USAGE_ACCESS)),
        )
        assertEquals(
            "Display over other apps is off. Today won't count until it's back on.",
            RequiredPermissions.lockOffBody(listOf(OVERLAY)),
        )
        assertEquals(
            "Usage access and display over other apps are off. Today won't count until they're back on.",
            RequiredPermissions.lockOffBody(listOf(USAGE_ACCESS, OVERLAY)),
        )
    }
}
