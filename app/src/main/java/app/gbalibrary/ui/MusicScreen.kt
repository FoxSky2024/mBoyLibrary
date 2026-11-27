package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun fmt(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** Forma de "galleta" (circulo ondulado) de los botones principales. */
private val CookieShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f; val cy = size.height / 2f; val r = minOf(cx, cy)
        val steps = 120
        for (i in 0..steps) {
            val a = i * 2f * PI.toFloat() / steps
            val rr = r * (0.93f + 0.07f * cos(8f * a))
            val x = cx + rr * cos(a); val y = cy + rr * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Paleta dinamica sacada de la portada, como en el reproductor de referencia. */
private class MusicTheme(val bg: Color, val card: Color, val card2: Color, val accent: Color, val onAccent: Color, val soft: Color)

private fun themeFrom(base: Color?): MusicTheme {
    val c = base ?: Color(0xFF8A6CDE)
    val black = Color(0xFF110C0D)
    return MusicTheme(
        bg = lerp(black, c, 0.10f),
        card = lerp(black, c, 0.42f),
        card2 = lerp(black, c, 0.22f),
        accent = lerp(c, Color.White, 0.72f),
        onAccent = lerp(black, c, 0.45f),
        soft = lerp(c, Color.White, 0.88f),
    )
}

@Composable
private fun rememberDominant(art: androidx.compose.ui.graphics.ImageBitmap?): Color? = remember(art) {
    art?.let {
        try {
            val px = android.graphics.Bitmap.createScaledBitmap(it.asAndroidBitmap(), 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3); android.graphics.Color.colorToHSV(px, hsv)
            hsv[1] = (hsv[1] * 1.25f).coerceIn(0.35f, 0.85f); hsv[2] = 0.75f
            Color(android.graphics.Color.HSVToColor(hsv))
        } catch (e: Exception) { null }
    }
}

@Composable
private fun rememberMusicTick(): Pair<Int, Int> {
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { pos = Music.positionMs(); dur = Music.durationMs(); delay(400) } }
    return pos to dur
}

/** Tocadiscos: placa con disco que gira (la portada es la etiqueta) y brazo que baja al sonar. */
@Composable
private fun Turntable(t: MusicTheme, art: androidx.compose.ui.graphics.ImageBitmap?, playing: Boolean, modifier: Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    val arm = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(playing) { while (playing) { delay(33); angle = (angle + 2.4f) % 360f } }
    LaunchedEffect(playing) { arm.animateTo(if (playing) 1f else 0f, androidx.compose.animation.core.tween(700)) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(14.dp)).background(lerp(t.card2, Color.Black, 0.25f))) {
        // el disco se centra en la placa; el brazo gira desde un pivote arriba a la derecha, fuera del disco
        val rec = minOf(maxHeight * 0.84f, maxWidth * 0.66f)
        val cxDp = maxWidth * 0.38f
        val cyDp = maxHeight * 0.5f
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(cxDp.toPx(), cyDp.toPx())
            val r = rec.toPx() / 2f
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.12f, c)
            drawCircle(lerp(t.card, Color.Black, 0.2f), r * 1.09f, c, style = Stroke(r * 0.06f))
        }
        Box(
            Modifier.offset(cxDp - rec / 2, cyDp - rec / 2).size(rec).rotate(angle).clip(CircleShape).background(Color(0xFF0A0A0C)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val rr = size.minDimension / 2f
                for (i in 1..9) drawCircle(Color.White.copy(alpha = 0.06f), rr * (0.55f + i * 0.045f), style = Stroke(1f))
            }
            Box(Modifier.size(rec * 0.5f).clip(CircleShape).background(t.card), contentAlignment = Alignment.Center) {
                art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) } ?: PawIcon(rec * 0.14f, t.soft)
            }
            Box(Modifier.size(rec * 0.04f).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
        }
        // la aguja va POR ENCIMA del disco
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(cxDp.toPx(), cyDp.toPx())
            val r = rec.toPx() / 2f
            val pivot = Offset(size.width * 0.90f, size.height * 0.16f)
            val tipRest = Offset(c.x + r * 1.0f, c.y - r * 0.1f)
            val tipPlay = Offset(c.x + r * 0.62f, c.y + r * 0.25f)
            val k = arm.value
            val tip = Offset(tipRest.x + (tipPlay.x - tipRest.x) * k, tipRest.y + (tipPlay.y - tipRest.y) * k)
            drawLine(t.soft, pivot, tip, strokeWidth = size.minDimension * 0.02f)
            drawCircle(t.accent, size.minDimension * 0.045f, pivot)
            drawCircle(t.soft, size.minDimension * 0.02f, tip)
        }
    }
}

@Composable
private fun CookieButton(icon: androidx.compose.ui.graphics.vector.ImageVector, t: MusicTheme, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CookieShape).background(t.accent).clickable { Sfx.click(); onClick() }, contentAlignment = Alignment.Center) {
        Ic(icon, size * 0.5f, t.onAccent)
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return if (h < 6) "Buenas noches" else if (h < 12) "Buenos días" else if (h < 20) "Buenas tardes" else "Buenas noches"
}

