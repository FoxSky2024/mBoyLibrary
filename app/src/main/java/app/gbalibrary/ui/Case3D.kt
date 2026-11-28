package app.gbalibrary.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.ConsoleDef
import app.gbalibrary.data.Game
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

enum class CaseKind { GBA, GBC, GB, DS, GENERIC }

/** Aspecto fisico de la caja segun la consola: grosor, esquinas, marco y borde. */
class CaseStyle(
    val kind: CaseKind,
    val depthRatio: Float,
    val corner: Dp,
    val frame: Dp,
    val borderColor: Color,
    val frameColor: Color = Color(0xFF0B0B0D), // color del marco (plastico de la caja segun la consola)
)

fun caseStyleFor(console: ConsoleDef): CaseStyle = when (console.short) {
    // depthRatio = grosor / ALTO de la caja. El marco y el borde llevan el color de la consola (como su plastico).
    "GBA" -> CaseStyle(CaseKind.GBA, 0.125f, 4.dp, 3.dp, Color(0xCC8E7CFF), Color(0xFF3B2F7A)) // indigo GBA
    "GBC" -> CaseStyle(CaseKind.GBC, 0.120f, 4.dp, 3.dp, Color(0xCC4FD1C5), Color(0xFF17706B)) // verde agua GBC
    "GB" -> CaseStyle(CaseKind.GB, 0.130f, 3.dp, 3.dp, Color(0xCCC5C8CC), Color(0xFF6E7176)) // gris DMG
    "NES" -> CaseStyle(CaseKind.GENERIC, 0.115f, 4.dp, 3.dp, Color(0xCCD9D9D9), Color(0xFF8A8A8A)) // gris NES
    "SNES" -> CaseStyle(CaseKind.GENERIC, 0.115f, 4.dp, 3.dp, Color(0xCCB9B4D6), Color(0xFF5A557E)) // lila SNES
    "DS" -> CaseStyle(CaseKind.DS, 0.085f, 4.dp, 3.dp, Color(0xCCF2F4F7), Color(0xFF9AA0A8)) // plata/blanco DS Lite
    else -> CaseStyle(CaseKind.GENERIC, 0.110f, 4.dp, 3.dp, Color(0x66FFFFFF))
}

/**
 * Caja 3D de verdad: frente, lomo (izquierda), contraportada y canto (derecha) son cuatro caras que se colocan y
 * giran segun [angle] (grados; 0 = frente al usuario; positivo = el frente gira a la derecha y asoma el lomo).
 * Las caras que miran hacia atras se ocultan, asi al girar 360 se ve frente > lomo > atras > canto > frente.
 *
 * [angle], [glarePhase] y [glareStrength] se leen SOLO al dibujar (dentro de graphicsLayer/draw), por lo que
 * animarlos no recompone nada. [cheap] = reflejo del suelo: sin brillo ni contraportada.
 */
