package app.gbalibrary.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Escanea una carpeta SAF buscando juegos de sistemas que se lanzan con un emulador externo
 * (GameCube, Wii, PSP, PS2, 3DS, Switch, Vita...).
 *
 * A diferencia de [RomScanner]: NO lee ni copia el archivo (un .iso pesa gigas); solo guarda la
 * referencia (URI) y se apoya en el nombre de la carpeta para distinguir los .iso compartidos.
 */
object ExternalScanner {
    class Result(val games: List<Game>, val removedIds: List<String>)

    private class Dir(val docId: String, val name: String)

    suspend fun scanTree(ctx: Context, tree: Uri, existing: List<Game>): Result = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Game>()
        var listedOk = true
        val prefix = tree.toString() + "/document/"

        fun walk(docId: String, hint: String, depth: Int) {
            if (depth > 8) return
            val dirs = ArrayList<Dir>()
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
                val cols = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                )
                val ok = ctx.contentResolver.query(childrenUri, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            dirs.add(Dir(id, name))
                            continue
                        }
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext !in Systems.externalExtensions) continue
                        val console = Systems.detectExternal(ext, hint) ?: continue
                        val size = if (c.isNull(3)) -1L else c.getLong(3)
                        val gid = sha1("ext|${console.id}|$name|$size")
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        found[gid] = Game(
                            id = gid,
                            title = cleanTitle(name),
                            headerTitle = "",
                            code = "",
                            uri = uri.toString(),
                            fileName = name,
                            sizeBytes = size.coerceAtLeast(0L),
                            addedAt = System.currentTimeMillis(),
                            external = console.id,
                        )
                    }
                    true
                }
                if (ok == null) listedOk = false
            } catch (e: Exception) {
                listedOk = false
            }
            for (d in dirs) walk(d.docId, "$hint/${d.name}", depth + 1)
        }

        try {
            val rootName = DocumentFile.fromTreeUri(ctx, tree)?.name ?: ""
            walk(DocumentsContract.getTreeDocumentId(tree), rootName, 0)
        } catch (e: Exception) {
            listedOk = false
        }

        // Solo se quitan los que cuelgan de esta carpeta y ya no estan (y solo si se pudo listar todo).
        val removed = if (!listedOk) emptyList() else
            existing.filter { it.external.isNotEmpty() && it.uri.startsWith(prefix) && it.id !in found }.map { it.id }
        Result(found.values.toList(), removed)
    }

    private fun cleanTitle(file: String): String =
        file.substringBeforeLast('.')
            .replace(Regex("""[\[(][^\])]*[\])]"""), "")
            .replace('_', ' ').trim().ifEmpty { file }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
