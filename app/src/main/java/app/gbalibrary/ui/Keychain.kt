package app.gbalibrary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gbalibrary.data.Pawb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Llavero colgando. Al verlo se balancea y se queda quieto; tocarlo lo balancea con un destello,
 * doble toque lo inspecciona (onInspect) y mantenerlo presionado y arrastrar lo mueve (onMove/onEnd).
 */
@Composable
fun KeychainView(
    id: String, modifier: Modifier = Modifier, scale: Float = 1f, replay: Int = 0, swing: Boolean = true, yaw: Float = 0f,
    onMove: ((Float, Float) -> Unit)? = null, onEnd: (() -> Unit)? = null, onInspect: (() -> Unit)? = null,
) {
    val k = ShopCatalog.keychain(id) ?: return
    val angle = remember(id) { Animatable(0f) }
    var taps by remember(id) { mutableIntStateOf(0) }
    var burstStart by remember(id) { mutableLongStateOf(0L) }
    var now by remember(id) { mutableLongStateOf(0L) }
    LaunchedEffect(id, replay, taps) {
        if (swing) {
            angle.snapTo(if (taps == 0) 30f else 20f)
            angle.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 20f))
        }
    }
    // al tocarlo da una vuelta completa (se ven las capas del llavero 3D)
    val spin = remember(id) { Animatable(0f) }
    LaunchedEffect(id, taps) {
        if (taps > 0) { spin.snapTo(0f); spin.animateTo(360f, tween(800, easing = FastOutSlowInEasing)); spin.snapTo(0f) }
    }
    // animacion de particulas: siempre si el llavero tiene efecto; solo ~0.9 s tras tocarlo si no
    LaunchedEffect(id, burstStart) {
        while (k.fx != 0 || (burstStart != 0L && now - burstStart < 900_000_000L)) withFrameNanos { now = it }
    }
    val moveRef by rememberUpdatedState(onMove)
    val endRef by rememberUpdatedState(onEnd)
    val inspectRef by rememberUpdatedState(onInspect)
    val burstP = if (burstStart != 0L && now - burstStart in 0..900_000_000L) (now - burstStart) / 9e8f else -1f

    Column(
        modifier
            .width(84.dp * scale)
            .pointerInput(id) {
                detectTapGestures(
                    onTap = { taps++; burstStart = System.nanoTime(); now = burstStart },
                    onDoubleTap = { inspectRef?.invoke() },
                )
            }
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDrag = { change, d -> change.consume(); moveRef?.invoke(d.x, d.y) },
                    onDragEnd = { endRef?.invoke() },
                    onDragCancel = { endRef?.invoke() },
                )
            }
            .graphicsLayer { rotationZ = angle.value; transformOrigin = TransformOrigin(0.5f, 0f) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(20.dp * scale, 64.dp * scale)) { drawChain(Color(0xFFCFD8DC)) }
        Box(Modifier.size((if (k.img.isNotEmpty()) 78.dp else 58.dp) * scale)) {
            StackedCharm(k, angle.value * 1.3f + spin.value + yaw)
            if (k.fx != 0 || burstP >= 0f) Canvas(Modifier.fillMaxSize()) { drawFx(k.fx, now / 1e9f, burstP) }
        }
    }
}

private const val STACK_RES = 96
private const val STACK_LAYERS = 25

/**
 * Sprite stacking real: de la silueta del dibujo se construye un volumen (cojin redondeado) y se corta en
 * STACK_LAYERS rebanadas. Cada rebanada es DISTINTA: solo tiene los pixeles que existen a esa profundidad.
 * La capa exterior de cada columna lleva el dibujo (cara), las interiores son pared oscura.
 */
