package app.gbalibrary.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.gbalibrary.data.CoverFetcher
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Game
import app.gbalibrary.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

/**
 * Cache de imagenes de las cajas 3D (portada, captura y contraportada).
 *  - Memoria: LruCache de bitmaps ya decodificados y reducidos (rapido al volver a una caja).
 *  - Disco: los archivos de GBALibrary/Covers (portada) y Covers/.cache (captura / contraportada).
 * La clave incluye el id estable del juego y el sello de la portada, asi un cambio de portada recarga solo esa caja.
 */
object CoverBitmaps {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 10).toInt().coerceIn(8 shl 20, 64 shl 20),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun key(g: Game, kind: String) = "${g.id}|${g.coverStamp}|$kind"

    fun peek(key: String): ImageBitmap? = cache.get(key)?.asImageBitmap()

    private fun fileFor(ctx: Context, g: Game, kind: String): File = when (kind) {
        "snap" -> CoverFetcher.snapFile(ctx, g.id)
        "back" -> CoverFetcher.backFile(ctx, g.id)
        else -> Covers.coverFile(ctx, g.id)
    }

    /** Del cache o, si no esta, decodifica el archivo en un hilo de fondo. null si no existe. */
    suspend fun get(ctx: Context, g: Game, kind: String = "front"): ImageBitmap? {
        val k = key(g, kind)
        cache.get(k)?.let { return it.asImageBitmap() }
        val bmp = withContext(Dispatchers.IO) { decode(fileFor(ctx, g, kind)) } ?: return null
        cache.put(k, bmp)
        return bmp.asImageBitmap()
    }

    /** Deja en memoria las portadas de las [radius] cajas anteriores y siguientes a [center]. */
    suspend fun preload(ctx: Context, games: List<Game>, center: Int, radius: Int) {
        for (step in 0..radius) {
            for (j in listOf(center + step, center - step).distinct()) {
                val g = games.getOrNull(j) ?: continue
                if (g.hasCover && cache.get(key(g, "front")) == null) get(ctx, g)
            }
        }
    }

    private fun decode(f: File, maxSide: Int = 720): Bitmap? {
        if (!f.exists()) return null
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Bitmap de [game]. Se recuerda por (id, tipo): cuando cambia el sello de la portada se conserva la imagen
 * anterior hasta que la nueva esta lista, asi nunca queda un hueco.
 */
@Composable
fun rememberBitmap(game: Game, kind: String = "front", enabled: Boolean = true, version: Int = 0): ImageBitmap? {
    val ctx = LocalContext.current.applicationContext
    val k = CoverBitmaps.key(game, kind)
    var bmp by remember(game.id, kind) { mutableStateOf(CoverBitmaps.peek(k)) }
    LaunchedEffect(k, enabled, version, game.hasCover) {
        if (kind == "front" && !game.hasCover) { bmp = null; return@LaunchedEffect }
        if (!enabled) return@LaunchedEffect
        val cached = CoverBitmaps.peek(k)
        if (cached != null) { bmp = cached; return@LaunchedEffect }
        val loaded = CoverBitmaps.get(ctx, game, kind)
        if (loaded != null) bmp = loaded
    }
    return bmp
}

/** Marcador de la portada: mismo tamano y color que la caja, con un brillo suave que la recorre. Nunca un hueco blanco. */
@Composable
fun CoverPlaceholder(game: Game, modifier: Modifier = Modifier) {
    val hue = (game.id.hashCode().absoluteValue % 360).toFloat()
    val base = Color.hsv(hue, 0.40f, 0.30f)
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "shimmerX")
    Box(
        modifier.background(base).drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                    start = Offset(x * w - w * 0.3f, 0f),
                    end = Offset(x * w + w * 0.3f, h * 0.5f),
                ),
            )
        },
    )
}

/** Portada de la cara frontal: crossfade desde el marcador; sin portada, el degradado generado. */
@Composable
fun FrontArt(game: Game, modifier: Modifier = Modifier) {
    if (!game.hasCover) {
        CoverImage(game, modifier)
        return
    }
    val bmp = rememberBitmap(game, "front")
    Crossfade(targetState = bmp, modifier = modifier, animationSpec = tween(220), label = "cover") { b ->
        if (b == null) CoverPlaceholder(game, Modifier.fillMaxSize())
        else Image(b, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Lo que hay para dibujar la contraportada. */
class BackData(val real: ImageBitmap?, val snap: ImageBitmap?, val description: String)

/**
 * Datos de la contraportada. Lee lo que haya en disco y, solo si [enabled] y la busqueda automatica esta activa,
 * pide una vez los extras que falten (captura + descripcion) con las mismas fuentes que la portada.
 */
@Composable
fun rememberBackData(game: Game, enabled: Boolean): BackData? {
    val ctx = LocalContext.current.applicationContext
    var data by remember(game.id) { mutableStateOf<BackData?>(null) }
    LaunchedEffect(game.id, enabled) {
        if (!enabled) return@LaunchedEffect
        suspend fun load(): BackData {
            val real = CoverBitmaps.get(ctx, game, "back")
            val snap = CoverBitmaps.get(ctx, game, "snap")
            val desc = withContext(Dispatchers.IO) {
                runCatching { CoverFetcher.descFile(ctx, game.id).takeIf { it.exists() }?.readText()?.trim() }.getOrNull() ?: ""
            }
            return BackData(real, snap, desc)
        }
        var d = load()
        data = d
        if (d.real == null && (d.snap == null || d.description.isBlank()) && Settings.autoCovers.value) {
            if (CoverFetcher.fetchBackExtras(ctx, game)) {
                d = load()
                data = d
            }
        }
    }
    return data
}
