package com.example.kept.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.GalleryCard
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.domain.CollectionCopy
import com.example.kept.core.domain.CollectionForms
import com.example.kept.core.domain.CollectionSprig
import com.example.kept.core.domain.FormStatus
import com.example.kept.core.domain.FormTile
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.SprigState
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.Hill
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.Sky
import com.example.kept.core.ui.SkyScaffold
import com.example.kept.core.ui.fitLongestWord
import com.example.kept.core.ui.sprig.ShareCard
import com.example.kept.core.ui.sprig.SprigSpec
import com.example.kept.core.ui.sprig.SprigView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.ceil
import com.example.kept.core.ui.DaybreakColors as D

data class GalleryUi(val cards: List<GalleryCard> = emptyList(), val state: SprigState = SprigState())

@HiltViewModel
class GalleryViewModel @Inject constructor(repo: SprigRepository) : ViewModel() {
    val state = combine(repo.gallery, repo.state) { g, s -> GalleryUi(g, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUi())
}

/**
 * What sharing a reached form shows: the streak and date it was first reached, from its earliest
 * gallery entry. A form with no entry (Sprig itself, where every streak starts) uses today's
 * streak while it is the current form, else its threshold.
 */
internal data class FormShare(val form: SprigForm, val streak: Int, val date: LocalDate?) {
    /** The sheet's line under the name. */
    val sheetLine: String
        get() = if (form == SprigForm.SPRIG) "Where everyone starts" else "Reached at a $streak-day streak"

    fun content(): ShareCard.Content = ShareCard.Content(
        SprigSpec(form, SprigPose.CHEER),
        ShareCard.formHeadline(form, streak),
        date?.let { "Reached ${it.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}" } ?: sheetLine,
    )

    companion object {
        fun of(form: SprigForm, cards: List<GalleryCard>, state: SprigState): FormShare {
            val card = cards.filter { it.form == form }.minByOrNull { it.unlockedDate }
            return when {
                card != null -> FormShare(form, card.streakAtUnlock, card.unlockedDate)
                form == state.form -> FormShare(form, state.streakDays, null)
                else -> FormShare(form, form.streakThreshold, null)
            }
        }
    }
}

@Composable
fun GalleryScreen(vm: GalleryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    GalleryContent(s, onOpenForm = { selectedId = it.id })
    selectedId?.let { id ->
        val share = FormShare.of(SprigForm.fromId(id), s.cards, s.state)
        ShareSheet(share, onDismiss = { selectedId = null }, onShare = {
            ShareCard.share(ctx, share.content())
            selectedId = null
        })
    }
}

/**
 * The Sprig tab in Daybreak (`A6-Collection`, spec Collection), without the ViewModel so
 * screenshot tests can render it: a Tide sky with the current form, the streak, the pill toward
 * the next form and Sprig waving in its outfit; then "Forms" and a 3-column grid of all seven.
 * The whole page scrolls; the tab bar is outside it. Tapping a reached form calls [onOpenForm];
 * locked forms have no click at all.
 */
@Composable
internal fun GalleryContent(s: GalleryUi, onOpenForm: (SprigForm) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val form = s.state.form
    val streak = s.state.streakDays
    val galleryForms = s.cards.map { it.form }
    val tiles = CollectionForms.tiles(galleryForms, form)
    // The headline block's height, measured; a taller block (large font) moves the hill down.
    var textPx by remember { mutableIntStateOf(0) }
    val textBottom = CollectionSprig.TEXT_TOP_DP + ceil(with(density) { textPx.toDp() }.value).toInt()
    val crest = CollectionSprig.crestDp(textBottom)
    val skyHeight = inset + crest.dp + Hill.RISE

    BoxWithConstraints(modifier.fillMaxSize().background(D.cream).testTag("collection_screen")) {
        val screen = maxWidth.value.toInt()
        val widths = CollectionSprig.widths(screen)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("collection_page")) {
            SkyScaffold(
                sky = Sky.tide,
                skyHeight = skyHeight,
                modifier = Modifier.fillMaxWidth().height(skyHeight),
                skyContent = {
                    // Where the hill's crest is, for tests; takes no space and draws nothing.
                    Spacer(Modifier.offset(y = inset + crest.dp).size(0.dp).testTag("collection_crest"))
                    SprigView(
                        form,
                        SprigPose.WAVE,
                        Modifier.align(Alignment.TopEnd).padding(end = CollectionSprig.SPRIG_END_DP.dp)
                            .offset(y = inset + CollectionSprig.sprigTopDp(crest, widths.sprigDp, screen).dp)
                            .size(widths.sprigDp.dp).testTag("collection_sprig"),
                        wilted = s.state.wilted,
                    )
                    Hero(
                        form, streak, widths.textDp.dp,
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true)
                            .padding(top = inset + CollectionSprig.TEXT_TOP_DP.dp, start = CollectionSprig.TEXT_SIDE_DP.dp)
                            .onSizeChanged { textPx = it.height },
                    )
                },
            )
            // `A6`: the row starts 8dp under the sky's bottom edge (300), which is 48 below the
            // crest; the scaffold's sky ends 52 below it, so 4dp are left here.
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(CollectionCopy.FORMS, Modifier.alignByBaseline(), style = DaybreakType.section, color = D.ink)
                Text(
                    CollectionCopy.reached(CollectionForms.reachedCount(galleryForms, form)),
                    Modifier.alignByBaseline().testTag("collection_count"),
                    style = DaybreakType.bodySmall, fontWeight = FontWeight.SemiBold, color = D.muted, maxLines = 1,
                )
            }
            FormsGrid(tiles, screen, onOpenForm)
        }
    }
}

