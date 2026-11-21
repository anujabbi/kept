package com.example.kept.core.di

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [drainAndCancel] is what stops a lock-screen tick's write from landing in the next test
 * (issue #146). These pin down that it really waits for the work, nested work included, and that
 * work which never ends fails loudly rather than leaking.
 */
class ApplicationScopeDrainTest {

    private fun appScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test fun waits_for_work_in_flight_before_returning() {
        val scope = appScope()
        val gate = CompletableDeferred<Unit>()
        val writes = AtomicInteger()
        // Stands in for the claim: still suspended when the test body ends.
        scope.launch { gate.await(); writes.incrementAndGet() }
        scope.launch { gate.complete(Unit) }
        scope.drainAndCancel()
        assertEquals("the write landed before drain returned", 1, writes.get())
        assertFalse("and the scope is cancelled", scope.isActive)
    }

    @Test fun waits_for_work_launched_by_work_that_is_finishing() {
        val scope = appScope()
        val writes = AtomicInteger()
        scope.launch { scope.launch { writes.incrementAndGet() } }
        scope.drainAndCancel()
        assertEquals(1, writes.get())
    }

    @Test fun work_that_never_ends_fails_the_test_and_is_cancelled() {
        val scope = appScope()
        val forever = scope.launch { awaitCancellation() }
        try {
            scope.drainAndCancel(timeoutMillis = 200)
            fail("a coroutine that never ends must not pass silently")
        } catch (e: AssertionError) {
            assertTrue(e.message!!, e.message!!.contains("still running"))
        }
        runBlocking { forever.join() }
        assertTrue("cancelled, not left running into the next test", forever.isCancelled)
        assertFalse(scope.coroutineContext.job.isActive)
    }

    @Test fun an_idle_scope_returns_at_once() {
        val scope = appScope()
        scope.drainAndCancel()
        assertFalse(scope.isActive)
    }
}
