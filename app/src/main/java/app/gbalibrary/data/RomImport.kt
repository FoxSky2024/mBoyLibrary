package app.gbalibrary.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Copia las ROMs que el usuario agrega a GBALibrary/Games (no solo las referencia), asi sobreviven
 * aunque se borre el original o se pierda el permiso de la carpeta. El nombre del archivo copiado se
 * limpia de caracteres raros ("/", ":", etc.); el nombre original se conserva en Game.fileName.
 */
object RomImport {
    private const val TAG = "RomImport"
    private val BAD = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val ROM_EXT = setOf("gba", "agb", "gbc", "gb", "nds")

    fun safeName(raw: String): String {
        var s = raw.replace(BAD, "_").replace(Regex("""\s+"""), " ").trim().trim('.').trim()
        if (s.length > 100) s = s.take(100).trim()
        return s.ifBlank { "rom" }
    }

    /** Archivo de la ROM copiada, o null si ese juego no tiene copia (se lee desde su uri). */
    fun storedFile(ctx: Context, game: Game): File? =
        if (game.stored.isEmpty()) null else File(DataDir.games(ctx), game.stored).takeIf { it.exists() }

    /**
     * Devuelve [g] con la copia hecha en Games (campos stored/uri/entry). Si el juego ya tenia copia
     * no vuelve a copiar. Si la copia falla (sin espacio, sin permiso) devuelve [g] tal cual y el juego
     * sigue funcionando desde su ubicacion original.
     */
    fun withCopy(ctx: Context, g: Game, copier: (File) -> Boolean): Game {
        return try {
            val existing = GameStore.get(ctx).get(g.id)
            val dir = DataDir.games(ctx)
            if (existing != null && existing.stored.isNotEmpty() && File(dir, existing.stored).exists()) {
                return g.copy(stored = existing.stored, uri = existing.uri, entry = "")
            }

            val rawExt = g.fileName.substringAfterLast('.', "").lowercase()
            val ext = if (rawExt in ROM_EXT) rawExt else "gba"
            val base = safeName(g.fileName.substringBeforeLast('.', g.fileName))
            var dest = File(dir, "$base.$ext")
            if (dest.exists()) {
                // Mismo nombre: se distingue con el id; si ese ya existe, es la misma ROM copiada antes.
                dest = File(dir, "$base [${g.id.take(8)}].$ext")
            }
            if (!dest.exists()) {
                val tmp = File(dir, dest.name + ".part")
                if (!copier(tmp) || !tmp.renameTo(dest)) {
                    tmp.delete()
                    Log.w(TAG, "No se pudo copiar ${g.fileName} a Games; se usa el original")
                    return g
                }
            }
            g.copy(stored = dest.name, uri = Uri.fromFile(dest).toString(), entry = "")
        } catch (e: Exception) {
            Log.w(TAG, "Copia fallida", e)
            g
        }
    }
}
