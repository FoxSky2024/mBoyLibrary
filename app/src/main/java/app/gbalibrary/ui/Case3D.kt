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
)

fun caseStyleFor(console: ConsoleDef): CaseStyle = when (console.short) {
    "GBA" -> CaseStyle(CaseKind.GBA, 0.125f, 2.dp, 0.dp, Color(0x55FFFFFF))
    "GBC" -> CaseStyle(CaseKind.GBC, 0.120f, 2.dp, 0.dp, Color(0x55FFFFFF))
    "GB" -> CaseStyle(CaseKind.GB, 0.130f, 2.dp, 0.dp, Color(0x66000000))
    "DS" -> CaseStyle(CaseKind.DS, 0.105f, 5.dp, 5.dp, Color(0x66FFFFFF)) // caja de plastico negra, borde grueso
    else -> CaseStyle(CaseKind.GENERIC, 0.110f, 5.dp, 4.dp, Color(0x66FFFFFF))
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
    val depth = width * style.depthRatio
    val wPx = with(density) { width.toPx() }
    val dPx = with(density) { depth.toPx() }
    val camera = 14f * density.density

    Box(modifier.size(width, height)) {
        CaseFace(0f, 0f, dPx / 2f, width, height, angle, camera) {
            FrontFace(game, style, glarePhase, glareStrength, cheap)
        }
        CaseFace(270f, -wPx / 2f, 0f, depth, height, angle, camera) {
            Spine(game, style, console, depth, height, glarePhase, glareStrength, cheap)
        }
        CaseFace(90f, wPx / 2f, 0f, depth, height, angle, camera) {
            Spine(game, style, console, depth, height, glarePhase, glareStrength, cheap)
        }
        if (showBack && !cheap) {
            CaseFace(180f, 0f, -dPx / 2f, width, height, angle, camera) {
                BackFace(game, style, console, back)
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
private fun FrontFace(game: Game, style: CaseStyle, phase: () -> Float, strength: () -> Float, cheap: Boolean) {
    val shape = RoundedCornerShape(style.corner)
    Box(
        Modifier.fillMaxSize().clip(shape)
            .then(if (cheap) Modifier else Modifier.glare(phase, strength)),
    ) {
        if (style.frame > 0.dp) {
            // caja de plastico: marco negro grueso alrededor del arte
            Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).padding(style.frame)) {
                FrontArt(game, Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)))
            }
        } else {
            FrontArt(game, Modifier.fillMaxSize())
        }
        // volumen: realce a la izquierda (lado del lomo) y sombra a la derecha + borde fino
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.Black.copy(alpha = 0.18f)))))
        Box(Modifier.fillMaxSize().border(1.dp, style.borderColor, shape))
    }
}

// ------------------------------------------------------------------------------------------ lomo

@Composable
private fun Spine(
    game: Game, style: CaseStyle, console: ConsoleDef, sideW: Dp, h: Dp,
    phase: () -> Float, strength: () -> Float, cheap: Boolean,
) {
    val fs = (sideW.value * 0.50f).coerceIn(5f, 12f)
    val hue = (game.id.hashCode().absoluteValue % 360).toFloat()
    // fondo, bloque del logo, texto del logo, color del titulo
    val bg: Brush
    val logoBg: Color
    val logoColor: Color
    val titleColor: Color
    when (style.kind) {
        CaseKind.GBA -> { // carton negro con bloque verde
            bg = Brush.horizontalGradient(listOf(Color(0xFF0E1110), Color(0xFF1D2320)))
            logoBg = Color(0xFF2E9A4E); logoColor = Color.White; titleColor = Color.White
        }
        CaseKind.GBC -> { // carton con el color del juego
            val base = Color.hsv(hue, 0.55f, 0.50f)
            bg = Brush.horizontalGradient(listOf(base, Color.hsv(hue, 0.60f, 0.36f)))
            logoBg = Color.Black.copy(alpha = 0.45f); logoColor = Color.White; titleColor = Color.White
        }
        CaseKind.GB -> { // carton gris clasico
            bg = Brush.horizontalGradient(listOf(Color(0xFFD2D5D9), Color(0xFFB4B8BD)))
            logoBg = Color(0xFF8D9196); logoColor = Color(0xFF16181B); titleColor = Color(0xFF1B1D20)
        }
        CaseKind.DS -> { // plastico negro brillante
            bg = Brush.horizontalGradient(listOf(Color(0xFF16161A), Color(0xFF050506)))
            logoBg = Color(0xFF26262C); logoColor = Color.White; titleColor = Color.White
        }
        CaseKind.GENERIC -> {
            bg = Brush.horizontalGradient(listOf(console.bgTop, Color(0xFF050506)))
            logoBg = console.accent.copy(alpha = 0.55f); logoColor = Color.White; titleColor = Color.White
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds().background(bg)) {
        Row(
            Modifier.align(Alignment.Center).requiredSize(h, sideW).rotate(90f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.fillMaxHeight().weight(0.42f).background(logoBg), contentAlignment = Alignment.Center) {
                if (style.kind == CaseKind.DS) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontSize = (fs * 0.75f).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)) { append("NINTENDO ") }
                            withStyle(SpanStyle(fontSize = (fs * 1.25f).sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)) { append("DS") }
                        },
                        color = logoColor, maxLines = 1, softWrap = false,
                    )
                } else {
                    Text(
                        console.name.uppercase(), color = logoColor, fontSize = (fs * 0.95f).sp,
                        fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, letterSpacing = 0.3.sp,
                        maxLines = 1, softWrap = false,
                    )
                }
            }
            Text(
                game.title, color = titleColor, fontSize = fs.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.58f).padding(horizontal = 5.dp),
            )
        }
        // volumen del lomo y borde
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.30f)))))
        if (style.kind == CaseKind.DS) {
            // linea de brillo del plastico negro
            Box(Modifier.align(Alignment.CenterStart).padding(start = 2.dp).fillMaxHeight().requiredSize(1.dp, h).background(Color.White.copy(alpha = 0.22f)))
        }
        Box(Modifier.fillMaxSize().border(1.dp, style.borderColor.copy(alpha = 0.7f)))
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
            if (front != null) {
                Image(front, null, Modifier.fillMaxSize().blur(16.dp), contentScale = ContentScale.Crop, alpha = 0.55f)
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(console.bgTop.copy(alpha = 0.80f), Color.Black.copy(alpha = 0.90f)))))
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(console.name.uppercase(), color = console.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1)
                Text(game.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val desc = data?.description?.takeIf { it.isNotBlank() }
                    ?: "Un juego de ${console.name} de tu colección. Pulsa JUGAR para empezar donde lo dejaste."
                Text(desc, color = Color.White.copy(alpha = 0.85f), fontSize = 7.5.sp, lineHeight = 10.sp, maxLines = 9, overflow = TextOverflow.Ellipsis)
                val snap = data?.snap
                if (snap != null) {
                    Image(
                        snap, null,
                        Modifier.fillMaxWidth().aspectRatio(snap.width.toFloat() / snap.height.coerceAtLeast(1)).clip(RoundedCornerShape(3.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Spacer(Modifier.height(2.dp))
                }
                Box(Modifier.weight(1f))
                Text(console.name.uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent, Color.White.copy(alpha = 0.10f)))))
        Box(Modifier.fillMaxSize().border(1.dp, style.borderColor, shape))
    }
}
