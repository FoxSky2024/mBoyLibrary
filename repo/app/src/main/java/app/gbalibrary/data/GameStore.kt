package app.gbalibrary.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** Biblioteca persistente: library.json dentro de la carpeta de datos (GBALibrary). */
class GameStore private constructor(private val ctx: Context) {
    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games

    private fun file(): File = DataDir.libraryJson(ctx)

    init {
        reload()
    }

    /** Vuelve a leer library.json (al cambiar la carpeta de datos o importar una copia). */
    @Synchronized
    fun reload() {
        try {
            val f = file()
            _games.value = if (f.exists()) {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map { Game.fromJson(arr.getJSONObject(it)) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w("GameStore", "No se pudo leer library.json", e)
        }
    }

    fun get(id: String): Game? = _games.value.firstOrNull { it.id == id }

    @Synchronized
    private fun update(transform: (List<Game>) -> List<Game>) {
        val next = transform(_games.value)
        _games.value = next
        try {
            val f = file()
            f.parentFile?.mkdirs()
            val arr = JSONArray()
            next.forEach { arr.put(it.toJson()) }
            val tmp = File(f.path + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(arr.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w("GameStore", "No se pudo guardar library.json", e)
        }
    }

    private fun modify(id: String, change: (Game) -> Game) =
        update { list -> list.map { if (it.id == id) change(it) else it } }

    /** Agrega los juegos nuevos (por id) y refresca la ruta de los existentes. Devuelve cuantos eran nuevos. */
    fun addScanned(found: List<Game>): Int {
        var added = 0
        update { list ->
            val byId = list.associateBy { it.id }.toMutableMap()
            for (g in found) {
                val old = byId[g.id]
                when {
                    old == null -> {
                        byId[g.id] = g
                        added++
                    }
                    // Ahora hay copia en GBALibrary/Games: pasa a leerse de ahi.
                    g.stored.isNotEmpty() ->
                        byId[g.id] = old.copy(uri = g.uri, fileName = g.fileName, sizeBytes = g.sizeBytes, entry = "", stored = g.stored)
                    // Ya tenia copia: no se pisa con una referencia externa.
                    old.stored.isNotEmpty() -> Unit
                    else ->
                        byId[g.id] = old.copy(uri = g.uri, fileName = g.fileName, sizeBytes = g.sizeBytes, entry = g.entry)
                }
            }
            byId.values.toList()
        }
        return added
    }

    fun setFavorite(id: String, fav: Boolean) = modify(id) { it.copy(favorite = fav) }

    fun rename(id: String, title: String) = modify(id) { it.copy(title = title.trim().ifBlank { it.title }) }

    fun setInCollection(id: String, name: String, inCollection: Boolean) = modify(id) {
        it.copy(collections = if (inCollection) it.collections + name else it.collections - name)
    }

    /** Suma [seconds] al total acumulado (no solo a la ultima sesion) y marca la ultima vez jugado. */
    fun addPlay(id: String, seconds: Long, now: Long) = modify(id) {
        it.copy(playSeconds = it.playSeconds + seconds, lastPlayed = now)
    }

    /** Una apertura mas del juego (veces abierto). */
    fun addLaunch(id: String, now: Long) = modify(id) {
        it.copy(launches = it.launches + 1, lastPlayed = now)
    }

    fun setProgress(id: String, pct: Int) = modify(id) { it.copy(progress = pct.coerceIn(-1, 100)) }

    fun setNote(id: String, note: String) = modify(id) { it.copy(note = note) }

    fun setCover(id: String, has: Boolean) =
        modify(id) { it.copy(hasCover = has, coverStamp = System.currentTimeMillis()) }

    fun setBackground(id: String, has: Boolean) =
        modify(id) { it.copy(hasBackground = has, coverStamp = System.currentTimeMillis()) }

    fun markCoverTried(id: String, now: Long) = modify(id) { it.copy(coverTried = now) }

    fun setCompat(id: String, saveOverride: Int, rtcMode: Int) =
        modify(id) { it.copy(saveOverride = saveOverride, rtcMode = rtcMode) }

    fun setDetected(id: String, saveType: Int, rtc: Boolean) =
        modify(id) { if (it.detSave == saveType && it.detRtc == (if (rtc) 1 else 0)) it else it.copy(detSave = saveType, detRtc = if (rtc) 1 else 0) }

    fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    companion object {
        @Volatile
        private var instance: GameStore? = null

        fun get(ctx: Context): GameStore =
            instance ?: synchronized(this) {
                instance ?: GameStore(ctx.applicationContext).also { instance = it }
            }
    }
}
