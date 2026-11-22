package com.example.kept.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kept.core.domain.HabitDraft
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.OnboardingCopy
import com.example.kept.core.domain.ReadySprig
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.WelcomeLayout
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.HabitIcons
import com.example.kept.core.ui.HabitTemplate
import com.example.kept.core.ui.HabitTemplates
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.PageHeader
import com.example.kept.core.ui.PageHeaderRow
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.QuietButton
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.StepFooter
import com.example.kept.core.ui.StepPage
import com.example.kept.core.ui.dashedBorder
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.SprigView
import com.example.kept.feature.settings.HabitEditPage
import kotlin.math.ceil

private val D = DaybreakColors

// ---------------------------------------------------------------------------------------------
// Welcome (A1)

/**
 * The first screen (`A1-Welcome`): dawn sky, "KEPT", the headline and its line; Sprig (Bud, in
 * its scarf, waving) rising over the cream hill; "Pick my habits" and the caption at the bottom.
 * Sizes come from the real screen ([WelcomeLayout]); on a small screen at a large font the page
 * grows and scrolls rather than letting Sprig overlap the text.
 */
@Composable
internal fun WelcomeStep(onStart: () -> Unit) {
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var textPx by remember { mutableIntStateOf(0) }
    var buttonsPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Sky.dawn).testTag("onboarding_welcome")) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value - top.value - bottom.value
        val textH = ceil(with(density) { textPx.toDp() }.value)
        val buttonsH = ceil(with(density) { buttonsPx.toDp() }.value)
        val spec = WelcomeLayout.spec(screenW, screenH, WelcomeLayout.TEXT_TOP_DP + textH, buttonsH)
        val page = top + spec.pageDp.dp + bottom
        val skyHeight = top + spec.crestDp.dp + Hill.RISE
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SkyScaffold(
                sky = Sky.dawn,
                skyHeight = skyHeight,
                modifier = Modifier.fillMaxWidth().height(page),
                skyContent = {
                    Spacer(Modifier.offset(y = top + spec.crestDp.dp).size(0.dp).testTag("welcome_crest"))
                    SprigView(
                        SprigForm.BUD,
                        SprigPose.WAVE,
                        Modifier.offset(x = spec.sprigLeftDp.dp, y = top + spec.sprigTopDp.dp).size(spec.sprigDp.dp).testTag("welcome_sprig"),
                    )
                    WelcomeText(
                        (screenW - 2 * WelcomeLayout.TEXT_SIDE_DP).dp,
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                            .padding(top = top + WelcomeLayout.TEXT_TOP_DP.dp, start = WelcomeLayout.TEXT_SIDE_DP.dp, end = WelcomeLayout.TEXT_SIDE_DP.dp)
                            .onSizeChanged { textPx = it.height },
                    )
                },
            ) {
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth()
                        .padding(start = WelcomeLayout.TEXT_SIDE_DP.dp, end = WelcomeLayout.TEXT_SIDE_DP.dp, bottom = bottom + WelcomeLayout.BOTTOM_DP.dp)
                        .onSizeChanged { buttonsPx = it.height }
                        .testTag("welcome_buttons"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrimaryButton(
                        OnboardingCopy.WELCOME_BUTTON,
                        onClick = onStart,
                        modifier = Modifier.heightIn(min = 60.dp).testTag("welcome_start"),
                        textStyle = DaybreakType.button.copy(fontSize = 18.sp, lineHeight = 23.sp),
                    )
                    Text(
                        OnboardingCopy.WELCOME_CAPTION,
                        Modifier.testTag("welcome_caption"),
                        style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.ink, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** "KEPT" (16/800, +0.04em), the headline (52/0.96, never split inside a word) and the line. */
@Composable
private fun WelcomeText(width: Dp, modifier: Modifier = Modifier) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            OnboardingCopy.BRAND,
            style = DaybreakType.rowTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.04.em),
            color = D.ink,
        )
        val headline = DaybreakType.display.copy(letterSpacing = (-0.025).em)
        Text(
            OnboardingCopy.WELCOME_HEADLINE,
            Modifier.testTag("welcome_headline"),
            style = fitLongestWord(OnboardingCopy.WELCOME_HEADLINE, headline, width),
            color = D.ink,
        )
        Text(
            OnboardingCopy.WELCOME_LINE,
            Modifier.widthIn(max = 310.dp).testTag("welcome_line"),
            style = DaybreakType.body.copy(fontSize = 17.sp, lineHeight = (17 * 1.4).sp),
            color = D.ink,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Step 1: habits (A10)

/**
 * "What will you do every day?" (`A10-Onb-Habits`): the six presets and any habit added with
 * "Add your own", each toggled by a tap (purple ring and check when picked). Continue is
 * disabled with none picked and the footer counts the picks. At [HabitLimits.MAX_HABITS] the
 * "Add your own" row gives way to [HabitLimits.LIMIT_REACHED].
 */
@Composable
internal fun HabitsStep(
    s: OnboardingState,
    onToggle: (HabitTemplate) -> Unit,
    onAddOwn: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val custom = s.chosen.filter { t -> HabitTemplates.list.none { it.title == t.title } }
    StepPage(
        header = PageHeader.Progress(OnboardingStep.HABITS, OnboardingCopy.STEPS, onBack),
        title = OnboardingCopy.HABITS_TITLE,
        line = OnboardingCopy.HABITS_LINE,
        modifier = Modifier.testTag("onboarding_habits"),
        footer = {
            PrimaryButton(OnboardingCopy.CONTINUE, onClick = onContinue, enabled = s.chosen.isNotEmpty(), modifier = Modifier.testTag("onboarding_next"))
            Text(
                OnboardingCopy.habitsPicked(s.chosen.size),
                Modifier.testTag("onboarding_habit_count"),
                style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted, textAlign = TextAlign.Center,
            )
        },
    ) {
        (HabitTemplates.list + custom).forEach { t ->
            item(key = "habit_${t.title}") {
                PresetRow(t, picked = s.chosen.any { it.title == t.title }, onToggle = { onToggle(t) })
            }
        }
        item(key = "add_own") {
            if (s.atLimit) {
                Text(
                    HabitLimits.LIMIT_REACHED,
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp).testTag("onboarding_habit_limit"),
                    style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted,
                )
            } else {
                AddOwnRow(onAddOwn)
            }
        }
    }
}

/** A preset row: 64dp, radius 20, 2dp ring; picked is purple pale with a purple ring and check. */
@Composable
private fun PresetRow(t: HabitTemplate, picked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(if (picked) D.purplePale else D.white)
            .border(2.dp, if (picked) D.purple else D.lineSoft, shape)
            .toggleable(value = picked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("onboarding_habit_${t.title}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.purpleTint), contentAlignment = Alignment.Center) {
            Icon(DaybreakIcons.habit(t.iconKey) ?: HabitIcons.of(t.iconKey), null, Modifier.size(20.dp), tint = D.purple)
        }
        Column(Modifier.weight(1f)) {
            Text(t.title, style = DaybreakType.rowTitle, color = D.ink)
            Text(OnboardingCopy.habitDetail(t.proofType, t.targetValue, t.unit), style = DaybreakType.caption, color = D.muted)
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (picked) D.purple else Color.Transparent)
                .border(2.dp, if (picked) D.purple else D.ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (picked) Icon(DaybreakIcons.Check, null, Modifier.size(15.dp), tint = D.white) }
    }
}

/** "Add your own": a 64dp dashed row with a white plus tile. */
@Composable
private fun AddOwnRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).dashedBorder(2.dp, D.ring, 20.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("onboarding_add_own"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.white), contentAlignment = Alignment.Center) {
            Icon(DaybreakIcons.Plus, null, Modifier.size(20.dp), tint = D.purple)
        }
        Text(OnboardingCopy.ADD_YOUR_OWN, style = DaybreakType.rowTitle, color = D.ink)
    }
}

