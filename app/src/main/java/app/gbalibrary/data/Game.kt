package app.gbalibrary.data

import org.json.JSONArray
import org.json.JSONObject

data class Game(
    val id: String,
    val title: String,
    val headerTitle: String,
    val code: String,
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastPlayed: Long = 0L,
    val playSeconds: Long = 0L,
    val favorite: Boolean = false,
    val collections: Set<String> = emptySet(),
    val coverStamp: Long = 0L,
    val note: String = "",
    val hasCover: Boolean = false,
    val hasBackground: Boolean = false,
    /** Nombre de la entrada dentro del .zip/.7z (vacio si la ROM es un .gba suelto o ya esta copiada). */
    val entry: String = "",
    /** Nombre del archivo copiado en GBALibrary/Games (vacio = se lee desde [uri]). */
    val stored: String = "",
    /** Cuantas veces se abrio el juego. */
    val launches: Int = 0,
    /** Ultima vez que se intento buscar portada en internet (0 = nunca). */
    val coverTried: Long = 0L,
    /** Tipo de guardado elegido a mano: -1 = automatico, 0..5 = forzado (ver SaveType). */
    val saveOverride: Int = -1,
    /** RTC: 0 = automatico, 1 = forzar activado, 2 = desactivado. */
    val rtcMode: Int = 0,
    /** Lo que se detecto/uso la ultima vez que se abrio: tipo de guardado (-1 = aun no abierto). */
    val detSave: Int = -1,
    /** RTC usado la ultima vez: -1 desconocido, 0 no, 1 si. */
    val detRtc: Int = -1,
    /** Progreso del juego en % puesto por el usuario (-1 = sin definir). */
    val progress: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("headerTitle", headerTitle)
        put("code", code)
        put("uri", uri)
        put("fileName", fileName)
        put("sizeBytes", sizeBytes)
        put("addedAt", addedAt)
        put("lastPlayed", lastPlayed)
        put("playSeconds", playSeconds)
        put("favorite", favorite)
        put("collections", JSONArray(collections.toList()))
        put("coverStamp", coverStamp)
        put("note", note)
        put("hasCover", hasCover)
        put("hasBackground", hasBackground)
        put("entry", entry)
        put("stored", stored)
        put("launches", launches)
        put("coverTried", coverTried)
        put("saveOverride", saveOverride)
        put("rtcMode", rtcMode)
        put("detSave", detSave)
        put("detRtc", detRtc)
        put("progress", progress)
    }

    companion object {
        fun fromJson(o: JSONObject): Game {
            val cols = o.optJSONArray("collections")?.let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            } ?: emptySet()
            return Game(
                id = o.getString("id"),
                title = o.optString("title"),
                headerTitle = o.optString("headerTitle"),
                code = o.optString("code"),
                uri = o.getString("uri"),
                fileName = o.optString("fileName"),
                sizeBytes = o.optLong("sizeBytes"),
                addedAt = o.optLong("addedAt"),
                lastPlayed = o.optLong("lastPlayed"),
                playSeconds = o.optLong("playSeconds"),
                favorite = o.optBoolean("favorite"),
                collections = cols,
                coverStamp = o.optLong("coverStamp"),
                note = o.optString("note"),
                hasCover = o.optBoolean("hasCover"),
                hasBackground = o.optBoolean("hasBackground"),
                entry = o.optString("entry"),
                stored = o.optString("stored"),
                launches = o.optInt("launches", 0),
                coverTried = o.optLong("coverTried", 0L),
                saveOverride = o.optInt("saveOverride", -1),
                rtcMode = o.optInt("rtcMode", 0),
                detSave = o.optInt("detSave", -1),
                detRtc = o.optInt("detRtc", -1),
                progress = o.optInt("progress", -1),
            )
        }
    }
}
