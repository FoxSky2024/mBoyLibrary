package app.gbalibrary.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Busca portadas en internet por el nombre limpio del juego (sin version, sin guiones, sin etiquetas).
 * Fuentes, en orden (si una falla se prueba la siguiente):
 *   1. Libretro Thumbnails (boxarts por plataforma, sin clave). Se baja el indice una vez y se cachea.
 *   2. SteamGridDB (solo si el usuario puso su clave de API en Ajustes).
 *   3. Wikipedia (imagen principal del articulo del juego, sin clave).
 * Todo se guarda en GBALibrary/Covers. Hay cache en disco: la misma portada no se descarga dos veces.
 */
object CoverFetcher {
    private const val TAG = "CoverFetcher"
    private const val UA = "GBALibrary/0.3 (Android; portadas de biblioteca personal)"
    private const val MAX_IMAGE = 6 * 1024 * 1024
    private const val MAX_INDEX = 8 * 1024 * 1024
    private const val INDEX_TTL = 14L * 24 * 3600 * 1000
    private const val COVER_SIDE = 900

    /** Pasados estos dias se vuelve a intentar con un juego sin portada (o al pulsar "Buscar faltantes"). */
    const val RETRY_AFTER_MS = 7L * 24 * 3600 * 1000

    /** true si la ultima busqueda fallo por falta de conexion (no se debe marcar el juego como "sin portada"). */
    @Volatile
    var networkFailed: Boolean = false
        private set

    private fun platformDir(game: Game): String = when (game.fileName.substringAfterLast('.', "").lowercase()) {
        "gbc" -> "Nintendo - Game Boy Color"
        "gb" -> "Nintendo - Game Boy"
        "nds" -> "Nintendo - Nintendo DS"
        else -> "Nintendo - Game Boy Advance"
    }

    private fun platformWord(game: Game): String = when (game.fileName.substringAfterLast('.', "").lowercase()) {
        "gbc" -> "Game Boy Color"
        "gb" -> "Game Boy"
        "nds" -> "Nintendo DS"
        else -> "Game Boy Advance"
    }

    // ---------------------------------------------------------------- API publica

    /** Busca y guarda la portada de [game]. true si quedo una portada nueva. */
    suspend fun fetchFor(ctx: Context, game: Game): Boolean = withContext(Dispatchers.IO) {
        try {
            networkFailed = false
            val name = RomHeader.searchName(game.title)
            val toks = tokens(name)
            if (toks.isEmpty()) return@withContext false
            val platform = platformDir(game)
            val dest = Covers.coverFile(ctx, game.id)

            // Cache: misma portada ya descargada para otro juego/otra vez.
            val cached = File(Covers.cacheDir(ctx), toks.joinToString("_") + "_" + platform.last().lowercaseChar() + ".jpg")
            if (cached.exists()) {
                cached.copyTo(dest, overwrite = true)
                return@withContext true
            }

            val variants = variantsOf(toks)
            var ok = false

            // 1) Libretro
            val index = libretroIndex(ctx, platform)
            for (q in variants) {
                val match = bestMatch(index, q) ?: continue
                val url = "https://thumbnails.libretro.com/${enc(platform)}/Named_Boxarts/${enc(match)}"
                if (download(url, dest)) { ok = true; break }
            }

            // 2) SteamGridDB (opcional)
            val key = Settings.sgdbKey.value
            if (!ok && key.isNotBlank()) {
                for (q in variants) {
                    val url = steamGridDb(q.joinToString(" "), key) ?: continue
                    if (download(url, dest)) { ok = true; break }
                }
            }

            // 3) Wikipedia
            if (!ok) {
                for (q in variants) {
                    val url = wikipedia(q, platformWord(game)) ?: continue
                    if (download(url, dest)) { ok = true; break }
                }
            }

            if (ok) {
                try { dest.copyTo(cached, overwrite = true) } catch (e: Exception) { Log.d(TAG, "cache: ${e.message}") }
            }
            ok
        } catch (e: Exception) {
            Log.w(TAG, "fetchFor fallo", e)
            false
        }
    }

