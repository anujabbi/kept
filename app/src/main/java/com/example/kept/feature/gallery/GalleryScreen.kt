package com.example.kept.feature.gallery

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kept.core.data.GalleryCard
import com.example.kept.core.data.SprigRepository
import com.example.kept.core.data.TimeSource
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.Variants
import com.example.kept.core.domain.WeekVariant
import com.example.kept.core.ui.KeptCard
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.Pill
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.ScreenTitle
import com.example.kept.core.ui.SecondaryButton
import com.example.kept.core.ui.SectionLabel
import com.example.kept.core.ui.sprig.ShareCard
import com.example.kept.core.ui.sprig.SprigSpec
import com.example.kept.core.ui.sprig.SprigView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class GalleryUi(val cards: List<GalleryCard> = emptyList(), val state: SprigState = SprigState(), val thisWeek: WeekVariant = Variants.table[0], val today: LocalDate = LocalDate.now())

@HiltViewModel
class GalleryViewModel @Inject constructor(private val repo: SprigRepository, time: TimeSource) : ViewModel() {
    val state = combine(repo.gallery, repo.state, time.observeToday()) { g, s, d -> GalleryUi(g, s, Variants.forDate(d), d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUi())
}

@Composable
fun GalleryScreen(onOpenRoadmap: () -> Unit, vm: GalleryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<GalleryCard?>(null) }
    val unlockedForms = s.cards.map { it.form }.toSet()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                ScreenTitle("Sprig", "${s.cards.size} of ${SprigForm.entries.size} looks collected")
                Spacer(Modifier.height(14.dp))
                KeptCard(background = c.purple50, border = null, shape = RoundedCornerShape(16.dp), onClick = onOpenRoadmap) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SprigView(s.state.form, SprigPose.IDLE, Modifier.size(84.dp), variant = s.thisWeek, wilted = s.state.wilted)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Now: ${s.state.form.displayName}", style = MaterialTheme.typography.titleMedium, color = c.purple900)
                            val next = SprigForm.next(s.state.form)
                            Text(
                                if (next != null) "${next.streakThreshold - s.state.streakDays} more days to ${next.displayName}" else "Final form reached",
                                style = MaterialTheme.typography.bodySmall, color = c.purple600,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(s.thisWeek.accentArgb)))
                                Spacer(Modifier.width(6.dp))
                                Text("This week's look: ${s.thisWeek.name}", style = MaterialTheme.typography.labelMedium, color = c.purple900)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionLabel("Collected", trailing = "Tap to share")
            }
        }
        items(s.cards, key = { it.id }) { card ->
            FormCard(card.form, card.variant, unlocked = true, caption = "${card.variant.name} · ${card.unlockedDate.format(DateTimeFormatter.ofPattern("d MMM"))}", onClick = { selected = card })
        }
        item(span = { GridItemSpan(2) }) {
            Column { Spacer(Modifier.height(10.dp)); SectionLabel("Still to earn") }
        }
        items(SprigForm.entries.filter { it !in unlockedForms }, key = { it.id }) { form ->
            FormCard(form, s.thisWeek, unlocked = false, caption = if (form.isPairOnly) "Pair streak ${SprigForm.DUO_PAIR_STREAK}" else "${form.streakThreshold}-day streak", onClick = onOpenRoadmap)
        }
    }

    selected?.let { card ->
        ShareSheet(card, onDismiss = { selected = null }, onShare = {
            ShareCard.share(ctx, ShareCard.Content(SprigSpec(card.form, SprigPose.CHEER, card.variant), ShareCard.formHeadline(card.form, card.streakAtUnlock), "${card.variant.name} look · ${card.unlockedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}"))
            selected = null
        })
    }
}

