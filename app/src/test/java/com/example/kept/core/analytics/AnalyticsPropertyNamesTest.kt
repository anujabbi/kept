package com.example.kept.core.analytics

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A privacy guard with teeth: no analytics call site may pass a package name (issue #10).
 *
 * Every privacy surface KEPT ships — the Settings toggle, the README, `docs/privacy-policy.md`,
 * the Play data-safety answers — promises that the apps you open are never sent anywhere. Two
 * captures used to break that promise (`lock_shown.blocked_package` and the `package` property on
 * `exception_added`/`exception_removed`), and nothing but a reviewer's eye would have caught the
 * third. This test reads the app's own source and fails on any property key called `package` or
 * `blocked_package`, or any key ending in `_package`.
 *
 * It is a text scan rather than a runtime assertion on purpose: the leak is in the *source* of a
 * capture, and the one that matters is the one nobody thought to exercise.
 */
class AnalyticsPropertyNamesTest {

    /** `"package" to`, `"blocked_package" to`, `"app_package" to`, … as a map entry or a pair. */
    private val forbiddenKey = Regex("""["'](?:[A-Za-z0-9_]*_)?package["']\s*(?:to\b|,|:|\))""")

    /** A line that is part of an `Analytics`/`capture(`/`screen(`/`register(` call. */
    private val analyticsCall = Regex("""\b(?:capture|screen|register)\s*\(|\bAnalytics\b""")

    @Test fun `no analytics call site passes a package name as a property`() {
        val offenders = mutableListOf<String>()
        mainSources().forEach { file ->
            val text = file.readText()
            if (!analyticsCall.containsMatchIn(text)) return@forEach
            // A capture can span several lines, so look at a window around each analytics call
            // rather than only the line the key sits on.
            val lines = text.lines()
            lines.forEachIndexed { i, line ->
                if (!analyticsCall.containsMatchIn(line)) return@forEachIndexed
                val window = lines.subList(i, minOf(lines.size, i + WINDOW_LINES)).joinToString("\n")
                forbiddenKey.find(window)?.let { m ->
                    offenders += "${file.path}:${i + 1}: ${m.value.trim()} near `${line.trim()}`"
                }
            }
        }
        assertTrue(
            "Analytics must never carry a package name. Offending call sites:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun `the scan actually reads the app source`() {
        // Guards the guard: a wrong working directory would make the test above pass vacuously.
        val files = mainSources()
        assertTrue("no Kotlin sources found under src/main", files.size > 50)
        assertTrue(
            "the analytics wrapper itself was not scanned",
            files.any { it.name == "Analytics.kt" },
        )
    }

    @Test fun `the pattern catches the leaks this test was written for`() {
        val samples = listOf(
            """analytics.capture("lock_shown", mapOf("blocked_package" to fg))""",
            """analytics.capture("exception_added", mapOf("package" to app.packageName))""",
            """analytics.capture("x", mapOf("target_package" to pkg))""",
        )
        samples.forEach { assertTrue(it, forbiddenKey.containsMatchIn(it)) }
        // `packageName` as a *value*, and a local named package, are not property keys.
        assertTrue(!forbiddenKey.containsMatchIn("""capture("lock_shown", mapOf("minutes_to_due" to n))"""))
    }

    private fun mainSources(): List<File> =
        mainSourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun mainSourceRoot(): File =
        listOf("src/main/java", "app/src/main/java", "../app/src/main/java")
            .map(::File)
            .firstOrNull { it.isDirectory }
            ?: error("cannot find src/main/java from ${File("").absolutePath}")

    private companion object {
        const val WINDOW_LINES = 8
    }
}
