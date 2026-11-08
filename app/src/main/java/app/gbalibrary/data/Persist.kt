package app.gbalibrary.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Espejo de las preferencias en GBALibrary/config.json y copias de seguridad (zip).
 *
 * - Cada cambio de ajustes se vuelca a config.json (con un pequeno retraso para agrupar cambios).
 * - Al instalar de nuevo (o al conceder el permiso y pasar a la carpeta publica) se lee config.json
 *   y se restauran escala UI, opacidad de paneles, controles personalizados, orden, etc.
 * - No se exportan las ubicaciones que dependen de un permiso de Android que se pierde al reinstalar
 *   (carpetas de ROMs y musica): hay que volver a elegirlas.
 */
object Persist {
    private const val TAG = "Persist"
    private val PREF_FILES = listOf("settings", "library")
    private const val KEY_ROOT = "__cfg_root"
    private val SKIP_KEYS = setOf("musicUri", "musicFolder", "folders")
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    // ---- Exportar ----

    /** Programa (con retraso) la escritura de config.json. */
    fun touch(ctx: Context) {
        val app = ctx.applicationContext
        val r = Runnable { Thread { exportConfig(app) }.start() }
        synchronized(this) {
            pending?.let { handler.removeCallbacks(it) }
            pending = r
        }
        handler.postDelayed(r, 800)
    }

    /** Escribe config.json ya mismo (al salir de una pantalla o antes de un respaldo). */
    fun flush(ctx: Context) {
        val app = ctx.applicationContext
        synchronized(this) {
            pending?.let { handler.removeCallbacks(it) }
            pending = null
        }
        exportConfig(app)
    }

    @Synchronized
    fun exportConfig(ctx: Context): Boolean = try {
        val prefs = JSONObject()
        for (name in PREF_FILES) {
            val sp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in sp.all) {
                if (k.startsWith("__") || k in SKIP_KEYS || v == null) continue
                val e = JSONObject()
                when (v) {
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is Boolean -> e.put("t", "b").put("v", v)
                    is String -> e.put("t", "s").put("v", v)
                    is Set<*> -> e.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                o.put(k, e)
            }
            prefs.put(name, o)
        }
        val root = JSONObject().put("version", 1).put("prefs", prefs)
        val f = DataDir.configJson(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(root.toString(1))
        if (!tmp.renameTo(f)) {
            f.writeText(root.toString(1))
            tmp.delete()
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo escribir config.json", e)
        false
    }

    // ---- Restaurar ----

    /**
     * Si esta carpeta de datos aun no se ha "adoptado" (instalacion nueva o carpeta recien migrada)
     * y trae un config.json, se aplica sobre las preferencias. Devuelve true si se restauro algo.
     */
    @Synchronized
    fun restoreConfigIfNeeded(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val rootPath = DataDir.root(ctx).path
        if (sp.getString(KEY_ROOT, null) == rootPath) return false
        val cfg = DataDir.configJson(ctx)
        val restored = cfg.exists() && applyConfigText(ctx, cfg.readText())
        sp.edit().putString(KEY_ROOT, rootPath).apply()
        if (restored) Log.i(TAG, "Ajustes restaurados desde ${cfg.path}")
        return restored
    }

    /** Aplica config.json (sin condiciones). Lo usa la importacion de una copia de seguridad. */
    @Synchronized
    fun applyConfigFile(ctx: Context): Boolean {
        val cfg = DataDir.configJson(ctx)
        if (!cfg.exists()) return false
        return applyConfigText(ctx, cfg.readText())
    }

    private fun applyConfigText(ctx: Context, text: String): Boolean = try {
        val prefs = JSONObject(text).getJSONObject("prefs")
        for (name in PREF_FILES) {
            val o = prefs.optJSONObject(name) ?: continue
            val ed = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            for (k in o.keys()) {
                if (k.startsWith("__") || k in SKIP_KEYS) continue
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "s" -> ed.putString(k, e.getString("v"))
                    "ss" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
            ed.apply()
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "config.json invalido", e)
        false
    }

    // ---- Copias de seguridad ----

    /** Zip de toda la carpeta de datos. Las ROMs (Games) solo si [includeRoms]. Cierra [out]. */
    fun exportBackup(ctx: Context, out: OutputStream, includeRoms: Boolean) {
        flush(ctx)
        val root = DataDir.root(ctx)
        ZipOutputStream(out.buffered()).use { zip ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(root).path.replace('\\', '/')
                val skip = rel.startsWith("Covers/.cache/") || rel.endsWith(".part") ||
                    rel.endsWith(".tmp") || rel == ".write_test" || (!includeRoms && rel.startsWith("Games/"))
                if (skip) return@forEach
                zip.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Restaura un zip hecho con [exportBackup] encima de la carpeta de datos. Devuelve cuantos archivos. */
    fun importBackup(ctx: Context, input: InputStream): Int {
        val root = DataDir.root(ctx)
        val rootCanon = root.canonicalPath
        var count = 0
        ZipInputStream(input.buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory) continue
                val target = File(root, e.name)
                // Proteccion contra rutas con ".." (zip slip).
                if (!target.canonicalPath.startsWith(rootCanon + File.separator)) continue
                target.parentFile?.mkdirs()
                target.outputStream().use { zin.copyTo(it) }
                count++
            }
        }
        if (count > 0) applyConfigFile(ctx)
        return count
    }
}
