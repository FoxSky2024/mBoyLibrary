package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import kotlinx.coroutines.delay

private fun fmt(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** Ventana propia de la música: disco giratorio, progreso, anterior/play/siguiente, aleatorio y volumen. */
@Composable
fun MusicScreen(actions: LibraryActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val playing by Music.playing
    val title by Music.title
    val artist by Music.artist
    val art by Music.art
    var posMs by remember { mutableIntStateOf(0) }
    var durMs by remember { mutableIntStateOf(0) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        while (true) { posMs = Music.positionMs(); durMs = Music.durationMs(); delay(400) }
    }
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) { while (playing) { delay(33); angle = (angle + 1.2f) % 360f } }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1433), p.bg)))) {
        ScreenHeader(title = "Música", onBack = onBack)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            val disc: Dp = if (wide) minOf(maxHeight - 24.dp, maxWidth * 0.4f, 300.dp) else minOf(maxWidth - 64.dp, maxHeight * 0.38f, 300.dp)
            val discBox: @Composable () -> Unit = {
                Box(Modifier.size(disc).rotate(angle).clip(CircleShape).background(Color(0xFF0C0C10)), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        for (i in 1..8) drawCircle(Color.White.copy(alpha = 0.05f), r * (0.5f + i * 0.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    }
                    Box(Modifier.size(disc * 0.56f).clip(CircleShape).background(Color(0xFF2A2250)), contentAlignment = Alignment.Center) {
                        art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: PawIcon(disc * 0.2f)
                    }
                    Box(Modifier.size(disc * 0.06f).clip(CircleShape).background(Color(0xFF0C0C10)))
                }
            }
            val controls: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!Music.hasSource() && Settings.musicFolder.value.isEmpty() && Settings.musicUri.value.isEmpty()) {
                        Text("Elige una carpeta con tu música", color = p.dim, fontSize = 13.sp)
                    } else {
                        Text(title.ifBlank { "—" }, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (artist.isNotBlank()) Text(artist, color = p.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val frac = if (durMs > 0) (drag ?: (posMs.toFloat() / durMs)) else 0f
                    Slider(frac.coerceIn(0f, 1f), { drag = it }, Modifier.fillMaxWidth(),
                        onValueChangeFinished = { drag?.let { Music.seekTo((it * durMs).toInt()); posMs = (it * durMs).toInt() }; drag = null })
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmt(if (drag != null) (drag!! * durMs).toInt() else posMs), color = p.dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(fmt(durMs), color = p.dim, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ic(Icons.Filled.Shuffle, 26.dp, if (Settings.musicShuffle.value) p.play else p.dim, Modifier.clickable { Sfx.click(); Settings.setMusicShuffle(!Settings.musicShuffle.value) }.padding(6.dp))
                        Ic(Icons.Filled.SkipPrevious, 34.dp, p.text, Modifier.clickable { Sfx.click(); Music.change(-1) }.padding(6.dp))
                        Box(Modifier.size(64.dp).clip(CircleShape).background(p.play).clickable { Sfx.click(); Music.toggle(ctx) }, contentAlignment = Alignment.Center) {
                            Ic(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, 34.dp, p.onPlay)
                        }
                        Ic(Icons.Filled.SkipNext, 34.dp, p.text, Modifier.clickable { Sfx.click(); Music.change(1) }.padding(6.dp))
                        Ic(if (Settings.musicMuted.value) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, 26.dp, p.dim,
                            Modifier.clickable { Sfx.click(); Settings.setMusicMuted(!Settings.musicMuted.value); Music.applyVolume() }.padding(6.dp))
                    }
                    Slider(Settings.musicVolume.floatValue, { Settings.setMusicVolume(it); Music.applyVolume() }, Modifier.fillMaxWidth(0.7f))
                    val nxt = remember(title) { Music.peek(1) }
                    if (nxt.isNotBlank()) Text("Siguiente: $nxt", color = p.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton("Elegir carpeta", actions.pickMusicFolder)
                        AppButton("Un archivo", actions.pickMusic)
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(Ds.Pad), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(0.45f), contentAlignment = Alignment.Center) { discBox() }
                    Box(Modifier.weight(0.55f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { controls() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Ds.Pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    discBox(); controls()
                }
            }
        }
    }
}
