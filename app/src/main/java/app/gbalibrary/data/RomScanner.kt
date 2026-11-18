package app.gbalibrary.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

object RomScanner {
    private val EXTENSIONS = setOf("gba", "agb", "gbc", "gb", "nds")

    /** Unicas extensiones que se miran; todo lo demas (imagenes, videos, musica, txt, pdf...) se ignora sin abrirlo. */
    private val WANTED = EXTENSIONS + setOf("zip", "7z")

    class ScanResult(val removedIds: List<String>)

    private class Child(val id: String, val name: String, val size: Long, val modified: Long)

    /**
     * Escaneo rapido de una carpeta SAF: una sola consulta por carpeta (DocumentsContract, mucho mas
     * rapido que DocumentFile), filtra por extension por el nombre sin abrir nada, entrega los juegos
     * por tandas ([onBatch]) a medida que los encuentra y no vuelve a leer lo que ya conoce
     * (ver [ScanIndex]). Devuelve los juegos cuyo archivo original ya no existe.
     */
    suspend fun scanTree(ctx: Context, tree: Uri, onBatch: suspend (List<Game>) -> Unit): ScanResult = withContext(Dispatchers.IO) {
        val index = ScanIndex.load(ctx)
        val store = GameStore.get(ctx)
        val prefix = tree.toString() + "/document/"
        val present = HashSet<String>()
        var listedOk = true
        val batch = ArrayList<Game>()

        suspend fun flush() {
            if (batch.isNotEmpty()) {
                val copy = ArrayList(batch)
                batch.clear()
                onBatch(copy)
            }
        }

        suspend fun walk(docId: String, depth: Int) {
            if (depth > 8) return
            val dirs = ArrayList<String>()
            val files = ArrayList<Child>()
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
                val cols = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
                ctx.contentResolver.query(childrenUri, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            dirs.add(id)
                        } else if (name.substringAfterLast('.', "").lowercase() in WANTED) {
                            files.add(Child(id, name, if (c.isNull(3)) -1L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4)))
                        }
                    }
                } ?: run { listedOk = false }
            } catch (e: Exception) {
                listedOk = false
            }
            for (f in files) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, f.id)
                val key = docUri.toString()
                present.add(key)
                val sig = "${f.size}:${f.modified}"
                val known = index.entries[key]
                // Ya leido y sin cambios (y sus juegos siguen en la biblioteca): no se vuelve a abrir.
                if (known != null && known.sig == sig && known.ids.all { store.get(it) != null }) continue
                val ext = f.name.substringAfterLast('.', "").lowercase()
                val games = try {
                    if (ext in EXTENSIONS) listOfNotNull(fromUri(ctx, docUri, f.name, f.size)) else fromArchive(ctx, docUri)
                } catch (e: Exception) {
                    emptyList()
                }
                index.entries[key] = ScanIndex.Entry(sig, games.map { it.id })
                batch.addAll(games)
                if (batch.size >= 3) flush()
            }
            flush()
            for (d in dirs) walk(d, depth + 1)
        }

        val removed = ArrayList<String>()
        try {
            walk(DocumentsContract.getTreeDocumentId(tree), 0)
            flush()
            if (listedOk) {
                val gone = index.entries.keys.filter { it.startsWith(prefix) && it !in present }
                for (k in gone) {
                    index.entries.remove(k)?.ids?.let { removed.addAll(it) }
                }
            }
        } catch (e: Exception) {
            // escaneo interrumpido: no se borra nada
        }
        index.save()
        ScanResult(removed)
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
            val game = Game(
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
            // Se copia a GBALibrary/Games (la copia es la ROM ya descomprimida).
            RomImport.withCopy(ctx, game) { dest -> f.copyTo(dest, overwrite = true); true }
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
        val game = Game(
            id = id,
            title = RomHeader.cleanTitle(fileName).ifBlank { info?.title ?: fileName },
            headerTitle = info?.title ?: "",
            code = info?.code ?: "",
            uri = uri.toString(),
            fileName = fileName,
            sizeBytes = size,
            addedAt = System.currentTimeMillis(),
        )
        // Se copia a GBALibrary/Games (no solo se referencia).
        return RomImport.withCopy(ctx, game) { dest ->
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } != null
            } catch (e: Exception) {
                false
            }
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
