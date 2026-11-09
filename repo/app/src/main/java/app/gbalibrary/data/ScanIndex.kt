package app.gbalibrary.data

import android.content.Context
import java.io.File

/**
 * Recuerda que archivos de las carpetas ya se leyeron (por tamano + fecha de modificacion), para que
 * "Volver a escanear" solo procese archivos nuevos o cambiados y detecte los borrados.
 * Formato: una linea por archivo: uri \t firma \t id1,id2
 */
class ScanIndex private constructor(private val file: File) {
    class Entry(val sig: String, val ids: List<String>)

    val entries = LinkedHashMap<String, Entry>()

    fun save() {
        try {
            file.writeText(entries.entries.joinToString("\n") { (k, v) -> "$k\t${v.sig}\t${v.ids.joinToString(",")}" })
        } catch (e: Exception) {
            // sin indice: el proximo escaneo sera completo
        }
    }

    companion object {
        fun load(ctx: Context): ScanIndex {
            val f = File(ctx.filesDir, "scan_index.tsv")
            val idx = ScanIndex(f)
            try {
                if (f.exists()) f.forEachLine { line ->
                    val p = line.split('\t')
                    if (p.size >= 2) idx.entries[p[0]] = Entry(p[1], if (p.size > 2 && p[2].isNotEmpty()) p[2].split(',') else emptyList())
                }
            } catch (e: Exception) {
                idx.entries.clear()
            }
            return idx
        }
    }
}