/** Ventana propia de la música, con el estilo del reproductor de referencia: tarjeta "Sonando ahora", tocadiscos y barra inferior. */
@Composable
fun MusicScreen(actions: LibraryActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val playing by Music.playing
    val title by Music.title
    val artist by Music.artist
    val art by Music.art
    val t = themeFrom(rememberDominant(art))
    val (posMs, durMs) = rememberMusicTick()
    var panel by remember { mutableIntStateOf(0) } // 0 Info, 1 Lista, 2 Letra
    val hasSource = Settings.musicFolder.value.isNotEmpty() || Settings.musicUri.value.isNotEmpty()
    val queue by produceState(emptyList<String>(), title, hasSource, Settings.musicShuffle.value) {
        value = withContext(Dispatchers.IO) { Music.queueTitles(40) }
    }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.bg, Color(0xFF0B0809))))) {
        // barra superior
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(t.card).clickable { Sfx.click(); onBack() }, contentAlignment = Alignment.Center) { Ic(Icons.Filled.ArrowBack, 20.dp, t.soft) }
            Text("Música", color = t.soft, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Box(Modifier.size(38.dp).clip(CircleShape).background(t.card).clickable { Sfx.click(); MusicNav.mini.value = true; MusicNav.show.value = false }, contentAlignment = Alignment.Center) { Ic(Icons.Filled.UnfoldLess, 20.dp, t.soft) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > 640.dp
            val main: @Composable (Modifier) -> Unit = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${greeting()}, ${Settings.profileName.value.ifBlank { "Jugador" }}", color = t.soft, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
                    // tarjeta Sonando ahora
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(t.card, lerp(t.card, Color.Black, 0.35f)))).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("▮▮  SONANDO AHORA", color = t.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(title.ifBlank { if (hasSource) "—" else "Sin música" }, color = t.soft, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(artist.ifBlank { "Elige una carpeta con tu música" }, color = t.accent.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Pista ${Music.queueIndex() + 1} de ${Music.trackCount()}", color = t.accent.copy(alpha = 0.7f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
                            Box(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CookieButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, t, 54.dp) { Music.toggle(ctx) }
                            }
                        }
                        Turntable(t, art, playing, Modifier.weight(1.3f).heightIn(max = 220.dp).aspectRatio(1.25f))
                    }
                    // fila de controles + siguientes
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.card2).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("A continuación", color = t.soft, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("Lo próximo en tu lista", color = t.accent.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                            Ic(Icons.Filled.Shuffle, 22.dp, if (Settings.musicShuffle.value) t.accent else t.soft.copy(alpha = 0.5f), Modifier.clip(CircleShape).clickable { Sfx.click(); Music.setShuffle(!Settings.musicShuffle.value) }.padding(8.dp))
                            CookieButton(Icons.Filled.SkipPrevious, t, 40.dp) { Music.change(-1) }
                            Box(Modifier.width(6.dp))
                            CookieButton(Icons.Filled.SkipNext, t, 40.dp) { Music.change(1) }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            queue.drop(1).take(10).forEachIndexed { i, q ->
                                Text(q.ifBlank { "—" }, color = t.soft, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(110.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(t.card).clickable { Sfx.click(); Music.change(i + 1) }.padding(8.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // botones redondos: elegir carpeta / elegir un archivo
                        Box(Modifier.size(52.dp).clip(CircleShape).background(t.soft).clickable { Sfx.click(); actions.pickMusicFolder() }, contentAlignment = Alignment.Center) { Ic(Icons.Filled.Folder, 24.dp, t.bg) }
                        Box(Modifier.size(52.dp).clip(CircleShape).background(t.soft).clickable { Sfx.click(); actions.pickMusic() }, contentAlignment = Alignment.Center) { Ic(Icons.Filled.MusicNote, 24.dp, t.bg) }
                        // imagen propia para la cancion que suena
                        Box(Modifier.size(52.dp).clip(CircleShape).background(t.accent).clickable { Sfx.click(); actions.pickSongImage() }, contentAlignment = Alignment.Center) { Ic(Icons.Filled.PhotoLibrary, 24.dp, t.onAccent) }
                    }
                    Box(Modifier.height(8.dp))
                }
            }
            val side: @Composable (Modifier) -> Unit = { m ->
                Column(m.clip(RoundedCornerShape(26.dp)).background(t.card2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Info", "Lista", "Letra").forEachIndexed { i, n ->
                            Text(n, color = if (panel == i) t.onAccent else t.soft, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (panel == i) t.accent else t.card).clickable { Sfx.click(); panel = i }.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (panel) {
                            0 -> {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(t.card).clickable { Sfx.click(); actions.pickSongImage() }, contentAlignment = Alignment.Center) {
                                    art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) } ?: PawIcon(48.dp, t.soft)
                                }
                                Text("Información", color = t.soft, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                InfoLine("Nombre", title.ifBlank { "—" }, t); InfoLine("Artista", artist.ifBlank { "—" }, t)
                                InfoLine("Duración", fmt(durMs), t); InfoLine("Pista", "${Music.queueIndex() + 1} / ${Music.trackCount()}", t)
                            }
                            1 -> queue.forEachIndexed { i, q ->
                                Text(q.ifBlank { "—" }, color = if (i == 0) t.onAccent else t.soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (i == 0) t.accent else t.card).clickable { Sfx.click(); if (i > 0) Music.change(i) }.padding(10.dp))
                            }
                            else -> Text("Sin letra disponible para esta canción.", color = t.accent.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    main(Modifier.weight(1.6f).fillMaxHeight())
                    side(Modifier.weight(1f).fillMaxHeight().padding(end = 12.dp, bottom = 8.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    main(Modifier.fillMaxWidth()); side(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(360.dp)); Box(Modifier.height(8.dp))
                }
            }
        }
        BottomBar(t, art, title, artist, posMs, durMs, playing)
    }
}

@Composable
private fun InfoLine(k: String, v: String, t: MusicTheme) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.card).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = t.accent.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(v, color = t.soft, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}


/** Barra de progreso propia (la de Material dibuja un pulgar enorme en esta version). */
@Composable
private fun SeekBar(frac: Float, color: Color, track: Color, modifier: Modifier = Modifier, onChange: (Float) -> Unit, onFinish: () -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    Box(
        modifier.height(22.dp)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / w).coerceIn(0f, 1f)); onFinish() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { onFinish() }, onDragCancel = { onFinish() }) { ch, _ -> onChange((ch.position.x / w).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(track))
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

/** Barra inferior: miniatura, titulo, progreso, aleatorio / anterior / play / siguiente / repetir y volumen. */
@Composable
private fun BottomBar(t: MusicTheme, art: androidx.compose.ui.graphics.ImageBitmap?, title: String, artist: String, posMs: Int, durMs: Int, playing: Boolean) {
    val ctx = LocalContext.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = if (durMs > 0) (drag ?: (posMs.toFloat() / durMs)) else 0f
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(t.card2).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(t.card), contentAlignment = Alignment.Center) {
                art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) }
            }
            Column(Modifier.weight(1f)) {
                Text(title.ifBlank { "—" }, color = t.soft, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = t.accent.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Ic(Icons.Filled.Shuffle, 20.dp, if (Settings.musicShuffle.value) t.accent else t.soft.copy(alpha = 0.5f), Modifier.clip(CircleShape).clickable { Sfx.click(); Music.setShuffle(!Settings.musicShuffle.value) }.padding(6.dp))
            Ic(Icons.Filled.SkipPrevious, 24.dp, t.soft, Modifier.clip(CircleShape).clickable { Sfx.click(); Music.change(-1) }.padding(6.dp))
            CookieButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, t, 44.dp) { Music.toggle(ctx) }
            Ic(Icons.Filled.SkipNext, 24.dp, t.soft, Modifier.clip(CircleShape).clickable { Sfx.click(); Music.change(1) }.padding(6.dp))
            Ic(if (Music.repeat.value) Icons.Filled.RepeatOne else Icons.Filled.Repeat, 20.dp, if (Music.repeat.value) t.accent else t.soft.copy(alpha = 0.5f), Modifier.clip(CircleShape).clickable { Sfx.click(); Music.setRepeat(!Music.repeat.value) }.padding(6.dp))
            Ic(if (Settings.musicMuted.value) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, 20.dp, t.soft, Modifier.clip(CircleShape).clickable { Sfx.click(); Settings.setMusicMuted(!Settings.musicMuted.value); Music.applyVolume() }.padding(6.dp))
            SeekBar(Settings.musicVolume.floatValue, t.accent, t.card, Modifier.width(80.dp), onChange = { Settings.setMusicVolume(it); Music.applyVolume() }, onFinish = {})
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fmt(if (drag != null) (drag!! * durMs).toInt() else posMs), color = t.accent.copy(alpha = 0.7f), fontSize = 11.sp)
            SeekBar(frac, t.accent, t.card, Modifier.weight(1f), onChange = { drag = it }, onFinish = { drag?.let { Music.seekTo((it * durMs).toInt()) }; drag = null })
            Text(fmt(durMs), color = t.accent.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

/** Reproductor mini flotante (pastilla con portada, titulo, play y siguiente) sobre la biblioteca. */
@Composable
fun MiniMusicPlayer(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val playing by Music.playing
    val title by Music.title
    val artist by Music.artist
    val art by Music.art
    val t = themeFrom(rememberDominant(art))
    Row(
        modifier.clip(RoundedCornerShape(50)).background(t.card2).padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            .clickable { Sfx.click(); MusicNav.mini.value = false; MusicNav.show.value = true },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(t.card), contentAlignment = Alignment.Center) {
            art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High) } ?: PawIcon(18.dp, t.soft)
        }
        Column(Modifier.width(120.dp)) {
            Text(title.ifBlank { "Música" }, color = t.soft, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = t.accent.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CookieButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, t, 34.dp) { Music.toggle(ctx) }
        Ic(Icons.Filled.SkipNext, 22.dp, t.soft, Modifier.clip(CircleShape).clickable { Sfx.click(); Music.change(1) }.padding(4.dp))
    }
}
