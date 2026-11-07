package app.gbalibrary.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * Soporte de .zip (java.util.zip) y .7z (commons-compress). Las ROMs se descomprimen SOLO en
 * la cache de la app (cacheDir/roms) y se borran al salir del juego / de la app.
 */
object Archive {
    private const val TAG = "Archive"
    private val ROM_EXT = setOf("gba", "agb", "gbc", "gb")

    enum class Kind { NONE, ZIP, SEVENZ }

    class Entry(val name: String, val size: Long) {
        /** Nombre sin carpetas, para mostrar. */
        val display: String get() = name.substringAfterLast('/').substringAfterLast('\\')
    }

    fun isRomName(name: String) = name.substringAfterLast('.', "").lowercase() in ROM_EXT

    /** Detecta el tipo mirando los primeros bytes (no depende del nombre). */
    fun kind(ctx: Context, uri: Uri): Kind {
        val b = ByteArray(6)
        val n = try {
            ctx.contentResolver.openInputStream(uri)?.use { readFully(it, b) } ?: 0
        } catch (e: Exception) {
            0
        }
        if (n >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() &&
            (b[2] == 3.toByte() || b[2] == 5.toByte() || b[2] == 7.toByte())
        ) return Kind.ZIP
        if (n >= 6 && b[0] == 0x37.toByte() && b[1] == 0x7A.toByte() && b[2] == 0xBC.toByte() &&
            b[3] == 0xAF.toByte() && b[4] == 0x27.toByte() && b[5] == 0x1C.toByte()
        ) return Kind.SEVENZ
        return Kind.NONE
    }

    fun nameLooksArchive(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("zip", "7z")

    // ---- Listado ----

    fun listRoms(ctx: Context, uri: Uri): List<Entry> = try {
        when (kind(ctx, uri)) {
            Kind.ZIP -> listZip(ctx, uri)
            Kind.SEVENZ -> list7z(ctx, uri)
            Kind.NONE -> emptyList()
        }
    } catch (t: Throwable) {
        Log.w(TAG, "No se pudo listar el archivo", t)
        emptyList()
    }

    private fun <T> withZip(ctx: Context, uri: Uri, block: (ZipInputStream) -> T): T {
        var last: Throwable? = null
        for (cs in listOf(Charsets.UTF_8, Charset.forName("ISO-8859-1"))) {
            try {
                val raw = ctx.contentResolver.openInputStream(uri) ?: error("sin flujo")
                raw.use { return block(ZipInputStream(BufferedInputStream(it, 1 shl 16), cs)) }
            } catch (t: IllegalArgumentException) {
                last = t
            }
        }
        throw last ?: IllegalStateException("zip ilegible")
    }

    private fun listZip(ctx: Context, uri: Uri): List<Entry> = withZip(ctx, uri) { zin ->
        val out = ArrayList<Entry>()
        while (true) {
            val e = zin.nextEntry ?: break
            if (!e.isDirectory && isRomName(e.name)) out.add(Entry(e.name, e.size))
        }
        out
    }

    private fun list7z(ctx: Context, uri: Uri): List<Entry> {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        SevenZFile(SeekableInMemoryByteChannel(bytes)).use { sz ->
            val out = ArrayList<Entry>()
            while (true) {
                val e = sz.nextEntry ?: break
                if (!e.isDirectory && isRomName(e.name)) out.add(Entry(e.name, e.size))
            }
            return out
        }
    }

    // ---- Extraccion ----

    private fun cacheDir(ctx: Context) = File(ctx.cacheDir, "roms").apply { mkdirs() }

    /** Descomprime la entrada [entry] del archivo a la cache y devuelve la ROM (.gba/.gbc/.gb) o null. */
    fun extractToCache(ctx: Context, uri: Uri, entry: String): File? = try {
        val dest = File(cacheDir(ctx), "rom_${(uri.toString() + "|" + entry).hashCode().toUInt()}.${entry.substringAfterLast('.', "gba").lowercase()}")
        val ok = when (kind(ctx, uri)) {
            Kind.ZIP -> extractZip(ctx, uri, entry, dest)
            Kind.SEVENZ -> extract7z(ctx, uri, entry, dest)
            Kind.NONE -> false
        }
        if (ok) dest else { dest.delete(); null }
    } catch (t: Throwable) {
        Log.w(TAG, "No se pudo descomprimir", t)
        null
    }

    private fun extractZip(ctx: Context, uri: Uri, entry: String, dest: File): Boolean =
        withZip(ctx, uri) { zin ->
            var found = false
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name == entry) {
                    dest.outputStream().use { o -> zin.copyTo(o) }
                    found = true
                    break
                }
            }
            found
        }

    private fun extract7z(ctx: Context, uri: Uri, entry: String, dest: File): Boolean {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        SevenZFile(SeekableInMemoryByteChannel(bytes)).use { sz ->
            while (true) {
                val e = sz.nextEntry ?: break
                if (e.name == entry) {
                    dest.outputStream().use { o ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = sz.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                        }
                    }
                    return true
                }
            }
        }
        return false
    }

    /** Borra todo lo descomprimido (se llama al cerrar el juego y al salir de la app). */
    fun clearCache(ctx: Context) {
        try {
            File(ctx.cacheDir, "roms").listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            // ignorado
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = input.read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return total
    }
}
