package app.gbalibrary.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** Biblioteca persistente (library.json en el almacenamiento interno de la app). */
class GameStore private constructor(private val ctx: Context) {
    private val file = File(ctx.filesDir, "library.json")
    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games

    init {
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                _games.value = (0 until arr.length()).map { Game.fromJson(arr.getJSONObject(it)) }
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
            val arr = JSONArray()
            next.forEach { arr.put(it.toJson()) }
            val tmp = File(file.path + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
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
                if (old == null) {
                    byId[g.id] = g
                    added++
                } else {
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

    fun addPlay(id: String, seconds: Long, now: Long) = modify(id) {
        it.copy(playSeconds = it.playSeconds + seconds, lastPlayed = now)
    }

    fun setNote(id: String, note: String) = modify(id) { it.copy(note = note) }

    fun setCover(id: String, has: Boolean) =
        modify(id) { it.copy(hasCover = has, coverStamp = System.currentTimeMillis()) }

    fun setBackground(id: String, has: Boolean) =
        modify(id) { it.copy(hasBackground = has, coverStamp = System.currentTimeMillis()) }

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
