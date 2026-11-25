package app.gbalibrary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Caras reales de la caja 3D (frente / lomo / reverso). Se llama desde [CoverFetcher.fetchBackExtras], asi
 * comparte la cola, el cache de disco (Covers/.cache) y el ajuste Settings.autoCovers con la portada.
 *
 * Orden: ScreenScraper (box-2D, box-2D-back, box-2D-side o el "wrap" box-texture; por hash de la ROM y luego por
 * nombre; credenciales opcionales en Ajustes) y, si no hay nada, la caja genera lomo y reverso (nunca queda vacio).
 * El frente suelto lo siguen buscando Libretro / SteamGridDB en [CoverFetcher.fetchFor].
 */
object BoxArtFetcher {
    private const val TAG = "BoxArtFetcher"
    private const val MAX_IMAGE = 8 * 1024 * 1024
    private const val SOFT = "mBoyLibrary"

    fun frontFile(ctx: Context, id: String) = File(Covers.cacheDir(ctx), "$id.front.jpg")
    fun spineFile(ctx: Context, id: String) = File(Covers.cacheDir(ctx), "$id.spine.jpg")
    fun backFile(ctx: Context, id: String) = CoverFetcher.backFile(ctx, id)
    private fun triedFile(ctx: Context, id: String) = File(Covers.cacheDir(ctx), "$id.facestried")
    private fun hashFile(ctx: Context, id: String) = File(Covers.cacheDir(ctx), "$id.hashes")

    private fun hasCreds() = Settings.ssDevId.value.isNotBlank() && Settings.ssDevPass.value.isNotBlank()

    /** Fraccion del ancho total del wrap (reverso | lomo | frente) que ocupa el lomo en cada consola. */
    private fun spineFraction(game: Game): Float = when (game.fileName.substringAfterLast('.', "").lowercase()) {
        "gba" -> 0.065f
        "gbc" -> 0.07f
        "gb" -> 0.07f
        "nds", "dsi" -> 0.05f
        else -> 0.07f
    }

    private fun systemId(game: Game): Int = when (game.fileName.substringAfterLast('.', "").lowercase()) {
        "gba" -> 12
        "gbc" -> 10
        "gb" -> 9
        "nds", "dsi" -> 15
        else -> 0
    }

    /** true si ya hay lomo y reverso reales (de ScreenScraper o puestos a mano). */
    suspend fun fetchFaces(ctx: Context, game: Game): Boolean = withContext(Dispatchers.IO) {
        try {
            if (BundledBoxes.install(ctx, game, overwriteFront = false)) return@withContext true
            if (spineFile(ctx, game.id).exists() && backFile(ctx, game.id).exists()) return@withContext true
            val tried = triedFile(ctx, game.id)
            if (tried.exists() && System.currentTimeMillis() - tried.lastModified() < CoverFetcher.RETRY_AFTER_MS) return@withContext false
            if (!hasCreds()) return@withContext false // sin credenciales no se consulta (y no se marca como intentado)
            var answered = false
            val ok = screenScraper(ctx, game) { answered = true }
            if (answered) tried.writeText(System.currentTimeMillis().toString()) // sin repetir peticiones fallidas
            ok
        } catch (e: Exception) {
            Log.w(TAG, "fetchFaces fallo", e)
            false
        }
    }

    // ------------------------------------------------------------------ ScreenScraper

