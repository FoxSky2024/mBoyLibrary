@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.input.PadInput
import kotlin.math.roundToInt

/** Ajustes > Controles > Mando: diagrama estilo Xbox, remapeo, prueba y vibracion. */
@Composable
fun PadSettingsSection() {
    val p = LocalPalette.current
    var profileN by remember { mutableIntStateOf(1) }
    var listening by remember { mutableStateOf<Int?>(null) } // tecla del diagrama que se esta remapeando
    var testing by remember { mutableStateOf(false) }
    val profile = PadInput.profile(profileN)

    // Captura / prueba viven mientras esta pantalla este abierta.
    DisposableEffect(Unit) {
        onDispose {
            PadInput.capture = null
            PadInput.testing = false
            PadInput.pressed.clear()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (PadInput.connected.value) "Mando conectado: ${PadInput.deviceNames.value.joinToString(", ")}"
            else "Ningún mando conectado (conéctalo por Bluetooth o USB)",
            color = if (PadInput.connected.value) p.accent else p.dim, fontSize = 13.sp,
        )
        SettingRow("Ocultar botones táctiles al conectar un mando", Settings.padAutoHide.value) { Settings.setPadAutoHide(it) }
        Text("Con un mando conectado puedes volver a mostrarlos desde el menú del juego (Controles).", color = p.dim, fontSize = 12.sp)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = profileN == 1, onClick = { profileN = 1; listening = null; PadInput.capture = null }, label = { Text("Mando 1") })
            FilterChip(selected = profileN == 2, onClick = { profileN = 2; listening = null; PadInput.capture = null }, label = { Text("Mando 2") })
            OutlinedButton(onClick = { PadInput.resetProfile(profileN); listening = null; PadInput.capture = null }) { Text("Restablecer perfil") }
        }

        Text(
            when {
                testing -> "Modo prueba: presiona botones del mando y verás cuáles se activan."
                listening != null -> "Presiona ahora el botón físico donde quieres: ${PadInput.actionName(profile[listening!!] ?: 0)}"
                else -> "Toca un botón del diagrama y luego presiona el botón del mando donde quieres esa acción (se intercambian)."
            },
            color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        )

        PadDiagram(
            profile = profile,
            listening = listening,
            testing = testing,
            onTap = { key ->
                if (testing) return@PadDiagram
                if (listening == key) {
                    listening = null
                    PadInput.capture = null
                } else {
                    listening = key
                    PadInput.capture = { pressedKey ->
                        PadInput.swap(profileN, key, pressedKey)
                        PadInput.rumble(40, force = false)
                        listening = null
                    }
                }
            },
        )
        Text("Stick izquierdo: siempre funciona también como D-Pad.", color = p.dim, fontSize = 12.sp)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = {
                testing = !testing
                PadInput.testing = testing
                PadInput.pressed.clear()
                listening = null
                PadInput.capture = null
            }) { Text(if (testing) "Salir de prueba" else "Probar mando") }
            OutlinedButton(onClick = { PadInput.rumble(250, force = true) }) { Text("Probar vibración") }
        }
        SettingRow("Vibración en el mando (al guardar y al remapear)", Settings.padRumble.value) { Settings.setPadRumble(it) }
        if (PadInput.connected.value && !PadInput.canRumble()) {
            Text("Este mando no reporta vibración a Android.", color = p.dim, fontSize = 12.sp)
        }

        if (testing) {
            val down = PadInput.SLOTS.filter { PadInput.pressed[it.key] == true }
            Text(
                if (down.isEmpty()) "Presionado: —"
                else "Presionado: " + down.joinToString("  ") { "${it.name} → ${PadInput.actionName(profile[it.key] ?: 0)}" },
                color = p.accent, fontWeight = FontWeight.Bold,
            )
            Text(
                "Stick izq: %.2f, %.2f   Stick der: %.2f, %.2f".format(
                    PadInput.stickX.floatValue, PadInput.stickY.floatValue,
                    PadInput.rStickX.floatValue, PadInput.rStickY.floatValue,
                ),
                color = p.dim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = LocalPalette.current.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Diagrama de un mando tipo Xbox; cada boton muestra su nombre y la accion que tiene. */
@Composable
fun PadDiagram(profile: Map<Int, Int>, listening: Int?, testing: Boolean, onTap: (Int) -> Unit) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = if (maxWidth > 560.dp) 560.dp else maxWidth
        val h = w * 0.62f
        val d = LocalDensity.current
        Box(Modifier.size(w, h).align(Alignment.Center)) {
            Canvas(Modifier.size(w, h)) {
                val cw = size.width
                val ch = size.height
                fun pt(x: Float, y: Float) = Offset(cw * x, ch * y)
                // Silueta de un mando de Xbox: cuerpo ancho con dos agarres que bajan y se abren.
                val body = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cw * 0.20f, ch * 0.20f)
                    lineTo(cw * 0.80f, ch * 0.20f)
                    cubicTo(cw * 0.90f, ch * 0.20f, cw * 0.96f, ch * 0.32f, cw * 0.97f, ch * 0.54f)
                    cubicTo(cw * 0.985f, ch * 0.74f, cw * 0.965f, ch * 0.93f, cw * 0.88f, ch * 0.96f)
                    cubicTo(cw * 0.80f, ch * 0.99f, cw * 0.745f, ch * 0.84f, cw * 0.69f, ch * 0.76f)
                    cubicTo(cw * 0.63f, ch * 0.69f, cw * 0.37f, ch * 0.69f, cw * 0.31f, ch * 0.76f)
                    cubicTo(cw * 0.255f, ch * 0.84f, cw * 0.20f, ch * 0.99f, cw * 0.12f, ch * 0.96f)
                    cubicTo(cw * 0.035f, ch * 0.93f, cw * 0.015f, ch * 0.74f, cw * 0.03f, ch * 0.54f)
                    cubicTo(cw * 0.04f, ch * 0.32f, cw * 0.10f, ch * 0.20f, cw * 0.20f, ch * 0.20f)
                    close()
                }
                val shell = Color(0xFF2B323B)
                val rim = Color(0xFF5A6673)
                // gatillos y bumpers detras del cuerpo
                drawRoundRect(Color(0xFF3A434D), topLeft = pt(0.10f, 0.0f), size = Size(cw * 0.18f, ch * 0.085f), cornerRadius = CornerRadius(cw * 0.03f))
                drawRoundRect(Color(0xFF3A434D), topLeft = pt(0.72f, 0.0f), size = Size(cw * 0.18f, ch * 0.085f), cornerRadius = CornerRadius(cw * 0.03f))
                drawRoundRect(Color(0xFF353D47), topLeft = pt(0.10f, 0.10f), size = Size(cw * 0.20f, ch * 0.08f), cornerRadius = CornerRadius(cw * 0.03f))
                drawRoundRect(Color(0xFF353D47), topLeft = pt(0.70f, 0.10f), size = Size(cw * 0.20f, ch * 0.08f), cornerRadius = CornerRadius(cw * 0.03f))
                drawPath(body, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3B444F), shell, Color(0xFF1F252C))))
                drawPath(body, color = rim, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                // zonas decorativas: palanca izquierda, cruceta, palanca derecha y racimo ABXY
                drawCircle(Color(0xFF171C21), radius = cw * 0.075f, center = pt(0.24f, 0.37f))
                drawCircle(Color(0xFF171C21), radius = cw * 0.105f, center = pt(0.40f, 0.65f))
                drawCircle(Color(0xFF171C21), radius = cw * 0.075f, center = pt(0.62f, 0.64f))
                drawCircle(Color(0x22FFFFFF), radius = cw * 0.15f, center = pt(0.77f, 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                drawCircle(Color(0xFF1A1F25), radius = cw * 0.03f, center = pt(0.50f, 0.25f))
            }
            val btn = w * 0.085f
            PadInput.SLOTS.forEach { s ->
                val action = profile[s.key] ?: 0
                val isListening = listening == s.key
                val isDown = testing && PadInput.pressed[s.key] == true
                val bw = if (s.round) btn else btn * 1.7f
                val bh = if (s.round) btn else btn * 0.7f
                val bwPx = with(d) { bw.toPx() }
                val bhPx = with(d) { bh.toPx() }
                val wPx = with(d) { w.toPx() }
                val hPx = with(d) { h.toPx() }
                val color = when {
                    isDown -> p.play
                    isListening -> Color(0xFFFFB300)
                    s.key == android.view.KeyEvent.KEYCODE_BUTTON_A -> Color(0xFF3E8E41)
                    s.key == android.view.KeyEvent.KEYCODE_BUTTON_B -> Color(0xFFB23B3B)
                    s.key == android.view.KeyEvent.KEYCODE_BUTTON_X -> Color(0xFF3B66B2)
                    s.key == android.view.KeyEvent.KEYCODE_BUTTON_Y -> Color(0xFFB29B2E)
                    else -> Color(0xFF47515D)
                }
                Column(
                    Modifier
                        .offset { IntOffset((s.x * wPx - bwPx / 2f).roundToInt(), (s.y * hPx - bhPx / 2f).roundToInt()) }
                        .size(bw, bh)
                        .clip(if (s.round) CircleShape else RoundedCornerShape(8.dp))
                        .background(color)
                        .border(if (isListening) 3.dp else 1.dp, if (isListening) Color.White else Color(0x88FFFFFF), if (s.round) CircleShape else RoundedCornerShape(8.dp))
                        .clickable { onTap(s.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(s.name, color = Color.White.copy(alpha = 0.75f), fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1)
                    Text(PadInput.actionShort(action), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}
