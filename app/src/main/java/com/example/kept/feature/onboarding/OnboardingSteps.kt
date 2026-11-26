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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import com.example.kept.core.domain.HabitLimits
import com.example.kept.core.domain.LockWindowRules
import com.example.kept.core.domain.NightCopy
import com.example.kept.core.domain.NightSetting
import com.example.kept.core.domain.NightSliders
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
import com.example.kept.core.ui.TimeSlider
import com.example.kept.core.ui.BigTimeStyle
import com.example.kept.core.ui.boldParts
import com.example.kept.core.ui.dashedBorder
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.SprigView
import com.example.kept.feature.settings.PromisePage
import com.example.kept.core.domain.PromiseDraft
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
 * "What habits will you keep?" (`A10-Onb-Habits`, #205): the presets and any habit added with
 * "Add your own", each toggled by a tap (purple ring and check when picked). A ticked row shows
 * its how-often chip, which opens its promise (#218). Continue needs one every-day habit, and the
 * footer counts daily habits and weekly goals apart. At [HabitLimits.MAX_HABITS] every-day habits
 * the "Add your own" row gives way to [HabitLimits.LIMIT_REACHED].
 */
@Composable
internal fun HabitsStep(
    s: OnboardingState,
    onToggle: (HabitTemplate) -> Unit,
    onCadence: (String) -> Unit,
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
            PrimaryButton(OnboardingCopy.CONTINUE, onClick = onContinue, enabled = s.canContinue, modifier = Modifier.testTag("onboarding_next"))
            Text(
                OnboardingCopy.habitsFooter(s.daily.size, s.weekly.size),
                Modifier.testTag("onboarding_habit_count"),
                style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted, textAlign = TextAlign.Center,
            )
        },
    ) {
        (HabitTemplates.list + custom).forEach { t ->
            item(key = "habit_${t.title}") {
                val chosen = s.chosen.firstOrNull { it.title == t.title }
                PresetRow(chosen ?: t, chosen != null, s.daysOf(t.title), onToggle = { onToggle(t) }, onCadence = { onCadence(t.title) })
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

/**
 * A preset row: 64dp, radius 20, 2dp ring; picked is purple pale with a purple ring and check,
 * and the how-often chip under the habit's detail.
 */
@Composable
private fun PresetRow(t: HabitTemplate, picked: Boolean, days: Int?, onToggle: () -> Unit, onCadence: () -> Unit) {
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
            if (picked) CadenceChip(t.title, days, onCadence)
        }
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (picked) D.purple else Color.Transparent)
                .border(2.dp, if (picked) D.purple else D.ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (picked) Icon(DaybreakIcons.Check, null, Modifier.size(15.dp), tint = D.white) }
    }
}

/**
 * The how-often chip (#205): "Every day" or "4 days a week", a white pill with a purple ring and
 * a chevron, which opens the habit's promise (#218). Its touch target is 48dp tall; TalkBack reads
 * "Exercise, 4 days a week. Change how often" as a button.
 */
@Composable
private fun CadenceChip(title: String, days: Int?, onClick: () -> Unit) {
    val label = OnboardingCopy.cadence(days)
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag("onboarding_cadence_$title")
            .clickable(role = Role.Button, onClick = onClick)
            // One label, read once: the visible "4 days a week" would otherwise be read again after it.
            .clearAndSetSemantics {
                contentDescription = OnboardingCopy.cadenceDescription(title, days)
                role = Role.Button
                onClick { onClick(); true }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.clip(CircleShape).background(D.white).border(1.5.dp, D.purpleDone, CircleShape)
                .padding(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = DaybreakType.caption, fontWeight = FontWeight.Bold, color = D.purpleInk)
            Icon(DaybreakIcons.Chevron, null, Modifier.size(14.dp).rotate(90f), tint = D.purpleInk)
        }
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
 * The promise (#218) for a ticked habit's chip or for "Add your own": the same full-screen page as
 * Settings. Done changes the habit (or adds it, ticked) at once; the page closes after Sprig's cheer.
 */
@Composable
internal fun OnboardingPromisePage(s: OnboardingState, onDone: (PromiseDraft) -> Unit, onClose: () -> Unit) {
    val promise = s.promise ?: return
    PromisePage(
        promise.draft,
        s.promiseLimits,
        onDone = { d, saved -> onDone(d); saved() },
        onClose = onClose,
        taken = s.promiseTaken,
        modifier = Modifier.testTag("onboarding_promise"),
    )
}

// ---------------------------------------------------------------------------------------------
// Step 2: lock window (A11)

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
// Step 3: shutdown time (#164, #218)

/**
 * "Pick a shutdown time" (#218, after the lock window): Sprig's note, then a card with two
 * sliders in 15 minute steps, "Shutdown at" (8 pm to 2 am) and "Phone back at" (5 am to 10 am),
 * each time shown large, and the sentence under them that follows both. Continue turns night
 * lockdown on at those times; "Skip" goes on without it.
 */
@Composable
internal fun ShutdownStep(
    night: NightSetting,
    onFrom: (Int) -> Unit,
    onUntil: (Int) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    StepPage(
        header = PageHeader.Progress(OnboardingStep.SHUTDOWN, OnboardingCopy.STEPS, onBack),
        title = OnboardingCopy.SHUTDOWN_TITLE,
        line = null,
        modifier = Modifier.testTag("onboarding_shutdown"),
        bodySpacing = 14.dp,
        footer = {
            PrimaryButton(OnboardingCopy.CONTINUE, onClick = onContinue, modifier = Modifier.testTag("onboarding_next"))
            QuietButton(OnboardingCopy.SKIP, onClick = onSkip, modifier = Modifier.fillMaxWidth().testTag("onboarding_skip"))
        },
    ) {
        item(key = "note") { SprigNote(OnboardingCopy.SHUTDOWN_LINE) }
        item(key = "times") {
            val shape = RoundedCornerShape(22.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(D.white).border(1.5.dp, D.lineSoft, shape)
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SliderTime(NightCopy.SHUTDOWN_AT, night.fromMinute, "shutdown_from")
                TimeSlider(
                    NightSliders.fromIndex(night.fromMinute), NightSliders.FROM_STEPS, NightCopy.SHUTDOWN_AT, NightCopy.big(night.fromMinute),
                    NightSliders.FROM_LABELS, onChange = { onFrom(NightSliders.fromAt(it)) }, tag = "shutdown_from_slider",
                )
                Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.5.dp).background(D.line))
                SliderTime(NightCopy.PHONE_BACK_AT, night.untilMinute, "shutdown_until")
                TimeSlider(
                    NightSliders.untilIndex(night.untilMinute), NightSliders.UNTIL_STEPS, NightCopy.PHONE_BACK_AT, NightCopy.big(night.untilMinute),
                    NightSliders.UNTIL_LABELS, onChange = { onUntil(NightSliders.untilAt(it)) }, tag = "shutdown_until_slider",
                )
                Text(
                    boldParts(NightCopy.shutdownSentence(night.fromMinute, night.untilMinute)),
                    Modifier.padding(top = 10.dp).testTag("shutdown_sentence"),
                    style = DaybreakType.bodySmall.copy(lineHeight = (14 * 1.45).sp),
                    color = D.inkSoft,
                )
            }
        }
    }
}

/** Sprig's note at the top of a step: a small Sprig beside the line, on purple tint. */
@Composable
private fun SprigNote(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(D.purpleTint)
            .padding(horizontal = 14.dp, vertical = 12.dp).testTag("shutdown_note"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(D.purplePale), contentAlignment = Alignment.Center) {
            SprigView(SprigForm.SPRIG, SprigPose.IDLE, Modifier.size(44.dp))
        }
        Text(text, Modifier.weight(1f).testTag("shutdown_line"), style = DaybreakType.body, color = D.inkSoft)
    }
}

