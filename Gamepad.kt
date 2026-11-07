package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.emu.Engine
import app.gbalibrary.emu.GbaKey
import kotlin.math.abs
import kotlin.math.hypot

private val PadIdle = Color(0x44FFFFFF)
private val PadBorder = Color(0x66FFFFFF)

/** Boton (A, B, L, R, START, SELECT, ...) que rellena su espacio, con multitouch independiente. */
@Composable
fun PadButton(
    label: String,
    mask: Int,
    engine: Engine?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    fontSize: Int = 20,
    source: Int = Engine.SOURCE_TOUCH,
) {
    var pressed by remember { mutableStateOf(false) }
    val active = LocalPalette.current.accent.copy(alpha = 0.75f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (pressed) active else PadIdle)
            .border(2.dp, PadBorder, shape)
            .pointerInput(mask, enabled, source) {
                if (!enabled || engine == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    engine.setKey(source, mask, true)
                    waitForUpOrCancellation()
                    pressed = false
                    engine.setKey(source, mask, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
    }
}

/** Cruceta de 8 direcciones: se desliza el dedo y cambia de direccion sin soltar. */
@Composable
fun DPad(engine: Engine?, enabled: Boolean, modifier: Modifier = Modifier, source: Int = Engine.SOURCE_TOUCH) {
    var dirs by remember { mutableIntStateOf(0) }
    val activeColor = LocalPalette.current.accent.copy(alpha = 0.75f)
    Box(
        modifier = modifier.fillMaxSize().pointerInput(enabled, source) {
            if (!enabled || engine == null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val threshold = size.width * 0.14f

                fun maskFor(p: Offset): Int {
                    val dx = p.x - cx
                    val dy = p.y - cy
                    val ax = abs(dx)
                    val ay = abs(dy)
                    var m = 0
                    if (ax >= threshold && ax >= ay * 0.45f) {
                        m = m or (if (dx > 0) GbaKey.RIGHT else GbaKey.LEFT)
                    }
                    if (ay >= threshold && ay >= ax * 0.45f) {
                        m = m or (if (dy > 0) GbaKey.DOWN else GbaKey.UP)
                    }
                    return m
                }

                var last = -1
                fun applyDirs(m: Int) {
                    if (m != last) {
                        last = m
                        dirs = m
                        engine.setDirections(source, m)
                    }
                }

                applyDirs(maskFor(down.position))
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    applyDirs(maskFor(change.position))
                    change.consume()
                }
                applyDirs(0)
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val t = w / 3f
            val corner = CornerRadius(t / 3f, t / 3f)
            drawCircle(Color(0x22FFFFFF))
            drawRoundRect(PadIdle, topLeft = Offset(t, 0f), size = Size(t, w), cornerRadius = corner)
            drawRoundRect(PadIdle, topLeft = Offset(0f, t), size = Size(w, t), cornerRadius = corner)

            fun highlight(bit: Int, topLeft: Offset) {
                if ((dirs and bit) != 0) drawRect(activeColor, topLeft = topLeft, size = Size(t, t))
            }
            highlight(GbaKey.UP, Offset(t, 0f))
            highlight(GbaKey.DOWN, Offset(t, 2 * t))
            highlight(GbaKey.LEFT, Offset(0f, t))
            highlight(GbaKey.RIGHT, Offset(2 * t, t))
            drawCircle(Color(0x33000000), radius = t * 0.28f, center = Offset(w / 2f, w / 2f))
        }
    }
}

/**
 * Joystick analogico virtual: se arrastra dentro de la base y simula Arriba/Abajo/Izquierda/Derecha
 * (con diagonales). [deadzone] es la fraccion del radio por debajo de la cual no se registra nada.
 */
@Composable
fun Stick(engine: Engine?, enabled: Boolean, deadzone: Float, modifier: Modifier = Modifier, source: Int = Engine.SOURCE_TOUCH) {
    var knob by remember { mutableStateOf(Offset.Zero) } // -1..1
    var dirs by remember { mutableIntStateOf(0) }
    val activeColor = LocalPalette.current.accent.copy(alpha = 0.85f)
    Box(
        modifier = modifier.fillMaxSize().pointerInput(enabled, source, deadzone) {
            if (!enabled || engine == null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.width / 2f

                var last = -1
                fun update(p: Offset) {
                    var dx = (p.x - cx) / r
                    var dy = (p.y - cy) / r
                    val mag = hypot(dx, dy)
                    if (mag > 1f) { dx /= mag; dy /= mag }
                    knob = Offset(dx, dy)
                    var m = 0
                    if (hypot(dx, dy) >= deadzone) {
                        val ax = abs(dx)
                        val ay = abs(dy)
                        if (ax >= ay * 0.45f) m = m or (if (dx > 0) GbaKey.RIGHT else GbaKey.LEFT)
                        if (ay >= ax * 0.45f) m = m or (if (dy > 0) GbaKey.DOWN else GbaKey.UP)
                    }
                    if (m != last) {
                        last = m
                        dirs = m
                        engine.setDirections(source, m)
                    }
                }

                update(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    update(change.position)
                    change.consume()
                }
                knob = Offset.Zero
                dirs = 0
                engine.setDirections(source, 0)
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width / 2f
            val c = Offset(r, r)
            drawCircle(Color(0x33FFFFFF), radius = r, center = c)
            drawCircle(PadBorder, radius = r - 1.5f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            // zona muerta
            drawCircle(Color(0x22FFFFFF), radius = r * deadzone, center = c)
            val kr = r * 0.38f
            val kc = Offset(c.x + knob.x * (r - kr), c.y + knob.y * (r - kr))
            drawCircle(if (dirs != 0) activeColor else Color(0x88FFFFFF), radius = kr, center = kc)
            drawCircle(PadBorder, radius = kr, center = kc, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
    }
}
