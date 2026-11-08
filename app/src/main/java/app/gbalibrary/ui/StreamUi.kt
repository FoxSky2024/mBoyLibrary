@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.stream.ChatLine
import app.gbalibrary.stream.Emote
import app.gbalibrary.stream.JoinCode
import app.gbalibrary.stream.Proto
import app.gbalibrary.stream.StreamClient
import app.gbalibrary.stream.StreamHost
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.roundToInt

private fun qr(text: String): Bitmap? = try {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0)
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until m.width) for (y in 0 until m.height) bmp.setPixel(x, y, if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    Bitmap.createScaledBitmap(bmp, m.width * 6, m.height * 6, false)
} catch (e: Exception) {
    null
}

/** Emotes que suben flotando como en los lives; cada uno se quita de la lista al terminar. */
@Composable
fun FloatingEmotes(emotes: MutableList<Emote>, modifier: Modifier = Modifier) {
    Box(modifier) {
        for (e in emotes.toList()) {
            androidx.compose.runtime.key(e.id) { FloatingEmote(e) { emotes.remove(e) } }
        }
    }
}

@Composable
private fun FloatingEmote(e: Emote, onDone: () -> Unit) {
    val prog = remember { Animatable(0f) }
    val dx = remember { ((e.id * 37) % 70).toInt() }
    LaunchedEffect(Unit) {
        prog.animateTo(1f, tween(2800, easing = LinearEasing))
        onDone()
    }
    Text(
        e.glyph, fontSize = 30.sp,
        modifier = Modifier
            .offset { IntOffset((dx + (kotlin.math.sin(prog.value * 9f) * 14f)).roundToInt() * 2, (-prog.value * 520f).roundToInt()) }
            .graphicsLayer { alpha = if (prog.value > 0.7f) (1f - prog.value) / 0.3f else 1f },
    )
}

