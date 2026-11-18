package app.gbalibrary.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Carpeta de datos persistente (la fuente de verdad):
 *
 *   /Documents/GBALibrary/Games/       ROMs copiadas
 *   /Documents/GBALibrary/Saves/       .sav de cada juego
 *   /Documents/GBALibrary/States/      save states
 *   /Documents/GBALibrary/Covers/      portadas (descargadas y propias), avatar y fondo
 *   /Documents/GBALibrary/config.json  ajustes (espejo de las preferencias)
 *   /Documents/GBALibrary/library.json biblioteca (horas, ultima vez, veces abierto, favoritos...)
 *
 * Esta carpeta NUNCA se borra desde la app. Si el usuario no concede "acceso a todos los archivos"
 * (Android 11+) o el permiso de almacenamiento (Android 10 o menos), se usa una carpeta de respaldo
 * dentro de Android/data (sobrevive a actualizaciones, pero no a desinstalar) y en cuanto se
 * conceda el permiso se migra todo a /Documents/GBALibrary.
 */
object DataDir {
    private const val TAG = "DataDir"
    const val FOLDER = "GBALibrary"

    @Volatile
    private var rootRef: File? = null

    @Volatile
    var shared: Boolean = false
        private set

    /** Carpeta publica /Documents/GBALibrary. */
    fun sharedRoot(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER)

    /** Carpeta de respaldo (sin permisos especiales). */
    fun fallbackRoot(ctx: Context): File {
        val ext = ctx.getExternalFilesDir(null)
        return if (ext != null) File(ext, FOLDER) else File(ctx.filesDir, FOLDER)
    }

    fun hasStoragePermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun root(ctx: Context): File = rootRef ?: init(ctx)

    fun games(ctx: Context) = sub(ctx, "Games")
    fun saves(ctx: Context) = sub(ctx, "Saves")
    fun states(ctx: Context) = sub(ctx, "States")
    fun covers(ctx: Context) = sub(ctx, "Covers")
    fun screenshots(ctx: Context) = sub(ctx, "Screenshots")
    fun libraryJson(ctx: Context) = File(root(ctx), "library.json")
    fun configJson(ctx: Context) = File(root(ctx), "config.json")

    private fun sub(ctx: Context, name: String) = File(root(ctx), name).apply { mkdirs() }

    /** Elige la carpeta, la crea, y migra datos antiguos. Se llama al arrancar la app. */
    @Synchronized
    fun init(ctx: Context): File {
        rootRef?.let { return it }
        val app = ctx.applicationContext
        var chosen = pickRoot(app)
        ensureLayout(chosen)
        migrateLegacy(app, chosen)
        if (chosen == sharedRootOrNull() && !File(chosen, "library.json").exists()) {
            migrateTree(fallbackRoot(app), chosen)
        }
        rootRef = chosen
        shared = chosen.path == sharedRoot().path
        Log.i(TAG, "Carpeta de datos: ${chosen.path} (compartida=$shared)")
        return chosen
    }

    /**
     * Revisa si ahora hay permiso y por lo tanto la carpeta publica. Devuelve true si la carpeta
     * de datos cambio (hay que recargar biblioteca y ajustes).
     */
    @Synchronized
    fun refresh(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val current = rootRef ?: return false.also { init(app) }
        if (shared) return false
        if (!hasStoragePermission(app)) return false
        val target = sharedRoot()
        if (!canWrite(target)) return false
        ensureLayout(target)
        // Si la carpeta publica ya trae datos (reinstalacion) manda ella; si no, se copia la de respaldo.
        if (!File(target, "library.json").exists()) migrateTree(current, target)
        rootRef = target
        shared = true
        Log.i(TAG, "Carpeta de datos migrada a ${target.path}")
        return true
    }

    private fun sharedRootOrNull(): File? = sharedRoot()

    private fun pickRoot(app: Context): File {
        if (hasStoragePermission(app)) {
            val s = sharedRoot()
            if (canWrite(s)) return s
        }
        return fallbackRoot(app)
    }

    private fun canWrite(dir: File): Boolean = try {
        dir.mkdirs()
        val t = File(dir, ".write_test")
        t.writeText("ok")
        t.delete()
        true
    } catch (e: Exception) {
        false
    }

    private fun ensureLayout(root: File) {
        root.mkdirs()
        listOf("Games", "Saves", "States", "Covers", "Screenshots").forEach { File(root, it).mkdirs() }
    }

    /** Copia [src] dentro de [dst] sin pisar archivos que ya existan. */
    fun migrateTree(src: File, dst: File) {
        if (!src.exists() || src.path == dst.path) return
        try {
            src.walkTopDown().forEach { f ->
                val rel = f.relativeTo(src)
                val target = File(dst, rel.path)
                if (f.isDirectory) {
                    target.mkdirs()
                } else if (!target.exists()) {
                    target.parentFile?.mkdirs()
                    f.copyTo(target)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Migracion parcial desde ${src.path}", e)
        }
    }

    /** Datos de la 0.3.0-beta y anteriores (almacenamiento interno de la app) -> carpeta nueva. */
    private fun migrateLegacy(app: Context, root: File) {
        val oldLib = File(app.filesDir, "library.json")
        if (!oldLib.exists() || File(root, "library.json").exists()) return
        try {
            oldLib.copyTo(File(root, "library.json"))
            migrateTree(File(app.filesDir, "saves"), File(root, "Saves"))
            migrateTree(File(app.filesDir, "states"), File(root, "States"))
            migrateTree(File(app.filesDir, "art"), File(root, "Covers"))
            Log.i(TAG, "Datos de la version anterior migrados a ${root.path}")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo migrar la version anterior", e)
        }
    }
}
