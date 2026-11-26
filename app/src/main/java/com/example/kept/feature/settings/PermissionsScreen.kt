package com.example.kept.feature.settings

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.kept.core.domain.OnboardingCopy
import com.example.kept.core.lock.PermissionKind
import com.example.kept.core.lock.Permissions
import com.example.kept.core.lock.RequiredPermissions
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.DaybreakIcons
import com.example.kept.core.ui.DaybreakType
import com.example.kept.core.ui.PageHeader
import com.example.kept.core.ui.PrimaryButton
import com.example.kept.core.ui.StepPage

private val D = DaybreakColors

data class PermissionCardInfo(val kind: PermissionKind, val title: String, val why: String, val required: Boolean)

/** The cards' copy (`A13-Permissions`), in the order they show. */
val permissionCards = listOf(
    PermissionCardInfo(PermissionKind.USAGE_ACCESS, "Usage access", "Lets KEPT see which app is in front so it can lock it. Nothing is stored or shared.", true),
    PermissionCardInfo(PermissionKind.OVERLAY, "Display over other apps", "Lets the lock screen step in front of a locked app.", true),
    PermissionCardInfo(PermissionKind.NOTIFICATIONS, "Notifications", "Keeps the lock alive with a quiet notification, and brings your daily recap.", true),
    PermissionCardInfo(PermissionKind.BATTERY, "Battery exemption", "Stops Android from closing the lock in the background.", false),
    PermissionCardInfo(PermissionKind.CAMERA, "Camera", "Only used when a habit needs a photo.", false),
)

/** "Let KEPT do its job" is onboarding step 5. */
const val PERMISSIONS_STEP = 6

/** Re-evaluates permission state every time the screen comes back to the foreground. */
@Composable
fun rememberPermissionStates(perms: Permissions, kinds: List<PermissionKind>): Map<PermissionKind, Boolean> {
    var states by remember { mutableStateOf(kinds.associateWith { perms.granted(it) }) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) states = kinds.associateWith { perms.granted(it) } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return states
}

/** Settings → Permissions (`A13-Permissions-settings`): the shared body in settings mode, every permission. */
@Composable
fun PermissionsScreen(onBack: () -> Unit, vm: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()) =
    PermissionsBody(BodyMode.SETTINGS, vm.permissions, PermissionKind.entries, vm::reportPermissionAnswer, onBack = onBack)

/**
 * "Let KEPT do its job" (`A13-Permissions`, prop `mode`): one card per permission in [kinds]
 * with its tag and "Grant", or a green check once granted. Grant opens the real system screen
 * or dialog; the cards re-read [Permissions] on the way back (ON_RESUME, or the dialog's
 * result), and [onAnswer] reports that one tap's answer (issue #10).
 *
 * In onboarding mode the footer's button reads "Continue without the lock" while any of
 * [RequiredPermissions.onboardingGate] is missing, with its red line under it. In settings
 * mode there is no footer and the page ends with the "hollow day" note.
 */