/**
 * "Add your own" opens Add a habit (`A9-Habit-edit`), the same page as Settings' Add a habit.
 * Saving adds the habit to the picks, picked.
 */
@Composable
internal fun AddHabitPage(onBack: () -> Unit, onSave: (HabitTemplate) -> Unit) {
    HabitEditPage(
        HabitDraft(),
        onClose = onBack,
        onSave = { d -> onSave(HabitTemplate(d.name, d.iconKey, d.proof, d.savedTarget, d.savedUnit)) },
        modifier = Modifier.testTag("onboarding_add_habit"),
    )
}

// ---------------------------------------------------------------------------------------------
// Step 2: shutdown time (#164)

/**
 * "Pick a shutdown time" (#164; no mockup, built like `A10`): 10 PM, 11 PM and Midnight, one at
 * most picked and none to begin with. Continue is disabled until one is picked; "Skip" goes on
 * without a shutdown time, which leaves night lockdown off.
 */
@Composable
internal fun ShutdownStep(selected: Int?, onPick: (Int) -> Unit, onBack: () -> Unit, onContinue: () -> Unit, onSkip: () -> Unit) {
    StepPage(
        header = PageHeader.Progress(OnboardingStep.SHUTDOWN, OnboardingCopy.STEPS, onBack),
        title = OnboardingCopy.SHUTDOWN_TITLE,
        line = OnboardingCopy.SHUTDOWN_LINE,
        modifier = Modifier.testTag("onboarding_shutdown"),
        footer = {
            PrimaryButton(OnboardingCopy.CONTINUE, onClick = onContinue, enabled = selected != null, modifier = Modifier.testTag("onboarding_next"))
            QuietButton(OnboardingCopy.SKIP, onClick = onSkip, modifier = Modifier.fillMaxWidth().testTag("onboarding_skip"))
        },
    ) {
        OnboardingCopy.SHUTDOWN_TIMES.forEach { minute ->
            item(key = "shutdown_$minute") { ShutdownRow(minute, picked = selected == minute, onPick = { onPick(minute) }) }
        }
    }
}

