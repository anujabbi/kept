package com.example.kept.feature.lock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

            // Lock lifted (habits done, break granted, window ended): get out of the way.
            LaunchedEffect(s.lockActive, s.loaded) { if (s.loaded && !s.lockActive) goHome() }

            BackHandler { if (breaking) breaking = false else goHome() }

            KeptTheme {
                if (breaking) {
                    BreakLockScreen(
                        state = s,
                        onCancel = { breaking = false },
                        onConfirm = { vm.breakLock { goHome() } },
                    )
                } else {
                    LockScreen(
                        state = s,
                        blockedLabel = blockedLabel,
                        onDoHabit = { habitId -> openApp("timer/$habitId") },
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
