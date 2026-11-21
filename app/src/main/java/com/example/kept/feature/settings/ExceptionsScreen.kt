package com.example.kept.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.domain.OnboardingCopy
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.KeptSwitch
import com.example.kept.core.ui.cardSegment
import com.example.kept.core.ui.switchState
import com.example.kept.core.ui.PageHeader
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.StepPage

private val D = DaybreakColors

/** Which page a shared body is drawn for: an onboarding step, or its Settings page. */
enum class BodyMode { ONBOARDING, SETTINGS }

/** "What stays open?" is onboarding step 3. */
const val EXCEPTIONS_STEP = 3

/** Settings → What stays open (`A12-Exceptions-settings`): the shared body in settings mode. */
@Composable
fun ExceptionsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) =
    ExceptionsBody(BodyMode.SETTINGS, onBack = onBack, vm = vm)

/**
 * "What stays open?" (`A12-Exceptions`, prop `mode`): the device's always-open apps as chips,
 * then a search field and every app the person may except, each a row with a switch. Reads and
 * writes the real exceptions through [SettingsViewModel]; adding one during an active lock
 * still asks first ([LockChangeConfirmDialog]).
 */
@Composable
fun ExceptionsBody(mode: BodyMode, onBack: () -> Unit, onContinue: () -> Unit = {}, vm: SettingsViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val hard by vm.hardAllowlistLabels.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadApps() }
    ExceptionsContent(
        mode = mode,
        alwaysOpen = hard,
        apps = apps,
        query = query,
        onQuery = { query = it },
        onToggle = { p, allow -> vm.requestToggleException(p.app, allow) },
        onBack = onBack,
        onContinue = onContinue,
    )
    LockChangeConfirmDialog(vm)
}

/** [ExceptionsBody] without the ViewModel, so tests can render it. [apps] is null while loading. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExceptionsContent(
    mode: BodyMode,
    alwaysOpen: List<String>,
    apps: List<PickableApp>?,
    query: String,
    onQuery: (String) -> Unit,
    onToggle: (PickableApp, Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val list = apps ?: emptyList()
    val open = list.count { it.allowed }
    val rows = orderForPicker(list, query)
    val onboarding = mode == BodyMode.ONBOARDING
    StepPage(
        header = if (onboarding) PageHeader.Progress(EXCEPTIONS_STEP, OnboardingCopy.STEPS, onBack) else PageHeader.Back(onBack),
        title = OnboardingCopy.EXCEPTIONS_TITLE,
        line = OnboardingCopy.EXCEPTIONS_LINE,
        modifier = Modifier.testTag(if (onboarding) "onboarding_exceptions" else "settings_exceptions"),
        footer = if (onboarding) {
            { PrimaryButton(OnboardingCopy.CONTINUE, onClick = onContinue, modifier = Modifier.testTag("onboarding_next")) }
        } else null,
        bodySpacing = 0.dp,
    ) {
        item(key = "always") {
            Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(OnboardingCopy.ALWAYS_OPEN, Modifier.padding(horizontal = 4.dp), style = DaybreakType.body, fontWeight = FontWeight.Bold, color = D.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val labels = alwaysOpen.ifEmpty { OnboardingCopy.ALWAYS_OPEN_DEFAULT }
                    labels.take(MAX_CHIPS).forEach { AlwaysOpenChip(it) }
                    if (labels.size > MAX_CHIPS) AlwaysOpenChip("+${labels.size - MAX_CHIPS} more")
                }
            }
        }
        item(key = "exceptions_head") {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(OnboardingCopy.YOUR_EXCEPTIONS, style = DaybreakType.body, fontWeight = FontWeight.Bold, color = D.muted)
                    Text(OnboardingCopy.appsOpen(open), Modifier.testTag("exceptions_count"), style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.muted)
                }
                SearchField(query, onQuery)
                if (apps == null) Text("Loading your apps…", Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = DaybreakType.caption, color = D.muted)
            }
        }
        items(rows, key = { it.app.packageName }) { p ->
            AppRow(p, first = p === rows.first(), last = p === rows.last(), onToggle = { onToggle(p, it) })
        }
    }
}

private const val MAX_CHIPS = 14

/** 32dp, radius 16, green tint, `#1F6B3F` 13/700. */
@Composable
private fun AlwaysOpenChip(label: String) {
    Box(
        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(D.openTint).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = DaybreakType.caption, fontWeight = FontWeight.Bold, color = D.openInk, maxLines = 1) }
}

/** 48dp, radius 16, white with a 1.5dp `#E6DCCE` border, a faint magnifier and "Search apps". */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val style = DaybreakType.body.copy(color = D.ink)
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(D.purple),
        modifier = Modifier.fillMaxWidth().testTag("exceptions_search").semantics { contentDescription = OnboardingCopy.SEARCH_APPS },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(D.white).border(1.5.dp, D.field, shape).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(DaybreakIcons.Search, null, Modifier.size(18.dp), tint = D.faint)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(OnboardingCopy.SEARCH_APPS, style = style, color = D.faint, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/**
 * One app (`A12`): 56dp, its icon on a 32dp radius-10 tile (the initial on purple when the
 * device has no icon for it), name 16/700, "Stays open" in green or "Locks" in muted, and the
 * switch. The whole row is the switch. Rows share one white radius-22 card with 1.5dp rules
 * between them; each row draws its own part of the card so the list stays lazy.
 */
@Composable
private fun AppRow(p: PickableApp, first: Boolean, last: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).cardSegment(first, last)
            .toggleable(value = p.allowed, role = Role.Switch, onValueChange = onToggle)
            .semantics { stateDescription = switchState(p.allowed) }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("exception_${p.app.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTile(p.app.packageName, p.app.label)
        Column(Modifier.weight(1f)) {
            Text(p.app.label, style = DaybreakType.rowTitle, color = D.ink)
            Text(
                if (p.allowed) OnboardingCopy.STAYS_OPEN else OnboardingCopy.LOCKS,
                style = DaybreakType.smallLabel, fontWeight = FontWeight.SemiBold,
                color = if (p.allowed) D.openInk else D.muted,
            )
        }
        KeptSwitch(p.allowed, onCheckedChange = null)
    }
}

@Composable
private fun AppTile(pkg: String, label: String) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()?.let { d ->
            val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            d.setBounds(0, 0, 96, 96)
            d.draw(cv)
            b.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    if (bmp != null) {
        Image(bmp, null, Modifier.size(32.dp).clip(shape))
    } else {
        Box(Modifier.size(32.dp).clip(shape).background(D.purple), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), style = DaybreakType.bodySmall.copy(fontSize = 14.sp, lineHeight = 16.sp), fontWeight = FontWeight.ExtraBold, color = D.white, maxLines = 1)
        }
    }
}
