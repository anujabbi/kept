package com.example.kept.feature.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.data.HabitToday
import com.example.kept.core.data.prefs.Settings
import com.example.kept.core.domain.HomeCopy
import com.example.kept.core.domain.HomeLayout
import com.example.kept.core.domain.HomeSprig
import com.example.kept.core.domain.ProofType
import com.example.kept.core.domain.SprigLook
import com.example.kept.core.domain.minuteOfDayLabel
import com.example.kept.core.lock.RequiredPermissions
import com.example.kept.core.ui.AddFirstHabitRow
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakHabitRow
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.FIRST_LANDING_BODY
import com.example.kept.core.ui.FIRST_LANDING_BODY_NO_NOTIFICATIONS
import com.example.kept.core.ui.FirstLandingBanner
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.HabitRowState
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.LockOffBanner
import com.example.kept.core.ui.Motion
import com.example.kept.core.ui.SkyRamp
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.TooLateBanner
import com.example.kept.core.ui.keptTween
import com.example.kept.core.ui.rememberPhotoTick
import com.example.kept.core.ui.sprig.SprigView

/** What Home can ask for. Navigation is the caller's; Sprig is not a way to navigate. */
internal class HomeActions(
    val onComplete: (Long) -> Unit = {},
    val onUndo: (Long) -> Unit = {},
    val onPhoto: (Long, String) -> Unit = { _, _ -> },
    val onOpenPermissions: () -> Unit = {},
    val onOpenHabits: () -> Unit = {},
    val onFirstLandingSeen: () -> Unit = {},
    /** Logs today's session of a weekly goal; a second call the same day stores nothing. */
    val onWeeklyDone: (Long) -> Unit = {},
    val onWeeklyUndo: (Long) -> Unit = {},
    val onAddWeeklyGoal: () -> Unit = {},
)

@Composable
fun HomeScreen(
    onOpenRecap: (String) -> Unit,
    onOpenReveal: (Long) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHabits: () -> Unit,
    onAddWeeklyGoal: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()

    // Surface a fresh evolution first, then a pending recap.
    LaunchedEffect(s.unseenUnlock?.id) { s.unseenUnlock?.let { onOpenReveal(it.id) } }
    LaunchedEffect(s.pendingRecapDate, s.unseenUnlock) {
        if (s.pendingRecapDate != null && s.unseenUnlock == null) vm.openPendingRecap(onOpenRecap)
    }

    // The first-landing banner lasts while Home stays on screen. Leaving Home, by navigating away
    // or the app going to the background, ends it; a rotation does not (issue #41).
    val owner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(owner, activity) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) vm.firstLandingSeen()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (!s.loaded) return

    HomeContent(
        s,
        remember(vm, onOpenPermissions, onOpenHabits, onAddWeeklyGoal) {
            HomeActions(
                onComplete = { vm.complete(it) },
                onUndo = { vm.undo(it) },
                onPhoto = { id, path -> vm.completeWithPhoto(id, path) },
                onOpenPermissions = onOpenPermissions,
                onOpenHabits = onOpenHabits,
                onFirstLandingSeen = { vm.firstLandingSeen() },
                onWeeklyDone = { vm.logWeekly(it) },
                onWeeklyUndo = { vm.undoWeekly(it) },
                onAddWeeklyGoal = onAddWeeklyGoal,
            )
        },
    )
}

/**
 * Home in Daybreak (spec, Home): a sky that follows the progress ramp, Sprig rising over the hill
 * as habits are done, then the habits, the week and the shield. Roomy for 1 to 3 habits
 * (`Main.dc.html`), compact from 4 (`A2-Home-6.dc.html`), where only the habit list scrolls.
 */
