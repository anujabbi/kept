package com.example.kept

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.notify.KeptNotifications
import com.example.kept.core.ui.KeptTheme
import com.example.kept.feature.app.AppViewModel
import com.example.kept.feature.app.KeptApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val start by vm.startDestination.collectAsStateWithLifecycle()
            KeptTheme {
                start?.let { s ->
                    // Re-create the nav graph if the start destination changes (e.g. after seeding).
                    androidx.compose.runtime.key(s) {
                        KeptApp(startDestination = s, pendingRoute = pendingRoute, consumeRoute = { pendingRoute = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("seed", false) == true && BuildConfig.DEBUG_SEED) vm.seedDemo()
        intent?.getStringExtra(KeptNotifications.EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
