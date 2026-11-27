package com.example.kept.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kept.core.domain.AccountCopy

/**
 * Google's "G" mark, unaltered, from Google's sign-in branding guidelines (#193). Its four
 * colours are Google's brand colours, not Daybreak tokens, and appear nowhere else.
 */
val GoogleMark: ImageVector = ImageVector.Builder("GoogleMark", 48.dp, 48.dp, 48f, 48f).apply {
    fun part(color: Long, d: String) = addPath(addPathNodes(d), fill = SolidColor(Color(color)))
    part(0xFFEA4335, "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z")
    part(0xFF4285F4, "M46.98 24.55c0-1.57-0.15-3.09-0.38-4.55H24v9.02h12.94c-0.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z")
    part(0xFFFBBC05, "M10.53 28.59c-0.48-1.45-0.76-2.99-0.76-4.59s0.27-3.14 0.76-4.59l-7.98-6.19C0.92 16.46 0 20.12 0 24c0 3.88 0.92 7.54 2.56 10.78l7.97-6.19z")
    part(0xFF34A853, "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z")
}.build()

/**
 * "Sign in with Google" (#193): Daybreak's secondary button (56dp, fully round, 2dp ink outline)
 * on white, with Google's G at 20dp, as Google's guidelines allow for a custom button. While
 * [busy] it reads "Signing in…" and cannot be tapped again.
 */
@Composable
fun GoogleSignInButton(busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val d = DaybreakColors
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape,
        border = BorderStroke(2.dp, if (busy) d.disabled else d.ink),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = d.white, contentColor = d.ink, disabledContainerColor = d.white, disabledContentColor = d.muted),
    ) {
        Icon(GoogleMark, null, Modifier.size(20.dp), tint = Color.Unspecified)
        Spacer(Modifier.width(10.dp))
        Text(if (busy) AccountCopy.SIGNING_IN else AccountCopy.SIGN_IN, style = DaybreakType.button, textAlign = TextAlign.Center)
    }
}