    /** Descarga la portada desde una direccion pegada por el usuario. */
    suspend fun fetchFromUrl(ctx: Context, id: String, rawUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            var u = rawUrl.trim()
            if (u.startsWith("http://", ignoreCase = true)) u = "https://" + u.substring(7)
            if (!u.startsWith("https://", ignoreCase = true)) return@withContext false
            download(u, Covers.coverFile(ctx, id))
        } catch (e: Exception) {
            Log.w(TAG, "fetchFromUrl fallo", e)
            false
        }
    }

    // ---------------------------------------------------------------- Libretro

    private fun libretroIndex(ctx: Context, platform: String): List<String> {
        val cache = File(Covers.cacheDir(ctx), "libretro_" + platform.replace(' ', '_') + ".txt")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < INDEX_TTL
        if (fresh) return cache.readLines().filter { it.isNotBlank() }

        val bytes = httpGet("https://thumbnails.libretro.com/${enc(platform)}/Named_Boxarts/", MAX_INDEX)
        if (bytes != null) {
            val html = String(bytes, Charsets.UTF_8)
            val names = Regex("""href="([^"]+?)\.png"""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .map { URLDecoder.decode(it.groupValues[1].replace("+", "%2B"), "UTF-8") + ".png" }
                .distinct()
                .toList()
            if (names.isNotEmpty()) {
                try { cache.writeText(names.joinToString("\n")) } catch (e: Exception) { Log.d(TAG, "indice: ${e.message}") }
                return names
            }
        }
        // Sin red: se usa el indice viejo si existe.
        return if (cache.exists()) cache.readLines().filter { it.isNotBlank() } else emptyList()
    }

    /** Mejor archivo del indice para las palabras [q] (todas deben aparecer en el nombre). */
    private fun bestMatch(index: List<String>, q: List<String>): String? {
        if (q.isEmpty()) return null
        val qSet = q
        val qNorm = q.joinToString("")
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (file in index) {
            val title = file.removeSuffix(".png")
            val core = title.substringBefore(" (")
            val ct = tokens(core)
            val ctSet = ct.toSet()
            val cNorm = ct.joinToString("")
            val ok = qSet.all { it in ctSet } || cNorm == qNorm || cNorm.removeSuffix("version") == qNorm
            if (!ok) continue
            var score = (ct.size - q.size).coerceAtLeast(0) * 10
            score += when {
                title.contains("(USA") -> 0
                title.contains("(World") -> 1
                title.contains("(Europe") -> 2
                else -> 3
            }
            if (Regex("""\((Beta|Proto|Demo|Sample|Unl|Kiosk)""", RegexOption.IGNORE_CASE).containsMatchIn(title)) score += 50
            if (score < bestScore) { bestScore = score; best = file }
        }
        return best
    }

    // ---------------------------------------------------------------- SteamGridDB

    private fun steamGridDb(name: String, key: String): String? {
        val auth = mapOf("Authorization" to "Bearer $key")
        val search = httpGet("https://www.steamgriddb.com/api/v2/search/autocomplete/${enc(name)}", 1_000_000, auth) ?: return null
        val data = JSONObject(String(search, Charsets.UTF_8)).optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        val id = data.getJSONObject(0).optInt("id", -1)
        if (id < 0) return null
        val grids = httpGet("https://www.steamgriddb.com/api/v2/grids/game/$id?dimensions=600x900&types=static", 2_000_000, auth)
            ?: return null
        val arr = JSONObject(String(grids, Charsets.UTF_8)).optJSONArray("data") ?: return null
        if (arr.length() == 0) return null
        return arr.getJSONObject(0).optString("url").ifBlank { null }
    }

    // ---------------------------------------------------------------- Wikipedia

    private fun wikipedia(q: List<String>, platformWord: String): String? {
        val search = enc(q.joinToString(" ") + " $platformWord video game")
        val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search" +
            "&gsrsearch=$search&gsrlimit=4&prop=pageimages&piprop=thumbnail&pithumbsize=600"
        val body = httpGet(url, 1_000_000) ?: return null
        val pages = JSONObject(String(body, Charsets.UTF_8)).optJSONObject("query")?.optJSONObject("pages") ?: return null
        val list = pages.keys().asSequence().map { pages.getJSONObject(it) }.sortedBy { it.optInt("index", 99) }.toList()
        for (p in list) {
            val thumb = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() } ?: continue
            val tt = tokens(p.optString("title").substringBefore(" ("))
            if (q.all { it in tt }) return thumb // el articulo debe nombrar el juego, si no es otra cosa
        }
        return null
    }

    // ---------------------------------------------------------------- utilidades

    private fun download(url: String, dest: File): Boolean {
        val bytes = httpGet(url, MAX_IMAGE) ?: return false
        return Covers.importBytes(bytes, dest, COVER_SIDE)
    }

    private fun httpGet(url: String, maxBytes: Int, headers: Map<String, String> = emptyMap()): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.d(TAG, "GET fallo $url: ${e.message}")
            if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException) {
                networkFailed = true
            }
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Palabras en minuscula, sin signos (y sin "the"). */
    private fun tokens(s: String): List<String> =
        s.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim().split(' ').filter { it.isNotEmpty() && it != "the" }

    /**
     * "pokemon azoth river edition" -> [pokemon azoth river edition], [pokemon azoth river], [pokemon azoth].
     * Se prueba de lo mas especifico a lo menos; nunca se baja de 2 palabras (si el nombre tiene 2 o mas)
     * para no poner la portada de otro juego.
     */
    private fun variantsOf(toks: List<String>): List<List<String>> {
        if (toks.size <= 1) return if (toks.firstOrNull()?.length ?: 0 >= 4) listOf(toks) else emptyList()
        return (toks.size downTo 2).map { toks.take(it) }
    }
}