    private fun hashes(ctx: Context, game: Game): Array<String>? {
        val cache = hashFile(ctx, game.id)
        if (cache.exists()) cache.readText().split(' ').takeIf { it.size == 4 }?.let { return it.toTypedArray() }
        var tmp: File? = null
        val input = try {
            if (game.entry.isNotEmpty()) {
                tmp = Archive.extractToCache(ctx, android.net.Uri.parse(game.uri), game.entry)
                tmp?.inputStream()
            } else ctx.contentResolver.openInputStream(android.net.Uri.parse(game.uri))
        } catch (e: Exception) { null } ?: return null
        return try {
            val crc = CRC32()
            val md5 = MessageDigest.getInstance("MD5")
            val sha = MessageDigest.getInstance("SHA-1")
            var size = 0L
            input.use { s ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    crc.update(buf, 0, n); md5.update(buf, 0, n); sha.update(buf, 0, n); size += n
                }
            }
            fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }
            val r = arrayOf("%08X".format(crc.value), hex(md5.digest()), hex(sha.digest()), size.toString())
            cache.writeText(r.joinToString(" "))
            r
        } finally {
            tmp?.delete()
        }
    }

    private class Media(val type: String, val region: String, val url: String)

    private fun regionRank(r: String) = listOf("wor", "us", "eu", "uk", "fr", "es", "de", "ss", "jp").indexOf(r).let { if (it < 0) 50 else it }

    private fun queryMedias(game: Game, h: Array<String>?, byName: Boolean, onAnswer: () -> Unit): List<Media>? {
        val sys = systemId(game)
        val sb = StringBuilder("https://api.screenscraper.fr/api2/jeuInfos.php?output=json&softname=$SOFT")
        sb.append("&devid=").append(CoverFetcher.enc(Settings.ssDevId.value)).append("&devpassword=").append(CoverFetcher.enc(Settings.ssDevPass.value))
        if (Settings.ssUser.value.isNotBlank()) sb.append("&ssid=").append(CoverFetcher.enc(Settings.ssUser.value)).append("&sspassword=").append(CoverFetcher.enc(Settings.ssPass.value))
        if (sys > 0) sb.append("&systemeid=$sys")
        if (!byName && h != null) sb.append("&crc=${h[0]}&md5=${h[1]}&sha1=${h[2]}&romtaille=${h[3]}")
        sb.append("&romnom=").append(CoverFetcher.enc(if (byName) RomHeader.searchName(game.title) else game.fileName))
        val body = CoverFetcher.httpGet(sb.toString(), 2_000_000) ?: return null
        onAnswer()
        val text = String(body, Charsets.UTF_8)
        if (!text.trimStart().startsWith("{")) return emptyList() // "Erreur: ..." (no encontrado)
        val arr = JSONObject(text).optJSONObject("response")?.optJSONObject("jeu")?.optJSONArray("medias") ?: return emptyList()
        val out = ArrayList<Media>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val url = m.optString("url")
            if (url.isNotBlank()) out.add(Media(m.optString("type"), m.optString("region"), url))
        }
        return out
    }

    private fun best(medias: List<Media>, type: String) = medias.filter { it.type == type }.minByOrNull { regionRank(it.region) }

    private fun screenScraper(ctx: Context, game: Game, onAnswer: () -> Unit): Boolean {
        val h = hashes(ctx, game)
        var medias = if (h != null) queryMedias(game, h, false, onAnswer) else null
        if (medias.isNullOrEmpty()) medias = queryMedias(game, h, true, onAnswer) // por nombre
        if (medias.isNullOrEmpty()) return false

        val wrap = best(medias, "box-texture")
        if (wrap != null) {
            val bytes = CoverFetcher.httpGet(wrap.url, MAX_IMAGE)
            if (bytes != null && saveWrap(ctx, game, bytes)) return true
        }
        var any = false
        best(medias, "box-2D")?.let { m -> CoverFetcher.httpGet(m.url, MAX_IMAGE)?.let { if (Covers.importBytes(it, frontFile(ctx, game.id), 900)) any = true } }
        best(medias, "box-2D-back")?.let { m -> CoverFetcher.httpGet(m.url, MAX_IMAGE)?.let { if (Covers.importBytes(it, backFile(ctx, game.id), 900)) any = true } }
        best(medias, "box-2D-side")?.let { m -> CoverFetcher.httpGet(m.url, MAX_IMAGE)?.let { if (Covers.importBytes(it, spineFile(ctx, game.id), 600)) any = true } }
        return any && spineFile(ctx, game.id).exists() && backFile(ctx, game.id).exists()
    }

    // ------------------------------------------------------------------ Recorte del wrap

    /** Recorta un wrap (reverso | lomo | frente) y guarda las tres caras. Tambien sirve para importar uno a mano. */
    fun saveWrap(ctx: Context, game: Game, bytes: ByteArray): Boolean {
        val parts = sliceWrap(bytes, spineFraction(game)) ?: return false
        return try {
            save(parts[0], backFile(ctx, game.id))
            save(parts[1], spineFile(ctx, game.id))
            save(parts[2], frontFile(ctx, game.id))
            true
        } finally {
            parts.forEach { it.recycle() }
        }
    }

    private fun save(b: Bitmap, f: File) {
        f.parentFile?.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    /**
     * Divide el wrap en [reverso, lomo, frente]. La posicion esperada sale de la proporcion del lomo de la consola
     * (GBA ~6-7% del ancho total); despues se "ajusta" a los dos bordes verticales mas marcados de la imagen
     * (cambio brusco de luminosidad) dentro de limites razonables, porque los wraps reales no siempre son exactos.
     */
    fun sliceWrap(bytes: ByteArray, spineFrac: Float): Array<Bitmap>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var s = 1
        while (opts.outWidth / (s * 2) >= 2400) s *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w < h * 1.2f) { bmp.recycle(); return null } // no parece un wrap plano

        // fuerza de borde por columna (muestreando filas)
        val rows = 48
        val lum = Array(rows) { r ->
            val y = (r * (h - 1)) / (rows - 1)
            val px = IntArray(w)
            bmp.getPixels(px, 0, w, 0, y, w, 1)
            IntArray(w) { x -> val c = px[x]; ((c shr 16 and 255) * 3 + (c shr 8 and 255) * 6 + (c and 255)) / 10 }
        }
        val e = FloatArray(w)
        var mean = 0f
        for (x in 2 until w - 2) {
            var sum = 0
            for (r in 0 until rows) sum += abs(lum[r][x + 2] - lum[r][x - 2])
            e[x] = sum.toFloat() / rows
            mean += e[x]
        }
        mean = max(1f, mean / w)
        for (x in 0 until w) e[x] /= mean

        val b1e = (1f - spineFrac) / 2f * w
        val b2e = b1e + spineFrac * w
        var bestX1 = b1e.toInt()
        var bestX2 = b2e.toInt()
        var bestScore = -1e9f
        val minSpine = (0.035f * w).toInt()
        val maxSpine = (0.16f * w).toInt()
        for (x1 in (0.12f * w).toInt()..(0.62f * w).toInt()) {
            if (e[x1] < 1.2f) continue // solo bordes marcados
            for (sw in minSpine..maxSpine) {
                val x2 = x1 + sw
                if (x2 > w - (0.30f * w).toInt()) break
                val score = e[x1] + e[x2] - 6f * (abs(x1 - b1e) + abs(x2 - b2e)) / w
                if (score > bestScore) { bestScore = score; bestX1 = x1; bestX2 = x2 }
            }
        }
        if (bestScore < 1.5f) { // sin bordes claros: proporcion pura
            bestX1 = b1e.toInt(); bestX2 = b2e.toInt()
        }
        bestX1 = bestX1.coerceIn(8, w - 24)
        bestX2 = bestX2.coerceIn(bestX1 + 8, w - 8)
        val inset = 1 // evita la linea del corte
        val back = Bitmap.createBitmap(bmp, 0, 0, max(4, bestX1 - inset), h)
        val spine = Bitmap.createBitmap(bmp, bestX1 + inset, 0, max(4, bestX2 - bestX1 - 2 * inset), h)
        val front = Bitmap.createBitmap(bmp, min(w - 4, bestX2 + inset), 0, max(4, w - bestX2 - inset), h)
        return arrayOf(back, spine, front)
    }
}
