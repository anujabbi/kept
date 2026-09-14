package com.example.kept.feature.app

import com.example.kept.core.analytics.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Every navigation destination reports a `$screen` (issue #10).
 *
 * Reflection over [Routes] rather than a hand-written list, so adding a route without naming its
 * screen fails here instead of quietly producing a gap in the funnel.
 */
class ScreenNamesTest {

    private val allRoutes: List<String> = Routes::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.isAccessible = true; it.get(null) as String }

    @Test fun every_route_except_onboarding_has_a_screen_name() {
        val unnamed = allRoutes.filter { it != Routes.ONBOARDING && screenNameFor(it) == null }
        assertEquals("routes with no \$screen_name", emptyList<String>(), unnamed)
        assertNotNull(allRoutes.firstOrNull())
    }

    /** Onboarding reports one screen view per step instead; a second here would double every one. */
    @Test fun onboarding_is_reported_by_the_screen_itself() {
        assertNull(screenNameFor(Routes.ONBOARDING))
    }

    /** The route pattern is what is matched, so no date or row id can ride along on a `$screen`. */
    @Test fun parameterised_routes_are_matched_as_patterns_not_as_filled_routes() {
        assertEquals(Screens.RECAP, screenNameFor(Routes.RECAP))
        assertEquals(Screens.REVEAL, screenNameFor(Routes.REVEAL))
        assertNull(screenNameFor(Routes.recap("2026-09-13")))
        assertNull(screenNameFor(Routes.reveal(7)))
    }

    @Test fun an_unknown_route_is_not_reported() {
        assertNull(screenNameFor(null))
        assertNull(screenNameFor("nowhere"))
    }
}
