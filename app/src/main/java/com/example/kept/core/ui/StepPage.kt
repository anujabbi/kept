package com.example.kept.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The onboarding page frame (`A10`–`A13`), shared with the Settings pages that reuse its
// bodies (`A12-Exceptions-settings`, `A13-Permissions-settings`): a header with a back arrow
// (and, in onboarding, the 5-segment progress bar), a page title and line, a scrolling body and
// a fixed footer over a 1.5dp rule.

private val D = DaybreakColors

/** What sits at the top of a page. */
sealed interface PageHeader {
    val onBack: () -> Unit

    /** Onboarding: back arrow and the progress bar at [step] of [total]. */
    data class Progress(val step: Int, val total: Int, override val onBack: () -> Unit) : PageHeader

    /** A Settings page: the back arrow alone. */
    data class Back(override val onBack: () -> Unit) : PageHeader
}

/** The header row: padding `24 20 4 12`, a 44dp back arrow (48 to touch), then the bar. */
@Composable
fun PageHeaderRow(header: PageHeader, modifier: Modifier = Modifier, color: Color = D.ink) {
    Row(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 20.dp, top = 22.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = null, onClick = header.onBack)
                .semantics { contentDescription = "Back" }
                .testTag("page_back"),
            contentAlignment = Alignment.Center,
        ) { Icon(DaybreakIcons.Back, null, Modifier.size(22.dp), tint = color) }
        if (header is PageHeader.Progress) StepProgress(header.step, header.total, Modifier.weight(1f))
    }
}

/** Five 6dp segments, 6dp apart: purple up to [step], `#E6DCCE` after. */
@Composable
fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Step $step of $total" }.testTag("step_progress"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < step) D.purple else D.field))
        }
    }
}

/** Title (34/800, never split inside a word) and its line (15/1.4, muted): padding `6 24 18`. */
@Composable
fun PageTitle(title: String, line: String?, width: Dp, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 18.dp)) {
        Text(title, Modifier.testTag("page_title"), style = fitLongestWord(title, DaybreakType.pageTitle, width - 48.dp), color = D.ink)
        if (line != null) {
            Text(
                line,
                Modifier.padding(top = 8.dp),
                style = DaybreakType.body,
                color = D.muted,
            )
        }
    }
}

/** The fixed footer: a 1.5dp `#EFE4D6` rule on top, padding `12 20 28`, 8dp between its parts. */
@Composable
fun StepFooter(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(D.cream)
            .drawBehind { drawLine(D.line, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx()) }
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * A cream page: [header], the title block, the [body] (a lazy list with 20dp sides) and an
 * optional fixed [footer]. The title stays put above the list as in the design; from font
 * scale 1.5 it scrolls with the list, so a small screen at a large font keeps room for the body.
 */
@Composable
fun StepPage(
    header: PageHeader,
    title: String,
    line: String?,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    bodySpacing: Dp = 8.dp,
    body: LazyListScope.() -> Unit,
) {
    val pinned = LocalDensity.current.fontScale < 1.5f
    BoxWithConstraints(modifier.fillMaxSize().background(D.cream)) {
        val width = maxWidth
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PageHeaderRow(header)
            if (pinned) PageTitle(title, line, width)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("page_body"),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(bodySpacing),
            ) {
                if (!pinned) item(key = "page_title") { PageTitleInList(title, line, width) }
                body()
            }
            if (footer != null) StepFooter(content = footer)
            else Box(Modifier.navigationBarsPadding())
        }
    }
}

/** The title block inside the list, which already has 20dp sides: 4dp more to reach the design's 24. */
@Composable
private fun PageTitleInList(title: String, line: String?, width: Dp) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
        Text(title, Modifier.testTag("page_title"), style = fitLongestWord(title, DaybreakType.pageTitle, width - 48.dp), color = D.ink)
        if (line != null) Text(line, Modifier.padding(top = 8.dp), style = DaybreakType.body, color = D.muted)
    }
}