@Composable
fun Case3D(
    game: Game,
    width: Dp,
    height: Dp,
    angle: () -> Float,
    glarePhase: () -> Float,
    glareStrength: () -> Float,
    showBack: Boolean,
    back: BackData?,
    modifier: Modifier = Modifier,
    cheap: Boolean = false,
) {
    val console = remember(game.id, game.fileName) { consoleOf(game) }
    val style = remember(console) { caseStyleFor(console) }
    val density = LocalDensity.current
    // La caja toma las proporciones REALES del arte (frente y lomo): asi calzan tamano y bordes.
    val frontBmp = rememberBitmap(game, "front")
    val spineImg = back?.spine ?: rememberBitmap(game, "spine")
    // La caja conserva SIEMPRE el tamano (proporciones) de los archivos de la consola (assets/bases/<consola>_font / _spine);
    // la caratula se ajusta dentro de ella y nunca la reescala. Sin molde: se usa el arte real.
    val tplFront = rememberBase(console, "font")
    val tplSpine = rememberBase(console, "spine")
    val aspect = (tplFront?.let { it.width.toFloat() / it.height }
        ?: frontBmp?.let { it.width.toFloat() / it.height })?.takeIf { it in 0.4f..1.3f }
    val fh = height
    val fw = if (aspect != null) height * aspect else width
    val spAspect = (tplSpine?.let { it.width.toFloat() / it.height }
        ?: spineImg?.let { it.width.toFloat() / it.height })?.takeIf { it in 0.04f..0.3f }
    val depth = if (spAspect != null) fh * spAspect else fh * style.depthRatio
    val wPx = with(density) { fw.toPx() }
    val dPx = with(density) { depth.toPx() }
    val camera = 2400f * density.density // casi sin perspectiva: frente, lomo y reverso miden lo mismo de alto

    Box(modifier.size(width, height), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.requiredSize(fw, fh)) {
            CaseFace(0f, 0f, dPx / 2f, fw, fh, angle, camera) {
                FrontFace(game, style, glarePhase, glareStrength, cheap, fitted = aspect != null)
            }
            CaseFace(270f, -wPx / 2f, 0f, depth, fh, angle, camera) {
                Spine(game, style, console, depth, fh, glarePhase, glareStrength, cheap, spineImg)
            }
            CaseFace(90f, wPx / 2f, 0f, depth, fh, angle, camera) {
                Spine(game, style, console, depth, fh, glarePhase, glareStrength, cheap, spineImg)
            }
            if (showBack && !cheap) {
                CaseFace(180f, 0f, -dPx / 2f, fw, fh, angle, camera) {
                    BackFace(game, style, console, back)
                }
            }
        }
    }
}

/**
 * Una cara de la caja. (cx, cz) = centro de la cara dentro de la caja sin girar (px); phi = hacia donde mira
 * (0 frente, 90 derecha, 180 atras, 270 izquierda). Con la caja girada th: posicion x = cx*cos + cz*sin,
 * giro propio = th + phi, y es visible solo si cos(th + phi) > 0.
 */
@Composable
private fun BoxScope.CaseFace(
    phi: Float,
    cx: Float,
    cz: Float,
    faceW: Dp,
    height: Dp,
    angle: () -> Float,
    camera: Float,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .align(Alignment.Center)
            .size(faceW, height)
            .graphicsLayer {
                val th = angle()
                val a = Math.toRadians((th + phi).toDouble())
                val t = Math.toRadians(th.toDouble())
                alpha = if (cos(a) > 0.0) 1f else 0f
                translationX = (cx * cos(t) + cz * sin(t)).toFloat()
                rotationY = th + phi
                cameraDistance = camera
                scaleX = 1.004f // solapa un pelo con la cara vecina: sin rendijas
            },
    ) { content() }
}

// ------------------------------------------------------------------------------------------ brillo

/**
 * Brillo especular tipo plastico / holografico: una banda diagonal blanca (con un leve tinte arcoiris en los bordes)
 * que recorre la cara segun [phase] (posicion en el carrusel + giro del dedo). Va con mezcla Screen y alfa bajo
 * (max ~0.3 en la caja central): ilumina, pero no tapa ni el arte ni el texto del lomo.
 */
private fun Modifier.glare(phase: () -> Float, strength: () -> Float, gloss: Boolean = true): Modifier = drawWithContent {
    drawContent()
    val s = strength().coerceIn(0f, 1f)
    if (s <= 0.01f) return@drawWithContent
    val w = size.width
    val h = size.height
    if (gloss) {
        // reflejo suave del plastico: ilumina la parte alta
        drawRect(
            Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f * s), 0.35f to Color.Transparent, endY = h),
            blendMode = BlendMode.Screen,
        )
    }
    val center = (0.5f + phase().coerceIn(-1.2f, 1.2f) * 0.85f) * w
    val half = w * 0.30f
    drawRect(
        Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF7FE9FF).copy(alpha = 0.05f * s),
                Color.White.copy(alpha = 0.30f * s),
                Color(0xFFFF9BE8).copy(alpha = 0.05f * s),
                Color.Transparent,
            ),
            start = Offset(center - half * 1.6f, -h * 0.2f),
            end = Offset(center + half * 1.6f, h * 1.2f),
        ),
        blendMode = BlendMode.Screen,
    )
}

// ------------------------------------------------------------------------------------------ frente

