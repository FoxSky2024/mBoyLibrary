package app.gbalibrary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Llavero colgando: al verlo se balancea de izquierda a derecha y poco a poco se queda quieto.
 * Tocarlo lo vuelve a balancear. [replay] cambia para repetir el balanceo (p. ej. al entrar al perfil).
 */
@Composable
fun KeychainView(id: String, modifier: Modifier = Modifier, scale: Float = 1f, replay: Int = 0, onMove: ((Float, Float) -> Unit)? = null) {
    val k = ShopCatalog.keychain(id) ?: return
    val angle = remember(id) { Animatable(0f) }
    var taps by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, replay, taps) {
        angle.snapTo(30f)
        angle.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 20f))
    }
    Column(
        modifier
            .graphicsLayer { rotationZ = angle.value; transformOrigin = TransformOrigin(0.5f, 0f) }
            .then(
                if (onMove != null) Modifier.pointerInput(id) {
                    detectDragGesturesAfterLongPress(onDrag = { change, d -> change.consume(); onMove(d.x, d.y) })
                } else Modifier,
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { taps++ },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(20.dp * scale, 64.dp * scale)) { drawChain(Color(0xFFCFD8DC)) }
        if (k.img.isNotEmpty()) {
            val b = rememberAssetImage(k.img)
            if (b != null) Image(b, null, Modifier.size(78.dp * scale), contentScale = ContentScale.Fit)
        } else {
            Canvas(Modifier.size(58.dp * scale)) { drawCharm(k) }
        }
    }
}

private fun DrawScope.drawChain(c: Color) {
    val w = size.width
    drawCircle(c, w * 0.36f, Offset(w / 2f, w * 0.4f), style = Stroke(w * 0.14f))
    var y = w * 0.8f
    var side = false
    while (y < size.height - w * 0.9f) {
        val lw = if (side) w * 0.28f else w * 0.7f
        drawRoundRect(c, Offset(w / 2f - lw / 2f, y), Size(lw, w * 0.95f), androidx.compose.ui.geometry.CornerRadius(lw / 2f), style = Stroke(w * 0.13f))
        y += w * 0.72f
        side = !side
    }
}

private fun DrawScope.drawCharm(k: ShopKeychain) {
    val w = size.width
    val line = Stroke(w * 0.05f)
    when (k.charm) {
        0 -> { // huella
            drawOval(k.c1, Offset(w * 0.22f, w * 0.46f), Size(w * 0.56f, w * 0.44f)); drawOval(k.c2, Offset(w * 0.22f, w * 0.46f), Size(w * 0.56f, w * 0.44f), style = line)
            listOf(Offset(0.16f, 0.36f), Offset(0.36f, 0.16f), Offset(0.64f, 0.16f), Offset(0.84f, 0.36f)).forEach {
                drawCircle(k.c1, w * 0.11f, Offset(w * it.x, w * it.y)); drawCircle(k.c2, w * 0.11f, Offset(w * it.x, w * it.y), style = line)
            }
        }
        1 -> { // estrella
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) w * 0.48f else w * 0.2f
                val a = Math.toRadians((-90 + i * 36).toDouble())
                val x = w / 2f + (r * Math.cos(a)).toFloat(); val y = w * 0.52f + (r * Math.sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); drawPath(path, k.c1); drawPath(path, k.c2, style = line)
        }
        2 -> { // corazon
            drawCircle(k.c1, w * 0.26f, Offset(w * 0.3f, w * 0.34f)); drawCircle(k.c1, w * 0.26f, Offset(w * 0.7f, w * 0.34f))
            val t = Path().apply { moveTo(w * 0.06f, w * 0.44f); lineTo(w * 0.94f, w * 0.44f); lineTo(w * 0.5f, w * 0.94f); close() }
            drawPath(t, k.c1); drawCircle(Color.White.copy(alpha = 0.55f), w * 0.07f, Offset(w * 0.28f, w * 0.28f))
        }
        3 -> { // gema
            val g = Path().apply { moveTo(w * 0.28f, w * 0.12f); lineTo(w * 0.72f, w * 0.12f); lineTo(w * 0.94f, w * 0.4f); lineTo(w * 0.5f, w * 0.94f); lineTo(w * 0.06f, w * 0.4f); close() }
            drawPath(g, k.c1); drawPath(g, k.c2, style = line)
            drawLine(k.c2, Offset(w * 0.06f, w * 0.4f), Offset(w * 0.94f, w * 0.4f), w * 0.04f)
            drawLine(k.c2, Offset(w * 0.28f, w * 0.12f), Offset(w * 0.38f, w * 0.4f), w * 0.04f); drawLine(k.c2, Offset(w * 0.72f, w * 0.12f), Offset(w * 0.62f, w * 0.4f), w * 0.04f)
            drawLine(k.c2, Offset(w * 0.38f, w * 0.4f), Offset(w * 0.5f, w * 0.94f), w * 0.04f); drawLine(k.c2, Offset(w * 0.62f, w * 0.4f), Offset(w * 0.5f, w * 0.94f), w * 0.04f)
        }
        else -> { // cartucho
            val body = Path().apply { moveTo(w * 0.14f, w * 0.08f); lineTo(w * 0.86f, w * 0.08f); lineTo(w * 0.86f, w * 0.78f); lineTo(w * 0.7f, w * 0.94f); lineTo(w * 0.3f, w * 0.94f); lineTo(w * 0.14f, w * 0.78f); close() }
            drawPath(body, k.c1); drawPath(body, k.c2, style = line)
            drawRoundRect(k.c2, Offset(w * 0.26f, w * 0.2f), Size(w * 0.48f, w * 0.34f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
            drawRect(k.c2, Offset(w * 0.34f, w * 0.7f), Size(w * 0.32f, w * 0.08f))
        }
    }
}