/** "{Form}" (44/800, never split inside a word), "Day {n} of your streak" and the pill. */
@Composable
private fun Hero(form: SprigForm, streak: Int, width: Dp, modifier: Modifier = Modifier) {
    val name = form.displayName
    val base = DaybreakType.headline.copy(fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em)
    Column(modifier.width(width)) {
        Text(name, Modifier.testTag("collection_headline"), style = fitLongestWord(name, base, width), color = D.ink)
        Text(
            CollectionCopy.day(streak),
            Modifier.padding(top = 8.dp).testTag("collection_day"),
            style = DaybreakType.body.copy(fontSize = 16.sp, lineHeight = (16 * 1.35).sp, fontWeight = FontWeight.SemiBold),
            color = D.tideInk,
        )
        // The pill's words are joined by no-break spaces, so it shrinks rather than wraps. Fitted
        // 1dp inside its padding: the padding's pixel rounding can leave the text 1px less room.
        val pill = CollectionCopy.nextPill(streak)
        val pillStyle = DaybreakType.caption.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp)
        Row(
            Modifier.padding(top = 14.dp).heightIn(min = 30.dp).clip(RoundedCornerShape(15.dp))
                .background(D.white.copy(alpha = 0.55f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pill,
                Modifier.testTag("collection_pill"),
                style = fitLongestWord(pill, pillStyle, width - 25.dp),
                color = D.tidePill,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private val TILE_GAP = 10.dp
private val GRID_SIDE = 20.dp
private val TILE_PAD_X = 6.dp
private val TILE_BORDER = 2.dp
private val TILE_RADIUS = 22.dp

/** Three columns, 10dp apart, 20dp sides; every card in a row as tall as the tallest. */
@Composable
private fun FormsGrid(tiles: List<FormTile>, screenDp: Int, onOpenForm: (SprigForm) -> Unit) {
    val tile = (screenDp.dp - GRID_SIDE * 2 - TILE_GAP * 2) / 3
    val inner = tile - TILE_PAD_X * 2 - TILE_BORDER * 2
    // One size for every card: the longest name (and note) fits whole, so the cards match.
    val nameStyle = fitLongestWord(
        tiles.joinToString(" ") { it.form.displayName },
        DaybreakType.rowTitle.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
        inner,
    )
    val noteStyle = fitLongestWord(
        tiles.joinToString(" ") { CollectionCopy.note(it) },
        DaybreakType.smallLabel.copy(fontWeight = FontWeight.SemiBold),
        inner,
    )
    Column(
        Modifier.fillMaxWidth().padding(start = GRID_SIDE, end = GRID_SIDE, bottom = 28.dp).testTag("collection_grid"),
        verticalArrangement = Arrangement.spacedBy(TILE_GAP),
    ) {
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                row.forEach { FormCard(it, nameStyle, noteStyle, onOpenForm, Modifier.weight(1f).fillMaxHeight()) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One form (`A6` card): reached is a white card in full colour, current a purple-tint card with a
 * purple ring and "You are here", locked a dashed outline with the silhouette and "Day {n}".
 */
@Composable
private fun FormCard(tile: FormTile, nameStyle: TextStyle, noteStyle: TextStyle, onOpenForm: (SprigForm) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(TILE_RADIUS)
    val (bg, stroke, ink) = when (tile.status) {
        FormStatus.CURRENT -> Triple(D.purpleTint, D.purple, D.purple)
        FormStatus.REACHED -> Triple(D.white, D.rowLine, D.ink)
        FormStatus.LOCKED -> Triple(Color.Transparent, D.ring, D.muted)
    }
    val edge = if (tile.status == FormStatus.LOCKED) {
        Modifier.drawBehind {
            val w = TILE_BORDER.toPx()
            drawRoundRect(
                stroke,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(TILE_RADIUS.toPx() - w / 2),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH_GAP.toPx()))),
            )
        }
    } else {
        Modifier.border(TILE_BORDER, stroke, shape)
    }
    val description = CollectionCopy.description(tile)
    // Locked cards get a label and nothing else: no click, so TalkBack does not offer one either.
    val tap = if (tile.tappable) {
        Modifier.clickable(role = Role.Button, onClickLabel = SHARE_LABEL) { onOpenForm(tile.form) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = SHARE_LABEL) { onOpenForm(tile.form); true }
            }
    } else {
        Modifier.clearAndSetSemantics { contentDescription = description }
    }
    Column(
        modifier.testTag("collection_form_${tile.form.name}").clip(shape).background(bg).then(edge).then(tap)
            .padding(start = TILE_PAD_X, end = TILE_PAD_X, top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SprigView(
            tile.form,
            SprigPose.IDLE,
            Modifier.size(72.dp),
            silhouetteAlpha = if (tile.status == FormStatus.LOCKED) LOCKED_ALPHA else null,
            animate = false,
        )
        Text(tile.form.displayName, style = nameStyle, color = ink, textAlign = TextAlign.Center)
        Text(CollectionCopy.note(tile), style = noteStyle, color = D.muted, textAlign = TextAlign.Center)
    }
}

/** `brightness(0) opacity(0.16)` in `A6`. */
private const val LOCKED_ALPHA = 0.16f
private val DASH = 6.dp
private val DASH_GAP = 4.dp
private const val SHARE_LABEL = "Share"

/** The share flow for a reached form: the form, when it was reached, and "Share this form". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(share: FormShare, onDismiss: () -> Unit, onShare: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = D.cream, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).testTag("collection_share_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SprigView(share.form, SprigPose.CHEER, Modifier.size(180.dp))
            Text(share.form.displayName, style = DaybreakType.pageTitle, color = D.ink)
            Text(share.sheetLine, Modifier.padding(top = 4.dp), style = DaybreakType.body, color = D.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(share.form.tagline, style = DaybreakType.bodySmall, color = D.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Share this form", onClick = onShare, modifier = Modifier.testTag("collection_share"))
        }
    }
}
