@file:OptIn(ExperimentalMaterial3Api::class)

package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.mutableStateOf
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
    val editDsScreens: () -> Unit = {},
    val pickPipVideo: () -> Unit = {},
    /** Cuando se guardo la ranura (ms) o 0 si esta vacia. */
    val slotTime: (String) -> Long = { 0L },
)

private val TABS = listOf("VIDEO", "AUDIO", "CONTROLES", "JUEGO", "VENTANA", "SALIR")

@Composable
fun GameMenu(title: String, speed: Int, layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, actions: MenuActions) {
    val p = LocalPalette.current
    var tab by remember { mutableIntStateOf(0) } // -1 = panel de ajustes plegado
    val glass = p.panel.copy(alpha = 0.93f)
    // Lista escalonada arriba a la izquierda (como un menu de juego), ajustes abajo al centro y una ilustracion
    // que entra deslizandose desde el borde izquierdo en la esquina inferior izquierda.
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0x55000000)).clickable(onClick = actions.close),
    ) {
        val illH = maxHeight * 0.42f
        val illW = illH * (900f / 884f)
        StatusIndicators(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp), tint = Color.White)

        // ---- ilustracion (entra desde fuera del borde izquierdo), teñida con tonos claros del color de la interfaz ----
        val slide = remember { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(Unit) { slide.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
        val tintC = androidx.compose.ui.graphics.lerp(p.accent, Color.White, 0.62f)
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(app.gbalibrary.R.drawable.pause_mascot), null,
            Modifier.align(Alignment.BottomStart).size(illW, illH)
                .graphicsLayer { translationX = -(illW.toPx() + 24.dp.toPx()) * (1f - slide.value); alpha = 0.35f + 0.65f * slide.value }
                .padding(start = 4.dp, bottom = 4.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tintC, androidx.compose.ui.graphics.BlendMode.Modulate),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            alignment = Alignment.BottomStart,
        )

        // ---- categorias escalonadas ----
        val entries = listOf("CONTINUAR") + TABS
        Column(
            Modifier.align(Alignment.TopStart).padding(top = 14.dp).fillMaxWidth().clickable(enabled = false, onClick = {}),
        ) {
            Text(
                title, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 12.sp,
                modifier = Modifier.padding(start = 22.dp, bottom = 4.dp),
            )
            entries.forEachIndexed { i, name ->
                val sel = i > 0 && i - 1 == tab
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
                    if (sel) Box(Modifier.fillMaxWidth().height(1.5.dp).background(p.accent))
                    Box(
                        Modifier
                            .padding(start = 22.dp + 16.dp * i)
                            .then(if (sel) Modifier.border(1.5.dp, p.accent).background(Color(0xCC000000)) else Modifier)
                            .clickable {
                                Sfx.click()
                                if (i == 0) actions.close() else tab = if (tab == i - 1 && i - 1 != TABS.lastIndex) -1 else i - 1
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            name + if (sel) "  \u270E" else "",
                            color = if (sel) p.accent else Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp, maxLines = 1,
                        )
                    }
                }
            }
        }

        // ---- ajustes (abajo, centrado, sin tapar el juego) ----
        if (tab >= 0) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = illW + 12.dp, bottom = 10.dp)
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(0.46f)
                    .background(glass, RoundedCornerShape(16.dp))
                    .clickable(enabled = false, onClick = {})
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(TABS[tab], color = p.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                when (tab) {
                    0 -> VideoTab(actions)
                    1 -> AudioTab()
                    2 -> ControlsTab(layout, padConnected, forceTouch, actions)
                    3 -> GameTab(speed, actions)
                    4 -> WindowTab(actions)
                    else -> ExitTab(actions)
                }
            }
        }
    }
}

@Composable
private fun GmLabel(text: String) {
    Text(text, color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
}

@Composable
private fun GmChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, name ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(name) })
        }
    }
}

@Composable
private fun GmSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GmLabel(label)
            Text(shown, color = LocalPalette.current.text)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun GmSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = LocalPalette.current.text)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun VideoTab(a: MenuActions) {
    GmLabel("Calidad de imagen")
    GmChips(listOf("Pixel Perfect", "Suave", "CRT / Scanlines"), Settings.filter.intValue) { Settings.setFilter(it) }
    Text(
        when (Settings.filter.intValue) {
            VideoFilter.PIXEL -> "Pixeles nitidos (nearest), como una GBA real."
            VideoFilter.SMOOTH -> "Imagen suavizada (lineal)."
            else -> "Pixeles nitidos con lineas de barrido tipo CRT."
        },
        color = LocalPalette.current.dim,
    )
    GmSlider("Brillo", Settings.brightness.floatValue, 0.5f..1.5f, "${(Settings.brightness.floatValue * 100).toInt()}%") { Settings.setBrightness(it) }
    GmLabel("Escala")
    GmChips(listOf("Ajustar", "Original", "Estirar", "Pantalla completa"), Settings.scale.intValue) { Settings.setScale(it) }
    Text(
        when (Settings.scale.intValue) {
            VideoScale.FIT -> "Llena la pantalla manteniendo la proporcion 3:2."
            VideoScale.ORIGINAL -> "Multiplo entero del tamano original (pixeles perfectos)."
            VideoScale.STRETCH -> "Estira la imagen a toda la pantalla."
            else -> "Llena toda la pantalla recortando los bordes."
        },
        color = LocalPalette.current.dim,
    )
    GmSwitch("Mostrar FPS", Settings.showFps.value) { Settings.setShowFps(it) }
    GmLabel("Orientación del emulador")
    GmChips(listOf("Automática", "Horizontal", "Vertical"), Settings.orientation.intValue) { Settings.setOrientation(it) }
    if (app.gbalibrary.emu.Native.isDs) {
        GmLabel("Nintendo DS: pantallas")
        GmChips(listOf("Arriba / abajo", "Lado a lado", "Solo arriba", "Solo abajo", "Grande + pequeña"), Settings.dsLayout.intValue) { Settings.setDsLayout(it) }
        GmSwitch("Intercambiar pantallas", Settings.dsSwap.value) { Settings.setDsSwap(it) }
        Button(onClick = a.editDsScreens) { Text("Editar pantallas (mover, escalar, rotar)") }
        Text("La pantalla inferior es táctil en todas las distribuciones. La posición se guarda por juego y orientación.", color = LocalPalette.current.dim)
    }
}