/** A slider's label (13/600 muted) over its time, large (44/800, tabular). */
@Composable
private fun SliderTime(label: String, minute: Int, tag: String) {
    Column {
        Text(label, style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted)
        Text(NightCopy.big(minute), Modifier.testTag("${tag}_time"), style = BigTimeStyle, color = D.ink)
    }
}

// ---------------------------------------------------------------------------------------------
// Step 6: how Sprig works (A14)

/**
 * "How Sprig works" (`A14-Onb-Ready`): a dawn sky with the header, the title and Sprig (stage 0)
 * waving on the right over the hill; three rules with duotone icons; "Start today", which
 * finishes onboarding. Widths and the crest come from the real screen ([ReadySprig]).
 */
@Composable
internal fun ReadyStep(saving: Boolean, hasWeeklyGoal: Boolean, onBack: () -> Unit, onStart: () -> Unit) {
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
                // Only with a weekly goal chosen (#205); otherwise the page is as before.
                if (hasWeeklyGoal) {
                    RuleLine(DaybreakIcons.Clock, D.purpleTint, OnboardingCopy.RULE_WEEKLY_BOLD, OnboardingCopy.RULE_WEEKLY, tint = D.purple, tag = "ready_weekly")
                }
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
private fun RuleLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tile: Color,
    bold: String,
    rest: String,
    tint: Color = Color.Unspecified,
    tag: String? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = tint)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = D.ink)) { append(bold) }
                append(rest)
            },
            Modifier.weight(1f).then(if (tag != null) Modifier.testTag(tag) else Modifier),
            style = DaybreakType.body,
            color = D.inkSoft,
        )
    }
}
