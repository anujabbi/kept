package com.example.kept.feature.celebration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.domain.EvolveCopy
import com.example.kept.core.domain.MomentFit
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.LockPalette
import com.example.kept.core.ui.MomentPetals
import com.example.kept.core.ui.MomentSprig
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.StatusBarIcons
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.rememberMomentClock
import com.example.kept.core.ui.sprig.ShareCard
import com.example.kept.core.ui.sprig.SprigSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

/**
 * The form reached, shown once when Home finds an unseen unlock (`Routes.REVEAL`, as before
 * issue #130). The gallery entry is marked seen as the screen opens; whether the pop, ring and
 * petals have played is kept in [SavedStateHandle], so a rotation does not play them again.
 */
@HiltViewModel
class RevealViewModel @Inject constructor(private val repo: SprigRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val id: Long = saved.get<Long>("galleryId") ?: 0
    val card = repo.gallery.map { list -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun seen() = viewModelScope.launch { repo.markSeen(id) }

    val introPending: Boolean get() = saved.get<Boolean>(KEY_INTRO_PLAYED) != true

    fun introStarted() {
        saved[KEY_INTRO_PLAYED] = true
    }

    companion object {
        const val KEY_INTRO_PLAYED = "evolve_intro_played"
    }
}

@Composable
fun SprigEvolvesScreen(onClose: () -> Unit, vm: RevealViewModel = hiltViewModel()) {
    val card by vm.card.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.seen() }
    val c = card ?: return
    SprigEvolvesContent(
        form = c.form,
        daysKept = c.streakAtUnlock,
        onShare = {
            ShareCard.share(ctx, ShareCard.Content(SprigSpec(c.form, SprigPose.CHEER), EvolveCopy.headline(c.form), EvolveCopy.kept(c.streakAtUnlock)))
        },
        onCarryOn = onClose,
        playIntro = vm.introPending,
        onIntroStarted = vm::introStarted,
    )
}

/** `rgba(255,194,61,0.8)`, the ring; `0.25`, the glow under Sprig. */
private val RingGold = Sky.gold.copy(alpha = 0.8f)
private val GlowGold = Sky.gold.copy(alpha = 0.25f)

/**
 * "Sprig evolves" (`A17-Evolve`, spec Moments): a night sky, "{n} days kept", "Sprig is now
 * {Form}", Sprig cheering in the new outfit at 280dp with its pop and a gold ring, the outfit and
 * what comes next, and "Share" and "Carry on". Without the ViewModel, so screenshot tests can
 * render it. Sprig shrinks on a short screen or at a large font (down to 110dp) before the page
 * scrolls.
 */
@Composable
internal fun SprigEvolvesContent(
    form: SprigForm,
    daysKept: Int,
    onShare: () -> Unit,
    onCarryOn: () -> Unit,
    modifier: Modifier = Modifier,
    playIntro: Boolean = true,
    onIntroStarted: () -> Unit = {},
    frozenAtMs: Long? = null,
) {
    val clock = rememberMomentClock(playIntro, onIntroStarted, frozenAtMs)
    val headline = EvolveCopy.headline(form)
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    var headPx by remember { mutableIntStateOf(0) }
    var linePx by remember { mutableIntStateOf(0) }
    var buttonsPx by remember { mutableIntStateOf(0) }
    fun dp(px: Int): Int = ceil(with(density) { px.toDp() }.value).toInt()

    StatusBarIcons(light = true)
    BoxWithConstraints(
        modifier.fillMaxSize().background(Sky.night).testTag("evolve")
            .semantics { paneTitle = headline },
    ) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        // Everything but Sprig: 72 above, the header, 30, 18, the line, at least 16, the buttons, 28.
        // Before the first measure the buttons count as 56, so the first frame never squeezes them.
        val rest = top.value.toInt() + 72 + dp(headPx) + 30 + 18 + dp(linePx) + MIN_GAP_DP + maxOf(dp(buttonsPx), 56) + 28 + bottom.value.toInt()
        val sprig = MomentFit.sprigDp(MomentFit.EVOLVE_DP, height, rest, width - 56)
        val total = maxOf(height, rest + sprig)
        val gap = total - rest - sprig + MIN_GAP_DP

        MomentPetals(clock, Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().height(total.dp).padding(start = 28.dp, end = 28.dp, top = top + 72.dp, bottom = bottom + 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).onSizeChanged { headPx = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(EvolveCopy.kept(daysKept), Modifier.testTag("evolve_kept"), style = KEPT_STYLE, color = LockPalette.soft, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        headline,
                        Modifier.testTag("evolve_headline").semantics { liveRegion = LiveRegionMode.Polite },
                        style = fitLongestWord(headline, HEADLINE_STYLE, (width - 56).dp),
                        color = DaybreakColors.skyTextLight, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(30.dp))
                MomentSprig(form, sprig, MomentFit.EVOLVE_DP, ringDp = 220, ringColor = RingGold, clock = clock, glow = GlowGold)
                Spacer(Modifier.height(18.dp))
                Text(
                    EvolveCopy.line(form),
                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).onSizeChanged { linePx = it.height }.testTag("evolve_line"),
                    style = LINE_STYLE, color = LockPalette.soft, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(gap.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).onSizeChanged { buttonsPx = it.height },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("evolve_share"),
                        shape = CircleShape,
                        border = BorderStroke(2.dp, DaybreakColors.skyTextLight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DaybreakColors.skyTextLight),
                        contentPadding = BUTTON_PADDING,
                    ) { Text(EvolveCopy.SHARE, style = DaybreakType.button, textAlign = TextAlign.Center) }
                    Button(
                        onClick = onCarryOn,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 56.dp).testTag("evolve_carry_on"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Sky.gold, contentColor = DaybreakColors.ink),
                        elevation = null,
                        contentPadding = BUTTON_PADDING,
                    ) { Text(EvolveCopy.CARRY_ON, style = DaybreakType.button, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

/** The design's `margin-top: auto` above the buttons, at least this much. */
private const val MIN_GAP_DP = 16

/** Two buttons share a 360dp screen; 10dp sides keep a large label on one line as long as possible. */
private val BUTTON_PADDING = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

/** `font-size: 15px; font-weight: 700`. */
private val KEPT_STYLE = DaybreakType.body.copy(fontWeight = FontWeight.Bold)

/** `font-size: 44px; line-height: 1; font-weight: 800; letter-spacing: -0.02em`. */
private val HEADLINE_STYLE = DaybreakType.headline.copy(fontSize = 44.sp, lineHeight = 44.sp)

/** `font-size: 17px; line-height: 1.4; font-weight: 600`. */
private val LINE_STYLE = DaybreakType.body.copy(fontSize = 17.sp, lineHeight = (17 * 1.4).sp, fontWeight = FontWeight.SemiBold)