/** Chat + emotes del anfitrion dentro del juego (sin interceptar toques) y distintivo EN VIVO. */
@Composable
fun StreamHostOverlay(host: StreamHost, modifier: Modifier = Modifier) {
    Box(modifier) {
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).width(170.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
        ) {
            host.chat.takeLast(6).forEach { l -> ChatLineView(l, compact = true) }
        }
        FloatingEmotes(host.emotes, Modifier.align(Alignment.BottomEnd).padding(end = 60.dp, bottom = 90.dp))
        Text(
            "● EN VIVO · ${host.viewers.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 46.dp)
                .background(Color(0xCCD32F2F), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ChatLineView(l: ChatLine, compact: Boolean = false) {
    val bg = if (compact) Color(0x99000000) else Color.Transparent
    Text(
        if (l.system) l.text else "${l.who}: ${l.text}",
        color = if (l.system) Color(0xFFB0BEC5) else Color.White,
        fontSize = if (compact) 11.sp else 13.sp,
        fontWeight = if (l.system) FontWeight.Normal else FontWeight.Medium,
        modifier = Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Panel "Transmitir" del menu de pausa: iniciar, codigo / QR, moderacion y cerrar. */
@Composable
fun StreamHostPanel(host: StreamHost?, onStart: () -> Unit, onStop: () -> Unit) {
    val p = LocalPalette.current
    Text("TRANSMITIR (P2P)", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    if (host == null || !host.running.value) {
        Text(
            "Transmite tu juego directo a hasta ${Proto.MAX_VIEWERS} amigos en la misma red WiFi (sin servidor, sin nube). " +
                "Verán tu pantalla y podrán escribir en el chat y mandar emotes. Al cerrar no se guarda nada.",
            color = p.text, fontSize = 13.sp,
        )
        Button(onClick = onStart) { Text("Iniciar transmisión") }
        return
    }
    val code = host.code ?: "—"
    Text("Código para unirse", color = p.dim, fontSize = 12.sp)
    Text(code.chunked(3).joinToString(" "), color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Black)
    Text("IP: ${host.ip}  ·  Los amigos deben estar en tu misma red WiFi.", color = p.dim, fontSize = 12.sp)
    val bmp = remember(code) { qr("mboy://join/$code") }
    if (bmp != null) {
        Image(bmp.asImageBitmap(), "QR", Modifier.size(150.dp).background(Color.White).padding(6.dp), filterQuality = FilterQuality.None)
        Text("QR: se abre con la cámara del teléfono de tu amigo (abre mBoyLibrary y entra solo).", color = p.dim, fontSize = 11.sp)
    }
    Text("Espectadores (${host.viewers.size}/${Proto.MAX_VIEWERS})", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    if (host.viewers.isEmpty()) Text("Nadie conectado todavía.", color = p.dim, fontSize = 12.sp)
    host.viewers.forEach { v ->
        Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(8.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${v.name}${if (v.muted) "  (silenciado)" else ""}", color = p.text, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedButton(onClick = { host.mute(v.id, !v.muted) }) { Text(if (v.muted) "Quitar silencio" else "Silenciar", fontSize = 12.sp) }
                OutlinedButton(onClick = { host.kick(v.id) }) { Text("Expulsar", fontSize = 12.sp) }
                OutlinedButton(onClick = { host.ban(v.id) }) { Text("Banear", fontSize = 12.sp, color = Color(0xFFFF8A80)) }
            }
        }
    }
    Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) {
        Text("Cerrar transmisión", color = Color.White)
    }
}

/** Pantalla del espectador: codigo -> video + chat a la derecha + emotes. */
@Composable
fun StreamViewerScreen(onBack: () -> Unit, onMinimize: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val client = remember { StreamClient.current ?: StreamClient().also { StreamClient.current = it } }
    var input by remember { mutableStateOf(StreamClient.pendingCode.value ?: "") }
    var msg by remember { mutableStateOf("") }
    val live = client.live.value
    val status = client.status.value

    fun join() {
        val raw = input.trim()
        val host = if (raw.contains('.')) raw else JoinCode.decode(raw, JoinCode.localIpv4())
        if (host == null) { client.status.value = "Código inválido, o no estás en la misma red WiFi"; return }
        client.connect(host, Settings.profileName.value)
    }
    LaunchedEffect(Unit) {
        StreamClient.pendingCode.value?.let { StreamClient.pendingCode.value = null; join() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(p.panel.copy(alpha = Settings.boxOpacity.floatValue)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("← Volver", color = p.accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp).then(Modifier.clickableNoRipple(onBack)))
            Text(if (live) "En vivo: ${client.hostName}" else "Ver transmisión de un amigo", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!live) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escribe el código de 6 dígitos que ve tu amigo (o escanea su QR con la cámara). Tienen que estar en la misma red WiFi.", color = p.text)
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(21) }, singleLine = true,
                    label = { Text("Código (o IP)") }, modifier = Modifier.fillMaxWidth(0.7f),
                )
                Button(onClick = { join() }) { Text("Conectar") }
                if (status.isNotEmpty()) Text(status, color = p.accent)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                        val f = client.frame.value
                        if (f != null) Image(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
                        else Text("Esperando video…", color = Color.White)
                        FloatingEmotes(client.emotes, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp))
                    }
                    FlowRow(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Proto.EMOTES.forEach { g -> OutlinedButton(onClick = { client.sendEmote(g) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text(g, fontSize = 18.sp) } }
                        OutlinedButton(onClick = onMinimize) { Text("Minimizar (PiP)") }
                        OutlinedButton(onClick = { client.disconnect(); onBack() }) { Text("Salir") }
                    }
                }
                Column(Modifier.width(210.dp).fillMaxHeight().background(Color(0x66000000)).padding(8.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), reverseScrolling = true), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        client.chat.forEach { ChatLineView(it) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(value = msg, onValueChange = { msg = it.take(200) }, singleLine = true, placeholder = { Text("Chat…") }, modifier = Modifier.weight(1f))
                        Button(onClick = { client.sendChat(msg); msg = "" }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("➤") }
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
