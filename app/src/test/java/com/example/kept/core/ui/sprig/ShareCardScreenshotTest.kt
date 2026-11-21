package com.example.kept.core.ui.sprig

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigPose
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The share bitmap, drawn by the same Sprig renderer in a bitmap-backed DrawScope. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w412dp-h915dp-420dpi")
class ShareCardScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private fun card(name: String, content: ShareCard.Content) {
        val bmp = ShareCard.render(content)
        assertEquals(ShareCard.WIDTH, bmp.width)
        assertEquals(ShareCard.HEIGHT, bmp.height)
        rule.setContent { Image(bmp.asImageBitmap(), null, Modifier.width(360.dp)) }
        rule.onRoot().captureRoboImage("src/test/screenshots/share_card_$name.png")
    }

    @Test fun recapKept() = card(
        "recap_kept",
        ShareCard.Content(SprigSpec(SprigForm.BLOOM, SprigPose.CHEER), "Day 9 kept", "3 of 3 habits"),
    )

    @Test fun celestialForm() = card(
        "celestial",
        ShareCard.Content(SprigSpec(SprigForm.CELESTIAL, SprigPose.CHEER), ShareCard.formHeadline(SprigForm.CELESTIAL, 100), "100 days kept"),
    )
}
