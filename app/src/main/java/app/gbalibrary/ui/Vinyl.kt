package app.gbalibrary.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Disco de vinilo abajo a la derecha (solo biblioteca / menus, nunca dentro del juego).
 * Al cambiar de cancion: disco grande + caratula + nombre; a los 5 s se desvanece y queda un mini
 * icono girando. Tocar el mini icono muestra el nombre otros 5 s. Si la musica se pausa, se esconde.
 */
@Composable
fun VinylOverlay(modifier: Modifier = Modifier) {
    val playing = Music.playing.value
    var showName by remember { mutableStateOf(false) }
    var tapStamp by remember { mutableIntStateOf(0) }

    // Giro de moneda (eje Y) y arrastre vertical sobre el disco.
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val flip = remember { Animatable(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var peekTitle by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { DiscClick.init(ctx) }
    val thresholdPx = with(density) { 40.dp.toPx() }
    val gesture = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                if (flip.isRunning) return@detectVerticalDragGestures
                dragging = true
                dragY = 0f
            },
            onVerticalDrag = { change, amount ->
                if (!dragging) return@detectVerticalDragGestures
                change.consume()
                dragY += amount
                // arriba = siguiente, abajo = anterior
                val d = if (dragY < 0f) 1 else -1
                if (kotlin.math.abs(dragY) > 8f) peekTitle = Music.peek(d)
            },
            onDragEnd = {
                val total = dragY
                dragging = false
                dragY = 0f
                if (kotlin.math.abs(total) >= thresholdPx && !flip.isRunning) {
                    val delta = if (total < 0f) 1 else -1
                    scope.launch {
                        var fired = false
                        flip.snapTo(0f)
                        flip.animateTo(360f, tween(600, easing = EaseOut)) {
                            // a mitad del giro (90 grados): suena el clic y cambia la cancion/caratula
                            if (!fired && value >= 90f) {
                                fired = true
                                DiscClick.play()
                                Music.change(delta)
                            }
                        }
                        flip.snapTo(0f)
                    }
                }
            },
            onDragCancel = {
                dragging = false
                dragY = 0f
            },
        )
    }

    // Cada cancion nueva muestra la tarjeta 5 s.
    LaunchedEffect(Music.trackStamp.intValue) {
        if (Music.trackStamp.intValue > 0) {
            showName = true
            delay(5000)
            showName = false
        }
    }
    // Tocar el mini icono: 5 s mas.
    LaunchedEffect(tapStamp) {
        if (tapStamp > 0) {
            showName = true
            delay(5000)
            showName = false
        }
    }

    AnimatedVisibility(visible = playing, modifier = modifier, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
        val spin = rememberInfiniteTransition(label = "vinyl")
        val angle by spin.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        )
        val bigAlpha by animateFloatAsState(if (showName) 1f else 0f, tween(600), label = "big")
        val miniAlpha by animateFloatAsState(if (showName) 0f else 0.75f, tween(600), label = "mini")

        Box(Modifier.padding(12.dp), contentAlignment = Alignment.BottomEnd) {
            // Tarjeta grande con caratula y nombre (se desvanece).
            if (bigAlpha > 0.01f) {
                Row(
                    Modifier
                        .alpha(bigAlpha)
                        .background(Color(0xE6101418), RoundedCornerShape(40.dp))
                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.widthIn(max = 180.dp)) {
                        Text(Music.title.value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (Music.artist.value.isNotEmpty()) {
                            Text(Music.artist.value, color = Color(0xFFB0BEC5), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(gesture) { Disc(size = 64, angle = angle, flipY = flip.value) }
                }
            }
            // Mini icono (aparece al desvanecerse la tarjeta). Tocar = mostrar el nombre.
            if (miniAlpha > 0.01f) {
                Box(
                    Modifier
                        .alpha(if (dragging) 0.75f else miniAlpha)
                        .size(48.dp)
                        .then(gesture)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tapStamp++ },
                    contentAlignment = Alignment.Center,
                ) { Disc(size = 34, angle = angle, flipY = flip.value) }
            }
            // Vista previa de la cancion de destino (alpha 0.35) mientras se arrastra.
            if (dragging && kotlin.math.abs(dragY) > 8f) {
                val maxPx = with(density) { 48.dp.toPx() }
                val off = dragY.coerceIn(-maxPx, maxPx)
                Row(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(0, (off * 0.6f).toInt() + (if (dragY < 0f) -maxPx.toInt() else maxPx.toInt())) }
                        .alpha(0.35f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (peekTitle.isNotEmpty()) {
                        Text(peekTitle, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                    }
                    Disc(size = 34, angle = 0f, flipY = 0f)
                }
            }
        }
    }
}

@Composable
private fun Disc(size: Int, angle: Float, flipY: Float = 0f) {
    val art = Music.art.value
    val accent = LocalPalette.current.accent
    val camera = with(LocalDensity.current) { 12.dp.toPx() * 4f }
    Box(
        Modifier
            .size(size.dp)
            .graphicsLayer { rotationY = flipY; cameraDistance = camera }
            // pasado 90 grados la cara se ve de espaldas: se contrarresta para que la caratula no salga espejada
            .graphicsLayer { scaleX = if (flipY in 90f..270f) -1f else 1f },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size.dp).graphicsLayer { rotationZ = angle }) {
            val r = this.size.width / 2f
            val c = Offset(r, r)
            drawCircle(Color(0xFF0B0B0D), radius = r, center = c)
            var g = r * 0.92f
            while (g > r * 0.40f) {
                drawCircle(Color(0xFF26262B), radius = g, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                g -= r * 0.09f
            }
            // brillo
            drawArc(
                Color(0x22FFFFFF), startAngle = 200f, sweepAngle = 40f, useCenter = true,
                topLeft = Offset(r * 0.1f, r * 0.1f), size = androidx.compose.ui.geometry.Size(r * 1.8f, r * 1.8f),
            )
            drawCircle(accent, radius = r * 0.36f, center = c)
        }
        if (art != null) {
            Image(
                bitmap = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size((size * 0.36f * 2f).dp).clip(androidx.compose.foundation.shape.CircleShape).rotate(angle),
            )
        }
        Box(Modifier.size((size * 0.08f).dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF111111)))
    }
}
