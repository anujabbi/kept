package com.example.kept.core.ui.sprig

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.kept.R
import com.example.kept.core.ui.DaybreakColors
import com.example.kept.core.ui.Sky
import com.example.kept.core.analytics.Analytics
import com.example.kept.core.domain.SprigForm
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream

/** Renders a 1080x1350 share card and hands it to the system share sheet. It shows no app usage. */
object ShareCard {

    /**
     * Reported from here rather than at the three call sites, so a fourth place to share from
     * cannot be added without the event coming with it (issue #10).
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun analytics(): Analytics
    }

    data class Content(
        val spec: SprigSpec,
        val headline: String,
        val subline: String,
        val footer: String = "KEPT · Keep Every Promise Today",
    )

    const val WIDTH = 1080
    const val HEIGHT = 1350

    /**
     * Draws the card: a cream page, a white card with a dawn panel that Sprig stands in, in the
     * outfit of its form, and the headline and subline in ink. [face] is the bold display
     * face; without one (tests, a missing font) the system bold is used.
     */
    fun render(content: Content, face: Typeface? = null): Bitmap {
        val w = WIDTH; val h = HEIGHT
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val ac = android.graphics.Canvas(bmp)
        val bold = face ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        ac.drawColor(DaybreakColors.cream.toArgb())
        val card = Paint().apply { color = DaybreakColors.white.toArgb(); isAntiAlias = true }
        ac.drawRoundRect(72f, 72f, w - 72f, h - 72f, 56f, 56f, card)
        val line = Paint().apply { color = DaybreakColors.line.toArgb(); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f }
        ac.drawRoundRect(72f, 72f, w - 72f, h - 72f, 56f, 56f, line)

        // The dawn panel Sprig stands in. Sprig's body is clipped at the bottom of its box, so
        // the box sits on the panel's bottom edge.
        val panelTop = 120f; val panelBottom = 880f
        val panel = Paint().apply { color = Sky.dawn.toArgb(); isAntiAlias = true }
        ac.drawRoundRect(120f, panelTop, w - 120f, panelBottom, 40f, 40f, panel)

        val sprig = 640f
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, Canvas(ac), Size(w.toFloat(), h.toFloat())) {
            translate((w - sprig) / 2f, panelBottom - sprig) { drawSprig(content.spec, Size(sprig, sprig)) }
        }

        val ink = Paint().apply { color = DaybreakColors.ink.toArgb(); isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = bold }
        ink.textSize = 76f
        ac.drawText(content.headline, w / 2f, 1010f, ink)
        val sec = Paint().apply { color = DaybreakColors.muted.toArgb(); isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 40f; typeface = bold }
        ac.drawText(content.subline, w / 2f, 1080f, sec)
        val foot = Paint().apply { color = DaybreakColors.faint.toArgb(); isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 30f; letterSpacing = 0.08f; typeface = bold }
        ac.drawText(content.footer, w / 2f, 1210f, foot)
        return bmp
    }

    fun share(ctx: Context, content: Content, chooserTitle: String = "Share") {
        val face = runCatching { ResourcesCompat.getFont(ctx, R.font.bricolage_grotesque_display_extrabold) }.getOrNull()
        val bmp = render(content, face)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "kept_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${content.headline} · ${content.subline}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        runCatching {
            EntryPointAccessors.fromApplication(ctx.applicationContext, Deps::class.java).analytics()
        }.getOrNull()?.capture("share_card_generated")
    }

    fun formHeadline(form: SprigForm, streak: Int): String = when (form) {
        SprigForm.SPRIG -> "Day $streak with Sprig"
        else -> "Sprig became ${form.displayName}"
    }
}
