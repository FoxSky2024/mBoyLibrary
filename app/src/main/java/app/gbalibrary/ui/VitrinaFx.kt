package app.gbalibrary.ui

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import app.gbalibrary.data.Pawb
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Efectos animados tematicos para la vitrina: id -> nombre. */
val VitrinaFxList = listOf(
    "" to "Ninguno", "holo" to "Holográfico", "destellos" to "Destellos", "nieve" to "Nieve",
    "brasas" to "Brasas", "lluvia" to "Lluvia", "glitch" to "Glitch", "neon" to "Neón",
)

private fun h(i: Int, k: Int): Float { var x = i * 374761393 + k * 668265263; x = (x xor (x shr 13)) * 1274126177; return ((x xor (x shr 16)) and 0xFFFF) / 65535f }

/** Capa animada encima del banner. No intercepta toques. */
@Composable
fun BannerFx(fx: String, modifier: Modifier = Modifier) {
    if (fx.isEmpty()) return
    val t by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier) {
        val w = size.width; val ht = size.height
        when (fx) {
            "holo" -> {
                val x = w * (t * 2.2f - 0.6f)
                val rainbow = listOf(Color(0x00FFFFFF), Color(0x66FF5EC8), Color(0x6600E5FF), Color(0x66FFEB3B), Color(0x6669F0AE), Color(0x00FFFFFF))
                drawRect(Brush.linearGradient(rainbow, Offset(x - w * 0.35f, 0f), Offset(x + w * 0.35f, ht)), blendMode = BlendMode.Screen)
                val x2 = w * (((t + 0.5f) % 1f) * 2.2f - 0.6f)
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent), Offset(x2 - 40f, 0f), Offset(x2 + 40f, ht)))
                for (i in 0 until 14) sparkle(Offset(h(i, 1) * w, h(i, 2) * ht), (0.5f + 0.5f * sin((t * 2f + h(i, 3)) * 2f * PI.toFloat())).coerceIn(0f, 1f), minOf(w, ht) * 0.05f * (0.5f + h(i, 4)), Color.White)
            }
            "destellos" -> for (i in 0 until 22) sparkle(Offset(h(i, 1) * w, h(i, 2) * ht), (0.5f + 0.5f * sin((t * 3f + h(i, 3)) * 2f * PI.toFloat())).coerceIn(0f, 1f), minOf(w, ht) * 0.07f * (0.4f + h(i, 4)), Color(0xFFFFF3B0))
            "nieve" -> for (i in 0 until 40) {
                val y = ((t * (0.5f + h(i, 3)) + h(i, 2)) % 1f) * (ht + 10f) - 5f
                val x = (h(i, 1) * w + sin((t + h(i, 4)) * 2f * PI.toFloat()) * 14f)
                drawCircle(Color.White.copy(alpha = 0.55f + 0.4f * h(i, 5)), 1.5f + 2.5f * h(i, 6), Offset(x, y))
            }
            "brasas" -> for (i in 0 until 34) {
                val p = (t * (0.6f + h(i, 3)) + h(i, 2)) % 1f
                val x = h(i, 1) * w + sin((t * 2f + h(i, 4)) * 2f * PI.toFloat()) * 10f
                drawCircle(Color(0xFFFF9A3C).copy(alpha = (1f - p) * 0.9f), 1.5f + 2.5f * h(i, 6) * (1f - p), Offset(x, ht - p * ht))
            }
            "lluvia" -> for (i in 0 until 46) {
                val p = (t * (1.6f + h(i, 3)) + h(i, 2)) % 1f
                val x = h(i, 1) * w; val y = p * (ht + 20f) - 10f
                drawLine(Color(0xFF9FD8FF).copy(alpha = 0.55f), Offset(x, y), Offset(x - 3f, y + 10f), 1.5f)
            }
            "glitch" -> {
                val step = (t * 24f).toInt()
                for (i in 0 until 4) {
                    if (h(step, i) > 0.55f) {
                        val y = h(step, i + 10) * ht; val bh = 2f + h(step, i + 20) * ht * 0.08f
                        drawRect(listOf(Color(0x55FF1744), Color(0x5500E5FF), Color(0x55FFFFFF))[i % 3], Offset((h(step, i + 30) - 0.5f) * 24f, y), Size(w, bh), blendMode = BlendMode.Screen)
                    }
                }
                var y = 0f; while (y < ht) { drawRect(Color.Black.copy(alpha = 0.10f), Offset(0f, y), Size(w, 1.5f)); y += 4f }
            }
            "neon" -> {
                val a = 0.35f + 0.35f * abs(sin(t * 2f * PI.toFloat()))
                drawRect(Brush.horizontalGradient(listOf(Color(0xFFFF2BD6).copy(alpha = a), Color.Transparent, Color(0xFF00E5FF).copy(alpha = a))), blendMode = BlendMode.Screen)
                drawRect(Color(0xFF00E5FF).copy(alpha = a), Offset.Zero, size, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.sparkle(c: Offset, a: Float, r: Float, color: Color) {
    if (a <= 0.02f) return
    val col = color.copy(alpha = a)
    drawLine(col, Offset(c.x - r * a, c.y), Offset(c.x + r * a, c.y), 2f)
    drawLine(col, Offset(c.x, c.y - r * a), Offset(c.x, c.y + r * a), 2f)
    drawCircle(col, r * 0.18f * a, c)
}

/** Video de la vitrina: en bucle, sin sonido, recortado al marco. */
@Composable
fun VideoBanner(path: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(Uri.fromFile(File(path)))
                setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
            }
        },
        update = { v -> if (!v.isPlaying) try { v.start() } catch (e: Exception) { /* ignorado */ } },
        modifier = modifier,
    )
}

