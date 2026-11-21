package com.example.kept.core.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * For a test's `@After`: waits until every coroutine still running on this [ApplicationScope] has
 * finished, then cancels the scope, so no work from one test can land in the next (issue #146).
 *
 * Each Robolectric test gets a fresh Hilt graph, and with it a fresh application scope, but
 * `KeptPreferences` sits on a `preferencesDataStore` delegate, which is one instance for the whole
 * test JVM. A tick on the lock screen runs `HabitActions.complete` and `claimMoment` on the
 * application scope, and nothing waited for it: a test could pass and end while that write was
 * still in flight, and the write then landed after the next test's `setUp` had reset
 * `celebrationShownDate`. The next test's day then counted as already celebrated, its moment never
 * came up, and its `waitUntil` timed out after 10 s.
 *
 * Waits for the work itself, not for wall-clock time. [timeoutMillis] is only a backstop: work that
 * never ends (a `collect` left running) fails the test here, by name, instead of hanging the run.
 */
fun CoroutineScope.drainAndCancel(timeoutMillis: Long = 10_000) {
    val job = coroutineContext.job
    try {
        runBlocking {
            withTimeout(timeoutMillis) {
                // A child can launch more work as it finishes, so wait until a pass finds none running.
                while (true) {
                    val running = job.children.filter { it.isActive }.toList()
                    if (running.isEmpty()) break
                    running.joinAll()
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        val stuck = job.children.filter { it.isActive }.toList()
        throw AssertionError("application-scope work still running after $timeoutMillis ms: $stuck", e)
    } finally {
        job.cancel()
    }
}
