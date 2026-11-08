@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.gbalibrary.data.Settings
import app.gbalibrary.stream.StreamClient
import kotlin.math.roundToInt

/**
 * Ventana de video flotante (PiP dentro de la app) para ver un stream, un video de la galeria o un
 * link de YouTube (reproductor oficial en WebView), en el menu y dentro del juego.
 */
object Pip {
    const val NONE = 0
    const val VIDEO = 1
    const val YOUTUBE = 2
    const val STREAM = 3

    val kind = mutableIntStateOf(NONE)
    val ref = mutableStateOf("") // uri del video o id de YouTube

    /** Posicion arrastrada por el usuario (px). null = esquina elegida en Ajustes. */
    val custom = mutableStateOf<Offset?>(null)

    fun showVideo(uri: String) { custom.value = custom.value; ref.value = uri; kind.intValue = VIDEO }
    fun showYoutube(id: String) { ref.value = id; kind.intValue = YOUTUBE }
    fun showStream() { kind.intValue = STREAM }
    fun close() { kind.intValue = NONE; ref.value = "" }

    private val YT = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/|live/))([A-Za-z0-9_-]{11})""")
    fun parseYoutubeId(s: String): String? {
        val t = s.trim()
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(t)) return t
        return YT.find(t)?.groupValues?.get(1)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PipOverlay(modifier: Modifier = Modifier) {
    val kind = Pip.kind.intValue
    if (kind == Pip.NONE) return
    val d = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val (wDp, hDp) = when (Settings.pipSize.intValue) { 0 -> 160 to 90; 2 -> 320 to 180; else -> 224 to 126 }
        val handle = 24
        val wPx = with(d) { wDp.dp.toPx() }
        val hPx = with(d) { (hDp + handle).dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val m = with(d) { (8 + Settings.uiMargin.intValue).dp.toPx() }
        val corner = Settings.pipCorner.intValue
        val base = Offset(
            if (corner == 0 || corner == 2) m else maxW - wPx - m,
            if (corner == 0 || corner == 1) m + with(d) { 40.dp.toPx() } else maxH - hPx - m,
        )
        val pos = (Pip.custom.value ?: base).let { Offset(it.x.coerceIn(0f, (maxW - wPx).coerceAtLeast(0f)), it.y.coerceIn(0f, (maxH - hPx).coerceAtLeast(0f))) }

        val curPos by rememberUpdatedState(pos)
        Column(
            Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(wDp.dp, (hDp + handle).dp)
                .alpha(Settings.pipOpacity.floatValue)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black),
        ) {
            // Barra para arrastrar + cerrar (el video queda libre para tocarlo, p. ej. YouTube)
            Row(
                Modifier.fillMaxWidth().height(handle.dp).background(Color(0xFF1B2330))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            Pip.custom.value = Offset(curPos.x + drag.x, curPos.y + drag.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⠿ arrastra", color = Color(0xFF90A4AE), fontSize = 10.sp, modifier = Modifier.weight(1f).padding0())
                Text("✕", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { Pip.close() }.padding0())
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (kind) {
                    Pip.VIDEO -> {
                        val uri = Pip.ref.value
                        AndroidView(
                            factory = { c ->
                                VideoView(c).apply {
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    setVideoURI(Uri.parse(uri))
                                    setOnPreparedListener { it.isLooping = true; start() }
                                    setOnErrorListener { _, _, _ -> Pip.close(); true }
                                }
                            },
                            onRelease = { it.stopPlayback() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Pip.YOUTUBE -> {
                        val id = Pip.ref.value
                        AndroidView(
                            factory = { c ->
                                WebView(c).apply {
                                    settings.javaScriptEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.domStorageEnabled = true
                                    webChromeClient = WebChromeClient()
                                    webViewClient = WebViewClient()
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    val html = "<html><body style='margin:0;background:#000'><iframe width='100%' height='100%' " +
                                        "src='https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0' frameborder='0' " +
                                        "allow='autoplay; encrypted-media; picture-in-picture' allowfullscreen></iframe></body></html>"
                                    loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                                }
                            },
                            onRelease = { it.destroy() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Pip.STREAM -> {
                        val f = StreamClient.current?.frame?.value
                        if (f != null) Image(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                        else Text("Sin transmisión", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun Modifier.padding0(): Modifier = this.then(Modifier.padding(horizontal = 8.dp))

/**
 * Controles de PiP: elegir fuente y configurar esquina, tamano, opacidad y "siempre encima".
 * Se usa en Ajustes (biblioteca) y en el menu de pausa (juego).
 */
@Composable
fun PipControls(pickVideo: () -> Unit, openStreamViewer: (() -> Unit)?) {
    val p = LocalPalette.current
    var ytDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fuente del video", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = pickVideo) { Text("Video de la galería") }
            OutlinedButton(onClick = { ytDialog = true }) { Text("Link de YouTube") }
            if (openStreamViewer != null) OutlinedButton(onClick = openStreamViewer) { Text("Stream de un amigo") }
            if (Pip.kind.intValue != Pip.NONE) OutlinedButton(onClick = { Pip.close() }) { Text("Cerrar ventana") }
        }
        Text("Posición", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("↖ Sup. izq.", "↗ Sup. der.", "↙ Inf. izq.", "↘ Inf. der.").forEachIndexed { i, n ->
                FilterChip(selected = Settings.pipCorner.intValue == i && Pip.custom.value == null, onClick = { Settings.setPipCorner(i); Pip.custom.value = null }, label = { Text(n) })
            }
        }
        Text("Tamaño", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Pequeño", "Mediano", "Grande").forEachIndexed { i, n ->
                FilterChip(selected = Settings.pipSize.intValue == i, onClick = { Settings.setPipSize(i) }, label = { Text(n) })
            }
        }
        Text("Opacidad ${(Settings.pipOpacity.floatValue * 100).roundToInt()}%", color = p.text, fontSize = 13.sp)
        Slider(value = Settings.pipOpacity.floatValue, onValueChange = { Settings.setPipOpacity(it) }, valueRange = 0.2f..1f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Siempre encima (sobre los botones táctiles)", color = p.text, modifier = Modifier.weight(1f))
            Switch(checked = Settings.pipOnTop.value, onCheckedChange = { Settings.setPipOnTop(it) })
        }
        Text("Arrastra la ventana desde su barra superior; ✕ la cierra.", color = p.dim, fontSize = 11.sp)
    }
    if (ytDialog) {
        AlertDialog(
            onDismissRequest = { ytDialog = false },
            title = { Text("Link de YouTube") },
            text = {
                Column {
                    OutlinedTextField(value = link, onValueChange = { link = it; bad = false }, singleLine = true, label = { Text("https://youtu.be/…") })
                    if (bad) Text("No parece un link de YouTube", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = Pip.parseYoutubeId(link)
                    if (id == null) bad = true else { Pip.showYoutube(id); ytDialog = false; link = "" }
                }) { Text("Reproducir") }
            },
            dismissButton = { TextButton(onClick = { ytDialog = false }) { Text("Cancelar") } },
        )
    }
}