private fun buildStack(src: IntArray): List<ImageBitmap> {
    val n = STACK_RES
    val inf = 1e6f
    val dist = FloatArray(n * n) { if ((src[it] ushr 24) > 127) inf else 0f }
    // distancia al borde (chamfer en dos pasadas)
    for (y in 0 until n) for (x in 0 until n) {
        val i = y * n + x
        var d = dist[i]
        if (d == 0f) continue
        d = minOf(d, (if (x > 0) dist[i - 1] else 0f) + 1f, (if (y > 0) dist[i - n] else 0f) + 1f)
        d = minOf(d, (if (x > 0 && y > 0) dist[i - n - 1] else 0f) + 1.4f, (if (x < n - 1 && y > 0) dist[i - n + 1] else 0f) + 1.4f)
        dist[i] = d
    }
    for (y in n - 1 downTo 0) for (x in n - 1 downTo 0) {
        val i = y * n + x
        var d = dist[i]
        if (d == 0f) continue
        d = minOf(d, (if (x < n - 1) dist[i + 1] else 0f) + 1f, (if (y < n - 1) dist[i + n] else 0f) + 1f)
        d = minOf(d, (if (x < n - 1 && y < n - 1) dist[i + n + 1] else 0f) + 1.4f, (if (x > 0 && y < n - 1) dist[i + n - 1] else 0f) + 1.4f)
        dist[i] = d
    }
    val radius = 8f // ancho del bisel redondeado en pixeles
    val h = FloatArray(n * n) {
        if (dist[it] <= 0f) 0f else {
            val t = 1f - minOf(dist[it] / radius, 1f)
            Math.sqrt((1f - t * t).toDouble()).toFloat().coerceAtLeast(0.25f)
        }
    }
    val step = 2f / (STACK_LAYERS - 1)
    fun shade(c: Int, f: Float): Int {
        val r = ((c shr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = ((c shr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((c and 0xFF) * f).toInt().coerceIn(0, 255)
        return (c and -0x1000000) or (r shl 16) or (g shl 8) or b
    }
    // layers[0] = cara trasera ... layers[N-1] = cara delantera
    return List(STACK_LAYERS) { k ->
        val d = -1f + k * step
        val out = IntArray(n * n)
        for (i in 0 until n * n) {
            val hh = h[i]
            if (hh <= 0f || kotlin.math.abs(d) > hh + 1e-4f) continue
            val skin = if (d >= 0f) d + step > hh else -d + step > hh
            val base = src[i] or (0xFF shl 24)
            out[i] = if (skin) {
                if (d >= 0f) shade(base, 0.7f + 0.3f * hh) else shade(base, 0.45f)
            } else shade(base, 0.5f + 0.15f * (1f - kotlin.math.abs(d)))
        }
        android.graphics.Bitmap.createBitmap(out, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

@Composable
private fun StackedCharm(k: ShopKeychain, yaw: Float) {
    val dens = LocalDensity.current
    val bmp: ImageBitmap? = if (k.img.isNotEmpty()) rememberAssetImage(k.img) else remember(k.id) {
        val px = 160
        ImageBitmap(px, px).also { b ->
            CanvasDrawScope().draw(dens, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(b), Size(px.toFloat(), px.toFloat())) { drawCharm(k) }
        }
    }
    val layers: List<ImageBitmap>? = remember(bmp) {
        val b = bmp ?: return@remember null
        val small = ImageBitmap(STACK_RES, STACK_RES)
        CanvasDrawScope().draw(dens, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(small), Size(STACK_RES.toFloat(), STACK_RES.toFloat())) {
            drawImage(b, dstSize = IntSize(STACK_RES, STACK_RES), filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
        }
        val px = IntArray(STACK_RES * STACK_RES)
        small.readPixels(px)
        buildStack(px)
    }
    Canvas(Modifier.fillMaxSize()) {
        val ls = layers ?: return@Canvas
        val rad = Math.toRadians(yaw.toDouble()).toFloat()
        val c = cos(rad); val sn = sin(rad)
        val pitch = 0.42f
        val cp = cos(pitch); val sp = sin(pitch)
        val half = size.width * 0.13f // medio grosor
        val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val cx = size.width / 2f; val cy = size.height / 2f
        // se dibuja de lo mas lejano a lo mas cercano al que mira
        val order = if (c >= 0f) ls.indices else ls.indices.reversed()
        for (i in order) {
            val d = -1f + i * (2f / (ls.size - 1))
            val z = d * half
            withTransform({
                translate(z * sn, -z * c * sp)
                scale(c, cp, Offset(cx, cy))
            }) {
                drawImage(ls[i], dstSize = dst, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
            }
        }
    }
}

private fun DrawScope.drawSpark(x: Float, y: Float, r: Float, c: Color) {
    val q = r * 0.25f
    val p = Path().apply {
        moveTo(x, y - r); lineTo(x + q, y - q); lineTo(x + r, y); lineTo(x + q, y + q)
        lineTo(x, y + r); lineTo(x - q, y + q); lineTo(x - r, y); lineTo(x - q, y - q); close()
    }
    drawPath(p, c)
}

private fun DrawScope.drawFx(fx: Int, sec: Float, burst: Float) {
    val w = size.width
    if (fx != 0) for (n in 0 until 12) {
        val seed = (n * 0.618034f) % 1f
        val seed2 = (n * 0.37f) % 1f
        val ph = (sec * 0.5f + n / 12f) % 1f
        val fade = sin(PI.toFloat() * ph)
        when (fx) {
            1 -> drawSpark(w * (0.05f + 0.9f * seed), w * (0.05f + 0.9f * seed2), w * 0.09f * fade, Color(0xFFFFF59D).copy(alpha = fade))
            2 -> {
                val c = androidx.compose.ui.graphics.lerp(Color(0xFFFFEB3B), Color(0xFFE53935), ph)
                drawCircle(c.copy(alpha = 1f - ph), w * 0.1f * (1f - ph) + 1f, Offset(w * (0.2f + 0.6f * seed) + sin(ph * 6.28f + n) * w * 0.04f, w * (1.0f - ph * 1.1f)))
            }
            3 -> drawCircle(Color.White.copy(alpha = 0.9f * fade), w * 0.045f, Offset(w * (0.05f + 0.9f * seed) + sin(ph * 6.28f + n) * w * 0.05f, w * (-0.1f + ph * 1.2f)))
            4 -> {
                val x = w * (0.1f + 0.8f * seed) + sin(ph * 6.28f + n) * w * 0.03f
                val y = w * (1.0f - ph * 1.2f)
                val r = w * 0.04f
                val c = Color(0xFFFF6F9C).copy(alpha = fade)
                drawCircle(c, r, Offset(x - r, y)); drawCircle(c, r, Offset(x + r, y))
                drawPath(Path().apply { moveTo(x - r * 2f, y + r * 0.3f); lineTo(x + r * 2f, y + r * 0.3f); lineTo(x, y + r * 2.6f); close() }, c)
            }
            else -> drawCircle(Color(0xFFB2EBF2).copy(alpha = 0.8f * fade), w * (0.03f + 0.04f * seed2), Offset(w * (0.1f + 0.8f * seed) + sin(ph * 6.28f + n) * w * 0.04f, w * (1.0f - ph * 1.2f)), style = Stroke(w * 0.015f))
        }
    }
    if (burst >= 0f) {
        val cx = w / 2f
        for (n in 0 until 14) {
            val a = n * 2f * PI.toFloat() / 14f
            val d = w * (0.2f + 0.75f * burst)
            drawSpark(cx + cos(a) * d, cx + sin(a) * d, w * 0.08f * (1f - burst) + 1f, (if (n % 2 == 0) Color(0xFFFFE082) else Color.White).copy(alpha = 1f - burst))
        }
        drawCircle(Color.White.copy(alpha = 0.6f * (1f - burst)), w * (0.3f + 0.6f * burst), Offset(cx, cx), style = Stroke(w * 0.03f))
    }
}

/** Vista de inspeccion: el llavero grande que se puede girar arrastrando; tocar fuera lo cierra. */
@Composable
fun KeychainInspect(id: String, onClose: () -> Unit) {
    val k = ShopCatalog.keychain(id) ?: return
    var ry by remember { mutableFloatStateOf(0f) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(Unit) { spin.animateTo(360f, tween(1400, easing = FastOutSlowInEasing)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xCC000000))
                .pointerInput(Unit) { detectHorizontalDragGestures { _, d -> ry += d * 0.6f } }
                .pointerInput(Unit) { detectTapGestures { onClose() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KeychainView(id, scale = 1.9f, swing = false, yaw = ry + spin.value)
                Text(k.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(k.rarity.label, color = k.rarity.color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Arrastra para girar · toca para cerrar", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

/**
 * Tres argollas semitransparentes (ancladas al borde inferior de la cabecera del perfil, se mueven con ella).
 * Mantener presionado un llavero y arrastrarlo: solo se puede soltar en una argolla (si hay otro, se intercambian).
 */
@Composable
fun KeychainRings(headerBottomRoot: Float) {
    val dens = LocalDensity.current
    var rootY by remember { mutableFloatStateOf(0f) }
    var w by remember { mutableIntStateOf(0) }
    var inspect by remember { mutableStateOf<String?>(null) }
    var dragI by remember { mutableIntStateOf(-1) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val ringPx = with(dens) { 28.dp.toPx() }
    val gapPx = with(dens) { 54.dp.toPx() }
    val endPx = with(dens) { 290.dp.toPx() }
    val scale = 0.9f
    val ringY = headerBottomRoot - rootY - with(dens) { 12.dp.toPx() }
    fun cx(i: Int) = w - endPx - i * gapPx
    val visible = headerBottomRoot > 0f && w > 0 && ringY > with(dens) { 40.dp.toPx() }
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootY = it.positionInRoot().y; w = it.size.width }) {
        if (visible) {
            for (i in 0..2) {
                Box(
                    Modifier.offset { IntOffset((cx(i) - ringPx / 2f).roundToInt(), (ringY - ringPx / 2f).roundToInt()) }
                        .size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
                        .border(2.dp, Color.White.copy(alpha = 0.40f), CircleShape),
                )
            }
            for (i in 0..2) {
                val kid = Pawb.kcSlots[i]
                if (kid.isEmpty()) continue
                androidx.compose.runtime.key(kid) {
                    val half = with(dens) { 42.dp.toPx() } * scale
                    val top = with(dens) { 8.dp.toPx() } * scale
                    KeychainView(
                        kid,
                        Modifier.offset {
                            IntOffset((cx(i) - half + (if (dragI == i) dx else 0f)).roundToInt(), (ringY - top + (if (dragI == i) dy else 0f)).roundToInt())
                        },
                        scale = scale,
                        onMove = { mx, my -> dragI = i; dx += mx; dy += my },
                        onEnd = {
                            if (dragI == i) {
                                val px = cx(i) + dx; val py = ringY + dy
                                var best = i; var bd = Float.MAX_VALUE
                                for (j in 0..2) {
                                    val ddx = px - cx(j); val ddy = py - ringY
                                    val d = ddx * ddx + ddy * ddy
                                    if (d < bd) { bd = d; best = j }
                                }
                                val lim = with(dens) { 70.dp.toPx() }
                                if (bd <= lim * lim && best != i) Pawb.swapKeychain(i, best)
                            }
                            dragI = -1; dx = 0f; dy = 0f
                        },
                        onInspect = { inspect = kid },
                    )
                }
            }
        }
    }
    inspect?.let { KeychainInspect(it) { inspect = null } }
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
        5 -> { // moneda
            val ce = Offset(w / 2f, w * 0.52f)
            drawCircle(k.c2, w * 0.46f, ce); drawCircle(k.c1, w * 0.4f, ce); drawCircle(k.c2, w * 0.28f, ce, style = line)
            drawCircle(k.c2, w * 0.09f, ce); for (q in listOf(Offset(-0.15f, -0.11f), Offset(0.15f, -0.11f), Offset(-0.07f, -0.2f), Offset(0.07f, -0.2f))) drawCircle(k.c2, w * 0.04f, Offset(ce.x + q.x * w, ce.y + q.y * w * 0.4f - w * 0.02f))
        }
        6 -> { // rayo
            val pth = Path().apply { moveTo(w * 0.6f, w * 0.04f); lineTo(w * 0.2f, w * 0.56f); lineTo(w * 0.46f, w * 0.56f); lineTo(w * 0.34f, w * 0.96f); lineTo(w * 0.82f, w * 0.38f); lineTo(w * 0.54f, w * 0.38f); lineTo(w * 0.72f, w * 0.04f); close() }
            drawPath(pth, k.c1); drawPath(pth, k.c2, style = line)
        }
        7 -> { // luna
            val a = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.1f, w * 0.1f), Size(w * 0.8f, w * 0.8f))) }
            val b = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.34f, w * 0.0f), Size(w * 0.7f, w * 0.7f))) }
            val m = Path.combine(PathOperation.Difference, a, b)
            drawPath(m, k.c1); drawPath(m, k.c2, style = line)
            drawSpark(w * 0.72f, w * 0.62f, w * 0.1f, k.c1)
        }
        8 -> { // cabeza de gato
            val ear1 = Path().apply { moveTo(w * 0.14f, w * 0.5f); lineTo(w * 0.16f, w * 0.08f); lineTo(w * 0.44f, w * 0.3f); close() }
            val ear2 = Path().apply { moveTo(w * 0.86f, w * 0.5f); lineTo(w * 0.84f, w * 0.08f); lineTo(w * 0.56f, w * 0.3f); close() }
            drawPath(ear1, k.c1); drawPath(ear2, k.c1); drawPath(ear1, k.c2, style = line); drawPath(ear2, k.c2, style = line)
            drawCircle(k.c1, w * 0.36f, Offset(w / 2f, w * 0.6f)); drawCircle(k.c2, w * 0.36f, Offset(w / 2f, w * 0.6f), style = line)
            drawCircle(k.c2, w * 0.05f, Offset(w * 0.37f, w * 0.55f)); drawCircle(k.c2, w * 0.05f, Offset(w * 0.63f, w * 0.55f))
            drawCircle(k.c2, w * 0.035f, Offset(w / 2f, w * 0.66f))
        }
        9 -> { // orbe
            val tl = Offset(w * 0.06f, w * 0.1f); val sz = Size(w * 0.88f, w * 0.88f)
            drawArc(k.c1, 180f, 180f, true, tl, sz); drawArc(Color.White, 0f, 180f, true, tl, sz)
            drawCircle(k.c2, w * 0.44f, Offset(w / 2f, w * 0.54f), style = line); drawLine(k.c2, Offset(w * 0.06f, w * 0.54f), Offset(w * 0.94f, w * 0.54f), w * 0.06f)
            drawCircle(Color.White, w * 0.12f, Offset(w / 2f, w * 0.54f)); drawCircle(k.c2, w * 0.12f, Offset(w / 2f, w * 0.54f), style = line)
        }
        10 -> { // corona
            val pth = Path().apply { moveTo(w * 0.08f, w * 0.84f); lineTo(w * 0.08f, w * 0.3f); lineTo(w * 0.3f, w * 0.56f); lineTo(w * 0.5f, w * 0.16f); lineTo(w * 0.7f, w * 0.56f); lineTo(w * 0.92f, w * 0.3f); lineTo(w * 0.92f, w * 0.84f); close() }
            drawPath(pth, k.c1); drawPath(pth, k.c2, style = line)
            for (x in listOf(0.26f, 0.5f, 0.74f)) drawCircle(k.c2, w * 0.05f, Offset(w * x, w * 0.72f))
        }
        11 -> { // llave
            drawCircle(k.c1, w * 0.22f, Offset(w * 0.3f, w * 0.3f), style = Stroke(w * 0.12f)); drawCircle(k.c2, w * 0.3f, Offset(w * 0.3f, w * 0.3f), style = Stroke(w * 0.02f))
            drawLine(k.c1, Offset(w * 0.44f, w * 0.44f), Offset(w * 0.86f, w * 0.88f), w * 0.11f)
            drawLine(k.c1, Offset(w * 0.68f, w * 0.7f), Offset(w * 0.82f, w * 0.58f), w * 0.09f); drawLine(k.c1, Offset(w * 0.78f, w * 0.8f), Offset(w * 0.92f, w * 0.68f), w * 0.09f)
        }
        12 -> { // flor
            for (i in 0 until 5) {
                val a = Math.toRadians((-90 + i * 72).toDouble())
                val ce = Offset(w / 2f + (w * 0.24f * Math.cos(a)).toFloat(), w * 0.52f + (w * 0.24f * Math.sin(a)).toFloat())
                drawCircle(k.c1, w * 0.2f, ce); drawCircle(k.c2, w * 0.2f, ce, style = Stroke(w * 0.03f))
            }
            drawCircle(k.c2, w * 0.14f, Offset(w / 2f, w * 0.52f)); drawCircle(Color(0xFFFFF176), w * 0.09f, Offset(w / 2f, w * 0.52f))
        }
        else -> { // cartucho
            val body = Path().apply { moveTo(w * 0.14f, w * 0.08f); lineTo(w * 0.86f, w * 0.08f); lineTo(w * 0.86f, w * 0.78f); lineTo(w * 0.7f, w * 0.94f); lineTo(w * 0.3f, w * 0.94f); lineTo(w * 0.14f, w * 0.78f); close() }
            drawPath(body, k.c1); drawPath(body, k.c2, style = line)
            drawRoundRect(k.c2, Offset(w * 0.26f, w * 0.2f), Size(w * 0.48f, w * 0.34f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
            drawRect(k.c2, Offset(w * 0.34f, w * 0.7f), Size(w * 0.32f, w * 0.08f))
        }
    }
}
