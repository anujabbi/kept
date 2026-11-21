package com.example.kept.feature.lock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.example.kept.core.ui.enableDaybreakEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.ui.KeptTheme
import com.example.kept.core.ui.PromiseKeptMoment
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDaybreakEdgeToEdge()
        // Only a fresh start reads the intent. A rebuilt activity (configuration change, or process
        // death) keeps what the ViewModel saved, which is the intent that raised the screen last.
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            val live by vm.state.collectAsStateWithLifecycle()
            val blockedLabel by vm.blockedLabel.collectAsStateWithLifecycle()
            val s = vm.shown(live)
            // The break screen's state lives in the ViewModel, so a rotation keeps it and its countdown.
            val breaking = vm.breakTimer.breaking
            // A day finished here in time plays "Promise kept" first; carrying on shows the open state.
            val momentUp by vm.moment.collectAsStateWithLifecycle()
            val moment = (momentUp || vm.finishing) && s.open && !breaking

            // This activity is its own surface, so the screens it shows report themselves. The break
            // screen reports itself once, when it opens (`LockViewModel.startBreak`).
            LaunchedEffect(breaking) {
                if (!breaking) vm.reportLockScreenViewed()
            }

            // Lock lifted by a break, the window ending, or this very package added as an exception
            // from Settings (issue #4): get out of the way. Finishing the day in time does not
            // dismiss: the screen turns into its open state and "Open {App}" leaves (issue #127).
            LaunchedEffect(s.shouldDismiss) {
                if (s.shouldDismiss) goHome()
            }

            BackHandler {
                when {
                    breaking -> vm.cancelBreak()
                    moment -> vm.carryOn()
                    else -> goHome()
                }
            }

            KeptTheme {
                if (breaking) {
                    BreakLockScreen(
                        state = s,
                        secondsLeft = vm.breakTimer.secondsLeft,
                        onCancel = { vm.cancelBreak() },
                        onConfirm = { vm.breakLock { goHome() } },
                        playIntro = !vm.breakTimer.introPlayed,
                        onIntroStarted = { vm.breakTimer.markIntroPlayed() },
                    )
                } else if (moment) {
                    PromiseKeptMoment(
                        form = s.sprig.form,
                        streakDays = s.sprig.streakDays,
                        onCarryOn = { vm.carryOn() },
                        playIntro = vm.momentIntroPending,
                        onIntroStarted = { vm.momentIntroStarted() },
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
                                onBreak = { vm.startBreak() },
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
        vm.onShown()
        readIntent(intent)
    }

    private fun readIntent(i: Intent?) {
        // The ViewModel learns which package this screen is covering, so it can drop the screen the
        // moment that package stops being blocked (issue #4).
        vm.setBlocked(i?.getStringExtra(EXTRA_PKG), i?.getStringExtra(EXTRA_LABEL))
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
        val launch = vm.blockedPackageNow?.takeIf { it.isNotBlank() }?.let { packageManager.getLaunchIntentForPackage(it) }
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