/** A shutdown row: the preset row's shape (`A10`) as a radio button, with a clock tile. */
@Composable
private fun ShutdownRow(minute: Int, picked: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(if (picked) D.purplePale else D.white)
            .border(2.dp, if (picked) D.purple else D.lineSoft, shape)
            .selectable(selected = picked, role = Role.RadioButton, onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("onboarding_shutdown_$minute"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(D.purpleTint), contentAlignment = Alignment.Center) {
            Icon(DaybreakIcons.Clock, null, Modifier.size(20.dp), tint = D.purple)
        }
        Column(Modifier.weight(1f)) {
            Text(OnboardingCopy.shutdownLabel(minute), style = DaybreakType.rowTitle, color = D.ink)
            Text(OnboardingCopy.SHUTDOWN_DETAIL, style = DaybreakType.caption, color = D.muted)
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (picked) D.purple else Color.Transparent)
                .border(2.dp, if (picked) D.purple else D.ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (picked) Icon(DaybreakIcons.Check, null, Modifier.size(15.dp), tint = D.white) }
    }
}

// ---------------------------------------------------------------------------------------------
// Step 3: lock window (A11)

/**
 * "When does the lock run?" (`A11-Onb-Rule`): the 6 am to midnight bar with the locked band, the
 * sentence under it, and two rows with − / + in 30 minute steps ([LockWindowRules]). A button is
 * disabled where its rule stops it.
 */
@Composable
internal fun LockWindowStep(window: LockWindowRules.Window, onChange: (LockWindowRules.Window) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    StepPage(
        header = PageHeader.Progress(OnboardingStep.LOCK_WINDOW, OnboardingCopy.STEPS, onBack),
        title = OnboardingCopy.RULE_TITLE,
        line = OnboardingCopy.RULE_LINE,
        modifier = Modifier.testTag("onboarding_lock_window"),
        bodySpacing = 14.dp,
        footer = { PrimaryButton(OnboardingCopy.CONTINUE, onClick = onContinue, modifier = Modifier.testTag("onboarding_next")) },
    ) {
        item(key = "bar") { WindowBar(window) }
        item(key = "from") {
            StepperRow(
                OnboardingCopy.LOCK_FROM, OnboardingCopy.LOCK_FROM_HINT, OnboardingCopy.time(window.fromMinute), "lock_from",
                canEarlier = LockWindowRules.canFromEarlier(window), canLater = LockWindowRules.canFromLater(window),
                onEarlier = { onChange(LockWindowRules.fromEarlier(window)) }, onLater = { onChange(LockWindowRules.fromLater(window)) },
            )
        }
        item(key = "due") {
            StepperRow(
                OnboardingCopy.GIVE_UP, OnboardingCopy.GIVE_UP_HINT, OnboardingCopy.time(window.dueMinute), "give_up",
                canEarlier = LockWindowRules.canDueEarlier(window), canLater = LockWindowRules.canDueLater(window),
                onEarlier = { onChange(LockWindowRules.dueEarlier(window)) }, onLater = { onChange(LockWindowRules.dueLater(window)) },
            )
        }
    }
}

