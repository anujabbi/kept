package com.example.kept.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Weekly looks are removed (issue #125): no screen may mention a weekly look or name one of the
 * old weekly colours. A text scan over every string literal in the app's source and its string
 * resources, so a stray "this week's look" fails here rather than on a phone.
 *
 * The word "look" on its own is allowed: the spec's Recap "Share this look" button means Sprig's
 * appearance, not a weekly look (see DECISIONS.md, issue #125).
 */
class NoWeeklyLookCopyTest {

    private val weeklyLookPhrase = Regex(
        """week['’]s look|looks collected|looks you collected|look,? only this week|\blook ·""",
        RegexOption.IGNORE_CASE,
    )
    private val variantName = Regex("""\b(?:Lilac|Ember|Tide|Moss|Frost|Honey|Rose|Ink|Mint|Nova|Royal|Beat)\b""")

    /** Double-quoted Kotlin string literals on one line, and the text of `<string>` resources. */
    private val kotlinLiteral = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val xmlString = Regex("""<string[^>]*>(.*?)</string>""")

    @Test fun `no string in the app mentions a weekly look or names a weekly variant`() {
        val offenders = mutableListOf<String>()
        val root = mainRoot()
        root.resolve("java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@forEachIndexed
                kotlinLiteral.findAll(line).map { it.groupValues[1] }.forEach { text ->
                    if (weeklyLookPhrase.containsMatchIn(text) || variantName.containsMatchIn(text)) offenders += "${file.name}:${i + 1}: \"$text\""
                }
            }
        }
        root.resolve("res").walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { file ->
            xmlString.findAll(file.readText()).map { it.groupValues[1] }.forEach { text ->
                if (weeklyLookPhrase.containsMatchIn(text) || variantName.containsMatchIn(text)) offenders += "${file.name}: \"$text\""
            }
        }
        assertTrue("Copy still mentions a weekly look:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** The patterns catch every phrase the old copy used, and leave Recap's "Share this look" alone. */
    @Test fun `the patterns catch the old weekly copy but not Sprig's look`() {
        listOf(
            "This week's look: Lilac", "3 of 7 looks collected", "Looks you collected",
            "in this week's look only", "Ember look, only this week.", "Tide look · 5 Sep",
        ).forEach { assertTrue(it, weeklyLookPhrase.containsMatchIn(it) || variantName.containsMatchIn(it)) }
        listOf("Share this look", "See my Sprigs", "Sprig became Bloom").forEach {
            assertFalse(it, weeklyLookPhrase.containsMatchIn(it) || variantName.containsMatchIn(it))
        }
    }

    private fun mainRoot(): File =
        listOf("src/main", "app/src/main", "../app/src/main")
            .map(::File)
            .firstOrNull { it.resolve("java").isDirectory }
            ?: error("cannot find src/main from ${File("").absolutePath}")
}
