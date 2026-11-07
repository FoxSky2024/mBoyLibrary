package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.VideoScale
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Frases de Eevee. */
object EeveeSay {
    const val ENTER = "¡Vamos!"
    const val SAVED = "¡Guardado!"
    const val PAUSE = "¿Descanso?"
    const val FAST = "¡Rápido, rápido!"
    val OOPS = listOf("Ou nou...", "Vaya...")
    val IDLE = listOf("¿Jugamos?", "Me gusta este juego", "¿Tienes snacks?")
    val TAP = listOf("¡Eevee!", "¡Hihi!", "¿Me das un snack?", "¡Vamos!", "¡Qué cosquillas!", "Me gusta este juego")
}

/** Estado compartido: la Activity llama a say(); el composable lo muestra. */
class EeveeState {
    var text by mutableStateOf<String?>(null)
    var stamp by mutableIntStateOf(0)
    var jump by mutableIntStateOf(0)

    fun say(t: String, hop: Boolean = true) {
        text = t
        stamp++
        if (hop) jump++
    }

    fun random(pool: List<String>) = say(pool.random())
}

// Sprite 20x16 (cuerpo 16 de ancho + cola). . = vacio, O contorno, B marron, D marron oscuro,
// C crema, E ojo, W brillo del ojo, N nariz.
private val BODY = listOf(
    "..OO........OO..",
    ".ODBO......OBDO.",
    ".OBBBOOOOOOBBBO.",
    ".OBBBBBBBBBBBBO.",
    "OBBBBBBBBBBBBBBO",
    "OBBEEBBBBBBEEBBO",
    "OBBEWBBBBBBEWBBO",
    "OBBBBBBNNBBBBBBO",
    ".OBBBCCCCCCBBBO.",
    "..OBCCCCCCCCBO..",
    "..OBBCCCCCCBBO..",
    "..OBBBBBBBBBBO..",
    "..OBBBBBBBBBBO..",
    "..OBBOBBBBOBBO..",
    "..OCCO....OCCO..",
    "..OOOO....OOOO..",
)
private val TAIL_A = listOf("...O", "..OC", "..OC", "..OB", ".OBO", ".OBO", "OBO.", "OO..") // rows 5..12
private val TAIL_B = listOf("....", "....", "....", "...O", ".OOC", "OBBC", "OBBO", ".OO.")

private fun colorOf(ch: Char): Color? = when (ch) {
    'O' -> Color(0xFF3B2412)
    'B' -> Color(0xFFB9783A)
    'D' -> Color(0xFF8A5528)
    'C' -> Color(0xFFF3E1B0)
    'E' -> Color(0xFF151515)
    'W' -> Color.White
    'N' -> Color(0xFF2A1A12)
    else -> null
}

/**
 * Eevee companero en pixel-art. Vive en el margen lateral donde no hay pantalla de juego y esquiva
 * los controles. Sprite chico y animacion a ~8 fps: no afecta al hilo de emulacion.
 */