@Composable
internal fun HomeContent(s: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    // The sky and Sprig follow the habits and any must-do session still holding; the layout
    // follows the number of habits alone, as it did before weekly goals.
    val total = s.day.total
    val done = s.day.done
    val layout = HomeLayout.forHabitCount(s.today.total)
    val light = SkyRamp.homeTextIsLight(done, total)
    val ink by animateColorAsState(SkyRamp.textOn(light), keptTween(Motion.SKY_MS), label = "sky-ink")
    var shieldOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = shieldOpen) { shieldOpen = false }

    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    // The headline block's height, measured; a taller block (large font) moves the hill down.
    var textHeightPx by remember(layout) { mutableIntStateOf(0) }
    val textTop = if (layout == HomeLayout.ROOMY) ROOMY_TEXT_TOP else COMPACT_TEXT_TOP
    val textBottom = textTop + with(density) { textHeightPx.toDp() }.value.toInt()
    val crest = HomeSprig.crestDp(layout, textBottom)
    val skyHeight = inset + crest.dp + Hill.RISE

    val toggleShield = { shieldOpen = !shieldOpen }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Column and Sprig widths come from the real screen width, not the 390dp design.
        val screen = maxWidth.value.toInt()
        val compact = HomeSprig.compactWidths(screen)
        val widths = when (layout) {
            HomeLayout.ROOMY -> HomeWidths(textDp = HomeSprig.roomyTextDp(screen), sprigDp = HomeSprig.sizeDp(layout))
            HomeLayout.COMPACT -> HomeWidths(textDp = compact.textDp, sprigDp = compact.sprigDp)
        }
        val sprigTop by animateDpAsState(
            inset + HomeSprig.topDp(layout, crest, done, total, widths.sprigDp).dp,
            keptTween(Motion.RISE_MS, Motion.RiseEasing),
            label = "sprig-top",
        )
        SkyScaffold(
            sky = SkyRamp.homeSky(done, total),
            skyHeight = skyHeight,
            lightContent = light,
            skyContent = {
                HomeSprigOnSky(s, layout, sprigTop, widths.sprigDp.dp)
                // Where the hill's crest is, for tests; takes no space and draws nothing.
                Box(Modifier.offset(y = inset + crest.dp).size(0.dp).testTag("home_crest"))
                HeadlineBlock(
                    s, layout, ink, widths.textDp.dp,
                    Modifier.wrapContentHeight(Alignment.Top, unbounded = true).padding(top = inset + textTop.dp)
                        .onSizeChanged { textHeightPx = it.height },
                )
            },
        ) {
            when (layout) {
                // Main: the hill's crest at 396 and "Today" at 434, so the page starts 14dp above
                // the sky's bottom edge (which is 52dp below the crest).
                HomeLayout.ROOMY -> RoomyPage(s, actions, shieldOpen, toggleShield, Modifier.liftBy(14.dp))
                HomeLayout.COMPACT -> CompactPage(s, actions, shieldOpen, toggleShield)
            }
        }
        if (shieldOpen) {
            // Tapping anywhere, the button included, closes the card. Not in the semantics tree:
            // TalkBack closes it with the button.
            Box(Modifier.fillMaxSize().testTag("home_shield_scrim").pointerInput(Unit) { detectTapGestures { shieldOpen = false } })
        }
    }
}

/**
 * Draws this [lift] higher and [lift] taller, so it starts above its slot but still ends at the
 * slot's bottom (an `offset` would leave [lift] of the slot empty at the bottom).
 */
private fun Modifier.liftBy(lift: Dp): Modifier = layout { m, c ->
    val px = lift.roundToPx()
    val maxH = if (c.hasBoundedHeight) c.maxHeight + px else c.maxHeight
    val p = m.measure(c.copy(minHeight = minOf(c.minHeight + px, maxH), maxHeight = maxH))
    layout(p.width, (p.height - px).coerceAtLeast(0)) { p.place(0, -px) }
}

/** The headline column's width and Sprig's size for this screen. */
private data class HomeWidths(val textDp: Int, val sprigDp: Int)

private const val ROOMY_TEXT_TOP = 24
private const val COMPACT_TEXT_TOP = 22

