package com.example.kept.feature.lock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.MainActivity
import com.example.kept.core.analytics.Screens
import com.example.kept.core.ui.KeptTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Shown over any locked app. Single instance, excluded from recents. Back goes to the launcher,
 * never back into the locked app.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    private val vm: LockViewModel by viewModels()
    private var blockedPackage by mutableStateOf("")
    private var blockedLabel by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            val s by vm.state.collectAsStateWithLifecycle()
            var breaking by androidx.compose.runtime.remember { mutableStateOf(false) }
            var breakStartedAt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
            // How long the break screen was held open. The countdown only unlocks the button after
            // a minute, so this says how much longer than that the user sat with it (issue #10).
            fun secondsWaited() = ((SystemClock.elapsedRealtime() - breakStartedAt) / 1000L).toInt()

            // This activity is its own surface: no NavController reaches it, so the two screens it
            // can show report themselves (issue #10).
            LaunchedEffect(breaking) {
                if (breaking) {
                    breakStartedAt = SystemClock.elapsedRealtime()
                    vm.analytics.capture("lock_break_started")
                    vm.analytics.screen(Screens.BREAK)
                } else {
                    vm.analytics.screen(Screens.LOCK)
                }
            }

            // Lock lifted (habits done, break granted, window ended) or this very package added as
            // an exception from Settings (issue #4): get out of the way. Finishing the day is the
            // one case that goes to KEPT instead of the launcher, because the celebration is
            // waiting on Home and this screen cannot show it (issue #9).
            LaunchedEffect(s.shouldDismiss) {
                if (s.shouldDismiss) { if (s.finishedToday) openApp("home") else goHome() }
            }

            val cancelBreak = {
                vm.analytics.capture("lock_break_cancelled")
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
                        blockedLabel = blockedLabel,
                        onComplete = { vm.complete(it) },
                        onBreak = { breaking = true },
                        onEmergency = { dial() },
                        onOpenKept = { openApp(null) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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

    private fun openApp(route: String?) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            route?.let { putExtra(EXTRA_DEEP_ROUTE, it) }
        })
        finishAndRemoveTask()
    }

    private fun dial() {
        // ACTION_DIAL needs no permission and the dialer is always in the hard allowlist.
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        const val EXTRA_PKG = "blocked_pkg"
        const val EXTRA_LABEL = "blocked_label"
        const val EXTRA_DEEP_ROUTE = "route"

        fun show(ctx: Context, pkg: String, label: String) {
            ctx.startActivity(
                Intent(ctx, LockActivity::class.java)
                    .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_LABEL, label)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }
}
