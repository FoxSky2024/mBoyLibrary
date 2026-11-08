@file:OptIn(ExperimentalMaterial3Api::class)

package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.VideoFilter
import app.gbalibrary.emu.VideoScale

/** Acciones que el menu delega en la Activity. */
class MenuActions(
    val close: () -> Unit,
    val save: (String) -> Unit,
    val load: (String) -> Unit,
    val reset: () -> Unit,
    val screenshot: () -> Unit,
    val exit: () -> Unit,
    val editControls: () -> Unit,
    val saveGameLayout: () -> Unit,
    val saveGlobalLayout: () -> Unit,
    val resetLayout: () -> Unit,
    val persistLayout: () -> Unit,
    val setForceTouch: (Boolean) -> Unit,
    val setSpeed: (Int) -> Unit,
    val toggleMusic: () -> Unit = {},
    val startStream: () -> Unit = {},
    val stopStream: () -> Unit = {},
    val pickPipVideo: () -> Unit = {},
)

private val TABS = listOf("VIDEO", "AUDIO", "CONTROLES", "JUEGO", "EN VIVO", "SALIR")

@Composable
fun GameMenu(title: String, speed: Int, layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, host: app.gbalibrary.stream.StreamHost?, actions: MenuActions) {
    val p = LocalPalette.current
    var tab by remember { mutableIntStateOf(0) }
    Box(
        Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(onClick = actions.close),
        contentAlignment = Alignment.Center,
    ) {
        StatusIndicators(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp), tint = Color.White)
        Row(
            Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.88f)
                .background(p.panel, RoundedCornerShape(14.dp))
                .clickable(enabled = false, onClick = {}),
        ) {
            Column(
                Modifier.width(150.dp).fillMaxHeight().background(p.bg, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)).padding(vertical = 12.dp),
            ) {
                Text(
                    title, color = p.text, fontWeight = FontWeight.Bold, maxLines = 2,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                TABS.forEachIndexed { i, name ->
                    Text(
                        name,
                        color = if (i == tab) p.accent else p.text,
                        fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (i == tab) p.card else Color.Transparent)
                            .clickable { Sfx.click(); tab = i }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
                Box(Modifier.weight(1f))
                Button(
                    onClick = actions.close,
                    colors = ButtonDefaults.buttonColors(containerColor = p.play),
                    modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth(),
                ) { Text("Continuar", color = Color.White) }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> VideoTab()
                    1 -> AudioTab()
                    2 -> ControlsTab(layout, padConnected, forceTouch, actions)
                    3 -> GameTab(speed, actions)
                    4 -> LiveTab(host, actions)
                    else -> ExitTab(actions)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
}

@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, name ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(name) })
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(label)
            Text(shown, color = LocalPalette.current.text)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = LocalPalette.current.text)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun VideoTab() {
    Label("Calidad de imagen")
    Chips(listOf("Pixel Perfect", "Suave", "CRT / Scanlines"), Settings.filter.intValue) { Settings.setFilter(it) }
    Text(
        when (Settings.filter.intValue) {
            VideoFilter.PIXEL -> "Pixeles nitidos (nearest), como una GBA real."
            VideoFilter.SMOOTH -> "Imagen suavizada (lineal)."
            else -> "Pixeles nitidos con lineas de barrido tipo CRT."
        },
        color = LocalPalette.current.dim,
    )
    SliderRow("Brillo", Settings.brightness.floatValue, 0.5f..1.5f, "${(Settings.brightness.floatValue * 100).toInt()}%") { Settings.setBrightness(it) }
    Label("Escala")
    Chips(listOf("Ajustar", "Original", "Estirar", "Pantalla completa"), Settings.scale.intValue) { Settings.setScale(it) }
    Text(
        when (Settings.scale.intValue) {
            VideoScale.FIT -> "Llena la pantalla manteniendo la proporcion 3:2."
            VideoScale.ORIGINAL -> "Multiplo entero del tamano original (pixeles perfectos)."
            VideoScale.STRETCH -> "Estira la imagen a toda la pantalla."
            else -> "Llena toda la pantalla recortando los bordes."
        },
        color = LocalPalette.current.dim,
    )
    SwitchRow("Mostrar FPS", Settings.showFps.value) { Settings.setShowFps(it) }
}