/** Proporcion (ancho/alto) del contenido de la vitrina: la del video o imagen que subio el usuario, limitada a un rango util. */
@Composable
fun vitrinaAspect(): Float {
    val vid = Pawb.vVideo.value
    val img = Pawb.vImg.value
    return remember(vid, img) {
        val raw = try {
            if (vid.isNotEmpty() && File(vid).exists()) {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(vid)
                    val w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
                    val hh = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
                    val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (hh > 0f) (if (rot == 90 || rot == 270) hh / w else w / hh) else 3f
                } finally { try { r.release() } catch (e: Exception) { /* ignorado */ } }
            } else if (img.isNotEmpty()) {
                val b = android.util.Base64.decode(img, android.util.Base64.DEFAULT)
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, o)
                if (o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 3f
            } else 3f
        } catch (e: Exception) { 3f }
        raw.coerceIn(1.2f, 5f)
    }
}

/** Copia un video elegido a la carpeta de la app (max 40 MB) y lo deja como video de la vitrina. */
fun importVitrinaVideo(ctx: Context, uri: Uri): Boolean = try {
    val dst = File(Pawb.bannerDir(ctx), "vitrina_${System.currentTimeMillis()}.mp4")
    var total = 0L
    ctx.contentResolver.openInputStream(uri)?.use { ins ->
        dst.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                total += n; if (total > 40L * 1024 * 1024) throw java.io.IOException("grande")
                out.write(buf, 0, n)
            }
        }
    } ?: throw java.io.IOException("sin datos")
    val old = Pawb.vVideo.value
    Pawb.setVitrinaVideo(dst.absolutePath)
    if (old.isNotEmpty()) File(old).delete()
    true
} catch (e: Exception) { false }

/** Vitrina en JSON para compartir por archivo (no hay servidor: se envia por mensajeria y el amigo la importa). */
fun exportVitrinaJson(): String {
    val o = org.json.JSONObject()
    o.put("v", 1); o.put("title", Pawb.vTitle.value); o.put("sub", Pawb.vSub.value); o.put("img", Pawb.vImg.value); o.put("fx", Pawb.vFx.value); o.put("w", Pawb.vW.floatValue.toDouble()); o.put("h", Pawb.vH.intValue)
    return o.toString()
}

fun importVitrinaJson(text: String): Boolean = try {
    val o = org.json.JSONObject(text)
    if (!o.has("v")) false else {
        Pawb.setVitrina(o.optString("title"), o.optString("sub"), o.optString("img"))
        Pawb.setVitrinaFx(o.optString("fx"))
        Pawb.setVitrinaSize(o.optDouble("w", 1.0).toFloat(), o.optInt("h", 0))
        true
    }
} catch (e: Exception) { false }
