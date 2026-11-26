package com.example.kept.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val D = DaybreakColors

/**
 * A time on a slider (issue #218): [steps] + 1 positions, [index] the one shown. Purple track and
 * thumb on a pale track; the [labels] sit under it at their fraction of the width (the end ones
 * flush with the ends). TalkBack reads [label] and the time ([valueText]), not a percentage.
 */
private val THUMB_W = 30.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlider(
    index: Int,
    steps: Int,
    label: String,
    valueText: String,
    labels: List<Pair<Float, String>>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "time_slider",
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val interaction = remember { MutableInteractionSource() }
        val colors = SliderDefaults.colors(
            thumbColor = D.purple,
            activeTrackColor = D.purple,
            inactiveTrackColor = D.purpleTint,
            activeTickColor = D.purpleDone,
            inactiveTickColor = D.purpleDone,
        )
        Slider(
            value = index.toFloat(),
            onValueChange = { v -> val i = v.roundToInt().coerceIn(0, steps); if (i != index) onChange(i) },
            valueRange = 0f..steps.toFloat(),
            steps = steps - 1,
            interactionSource = interaction,
            colors = colors,
            // A round thumb in a 30 x 48dp box, so the slider is 48dp tall to touch (the default is 44).
            thumb = {
                Box(Modifier.size(width = THUMB_W, height = 48.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(D.white).border(2.dp, D.purple, CircleShape).padding(4.dp).clip(CircleShape).background(D.purple))
                }
            },
            // A thin track with no gap at the thumb, as the mockup draws it.
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                    modifier = Modifier.height(6.dp),
                )
            },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = valueText
            }.testTag(tag),
        )
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("${tag}_labels")) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val halfPx = with(LocalDensity.current) { THUMB_W.toPx() / 2 }
            val gapPx = with(LocalDensity.current) { 6.dp.toPx() }
            val measurer = rememberTextMeasurer()
            val base = DaybreakType.smallLabel.copy(fontWeight = FontWeight.SemiBold)
            // At a large font on a narrow screen the labels step down in size together rather than
            // run into each other, as the lock window's bar labels do.
            val style = remember(widthPx, labels) {
                fun fits(st: androidx.compose.ui.text.TextStyle): Boolean {
                    val xs = labels.map { (at, text) ->
                        val w = measurer.measure(text, st, softWrap = false, maxLines = 1).size.width
                        labelX(at, w, widthPx, halfPx).let { it to it + w }
                    }
                    return xs.zipWithNext().all { (a, b) -> a.second + gapPx <= b.first }
                }
                var k = 1f
                while (k > 0.4f && !fits(base.copy(fontSize = base.fontSize * k, lineHeight = base.lineHeight * k))) k -= 0.05f
                base.copy(fontSize = base.fontSize * k, lineHeight = base.lineHeight * k)
            }
            labels.forEach { (at, text) ->
                Text(
                    text,
                    Modifier.layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0))
                        layout(c.maxWidth, p.height) { p.place(labelX(at, p.width, widthPx, halfPx), 0) }
                    },
                    style = style, color = D.muted, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A label centred under its point on the track (the thumb's centre runs half a thumb in from each end), kept inside the row. */
private fun labelX(at: Float, w: Int, widthPx: Float, halfPx: Float): Int {
    val centre = halfPx + (widthPx - 2 * halfPx) * at
    return (centre - w / 2f).roundToInt().coerceIn(0, (widthPx - w).roundToInt().coerceAtLeast(0))
}

/** A time shown large over its slider: 44/800 with tabular figures. */
val BigTimeStyle = DaybreakType.headline.copy(fontSize = 44.sp, lineHeight = 48.sp, fontFeatureSettings = "tnum")

/** A sentence with its bold parts in ink, as the copy objects split them. */
fun boldParts(parts: List<Pair<String, Boolean>>): AnnotatedString = buildAnnotatedString {
    parts.forEach { (text, bold) -> if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = D.ink)) { append(text) } else append(text) }
}
