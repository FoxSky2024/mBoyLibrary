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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.mutableIntStateOf
import app.gbalibrary.data.Settings
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

private const val ROW_DP = 40

/**
 * Disco flotante de la musica (biblioteca y juegos). Estado 1: disco pequeno asomando por el borde izquierdo, girando
 * mientras suena. Estado 2 (al tocarlo): sale un poco mas, sigue girando y muestra anterior / pausa / siguiente,
 * modo aleatorio y la lista para saltar a otra cancion. Se encoge solo a los 7 s sin tocarlo.
 */
@Composable
fun VinylOverlay(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val has = Music.hasSource() && (Music.title.value.isNotEmpty() || Music.playing.value)
    if (!has) return
    val playing = Music.playing.value
    var open by remember { mutableStateOf(false) }
    var stamp by remember { mutableIntStateOf(0) }
    LaunchedEffect(open, stamp) { if (open) { delay(7000); open = false } }

    var ang by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        var last = 0L
        while (playing) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (last != 0L) ang = (ang + (n - last) / 1_000_000f * 0.1125f) % 360f // una vuelta cada 3,2 s
                last = n
            }
        }
    }
    val discSize by androidx.compose.animation.core.animateDpAsState(if (open) 96.dp else 44.dp, tween(260), label = "discSize")
    val peek by androidx.compose.animation.core.animateDpAsState(if (open) (-26).dp else (-14).dp, tween(260), label = "discPeek")

    Column(modifier) {
        Box(
            Modifier
                .offset(x = peek)
                .size(discSize)
                .alpha(if (playing) 1f else 0.65f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = !open; stamp++ },
            contentAlignment = Alignment.Center,
        ) { Disc(size = discSize.value.toInt(), angle = ang) }
        AnimatedVisibility(open, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            val queue by androidx.compose.runtime.produceState(emptyList<String>(), Music.title.value, Settings.musicShuffle.value) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Music.queueTitles(14) }
            }
            val accent = LocalPalette.current.accent
            Column(
                Modifier.padding(start = 8.dp).background(Color(0xD9101418), RoundedCornerShape(16.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(Music.title.value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ic(androidx.compose.material.icons.Icons.Filled.Shuffle, 20.dp, if (Settings.musicShuffle.value) accent else Color.White.copy(alpha = 0.6f),
                        Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { Sfx.click(); stamp++; Music.setShuffle(!Settings.musicShuffle.value) }.padding(6.dp))
                    Ic(androidx.compose.material.icons.Icons.Filled.SkipPrevious, 22.dp, Color.White,
                        Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { Sfx.click(); stamp++; Music.change(-1) }.padding(6.dp))
                    Ic(if (playing) androidx.compose.material.icons.Icons.Filled.Pause else androidx.compose.material.icons.Icons.Filled.PlayArrow, 24.dp, Color.Black,
                        Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(Color.White).clickable { Sfx.click(); stamp++; Music.toggle(ctx) }.padding(6.dp))
                    Ic(androidx.compose.material.icons.Icons.Filled.SkipNext, 22.dp, Color.White,
                        Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { Sfx.click(); stamp++; Music.change(1) }.padding(6.dp))
                }
                if (queue.size > 1) {
                    Column(Modifier.heightIn(max = 110.dp).widthIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        queue.drop(1).forEachIndexed { i, q ->
                            Text(
                                q.ifEmpty { "—" }, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { Sfx.click(); stamp++; Music.change(i + 1) }.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                        }
                    }
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
