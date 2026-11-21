package com.example.kept.feature.lock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.example.kept.core.ui.enableDaybreakEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.ui.KeptTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Shown over any locked app, in its own task (`singleTask`, empty affinity), excluded from
 * recents. Back goes to the launcher, never back into the locked app. Not `singleInstance`: an
 * activity started for a result from a single-instance activity is forced into a new task and
 * its result is cancelled at once, which would lose the photo a photo habit takes from here.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    private val vm: LockViewModel by viewModels()
    private var blockedPackage by mutableStateOf("")
    private var blockedLabel by mutableStateOf("")

    /** Counts the times this screen was raised; states from an earlier showing are not shown. */
    private var showing by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDaybreakEdgeToEdge()
        readIntent(intent)
        setContent {
            val live by vm.state.collectAsStateWithLifecycle()
            val s = live.forShowing(showing)
            var breaking by remember { mutableStateOf(false) }
            var breakStartedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            // How long the break screen was held open. The countdown only unlocks the button after
            // a minute, so this says how much longer than that the user sat with it (issue #10).
            fun secondsWaited() = ((SystemClock.elapsedRealtime() - breakStartedAt) / 1000L).toInt()

            // This activity is its own surface, so the screens it shows report themselves.
            LaunchedEffect(breaking) {
                if (breaking) {
                    breakStartedAt = SystemClock.elapsedRealtime()
                    vm.reportBreakScreenOpened()
                } else {
                    vm.reportLockScreenViewed()
                }
            }

            // Lock lifted by a break, the window ending, or this very package added as an exception
            // from Settings (issue #4): get out of the way. Finishing the day in time does not
            // dismiss: the screen turns into its open state and "Open {App}" leaves (issue #127).
            LaunchedEffect(s.shouldDismiss) {
                if (s.shouldDismiss) goHome()
            }

            val cancelBreak = {
                vm.reportBreakCancelled()
                breaking = false
            }

            BackHandler { if (breaking) cancelBreak() else goHome() }

            KeptTheme {
                if (breaking) {
                    BreakLockScreen(
                        state = s,
                        onCancel = cancelBreak,
                        onConfirm = { vm.breakLock(secondsWaited()) { goHome() } },
                    )
                } else {
                    LockScreen(
                        state = s,
                        appLabel = blockedLabel,
                        actions = remember(vm) {
                            LockActions(
                                onComplete = { vm.complete(it) },
                                onUndo = { vm.undo(it) },
                                onPhoto = { id, path -> vm.completeWithPhoto(id, path) },
                                onPhone = { dial() },
                                onBreak = { breaking = true },
                                onOpenApp = { openBlockedApp() },
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Raised again over an app, perhaps a day after it was left in its open state: start from
        // a blank state rather than flash the last one (issue #127).
        showing++
        vm.reshow(showing)
        readIntent(intent)
    }

    private fun readIntent(i: Intent?) {
        blockedPackage = i?.getStringExtra(EXTRA_PKG) ?: blockedPackage
        blockedLabel = i?.getStringExtra(EXTRA_LABEL) ?: blockedLabel
        // Tell the ViewModel which package this screen is covering so it can drop the screen the
        // moment that package stops being blocked (issue #4).
        vm.setBlockedPackage(blockedPackage)
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    /**
     * "Open {App}" (issue #127): launch the app this screen was covering and leave. The lock is no
     * longer active once every habit is done (`LockPolicy.isLockActive` needs habits left), so the
     * foreground watcher lets the app through instead of raising this screen again.
     */
    private fun openBlockedApp() {
        val launch = blockedPackage.takeIf { it.isNotBlank() }?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) { goHome(); return }
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    private fun dial() {
        // ACTION_DIAL needs no permission and the dialer is always in the hard allowlist.
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        const val EXTRA_PKG = "blocked_pkg"
        const val EXTRA_LABEL = "blocked_label"

        fun show(ctx: Context, pkg: String, label: String) {
            ctx.startActivity(
                Intent(ctx, LockActivity::class.java)
                    .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_LABEL, label)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }
}
