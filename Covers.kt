package app.gbalibrary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Imagenes personalizadas elegidas por el usuario (portada, fondo del juego, avatar y
 * fondo de la biblioteca). Se copian a la app reducidas, asi no dependen de permisos.
 */
object Covers {
    private fun dir(ctx: Context) = File(ctx.filesDir, "art").apply { mkdirs() }

    fun coverFile(ctx: Context, id: String) = File(dir(ctx), "$id.cover.jpg")
    fun backgroundFile(ctx: Context, id: String) = File(dir(ctx), "$id.bg.jpg")
    fun avatarFile(ctx: Context) = File(dir(ctx), "avatar.jpg")
    fun libraryBgFile(ctx: Context) = File(dir(ctx), "library_bg.jpg")

    /** Lee la imagen de [uri], la reduce a un maximo de [maxSide] px y la guarda como JPEG en [dest]. */
    fun import(ctx: Context, uri: Uri, dest: File, maxSide: Int): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val longest = maxOf(bmp.width, bmp.height)
            val out = if (longest > maxSide) {
                val k = maxSide.toFloat() / longest
                Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            } else bmp
            FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun load(file: File): ImageBitmap? {
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.asImageBitmap()
    }

    /** Version desenfocada barata: se reduce mucho y se vuelve a escalar con suavizado. */
    fun loadBlurred(file: File): ImageBitmap? {
        if (!file.exists()) return null
        val bmp = BitmapFactory.decodeFile(file.path) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, (bmp.width / 24).coerceAtLeast(2), (bmp.height / 24).coerceAtLeast(2), true)
        return small.asImageBitmap()
    }

    fun deleteFor(ctx: Context, id: String) {
        coverFile(ctx, id).delete()
        backgroundFile(ctx, id).delete()
    }
}
