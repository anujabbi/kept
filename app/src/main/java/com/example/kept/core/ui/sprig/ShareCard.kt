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
import androidx.core.content.FileProvider
import com.example.kept.core.domain.SprigForm
import java.io.File
import java.io.FileOutputStream

/** Renders a 1080x1350 share card and hands it to the system share sheet. */
object ShareCard {

    data class Content(
        val spec: SprigSpec,
        val headline: String,
        val subline: String,
        val footer: String = "KEPT · Keep Every Promise Today",
    )

    fun render(content: Content): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val ac = android.graphics.Canvas(bmp)
        val accent = content.spec.variant.accentArgb.toInt()

        // Background: soft surface with an accent wash.
        ac.drawColor(0xFFF7F6F3.toInt())
        val wash = Paint().apply { color = accent; alpha = 28 }
        ac.drawRoundRect(60f, 60f, w - 60f, h - 60f, 48f, 48f, wash)
        val card = Paint().apply { color = 0xFFFFFFFF.toInt() }
        ac.drawRoundRect(90f, 90f, w - 90f, h - 90f, 40f, 40f, card)

        // Sprig
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, Canvas(ac), Size(w.toFloat(), h.toFloat())) {
            translate(190f, 200f) { drawSprig(content.spec, Size(700f, 680f)) }
        }

        val ink = Paint().apply { color = 0xFF1F1E1D.toInt(); isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        ink.textSize = 72f
        ac.drawText(content.headline, w / 2f, 980f, ink)
        val sec = Paint().apply { color = accent; isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 40f; typeface = Typeface.DEFAULT }
        ac.drawText(content.subline, w / 2f, 1045f, sec)
        val foot = Paint().apply { color = 0xFF9A9892.toInt(); isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 30f; letterSpacing = 0.08f }
        ac.drawText(content.footer, w / 2f, 1200f, foot)
        return bmp
    }

    fun share(ctx: Context, content: Content, chooserTitle: String = "Share") {
        val bmp = render(content)
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
    }

    fun formHeadline(form: SprigForm, streak: Int): String = when (form) {
        SprigForm.SPRIG -> "Day $streak with Sprig"
        else -> "Sprig became ${form.displayName}"
    }
}