@Composable
fun PermissionsBody(
    mode: BodyMode,
    perms: Permissions,
    kinds: List<PermissionKind>,
    /** Called once per Grant tap, with the answer the user gave (issue #10). */
    onAnswer: (PermissionKind, Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var states by remember { mutableStateOf(kinds.associateWith { perms.granted(it) }) }
    // Which permission the user was last sent to grant. Two of the five are system settings screens
    // rather than dialogs, so the answer only arrives on the way back — matching it to the request
    // is what makes one tap produce exactly one granted-or-denied event (issue #10).
    var asked by remember { mutableStateOf<PermissionKind?>(null) }
    val refresh = {
        states = kinds.associateWith { perms.granted(it) }
        asked?.let { kind ->
            asked = null
            onAnswer(kind, states[kind] == true)
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val requestNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    PermissionsContent(
        mode = mode,
        kinds = kinds,
        granted = { states[it] ?: perms.granted(it) },
        onGrant = { kind ->
            asked = kind
            when (kind) {
                PermissionKind.USAGE_ACCESS -> ctx.startActivity(perms.usageAccessIntent())
                PermissionKind.OVERLAY -> ctx.startActivity(perms.overlayIntent())
                PermissionKind.NOTIFICATIONS -> if (android.os.Build.VERSION.SDK_INT >= 33) requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS) else ctx.startActivity(perms.appSettingsIntent())
                PermissionKind.BATTERY -> ctx.startActivity(perms.batteryIntent())
                PermissionKind.CAMERA -> requestCamera.launch(Manifest.permission.CAMERA)
            }
        },
        onBack = onBack,
        onContinue = onContinue,
    )
}

/** [PermissionsBody] without [Permissions], so tests can render it. */
@Composable
internal fun PermissionsContent(
    mode: BodyMode,
    kinds: List<PermissionKind>,
    granted: (PermissionKind) -> Boolean,
    onGrant: (PermissionKind) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val onboarding = mode == BodyMode.ONBOARDING
    val cards = permissionCards.filter { it.kind in kinds }
    val missing = RequiredPermissions.missing(RequiredPermissions.onboardingGate, granted)
    StepPage(
        header = if (onboarding) PageHeader.Progress(PERMISSIONS_STEP, OnboardingCopy.STEPS, onBack) else PageHeader.Back(onBack),
        title = OnboardingCopy.PERMISSIONS_TITLE,
        line = OnboardingCopy.PERMISSIONS_LINE,
        modifier = Modifier.testTag(if (onboarding) "onboarding_permissions" else "settings_permissions"),
        footer = if (onboarding) {
            {
                PrimaryButton(
                    RequiredPermissions.continueLabel(missing),
                    onClick = onContinue,
                    modifier = Modifier.testTag("onboarding_next"),
                    containerColor = if (missing.isEmpty()) D.ink else D.muted,
                )
                RequiredPermissions.onboardingWarning(missing)?.let {
                    Text(
                        it,
                        Modifier.fillMaxWidth().testTag("onboarding_permissions_warning"),
                        style = DaybreakType.caption, fontWeight = FontWeight.SemiBold, color = D.danger, textAlign = TextAlign.Center,
                    )
                }
            }
        } else null,
    ) {
        items(cards, key = { it.kind.name }) { card ->
            PermissionCard(card, granted(card.kind)) { onGrant(card.kind) }
        }
        if (!onboarding) item(key = "hollow") {
            Text(
                OnboardingCopy.PERMISSIONS_HOLLOW,
                Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
                style = DaybreakType.caption, color = D.muted,
            )
        }
    }
}

/**
 * One permission (`A13`): a white radius-22 card, its border amber while a required one is
 * missing; the name 16/700 with its tag (22dp, 11/800), the reason 13/1.35 in muted; on the
 * right "Grant" (40dp ink pill, 48dp to touch) or a 36dp green check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionCard(card: PermissionCardInfo, granted: Boolean, onGrant: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val required = card.kind in RequiredPermissions.onboardingGate
    val ring = if (!granted && required) D.requiredRing else D.lineSoft
    val (tagBg, tagInk) = when {
        granted -> D.openTint to D.openInk
        required -> D.amberTint to D.amberLabel
        else -> D.purplePale to D.purpleInk
    }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(D.white).border(1.5.dp, ring, shape)
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
            .testTag("permission_${card.kind.eventValue}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(card.title, Modifier.align(Alignment.CenterVertically), style = DaybreakType.rowTitle, color = D.ink)
                Box(
                    Modifier.align(Alignment.CenterVertically).heightIn(min = 22.dp).clip(RoundedCornerShape(11.dp)).background(tagBg).padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        RequiredPermissions.tag(card.kind, granted),
                        Modifier.testTag("permission_tag_${card.kind.eventValue}"),
                        style = DaybreakType.smallLabel.copy(fontSize = 11.sp, lineHeight = 14.sp), fontWeight = FontWeight.ExtraBold, color = tagInk, maxLines = 1,
                    )
                }
            }
            Text(card.why, style = DaybreakType.caption.copy(lineHeight = (13 * 1.35).sp), color = D.muted)
        }
        if (granted) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(D.greenBright).semantics { contentDescription = "${card.title} granted" },
                contentAlignment = Alignment.Center,
            ) { Icon(DaybreakIcons.Check, null, Modifier.size(18.dp), tint = D.white) }
        } else {
            Box(
                Modifier.sizeIn(minHeight = 48.dp, minWidth = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Grant ${card.title.lowercase()}", onClick = onGrant)
                    .testTag("permission_grant_${card.kind.eventValue}"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(D.ink).padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(OnboardingCopy.GRANT, style = DaybreakType.bodySmall, fontWeight = FontWeight.Bold, color = D.cream, maxLines = 1) }
            }
        }
    }
}

fun Context.hasCamera(): Boolean = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