@Composable
fun EeveeCompanion(state: EeveeState, layout: ControlLayout, padVisible: Boolean, paused: Boolean) {
    if (!Settings.eeveeOn.value) return
    val density = LocalDensity.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(paused) {
        while (!paused) {
            delay(125)
            tick++
        }
    }
    // Charla aleatoria cada 45-90 s.
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(45_000, 90_000))
            if (!paused) state.random(EeveeSay.IDLE)
        }
    }
    // Se oculta el globo a los 2.5 s.
    LaunchedEffect(state.stamp) {
        if (state.text != null) {
            delay(2500)
            state.text = null
        }
    }
    // Salto de reaccion.
    var hop by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.jump) {
        if (state.jump > 0) {
            hop = 3
            delay(130); hop = 1
            delay(130); hop = 0
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        // Rectangulo del juego segun el modo de escala (misma cuenta que GbaGlView).
        val gw = 240f
        val gh = 160f
        val (dw, dh) = when (Settings.scale.intValue) {
            VideoScale.STRETCH -> sw to sh
            VideoScale.ORIGINAL -> { val s = max(1f, floor(min(sw / gw, sh / gh))); gw * s to gh * s }
            VideoScale.FULL -> { val s = max(sw / gw, sh / gh); gw * s to gh * s }
            else -> { val s = min(sw / gw, sh / gh); gw * s to gh * s }
        }
        val margin = (sw - dw) / 2f
        val onRight = Settings.eeveeSide.intValue == 0
        val pad = with(density) { 6.dp.toPx() }

        var unitDp = 3.dp
        var spriteW = with(density) { (20 * unitDp.toPx()) }
        var spriteH = with(density) { (16 * unitDp.toPx()) }
        var x = 0f
        var y = 0f
        var corner = false

        if (margin >= spriteW + pad * 2) {
            // Margen lateral libre: se coloca en el centro del margen, esquivando controles.
            val cx = if (onRight) sw - margin / 2f else margin / 2f
            x = cx - spriteW / 2f
            y = sh * 0.42f
            if (padVisible) {
                val rects = layout.items.toList().map { it2 ->
                    val k = Settings.padSize.floatValue * it2.size
                    val w2 = with(density) { (Kind.baseW(it2.kind) * k).dp.toPx() }
                    val h2 = with(density) { (Kind.baseH(it2.kind) * k).dp.toPx() }
                    floatArrayOf(it2.x * sw - w2 / 2f - pad, it2.y * sh - h2 / 2f - pad, it2.x * sw + w2 / 2f + pad, it2.y * sh + h2 / 2f + pad)
                }
                fun free(yy: Float) = rects.none { r -> x < r[2] && x + spriteW > r[0] && yy < r[3] && yy + spriteH > r[1] }
                val step = with(density) { 14.dp.toPx() }
                var found = false
                var yy = sh * 0.30f
                while (yy <= sh - spriteH - pad) {
                    if (free(yy)) { y = yy; found = true; break }
                    yy += step
                }
                if (!found) {
                    yy = sh * 0.30f - step
                    while (yy >= pad + spriteH) {
                        if (free(yy)) { y = yy; found = true; break }
                        yy -= step
                    }
                }
                if (!found) corner = true
            }
        } else {
            corner = true
        }
        if (corner) {
            // Pantalla (o controles) ocupan todo: Eevee chiquito en una esquinita, encima del marco.
            unitDp = 2.dp
            spriteW = with(density) { (20 * unitDp.toPx()) }
            spriteH = with(density) { (16 * unitDp.toPx()) }
            x = if (onRight) sw - spriteW - pad else pad
            y = sh - spriteH - pad - with(density) { 44.dp.toPx() }
        }

        Eevee(state, tick, hop, unitDp, Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) })
    }
}

@Composable
private fun Eevee(state: EeveeState, tick: Int, hop: Int, unit: Dp, modifier: Modifier) {
    val phase = tick % 80 // 10 s de ciclo
    val blink = phase == 7 || phase == 8 || phase == 41
    val tailB = (tick / 3) % 2 == 1
    val breathe = (tick / 5) % 2
    val widthDp = unit * 20
    val heightDp = unit * 16
    Box(modifier.size(widthDp, heightDp + unit * 4)) {
        run {
            val bubble = state.text
            if (bubble != null) {
                Text(
                    bubble,
                    color = Color(0xFF222222),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-26).dp)
                        .wrapContentSize(Alignment.Center, unbounded = true)
                        .widthIn(max = 96.dp)
                        .background(Color(0xF2FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            val tapSource = remember { MutableInteractionSource() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = tapSource, indication = null) { state.random(EeveeSay.TAP) },
            ) {
                val u = unit.toPx()
                val lift = hop * u
                val bob = breathe * u * 0.5f
                fun px(c: Int, r: Int, color: Color, dy: Float = 0f) {
                    drawRect(color, Offset(c * u, (r + 2) * u - lift + dy), Size(u + 0.6f, u + 0.6f))
                }
                // Sombra
                drawRect(Color(0x33000000), Offset(2 * u, 18 * u), Size(14 * u, u))
                for (r in BODY.indices) {
                    for (c in BODY[r].indices) {
                        var ch = BODY[r][c]
                        if (blink && (r == 5 || r == 6) && (ch == 'E' || ch == 'W')) ch = if (r == 6) 'O' else 'B'
                        val col = colorOf(ch) ?: continue
                        // La respiracion mueve solo el torso (filas 8..13).
                        px(c, r, col, if (r in 8..13) bob else 0f)
                    }
                }
                val tail = if (tailB) TAIL_B else TAIL_A
                for (i in tail.indices) {
                    for (c in tail[i].indices) {
                        val col = colorOf(tail[i][c]) ?: continue
                        px(16 + c, 5 + i, col, bob)
                    }
                }
            }
        }
    }
}