/** The card: a 28dp bar with the night-coloured band, its four labels, and the sentence. */
@Composable
private fun WindowBar(window: LockWindowRules.Window) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(D.white).border(1.5.dp, D.lineSoft, shape).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).background(D.purplePale).testTag("lock_bar"),
        ) {
            val l = LockWindowRules.barFraction(window.fromMinute)
            val r = LockWindowRules.barFraction(window.dueMinute)
            Box(
                Modifier.offset(x = maxWidth * l).width(maxWidth * (r - l).coerceAtLeast(0f)).height(28.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Sky.night).testTag("lock_band"),
            )
        }
        // The four labels keep at least 8dp between them: at a large font on a narrow screen they
        // step down in size together rather than run into each other.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val base = DaybreakType.smallLabel.copy(fontWeight = FontWeight.SemiBold)
            val room = with(density) { (maxWidth - 8.dp * (OnboardingCopy.BAR_LABELS.size - 1)).toPx() }
            val style = remember(room, density) {
                var k = 1f
                fun total(s: TextStyle) = OnboardingCopy.BAR_LABELS.sumOf { measurer.measure(it, s, softWrap = false, maxLines = 1, density = density).size.width }
                while (k > 0.3f && total(base.copy(fontSize = base.fontSize * k, lineHeight = base.lineHeight * k)) > room) k -= 0.05f
                base.copy(fontSize = base.fontSize * k, lineHeight = base.lineHeight * k)
            }
            Row(Modifier.fillMaxWidth().testTag("lock_bar_labels"), horizontalArrangement = Arrangement.SpaceBetween) {
                OnboardingCopy.BAR_LABELS.forEach { Text(it, style = style, color = D.muted, maxLines = 1, softWrap = false) }
            }
        }
        val sentence = buildAnnotatedString {
            OnboardingCopy.ruleSentence(window.fromMinute, window.dueMinute).forEach { (text, bold) ->
                if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) } else append(text)
            }
        }
        Text(
            sentence,
            Modifier.padding(top = 4.dp).testTag("lock_sentence"),
            style = DaybreakType.bodySmall.copy(lineHeight = (14 * 1.45).sp),
            color = D.inkSoft,
        )
    }
}

/**
 * A row (`A11`): label 16/700 and hint 13/500 on the left, the stepper on the right (48dp tall,
 * purple pale, − and + 48dp wide, the time 16/800 with tabular figures, at least 76dp). When the
 * label's or hint's longest word would not fit beside the stepper (a narrow screen at a large
 * font), the stepper goes under the text instead, so no word is split.
 */
@Composable
private fun StepperRow(
    label: String,
    hint: String,
    value: String,
    tag: String,
    canEarlier: Boolean,
    canLater: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val valueStyle = DaybreakType.rowTitle.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")
    val signStyle = TextStyle(fontFamily = DaybreakType.rowTitle.fontFamily, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(shape).background(D.white).border(1.5.dp, D.lineSoft, shape)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .testTag("stepper_$tag"),
    ) {
        val inner = maxWidth
        fun widest(text: String, style: TextStyle): Dp = with(density) {
            text.split(' ').maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }.toDp()
        }
        val valueW = with(density) { measurer.measure(value, valueStyle, softWrap = false, maxLines = 1).size.width.toDp() }
        val stepperW = 48.dp * 2 + maxOf(76.dp, valueW + 8.dp)
        // Side by side only when the whole label fits on one line beside the stepper (and no word
        // of the hint is wider than that room); otherwise the stepper goes under the text (QA on
        // PR #147: at 412dp and font 2.0 the label wrapped a word per line beside it).
        val labelW = with(density) { measurer.measure(label, DaybreakType.rowTitle, softWrap = false, maxLines = 1).size.width.toDp() }
        val textW = maxOf(labelW, widest(hint, DaybreakType.caption))
        val side = textW + 12.dp + stepperW <= inner
        val text: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text(label, Modifier.testTag("${tag}_label"), style = DaybreakType.rowTitle, color = D.ink)
                Text(hint, style = DaybreakType.caption, color = D.muted)
            }
        }
        val stepper: @Composable () -> Unit = {
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(D.purplePale)
                    .semantics(mergeDescendants = false) { stateDescription = value },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton("−", "$label, earlier", canEarlier, signStyle, onEarlier, "${tag}_earlier")
                Text(
                    value,
                    Modifier.widthIn(min = 76.dp).padding(horizontal = 4.dp).testTag("${tag}_value"),
                    style = valueStyle, color = D.ink, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                )
                StepButton("+", "$label, later", canLater, signStyle, onLater, "${tag}_later")
            }
        }
        if (side) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                text(Modifier.weight(1f))
                stepper()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                text(Modifier)
                stepper()
            }
        }
    }
}