@Composable
private fun AudioTab() {
    GmSwitch("Sonido activado", !Settings.muted.value) { Settings.setMuted(!it) }
    GmSlider("Volumen", Settings.volume.intValue.toFloat(), 0f..100f, "${Settings.volume.intValue}") { Settings.setVolume(it.toInt()) }
    Text("Latencia: fija (~70 ms) para evitar cortes.", color = LocalPalette.current.dim)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ControlsTab(layout: ControlLayout, padConnected: Boolean, forceTouch: Boolean, a: MenuActions) {
    GmSwitch("Mostrar botones tactiles", Settings.padVisible.value) { Settings.setPadVisible(it) }
    if (padConnected) {
        GmSwitch("Mostrar tactiles aunque haya mando", forceTouch) { a.setForceTouch(it) }
    }
    GmSlider("Tamano general", Settings.padSize.floatValue, 0.6f..1.6f, "${(Settings.padSize.floatValue * 100).toInt()}%") { Settings.setPadSize(it) }
    GmSlider("Opacidad general", Settings.padOpacity.floatValue, 0.15f..1f, "${(Settings.padOpacity.floatValue * 100).toInt()}%") { Settings.setPadOpacity(it) }
    GmLabel("Tipo de movimiento")
    GmChips(listOf("Cruceta clasica", "Joystick analogico"), layout.moveType) {
        layout.moveType = it
        a.persistLayout()
    }
    if (layout.moveType == MoveType.STICK) {
        GmSlider("Zona muerta", layout.deadzone, 0.05f..0.7f, "${(layout.deadzone * 100).toInt()}%") { layout.deadzone = it }
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
    var tick by remember { mutableIntStateOf(0) } // refresca la hora de las ranuras tras guardar
    var confirmReset by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val refresh = tick // leer tick aqui hace que la hora se actualice al guardar
    fun when_(slot: String): String {
        val t = a.slotTime(slot)
        return if (t <= 0L) "vacia" else formatAgo(t)
    }
    GmLabel("Guardado rapido  ·  ${when_("quick")}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { a.save("quick"); tick++ }) { Text("Guardar") }
        OutlinedButton(onClick = { a.load("quick") }) { Text("Cargar") }
    }
    GmLabel("Estados (3 ranuras)")
    (1..3).forEach { slot ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(110.dp)) {
                Text("Ranura $slot", color = LocalPalette.current.text)
                Text(when_("slot$slot"), color = LocalPalette.current.dim, fontSize = 11.sp)
            }
            Button(onClick = { a.save("slot$slot"); tick++ }) { Text("Guardar") }
            OutlinedButton(onClick = { a.load("slot$slot") }) { Text("Cargar") }
        }
    }
    GmLabel("Velocidad")
    GmChips(listOf("x1", "Fast-Forward x${Settings.ffSpeed.intValue}"), if (speed > 1) 1 else 0) {
        a.setSpeed(if (it == 0) 1 else Settings.ffSpeed.intValue)
    }
    if (Settings.musicInGame.value && Music.hasSource()) {
        GmLabel("Música")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = a.toggleMusic) { Text(if (Music.playing.value) "Pausar música" else "Reanudar música") }
            OutlinedButton(onClick = { Music.next() }) { Text("Siguiente") }
        }
        GmSlider("Volumen música", Settings.musicVolume.floatValue, 0f..1f, "${(Settings.musicVolume.floatValue * 100).toInt()}%") {
            Settings.setMusicVolume(it)
            Music.applyVolume()
        }
        Text("El volumen del juego se ajusta en la pestaña de audio.", color = LocalPalette.current.dim, fontSize = 11.sp)
    }
    GmLabel("Rendimiento")
    PerfControls()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            if (confirmReset) { confirmReset = false; a.reset() } else confirmReset = true
        }) { Text(if (confirmReset) "¿Seguro? Reiniciar" else "Reiniciar juego", color = if (confirmReset) Color(0xFFFF8A80) else Color.Unspecified) }
        OutlinedButton(onClick = a.screenshot) { Text("Captura de pantalla") }
    }
}

@Composable
private fun WindowTab(a: MenuActions) {
    GmLabel("Video en ventana (PiP)")
    PipControls(pickVideo = a.pickPipVideo)
}

@Composable
private fun ExitTab(a: MenuActions) {
    GmLabel("Salir al menu")
    Text("Tu progreso se guarda automaticamente al salir y se retoma al volver a entrar.", color = LocalPalette.current.text)
    Button(
        onClick = a.exit,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
    ) { Text("Salir al menu", color = Color.White) }
}
