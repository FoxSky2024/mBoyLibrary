package app.gbalibrary.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Xml
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

class FeedSource(val name: String, val url: String)
class FeedItem(val source: String, val title: String, val link: String, val date: String, val summary: String, val image: String)

/** Fuentes de noticias (RSS/Atom) guardadas por el usuario y descarga/lectura de publicaciones. */
object Feeds {
    private var sp: android.content.SharedPreferences? = null
    val sources = mutableStateListOf<FeedSource>()
    val widgets = mutableStateListOf<String>()
    private val images = LruCache<String, Bitmap>(24)

    @Synchronized
    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("feeds", Context.MODE_PRIVATE)
        sp = p
        try {
            val arr = JSONArray(p.getString("sources", "[]"))
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { sources.add(FeedSource(it.getString("n"), it.getString("u"))) }
        } catch (e: Exception) { }
        val w = p.getString("widgets", null)
        if (w == null) widgets.addAll(listOf("stats", "last", "badges")) else widgets.addAll(w.split(',').filter { it.isNotBlank() })
    }

    private fun save() {
        val arr = JSONArray()
        sources.forEach { arr.put(JSONObject().put("n", it.name).put("u", it.url)) }
        sp?.edit()?.putString("sources", arr.toString())?.putString("widgets", widgets.joinToString(","))?.apply()
    }

    fun add(name: String, url: String) {
        sources.add(FeedSource(name.ifBlank { hostOf(url) }, url.trim()))
        save()
    }
    fun remove(s: FeedSource) { sources.remove(s); save() }
    fun addWidget(t: String) { widgets.add(t); save() }
    fun removeWidget(i: Int) { if (i in widgets.indices) { widgets.removeAt(i); save() } }

    var quickNote: String
        get() = sp?.getString("quickNote", "") ?: ""
        set(v) { sp?.edit()?.putString("quickNote", v)?.apply() }

    fun hostOf(url: String) = try { URL(normalize(url)).host.removePrefix("www.") } catch (e: Exception) { url }

    /** Convierte paginas conocidas (Steam, GitHub, Reddit) en su feed y agrega https:// si falta. */
    fun normalize(raw: String): String {
        var u = raw.trim()
        if (!u.startsWith("http")) u = "https://$u"
        Regex("steampowered\\.com/(?:app|news/app)/(\\d+)").find(u)?.let { return "https://store.steampowered.com/feeds/news/app/${it.groupValues[1]}/" }
        Regex("^https://github\\.com/([^/]+/[^/]+)/?$").find(u)?.let { return "https://github.com/${it.groupValues[1]}/releases.atom" }
        if (Regex("^https://(www\\.)?reddit\\.com/r/[^/]+/?$").matches(u)) return u.trimEnd('/') + "/.rss"
        return u
    }

    private fun get(url: String, maxBytes: Int = 2_000_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "GBALibrary/0.5 (feed reader)")
        c.instanceFollowRedirects = true
        try {
            val bytes = c.inputStream.use { it.readNBytesCompat(maxBytes) }
            return String(bytes, Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) { val n = read(buf); if (n < 0) break; out.write(buf, 0, n) }
        return out.toByteArray()
    }

    /** Descarga y lee las publicaciones; si la URL es una pagina web busca su enlace RSS/Atom. */
    fun fetch(src: FeedSource): List<FeedItem> {
        val url = normalize(src.url)
        var body = get(url)
        if (!looksLikeFeed(body)) {
            val m = Regex("<link[^>]+type=[\"']application/(?:rss|atom)\\+xml[\"'][^>]*>", RegexOption.IGNORE_CASE).find(body)
            val href = m?.let { Regex("href=[\"']([^\"']+)[\"']").find(it.value)?.groupValues?.get(1) } ?: return emptyList()
            body = get(URL(URL(url), href.replace("&amp;", "&")).toString())
            if (!looksLikeFeed(body)) return emptyList()
        }
        return parse(src.name, body)
    }

    private fun looksLikeFeed(b: String) = b.take(2000).let { it.contains("<rss") || it.contains("<feed") || it.contains("<rdf:RDF") }

    private fun stripHtml(s: String): String =
        android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_COMPACT).toString().replace('￼', ' ').replace(Regex("\\s+"), " ").trim()

    private fun parse(source: String, xml: String): List<FeedItem> {
        val out = ArrayList<FeedItem>()
        val p = Xml.newPullParser()
        p.setInput(java.io.StringReader(xml))
        var title = ""; var link = ""; var date = ""; var sum = ""; var img = ""
        var inItem = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val n = p.name.lowercase()
                if (n == "item" || n == "entry") { inItem = true; title = ""; link = ""; date = ""; sum = ""; img = "" }
                else if (inItem) when (n) {
                    "title" -> title = stripHtml(p.nextText())
                    "link" -> { val h = p.getAttributeValue(null, "href"); if (h != null) { if (link.isEmpty() || p.getAttributeValue(null, "rel") != "enclosure") link = h; p.nextText() } else link = p.nextText().trim() }
                    "pubdate", "published", "updated", "date" -> if (date.isEmpty()) date = p.nextText().trim().take(25)
                    "description", "summary", "content", "encoded" -> {
                        val raw = p.nextText()
                        if (img.isEmpty()) Regex("<img[^>]+src=[\"']([^\"']+)[\"']").find(raw)?.let { img = it.groupValues[1] }
                        if (sum.length < 20) sum = stripHtml(raw).take(280)
                    }
                    "enclosure", "thumbnail", "content_" -> p.getAttributeValue(null, "url")?.let { if (img.isEmpty() && (p.getAttributeValue(null, "type")?.startsWith("image") != false)) img = it }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                val n = p.name.lowercase()
                if ((n == "item" || n == "entry") && inItem) {
                    inItem = false
                    if (title.isNotBlank()) out.add(FeedItem(source, title, link, date, sum, img))
                }
            }
            ev = p.next()
        }
        return out
    }

    fun loadImage(url: String): Bitmap? {
        images.get(url)?.let { return it }
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000
            val bytes = c.inputStream.use { it.readNBytesCompat(3_000_000) }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            val o2 = BitmapFactory.Options().apply { inSampleSize = (o.outWidth / 640).coerceAtLeast(1) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)?.also { images.put(url, it) }
        } catch (e: Exception) { null }
    }
}
