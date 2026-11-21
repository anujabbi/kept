package com.example.kept.core.ui.shots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue

/**
 * Every line of the text tagged [tag] ends between words, never inside one ("Instagra|m"), and
 * nothing is cut off. [at] names the case in the failure message.
 */
fun assertNoWordSplit(rule: ComposeContentTestRule, tag: String, at: String) {
    val result = mutableListOf<TextLayoutResult>()
    rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(result)
    val r = result.single()
    val text = r.layoutInput.text.text
    val lines = (0 until r.lineCount).map { text.substring(r.getLineStart(it), r.getLineEnd(it)) }
    for (i in 0 until r.lineCount - 1) {
        val end = r.getLineEnd(i)
        val between = end >= text.length || text[end - 1] == ' ' || text[end] == ' '
        assertTrue("$at: \"$text\" breaks inside a word: $lines", between)
    }
    assertTrue("$at: \"$text\" is cut off: $lines", r.getLineEnd(r.lineCount - 1) == text.length)
}