@Composable
private fun StepButton(sign: String, description: String, enabled: Boolean, style: TextStyle, onClick: () -> Unit, tag: String) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(sign, style = style, color = if (enabled) D.purple else D.disabled) }
}

// ---------------------------------------------------------------------------------------------
// Step 6: how Sprig works (A14)

/**
 * "How Sprig works" (`A14-Onb-Ready`): a dawn sky with the header, the title and Sprig (stage 0)
 * waving on the right over the hill; three rules with duotone icons; "Start today", which
 * finishes onboarding. Widths and the crest come from the real screen ([ReadySprig]).
 */
@Composable
internal fun ReadyStep(saving: Boolean, onBack: () -> Unit, onStart: () -> Unit) {
    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    var titlePx by remember { mutableIntStateOf(0) }
    val textBottom = ReadySprig.TEXT_TOP_DP + ceil(with(density) { titlePx.toDp() }.value).toInt()
    val crest = ReadySprig.crestDp(textBottom)
    val skyHeight = inset + crest.dp + Hill.RISE
    BoxWithConstraints(Modifier.fillMaxSize().background(D.cream).testTag("onboarding_sprig")) {
        val screen = maxWidth.value.toInt()
        val widths = ReadySprig.widths(screen)
        Column(Modifier.fillMaxSize()) {
            SkyScaffold(
                sky = Sky.dawn,
                skyHeight = skyHeight,
                modifier = Modifier.fillMaxWidth().height(skyHeight),
                skyContent = {
                    Spacer(Modifier.offset(y = inset + crest.dp).size(0.dp).testTag("ready_crest"))
                    SprigView(
                        SprigForm.SPRIG,
                        SprigPose.WAVE,
                        Modifier.align(Alignment.TopEnd).padding(end = ReadySprig.SPRIG_END_DP.dp)
                            .offset(y = inset + ReadySprig.sprigTopDp(crest, widths.sprigDp, screen).dp)
                            .size(widths.sprigDp.dp).testTag("ready_sprig"),
                    )
                    PageHeaderRow(PageHeader.Progress(OnboardingStep.READY, OnboardingCopy.STEPS, onBack), Modifier.padding(top = inset))
                    val title = OnboardingCopy.READY_TITLE
                    val w = widths.textDp.dp
                    Text(
                        title,
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                            .padding(top = inset + ReadySprig.TEXT_TOP_DP.dp, start = ReadySprig.TEXT_SIDE_DP.dp)
                            .width(w)
                            .onSizeChanged { titlePx = it.height }
                            .testTag("ready_title"),
                        style = fitLongestWord(title, DaybreakType.pageTitle, w),
                        color = D.ink,
                    )
                },
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 16.dp)
                    .testTag("ready_rules"),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                RuleLine(DaybreakIcons.Growth, D.greenTint, OnboardingCopy.RULE_GROWS_BOLD, OnboardingCopy.RULE_GROWS)
                RuleLine(DaybreakIcons.Padlock, D.purpleTint, OnboardingCopy.RULE_BREAKS_BOLD, OnboardingCopy.RULE_BREAKS)
                RuleLine(DaybreakIcons.ShieldDuo, D.shieldTile, OnboardingCopy.RULE_SHIELD_BOLD, OnboardingCopy.RULE_SHIELD)
            }
            StepFooter {
                PrimaryButton(
                    if (saving) OnboardingCopy.STARTING else OnboardingCopy.START_TODAY,
                    onClick = onStart,
                    enabled = !saving,
                    modifier = Modifier.testTag("onboarding_finish"),
                )
            }
        }
    }
}

/** A rule: a 40dp round tile with the duotone icon, then 15/1.4 text with its bold opening in ink. */
@Composable
private fun RuleLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tile: Color, bold: String, rest: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = Color.Unspecified)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = D.ink)) { append(bold) }
                append(rest)
            },
            Modifier.weight(1f),
            style = DaybreakType.body,
            color = D.inkSoft,
        )
    }
}
