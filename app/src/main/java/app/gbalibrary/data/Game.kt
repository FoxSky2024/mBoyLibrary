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
    /** Nombre de la entrada dentro del .zip/.7z (vacio si la ROM es un .gba suelto). */
    val entry: String = "",
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
            )
        }
    }
}
