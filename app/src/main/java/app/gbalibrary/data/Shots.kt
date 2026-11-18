package app.gbalibrary.data

import android.content.Context
import java.io.File

/**
 * Capturas de pantalla: GBALibrary/Screenshots/[Nombre del juego]/<hora>_<id>.png.
 * El id va en el nombre del archivo, asi la galeria de cada juego sigue funcionando aunque
 * se renombre el juego.
 */
object Shots {
    class Shot(val file: File, val gameId: String, val gameName: String, val time: Long)

    fun newFile(ctx: Context, game: Game): File {
        val dir = File(DataDir.screenshots(ctx), RomImport.safeName(game.title)).apply { mkdirs() }
        return File(dir, "${System.currentTimeMillis()}_${game.id}.png")
    }

    /** Todas las capturas (o solo las de [gameId]), de la mas nueva a la mas vieja. */
    fun list(ctx: Context, gameId: String? = null): List<Shot> {
        val out = ArrayList<Shot>()
        val root = DataDir.screenshots(ctx)
        root.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            dir.listFiles()?.forEach { f ->
                if (!f.isFile || !f.name.endsWith(".png", true)) return@forEach
                val stem = f.name.substringBeforeLast('.')
                val time = stem.substringBefore('_').toLongOrNull() ?: f.lastModified()
                val id = if ('_' in stem) stem.substringAfter('_') else ""
                if (gameId == null || id == gameId) out.add(Shot(f, id, dir.name, time))
            }
        }
        return out.sortedByDescending { it.time }
    }

    fun delete(shot: Shot): Boolean {
        val ok = shot.file.delete()
        // Si la carpeta del juego quedo vacia, se quita.
        shot.file.parentFile?.let { if (it.listFiles().isNullOrEmpty()) it.delete() }
        return ok
    }
}