@Composable
private fun FrontFace(game: Game, style: CaseStyle, phase: () -> Float, strength: () -> Float, cheap: Boolean, fitted: Boolean) {
    Box(
        Modifier.fillMaxSize()
            .then(if (cheap) Modifier else Modifier.glare(phase, strength)),
    ) {
        // sin marco ni borde propio: solo el arte de la caja
        FrontArt(game, Modifier.fillMaxSize(), ContentScale.Crop)
        // volumen: realce a la izquierda (lado del lomo) y sombra a la derecha
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.14f)))))
    }
}

// ------------------------------------------------------------------------------------------ bases por consola

/**
 * Molde de la caja de cada consola (assets/bases/<consola>_{spine,back}.png): bandas del logo, tabla de compatibilidad,
 * Nintendo... con la parte central transparente. Se dibuja sobre el lomo / reverso generados cuando no hay imagen real.
 * Para otra consola basta con poner sus png en assets/bases.
 */
@Composable
private fun rememberBase(console: ConsoleDef, kind: String): ImageBitmap? {
    val ctx = LocalContext.current.applicationContext
    val key = console.short.lowercase() + "_" + kind
    val bmp by produceState<ImageBitmap?>(BaseCache.map[key], key) {
        if (BaseCache.map.containsKey(key)) { value = BaseCache.map[key]; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.assets.open("bases/$key.png").use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }.also { BaseCache.map[key] = it }
    }
    return bmp
}

private object BaseCache { val map = HashMap<String, ImageBitmap?>() }

// ------------------------------------------------------------------------------------------ lomo

@Composable
private fun Spine(
    game: Game, style: CaseStyle, console: ConsoleDef, sideW: Dp, h: Dp,
    phase: () -> Float, strength: () -> Float, cheap: Boolean, real: ImageBitmap? = null,
) {
    val base = if (real == null) rememberBase(console, "spine") else null
    // Sin lomo real: color tomado del arte del frente + molde de la consola (logo arriba, Nintendo abajo). Sin texto.
    val front = if (real == null) rememberBitmap(game, "front") else null
    val tint = remember(front) {
        front?.let {
            try {
                val px = android.graphics.Bitmap.createScaledBitmap(it.asAndroidBitmap(), 1, 1, true).getPixel(0, 0)
                Color(px).let { c -> Color(c.red * 0.7f, c.green * 0.7f, c.blue * 0.7f, 1f) }
            } catch (e: Exception) { null }
        }
    }
    val bg = tint?.let { Brush.horizontalGradient(listOf(it, Color(it.red * 0.8f, it.green * 0.8f, it.blue * 0.8f, 1f))) }
        ?: Brush.horizontalGradient(listOf(console.bgTop, Color(0xFF050506)))
    Box(Modifier.fillMaxSize().clipToBounds().background(bg)) {
        if (real != null) Image(real, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) // lomo real del wrap / ScreenScraper
        if (base != null) Image(base, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        // volumen del lomo y borde
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.30f)))))
        if (style.kind == CaseKind.DS) {
            // linea de brillo del plastico negro
            Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp).fillMaxHeight().requiredSize(1.dp, h).background(Color.White.copy(alpha = 0.22f)))
        }
    }
    if (!cheap) Box(Modifier.fillMaxSize().glare({ phase() + 0.4f }, { strength() * 0.5f }, gloss = false))
}

// ------------------------------------------------------------------------------------------ atras

/**
 * Contraportada. Si hay una imagen real se usa tal cual. Si no, se genera: el arte frontal oscurecido y desenfocado,
 * titulo, logo de la consola, descripcion (Wikipedia) y una captura de pantalla (Libretro) cuando existen; nunca vacia.
 */
@Composable
private fun BackFace(game: Game, style: CaseStyle, console: ConsoleDef, data: BackData?) {
    val shape = RoundedCornerShape(style.corner)
    val front = rememberBitmap(game, "front")
    Box(Modifier.fillMaxSize().clip(shape).background(Color(0xFF0B0B0D))) {
        val real = data?.real
        if (real != null) {
            Image(real, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            NoData()
            val base = rememberBase(console, "back")
            if (base != null) Image(base, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent, Color.White.copy(alpha = 0.10f)))))
    }
}
