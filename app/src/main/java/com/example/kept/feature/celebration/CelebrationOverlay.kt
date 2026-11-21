package com.example.kept.feature.celebration

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kept.core.ui.PromiseKeptMoment

/**
 * The full-screen "Promise kept." moment in KEPT (issue #9, #130, `A16-Celebrate`). Hosted above
 * the nav graph rather than inside HomeScreen, so it can also cover the Sprig or Settings tab
 * when a day is finished from a notification while one of those is open. A tap anywhere or Back
 * carries on; it no longer leaves on its own (DECISIONS.md, #130).
 */
@Composable
fun CelebrationHost(vm: CelebrationViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val showing by vm.showing.collectAsStateWithLifecycle()
    LaunchedEffect(s.due) { if (s.due) vm.begin() }
    BackHandler(enabled = showing) { vm.dismiss() }
    AnimatedVisibility(showing, enter = fadeIn(tween(220)), exit = fadeOut(tween(260))) {
        PromiseKeptMoment(
            form = s.form,
            streakDays = s.streakDays,
            onCarryOn = vm::dismiss,
            playIntro = vm.introPending,
            onIntroStarted = vm::introStarted,
        )
    }
}
