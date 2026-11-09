package app.gbalibrary.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.gbalibrary.data.Game
import app.gbalibrary.data.Profile
import app.gbalibrary.data.Settings
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

class BadgeDef(val id: String, val name: String, val desc: String, val icon: ImageVector, val test: (ProfileStats) -> Boolean)

class ProfileStats(
    val games: Int,
    val seconds: Long,
    val shots: Int,
    val favorites: Int,
    val completed: Int,
    val saves: Int,
) {
    val xp: Int = (seconds / 360).toInt() + games * 20 + shots * 5 + saves * 15 + completed * 50
    val level: Int = sqrt(xp / 25.0).toInt() + 1
    private val base: Int get() = 25 * (level - 1) * (level - 1)
    private val next: Int get() = 25 * level * level
    val levelFrac: Float get() = ((xp - base).toFloat() / (next - base).toFloat()).coerceIn(0f, 1f)
    val xpToNext: Int get() = next - xp
    val badges: List<BadgeDef> get() = AllBadges.filter { it.test(this) }

    companion object {
        fun of(all: List<Game>, shots: Int, saves: Int) = ProfileStats(
            games = all.size,
            seconds = all.sumOf { it.playSeconds },
            shots = shots,
            favorites = all.count { it.favorite },
            completed = all.count { it.progress >= 100 },
            saves = saves,
        )
    }
}

val AllBadges: List<BadgeDef> = listOf(
    BadgeDef("first", "Primer juego", "Añade un juego", Icons.Filled.SportsEsports) { it.games >= 1 },
    BadgeDef("coll", "Coleccionista", "10 juegos en la biblioteca", Icons.Filled.Inventory2) { it.games >= 10 },
    BadgeDef("h10", "Aficionado", "10 horas jugadas", Icons.Filled.Schedule) { it.seconds >= 36000 },
    BadgeDef("h100", "Veterano", "100 horas jugadas", Icons.Filled.EmojiEvents) { it.seconds >= 360000 },
    BadgeDef("shot", "Fotógrafo", "10 capturas", Icons.Filled.PhotoCamera) { it.shots >= 10 },
    BadgeDef("fav", "Favoritos", "3 juegos favoritos", Icons.Filled.Favorite) { it.favorites >= 3 },
    BadgeDef("done", "Completista", "Un juego al 100%", Icons.Filled.Verified) { it.completed >= 1 },
    BadgeDef("lv10", "Nivel 10", "Llega al nivel 10", Icons.Filled.Star) { it.level >= 10 },
)

class StatusDef(val name: String, val color: Color)

val Statuses = listOf(
    StatusDef("En línea", Color(0xFF66C0F4)),
    StatusDef("Ausente", Color(0xFFE5B143)),
    StatusDef("No molestar", Color(0xFFE24D4D)),
    StatusDef("Invisible", Color(0xFF8FA3B3)),
)

val FrameColors = listOf(
    "Acento" to Color.Unspecified,
    "Oro" to Color(0xFFFFD54F),
    "Verde" to Color(0xFF5BA32B),
    "Violeta" to Color(0xFFB388FF),
    "Rojo" to Color(0xFFE24D4D),
    "Blanco" to Color(0xFFFFFFFF),
)

val Banners = listOf(
    "Océano" to listOf(Color(0xFF1B4F72), Color(0xFF2E86C1)),
    "Bosque" to listOf(Color(0xFF145A32), Color(0xFF52BE80)),
    "Ocaso" to listOf(Color(0xFF7B241C), Color(0xFFF5B041)),
    "Noche" to listOf(Color(0xFF17202A), Color(0xFF5B2C6F)),
    "Ceniza" to listOf(Color(0xFF2C3E50), Color(0xFF95A5A6)),
    "Neón" to listOf(Color(0xFF0B0B2B), Color(0xFF00E5FF)),
)

/** Copia compartible del perfil. Respeta la privacidad: sin juegos/tiempo si estan ocultos. */
fun profileSnapshot(all: List<Game>, st: ProfileStats): JSONObject {
    val j = JSONObject()
    j.put("v", 1)
    j.put("i", Profile.id.value)
    j.put("n", Settings.profileName.value.ifBlank { "Jugador" })
    j.put("s", Profile.status.intValue)
    j.put("h", Profile.headline.value)
    j.put("b", Profile.bio.value)
    j.put("l", Profile.location.value)
    j.put("f", Profile.frame.intValue)
    j.put("r", Profile.banner.intValue)
    j.put("lv", st.level)
    j.put("g", st.games)
    j.put("nt", if (Profile.acceptNotes.value) 1 else 0)
    j.put("bd", JSONArray(st.badges.map { it.id }))
    if (Profile.showTime.value) j.put("t", st.seconds)
    if (Profile.showGames.value) {
        val ids = Profile.showcaseIds()
        val picked = ids.mapNotNull { id -> all.firstOrNull { it.id == id }?.title }
        val titles = picked.ifEmpty { all.sortedByDescending { it.playSeconds }.take(3).map { it.title } }
        j.put("top", JSONArray(titles.map { it.take(40) }))
    }
    return j
}