@Composable
private fun BoxScope.HomeSprigOnSky(s: HomeUiState, layout: HomeLayout, top: Dp, size: Dp) {
    // Only a day finished before the give-up time is worth cheering about (issue #7), and not
    // while a weekly goal's must-do session is still holding the lock (story #175).
    val pose = SprigLook.pose(wilted = s.sprig.wilted, allDone = s.day.keptOnTime)
    val place = when (layout) {
        HomeLayout.ROOMY -> Modifier.align(Alignment.TopCenter)
        HomeLayout.COMPACT -> Modifier.align(Alignment.TopEnd).padding(end = HomeSprig.COMPACT_SPRIG_END_DP.dp)
    }
    // Tapping hops (SprigView handles it); it opens nothing.
    SprigView(s.form, pose, place.offset(y = top).size(size).testTag("home_sprig"), wilted = s.sprig.wilted)
}

/** The streak pill and the headline with its line. Roomy: pill top right; compact: top left. */
@Composable
private fun HeadlineBlock(s: HomeUiState, layout: HomeLayout, ink: Color, textWidth: Dp, modifier: Modifier = Modifier) {
    val total = s.day.total
    val done = s.day.done
    val headline = HomeCopy.headline(total, done, s.day.keptOnTime)
    val giveUp = (s.lock?.settings ?: Settings()).dueMinute.minuteOfDayLabel()
    val line = HomeCopy.line(total, done, giveUp, s.pastDue, s.lock?.breakActiveUntil != null, s.day.weeklyMissed)
    val roomy = layout == HomeLayout.ROOMY
    val base = if (roomy) DaybreakType.headline else DaybreakType.headline.copy(fontSize = 38.sp, lineHeight = 38.sp)
    val headStyle = fitLongestWord(headline, base, textWidth)
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        StreakPill(s.sprig.streakDays, ink, if (roomy) Modifier.align(Alignment.End) else Modifier)
        Spacer(Modifier.height(if (roomy) 10.dp else 16.dp))
        Column(if (roomy) Modifier.widthIn(max = textWidth) else Modifier.width(textWidth)) {
            Text(headline, Modifier.testTag("home_headline"), style = headStyle, color = ink)
            Spacer(Modifier.height(8.dp))
            Text(
                line,
                Modifier.widthIn(max = if (roomy) minOf(300.dp, textWidth) else textWidth).testTag("home_line"),
                style = DaybreakType.body.copy(lineHeight = 20.sp),
                color = ink,
            )
        }
    }
}

/** Banners, in the spec's order, when they apply. */
@Composable
private fun ColumnScope.Banners(s: HomeUiState, actions: HomeActions) {
    if (s.missingLockPermissions.isNotEmpty()) {
        LockOffBanner(RequiredPermissions.lockOffBody(s.missingLockPermissions), onFix = actions.onOpenPermissions, Modifier.testTag("home_lock_off"))
    }
    if (s.firstLanding) {
        FirstLandingBanner(
            onGotIt = actions.onFirstLandingSeen,
            modifier = Modifier.testTag("home_first_landing"),
            body = if (s.notificationsGranted) FIRST_LANDING_BODY else FIRST_LANDING_BODY_NO_NOTIFICATIONS,
        )
    }
    if (showTooLate(s)) TooLateBanner(Modifier.testTag("home_too_late"))
}

internal fun showTooLate(s: HomeUiState): Boolean = s.pastDue && s.today.total > 0 && !s.today.allDoneOnTime

private fun anyBanner(s: HomeUiState) = s.missingLockPermissions.isNotEmpty() || s.firstLanding || showTooLate(s)

@Composable
private fun TodayHeader(s: HomeUiState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text("Today", style = DaybreakType.section, color = DaybreakColors.ink)
        if (s.today.total > 0) {
            Text(HomeCopy.count(s.today.done, s.today.total), style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = DaybreakColors.muted, maxLines = 1)
        }
    }
}

@Composable
private fun Week(s: HomeUiState, shieldOpen: Boolean, onToggleShield: () -> Unit, gapToShield: Dp) {
    WeekSection(
        // Today is gold once the whole promise is kept: the habits and, on a must-do day, the session (story #176).
        s.lastSeven.map { (d, r) -> d to dayDot(d == s.date, r, s.day.keptOnTime) },
        s.sprig.bestStreak,
    )
    // The shield button's touch area is 48dp around a 28dp visual, so 10dp of the gap is inside it.
    Spacer(Modifier.height(gapToShield - 10.dp))
    ShieldButton(ready = s.sprig.shieldAvailable, open = shieldOpen, onToggle = onToggleShield)
}

