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
 * Imagenes: portada y fondo de cada juego (elegidas, pegadas por URL o descargadas), avatar y fondo
 * de la biblioteca. Viven en GBALibrary/Covers y se guardan reducidas (JPEG), asi no dependen de
 * permisos ni de la galeria.
 */
object Covers {
    private fun dir(ctx: Context) = DataDir.covers(ctx)

    /** Cache de descargas (indices de Libretro, portadas ya bajadas) para no repetir peticiones. */
    fun cacheDir(ctx: Context) = File(dir(ctx), ".cache").apply { mkdirs() }

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
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSide) }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            writeJpeg(bmp, dest, maxSide)
        } catch (e: Exception) {
            false
        }
    }

    /** Igual que [import] pero con los bytes de una imagen ya descargada. */
    fun importBytes(bytes: ByteArray, dest: File, maxSide: Int): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSide) }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false
            writeJpeg(bmp, dest, maxSide)
        } catch (e: Exception) {
            false
        }
    }

    private fun sampleFor(w: Int, h: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun writeJpeg(bmp: Bitmap, dest: File, maxSide: Int): Boolean {
        val longest = maxOf(bmp.width, bmp.height)
        val out = if (longest > maxSide) {
            val k = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
        } else bmp
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return true
    }

    fun load(file: File): ImageBitmap? {
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.asImageBitmap()
    }

    fun deleteFor(ctx: Context, id: String) {
        coverFile(ctx, id).delete()
        backgroundFile(ctx, id).delete()
    }
}