@Composable
private fun ShareSheet(card: GalleryCard, onDismiss: () -> Unit, onShare: () -> Unit) {
    val c = KeptTheme.colors
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface2, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SprigView(card.form, SprigPose.CHEER, Modifier.size(180.dp), variant = card.variant)
            Text(card.form.displayName, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Text("${card.variant.name} look · reached at a ${card.streakAtUnlock}-day streak", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(card.form.tagline, style = MaterialTheme.typography.bodySmall, color = c.textMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Share this look", onClick = onShare)
        }
    }
}

@Composable
fun FormCard(form: SprigForm, variant: WeekVariant, unlocked: Boolean, caption: String, onClick: () -> Unit) {
    val c = KeptTheme.colors
    KeptCard(Modifier.fillMaxWidth(), background = if (unlocked) c.surface2 else c.surface1, border = if (unlocked) c.border else null, onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.1f), contentAlignment = Alignment.Center) {
            if (unlocked) {
                SprigView(form, SprigPose.IDLE, Modifier.fillMaxSize(), variant = variant, animate = false)
            } else {
                Box(Modifier.fillMaxSize().alpha(0.18f)) { SprigView(form, SprigPose.IDLE, Modifier.fillMaxSize(), variant = variant, animate = false, showAccessory = false) }
                Icon(Icons.Outlined.Lock, null, tint = c.textMuted, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(form.displayName, style = MaterialTheme.typography.titleSmall, color = if (unlocked) c.textPrimary else c.textMuted)
        Text(caption, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
    }
}

/** Full-screen reveal shown once when a form is unlocked. */
@HiltViewModel
class RevealViewModel @Inject constructor(private val repo: SprigRepository, saved: SavedStateHandle) : ViewModel() {
    private val id: Long = saved.get<Long>("galleryId") ?: 0
    val card = repo.gallery.map { list -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun seen() = viewModelScope.launch { repo.markSeen(id) }
}

@Composable
fun EvolutionRevealScreen(galleryId: Long, onClose: () -> Unit, vm: RevealViewModel = hiltViewModel()) {
    val card by vm.card.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    val ctx = LocalContext.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.seen(); kotlinx.coroutines.delay(250); revealed = true }
    val scale by animateFloatAsState(if (revealed) 1f else 0.4f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "reveal")
    val alpha by animateFloatAsState(if (revealed) 1f else 0f, tween(500), label = "alpha")
    val card0 = card ?: return

    Column(Modifier.fillMaxSize().background(c.purple50).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Pill("New form", background = Color(card0.variant.accentArgb).copy(alpha = 0.18f), foreground = c.purple900)
        Spacer(Modifier.height(18.dp))
        SprigView(card0.form, SprigPose.CHEER, Modifier.size(240.dp).scale(scale), variant = card0.variant)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sprig became ${card0.form.displayName}", style = MaterialTheme.typography.headlineMedium, color = c.purple900, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(card0.form.tagline, style = MaterialTheme.typography.bodyLarge, color = c.purple600, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("${card0.variant.name} look, only this week. It's yours to keep.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Show it off", onClick = {
            ShareCard.share(ctx, ShareCard.Content(SprigSpec(card0.form, SprigPose.CHEER, card0.variant), "Sprig became ${card0.form.displayName}", "${card0.streakAtUnlock} days kept · ${card0.variant.name} look"))
        })
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Keep going", onClick = onClose)
    }
}

@Composable
fun RoadmapScreen(onBack: () -> Unit, vm: GalleryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = KeptTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.textSecondary) }
            Text("How Sprig grows", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text("Every day you keep your habits, the streak grows. Streak milestones evolve Sprig. Miss a day without a shield and Sprig goes back to the start, but every form you've reached stays in your collection.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(18.dp))
        SprigForm.entries.forEach { form ->
            val reached = if (form.isPairOnly) s.state.pairStreak >= SprigForm.DUO_PAIR_STREAK else s.state.streakDays >= form.streakThreshold
            val current = form == s.state.form
            KeptCard(Modifier.fillMaxWidth(), background = if (current) c.purple50 else c.surface2, border = if (current) null else c.border, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).alpha(if (reached) 1f else 0.35f)) { SprigView(form, SprigPose.IDLE, Modifier.fillMaxSize(), variant = s.thisWeek, animate = false, showAccessory = false) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(form.displayName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            if (current) { Spacer(Modifier.width(8.dp)); Pill("Now", background = c.purple100, foreground = c.purple900) }
                        }
                        Text(if (form.isPairOnly) "Pair streak of ${SprigForm.DUO_PAIR_STREAK} with a buddy" else if (form.streakThreshold == 0) "Where everyone starts" else "${form.streakThreshold}-day streak", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Text(form.tagline, style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}