/**
 * `Main`: everything below the hill scrolls together. Weekly goal cards follow the habits, as in
 * weekly goals mockup 4. With no goal, the "Add a weekly goal" row comes last instead, so an
 * invitation does not push the week and the shield off the first screen.
 */
@Composable
private fun RoomyPage(s: HomeUiState, actions: HomeActions, shieldOpen: Boolean, onToggleShield: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (anyBanner(s)) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Banners(s, actions) }
        TodayHeader(s, Modifier.padding(horizontal = 4.dp))
        if (s.today.total == 0) AddFirstHabitRow(actions.onOpenHabits, Modifier.testTag("home_add_first"))
        s.today.habits.forEach { HomeHabitRow(it, roomy = true, actions) }
        val hasGoals = s.weeklyGoals.isNotEmpty()
        if (hasGoals) WeeklySection(s.weeklyGoals, actions, Modifier.padding(top = 14.dp), lockEndedToday = s.pastDue)
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp)) {
            Week(s, shieldOpen, onToggleShield, gapToShield = 12.dp)
        }
        if (!hasGoals) WeeklySection(s.weeklyGoals, actions, Modifier.padding(top = 4.dp, bottom = 20.dp))
    }
}

/**
 * `A2-Home-6`: the sky, "Today", the week and the shield stay put; only the habit list scrolls.
 * The weekly goal cards (or the "Add a weekly goal" row) are the end of that list.
 * The list always keeps at least [COMPACT_LIST_MIN] of height: on a small screen at a large font,
 * where the fixed parts would squeeze it to nothing, the whole page below the hill scrolls
 * instead, and the list keeps scrolling inside it.
 */
@Composable
private fun CompactPage(s: HomeUiState, actions: HomeActions, shieldOpen: Boolean, onToggleShield: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = constraints.maxHeight
        Layout(
            content = {
                Column {
                    if (anyBanner(s)) {
                        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Banners(s, actions) }
                    }
                    TodayHeader(s, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
                }
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp).testTag("home_habit_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.today.habits.forEach { HomeHabitRow(it, roomy = false, actions) }
                    WeeklySection(s.weeklyGoals, actions, Modifier.padding(top = 10.dp), lockEndedToday = s.pastDue)
                }
                Column {
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(DaybreakColors.line))
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp)) {
                        Week(s, shieldOpen, onToggleShield, gapToShield = 10.dp)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) { measurables, c ->
            val loose = c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
            val top = measurables[0].measure(loose)
            val bottom = measurables[2].measure(loose)
            val listH = maxOf(viewport - top.height - bottom.height, COMPACT_LIST_MIN.roundToPx())
            val list = measurables[1].measure(c.copy(minHeight = listH, maxHeight = listH))
            layout(c.maxWidth, top.height + listH + bottom.height) {
                top.place(0, 0)
                list.place(0, top.height)
                bottom.place(0, top.height + listH)
            }
        }
    }
}

/** The least height the compact habit list keeps: about two rows. */
private val COMPACT_LIST_MIN = 128.dp

/**
 * A habit on Home: [DaybreakHabitRow] plus the camera for photo habits ([rememberPhotoTick]).
 * The tick haptic for a photo habit plays once the photo is saved, not on the tap that opens the
 * camera.
 */
@Composable
private fun HomeHabitRow(h: HabitToday, roomy: Boolean, actions: HomeActions) {
    val snap = rememberPhotoTick { path -> actions.onPhoto(h.id, path) }
    val photo = h.habit.proofType == ProofType.PHOTO
    DaybreakHabitRow(
        title = h.habit.title,
        detail = HomeCopy.habitDetail(h.habit.targetValue, h.habit.unit),
        state = when {
            !h.isDone -> HabitRowState.ToDo
            h.late -> HabitRowState.DoneLate
            else -> HabitRowState.Done
        },
        onTick = { if (photo) snap() else actions.onComplete(h.id) },
        onUndo = { actions.onUndo(h.id) },
        modifier = Modifier.testTag("home_habit_${h.id}"),
        photo = photo,
        roomy = roomy,
    )
}
