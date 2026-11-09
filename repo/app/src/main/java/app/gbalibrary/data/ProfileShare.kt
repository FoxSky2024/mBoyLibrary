package app.gbalibrary.data

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Compartir perfil y notas SIN servidor: todo viaja dentro del enlace/QR.
 *   mboy://p?d=<perfil>   perfil de solo lectura
 *   mboy://n?d=<nota>     nota dirigida a un perfil (se guarda solo si coincide con su id)
 * Formato de <d>: JSON -> Deflate -> Base64 URL-safe.
 */
object ProfileShare {
    private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    private const val MAX_BYTES = 64 * 1024

    fun encode(json: JSONObject): String {
        val raw = json.toString().toByteArray(Charsets.UTF_8)
        val d = Deflater(9, true)
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(512)
        while (!d.finished()) out.write(tmp, 0, d.deflate(tmp))
        d.end()
        return Base64.encodeToString(out.toByteArray(), B64)
    }

    fun decode(s: String): JSONObject? = try {
        val bytes = Base64.decode(s, B64) + byteArrayOf(0) // Inflater(nowrap) pide un byte extra
        val inf = Inflater(true)
        inf.setInput(bytes)
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(1024)
        while (!inf.finished()) {
            val n = inf.inflate(tmp)
            if (n == 0) break
            out.write(tmp, 0, n)
            if (out.size() > MAX_BYTES) break
        }
        inf.end()
        if (out.size() > MAX_BYTES) null else JSONObject(String(out.toByteArray(), Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    fun profileLink(json: JSONObject) = "mboy://p?d=" + encode(json)
    fun noteLink(json: JSONObject) = "mboy://n?d=" + encode(json)

    /** Devuelve (tipo, json) si [uri] es un enlace mboy valido; tipo = "p" o "n". */
    fun parse(uri: Uri?): Pair<String, JSONObject>? {
        if (uri == null || uri.scheme != "mboy") return null
        val kind = uri.host ?: return null
        if (kind != "p" && kind != "n") return null
        val d = uri.getQueryParameter("d") ?: return null
        val j = decode(d) ?: return null
        return kind to j
    }

    fun parse(text: String): Pair<String, JSONObject>? =
        try { parse(Uri.parse(text.trim())) } catch (e: Exception) { null }
}
