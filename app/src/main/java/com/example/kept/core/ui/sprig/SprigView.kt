package com.example.kept.core.ui.sprig

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.example.kept.core.domain.Variants
import com.example.kept.core.domain.WeekVariant

@Composable
fun SprigView(
    form: SprigForm,
    pose: SprigPose,
    modifier: Modifier = Modifier,
    variant: WeekVariant = Variants.table[0],
    wilted: Boolean = false,
    animate: Boolean = true,
    showAccessory: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "breathPhase",
    )
    val spec = SprigSpec(form, pose, variant, wilted, if (animate) breath else 0f, showAccessory)
    Canvas(modifier.semantics { contentDescription = "Sprig, ${form.displayName}, ${pose.name.lowercase()}" }) {
        drawSprig(spec)
    }
}
