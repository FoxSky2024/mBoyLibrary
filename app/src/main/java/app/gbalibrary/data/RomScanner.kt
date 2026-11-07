package app.gbalibrary.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

object RomScanner {
    private val EXTENSIONS = setOf("gba", "agb", "gbc", "gb")

    /** Recorre recursivamente una carpeta elegida con el selector de Android (SAF). */
    suspend fun scanTree(ctx: Context, tree: Uri): List<Game> = withContext(Dispatchers.IO) {
        val out = ArrayList<Game>()
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return@withContext out

        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > 8) return
            val children = try {
                dir.listFiles()
            } catch (e: Exception) {
                emptyArray<DocumentFile>()
            }
            for (f in children) {
                if (f.isDirectory) {
                    walk(f, depth + 1)
                } else {
                    val name = f.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in EXTENSIONS) {
                        fromUri(ctx, f.uri, name, f.length())?.let { out.add(it) }
                    } else if (ext == "zip" || ext == "7z") {
                        out.addAll(fromArchive(ctx, f.uri))
                    }
                }
            }
        }
        walk(root, 0)
        out
    }

    suspend fun fromUris(ctx: Context, uris: List<Uri>): List<Game> = withContext(Dispatchers.IO) {
        uris.flatMap { u ->
            if (Archive.kind(ctx, u) != Archive.Kind.NONE) fromArchive(ctx, u)
            else listOfNotNull(fromUri(ctx, u))
        }
    }

    /** Todas las ROMs de un .zip/.7z (sin selector; para escaneo de carpetas). */
    fun fromArchive(ctx: Context, uri: Uri): List<Game> =
        Archive.listRoms(ctx, uri).mapNotNull { fromArchiveEntry(ctx, uri, it.name) }

    /** Identifica una ROM concreta dentro de un archivo: se descomprime a cache, se lee y se borra. */
    fun fromArchiveEntry(ctx: Context, uri: Uri, entry: String): Game? {
        val f = Archive.extractToCache(ctx, uri, entry) ?: return null
        return try {
            val head = ByteArray(1 shl 20)
            val read = f.inputStream().use { readFully(it, head) }
            if (read < 0xC0) return null
            val size = f.length()
            val md = MessageDigest.getInstance("SHA-1")
            md.update(head, 0, read)
            md.update(size.toString().toByteArray())
            val id = md.digest().joinToString("") { "%02x".format(it) }
            val info = RomHeader.parse(head, read)
            val shown = entry.substringAfterLast('/').substringAfterLast('\\')
            Game(
                id = id,
                title = RomHeader.cleanTitle(shown).ifBlank { info?.title ?: shown },
                headerTitle = info?.title ?: "",
                code = info?.code ?: "",
                uri = uri.toString(),
                fileName = shown,
                sizeBytes = size,
                addedAt = System.currentTimeMillis(),
                entry = entry,
            )
        } finally {
            f.delete()
        }
    }

    /** Lee solo la cabecera y 1 MB para identificar la ROM; no carga el archivo entero. */
    fun fromUri(ctx: Context, uri: Uri, nameHint: String? = null, sizeHint: Long = -1L): Game? {
        val head = ByteArray(1 shl 20)
        val read = try {
            ctx.contentResolver.openInputStream(uri)?.use { readFully(it, head) }
        } catch (e: Exception) {
            null
        }
        if (read == null || read < 0xC0) return null

        val meta = if (nameHint == null || sizeHint < 0) queryNameSize(ctx, uri) else null
        val fileName = nameHint ?: meta?.first ?: "rom.gba"
        val size = if (sizeHint >= 0) sizeHint else (meta?.second?.takeIf { it >= 0 } ?: read.toLong())

        val md = MessageDigest.getInstance("SHA-1")
        md.update(head, 0, read)
        md.update(size.toString().toByteArray())
        val id = md.digest().joinToString("") { "%02x".format(it) }

        val info = RomHeader.parse(head, read)
        return Game(
            id = id,
            title = RomHeader.cleanTitle(fileName).ifBlank { info?.title ?: fileName },
            headerTitle = info?.title ?: "",
            code = info?.code ?: "",
            uri = uri.toString(),
            fileName = fileName,
            sizeBytes = size,
            addedAt = System.currentTimeMillis(),
        )
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

    private fun queryNameSize(ctx: Context, uri: Uri): Pair<String, Long>? = try {
        ctx.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                Pair(c.getString(0) ?: "rom.gba", if (c.isNull(1)) -1L else c.getLong(1))
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}