@Composable
private fun AudioTab() {
    SwitchRow("Sonido activado", !Settings.muted.value) { Settings.setMuted(!it) }
    SliderRow("Volumen", Settings.volume.intValue.toFloat(), 0f..100f, "${Settings.volume.intValue}") { Settings.setVolume(it.toInt()) }
    Text("Latencia: fija (~70 ms) para evitar cortes.", color = LocalPalette.current.dim)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ControlsTab(layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, a: MenuActions) {
    SwitchRow("Mostrar botones tactiles", Settings.padVisible.value) { Settings.setPadVisible(it) }
    if (padConnected) {
        SwitchRow("Mostrar tactiles aunque haya mando", forceTouch) { a.setForceTouch(it) }
    }
    SliderRow("Tamano general", Settings.padSize.floatValue, 0.6f..1.6f, "${(Settings.padSize.floatValue * 100).toInt()}%") { Settings.setPadSize(it) }
    SliderRow("Opacidad general", Settings.padOpacity.floatValue, 0.15f..1f, "${(Settings.padOpacity.floatValue * 100).toInt()}%") { Settings.setPadOpacity(it) }
    Label("Tipo de movimiento")
    Chips(listOf("Cruceta clasica", "Joystick analogico"), layout.moveType) {
        layout.moveType = it
        a.persistLayout()
    }
    if (layout.moveType == MoveType.STICK) {
        SliderRow("Zona muerta", layout.deadzone, 0.05f..0.7f, "${(layout.deadzone * 100).toInt()}%") { layout.deadzone = it }
        OutlinedButton(onClick = a.persistLayout) { Text("Aplicar zona muerta") }
    }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(onClick = a.editControls) { Text("Editar controles") }
        OutlinedButton(onClick = a.saveGameLayout) { Text("Guardar para este juego") }
        OutlinedButton(onClick = a.saveGlobalLayout) { Text("Guardar global") }
        OutlinedButton(onClick = a.resetLayout) { Text("Restablecer") }
    }
}

@Composable
private fun GameTab(speed: Int, a: MenuActions) {
    Label("Guardado rapido")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { a.save("quick") }) { Text("Guardar") }
        OutlinedButton(onClick = { a.load("quick") }) { Text("Cargar") }
    }
    Label("Estados (3 ranuras)")
    (1..3).forEach { slot ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ranura $slot", color = LocalPalette.current.text, modifier = Modifier.width(80.dp))
            Button(onClick = { a.save("slot$slot") }) { Text("Guardar") }
            OutlinedButton(onClick = { a.load("slot$slot") }) { Text("Cargar") }
        }
    }
    Label("Velocidad")
    Chips(listOf("x1", "Fast-Forward x${Settings.ffSpeed.intValue}"), if (speed > 1) 1 else 0) {
        a.setSpeed(if (it == 0) 1 else Settings.ffSpeed.intValue)
    }
    if (Settings.musicInGame.value && Music.hasSource()) {
        Label("Música")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = a.toggleMusic) { Text(if (Music.playing.value) "Pausar música" else "Reanudar música") }
            OutlinedButton(onClick = { Music.next() }) { Text("Siguiente") }
        }
        SliderRow("Volumen música", Settings.musicVolume.floatValue, 0f..1f, "${(Settings.musicVolume.floatValue * 100).toInt()}%") {
            Settings.setMusicVolume(it)
            Music.applyVolume()
        }
        Text("El volumen del juego se ajusta en la pestaña de sonido.", color = LocalPalette.current.dim, fontSize = 11.sp)
    }
    Label("Rendimiento")
    PerfControls()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = a.reset) { Text("Reiniciar juego") }
        OutlinedButton(onClick = a.screenshot) { Text("Captura de pantalla") }
    }
}

@Composable
private fun LiveTab(host: app.gbalibrary.stream.StreamHost?, a: MenuActions) {
    StreamHostPanel(host, a.startStream, a.stopStream)
    Label("Video en ventana (PiP)")
    PipControls(pickVideo = a.pickPipVideo, openStreamViewer = null)
}

@Composable
private fun ExitTab(a: MenuActions) {
    Label("Salir al menu")
    Text("Tu progreso se guarda automaticamente al salir y se retoma al volver a entrar.", color = LocalPalette.current.text)
    Button(
        onClick = a.exit,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
    ) { Text("Salir al menu", color = Color.White) }
}
