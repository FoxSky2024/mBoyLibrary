package app.gbalibrary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

/** Portadas: la ultima captura de pantalla del juego (240x160) guardada al salir. */
object Covers {
    private fun dir(ctx: Context) = File(ctx.filesDir, "covers").apply { mkdirs() }

    fun file(ctx: Context, id: String) = File(dir(ctx), "$id.png")

    fun load(ctx: Context, id: String): ImageBitmap? {
        val f = file(ctx, id)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.asImageBitmap()
    }

    fun save(ctx: Context, id: String, argb: IntArray) {
        val bmp = Bitmap.createBitmap(argb, 240, 160, Bitmap.Config.ARGB_8888)
        FileOutputStream(file(ctx, id)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** True si la captura es (casi seguro) un frame negro, para no pisar una buena portada. */
    fun isBlank(argb: IntArray): Boolean {
        var i = 0
        while (i < argb.size) {
            if ((argb[i] and 0xFFFFFF) != 0) return false
            i += 13
        }
        return true
    }
}
