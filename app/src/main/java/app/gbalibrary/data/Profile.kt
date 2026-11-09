package app.gbalibrary.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Una nota recibida de otra persona (llega por enlace/QR y solo se guarda si va dirigida a este perfil). */
data class ProfileNote(val id: String, val from: String, val fromId: String, val text: String, val ts: Long)

/** Datos de personalizacion del perfil (estilo Steam). Se guardan en SharedPreferences "profile". */
object Profile {
    private var sp: SharedPreferences? = null
    private var notesFile: File? = null

    val id = mutableStateOf("")
    /** 0 En linea, 1 Ausente, 2 No molestar, 3 Invisible. */
    val status = mutableIntStateOf(0)
    val headline = mutableStateOf("")
    val bio = mutableStateOf("")
    val location = mutableStateOf("")
    val frame = mutableIntStateOf(0)
    val banner = mutableIntStateOf(0)
    /** IDs de hasta 3 juegos del escaparate, separados por coma. */
    val showcase = mutableStateOf("")
    val showGames = mutableStateOf(true)
    val showTime = mutableStateOf(true)
    val acceptNotes = mutableStateOf(true)
    val notes = mutableStateListOf<ProfileNote>()

    @Synchronized
    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
        sp = p
        var pid = p.getString("id", "") ?: ""
        if (pid.isEmpty()) {
            pid = UUID.randomUUID().toString().replace("-", "").take(12)
            p.edit().putString("id", pid).apply()
        }
        id.value = pid
        status.intValue = p.getInt("status", 0)
        headline.value = p.getString("headline", "") ?: ""
        bio.value = p.getString("bio", "") ?: ""
        location.value = p.getString("location", "") ?: ""
        frame.intValue = p.getInt("frame", 0)
        banner.intValue = p.getInt("banner", 0)
        showcase.value = p.getString("showcase", "") ?: ""
        showGames.value = p.getBoolean("showGames", true)
        showTime.value = p.getBoolean("showTime", true)
        acceptNotes.value = p.getBoolean("acceptNotes", true)
        loadNotes(ctx.applicationContext)
    }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp?.edit()?.apply { block(); apply() }
    }

    fun setStatus(v: Int) { status.intValue = v; edit { putInt("status", v) } }
    fun setHeadline(v: String) { headline.value = v; edit { putString("headline", v) } }
    fun setBio(v: String) { bio.value = v; edit { putString("bio", v) } }
    fun setLocation(v: String) { location.value = v; edit { putString("location", v) } }
    fun setFrame(v: Int) { frame.intValue = v; edit { putInt("frame", v) } }
    fun setBanner(v: Int) { banner.intValue = v; edit { putInt("banner", v) } }
    fun setShowGames(v: Boolean) { showGames.value = v; edit { putBoolean("showGames", v) } }
    fun setShowTime(v: Boolean) { showTime.value = v; edit { putBoolean("showTime", v) } }
    fun setAcceptNotes(v: Boolean) { acceptNotes.value = v; edit { putBoolean("acceptNotes", v) } }

    fun showcaseIds(): List<String> = showcase.value.split(',').filter { it.isNotBlank() }

    fun toggleShowcase(gameId: String) {
        val cur = showcaseIds().toMutableList()
        if (!cur.remove(gameId) && cur.size < 3) cur.add(gameId)
        val s = cur.joinToString(",")
        showcase.value = s
        edit { putString("showcase", s) }
    }

    // ---- Notas ----

    private fun loadNotes(ctx: Context) {
        notesFile = File(DataDir.root(ctx), "profile_notes.json")
        notes.clear()
        try {
            val f = notesFile ?: return
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                notes.add(ProfileNote(o.getString("id"), o.optString("from"), o.optString("fromId"), o.optString("text"), o.optLong("ts")))
            }
        } catch (e: Exception) {
            android.util.Log.w("Profile", "No se pudieron leer las notas", e)
        }
    }

    private fun saveNotes() {
        try {
            val arr = JSONArray()
            notes.forEach { n ->
                arr.put(JSONObject().put("id", n.id).put("from", n.from).put("fromId", n.fromId).put("text", n.text).put("ts", n.ts))
            }
            val f = notesFile ?: return
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(f)) { f.writeText(arr.toString()); tmp.delete() }
        } catch (e: Exception) {
            android.util.Log.w("Profile", "No se pudieron guardar las notas", e)
        }
    }

    /** Devuelve false si la nota ya existia (mismo id). */
    fun addNote(n: ProfileNote): Boolean {
        if (notes.any { it.id == n.id }) return false
        notes.add(0, n)
        while (notes.size > 200) notes.removeAt(notes.size - 1)
        saveNotes()
        return true
    }

    fun removeNote(id: String) {
        notes.removeAll { it.id == id }
        saveNotes()
    }
}
