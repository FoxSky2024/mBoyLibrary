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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
        val portrait = maxHeight > maxWidth
        StatusIndicators(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp), tint = Color.White)

        // ---- categorias escalonadas ----
        val entries = listOf("CONTINUAR") + TABS
        Column(
            Modifier.align(Alignment.TopStart).padding(top = 14.dp).fillMaxWidth().clickable(enabled = false, onClick = {}),
        ) {
            Text(
                title, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 11.sp, fontFamily = AppFonts.Body,
                modifier = Modifier.padding(start = 22.dp, bottom = 4.dp),
            )
            entries.forEachIndexed { i, name ->
                val sel = i > 0 && i - 1 == tab
                Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
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
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp, maxLines = 1, fontFamily = AppFonts.Body,
                        )
                    }
                }
            }
        }

        // ---- ajustes: en el centro, con fondo translucido (se ve el juego detras, pero se nota la pantalla delante) ----
        if (tab >= 0) {
            Column(
                Modifier
                    .align(if (portrait) Alignment.BottomCenter else Alignment.Center)
                    .padding(start = if (portrait) 10.dp else 190.dp, end = 10.dp, bottom = 10.dp)
                    .fillMaxWidth(if (portrait) 1f else 0.62f)
                    .fillMaxHeight(if (portrait) 0.5f else 0.66f)
                    .background(p.panel.copy(alpha = 0.58f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .clickable(enabled = false, onClick = {})
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.Body, fontSize = 12.sp)) {
                    Text(TABS[tab], color = p.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
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
}

@Composable
private fun GmLabel(text: String) {
    Text(text.uppercase(), color = LocalPalette.current.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
}

/** Boton con el mismo estilo que las categorias de arriba: caja con borde; resaltado = borde y texto del color de acento. */
@Composable
fun GmButton(highlight: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val p = LocalPalette.current
    val c = if (highlight) p.accent else Color.White
    Row(
        Modifier
            .then(if (highlight) Modifier.background(Color(0xCC000000)) else Modifier)
            .border(1.5.dp, if (highlight) p.accent else Color.White.copy(alpha = 0.3f))
            .clickable { Sfx.click(); onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides c) {
            androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.8.sp, fontFamily = AppFonts.Body)) { content() }
        }
    }
}

@Composable
private fun GmChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, name ->
            GmButton(i == selected, onClick = { onSelect(i) }) { Text(name) }
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
        PillSlider(value, range, onChange)
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
        GmButton(true, onClick = a.editDsScreens) { Text("Editar pantallas (mover, escalar, rotar)") }
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
        GmButton(false, onClick = a.persistLayout) { Text("Aplicar zona muerta") }
    }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GmButton(true, onClick = a.editControls) { Text("Editar controles") }
        GmButton(false, onClick = a.saveGameLayout) { Text("Guardar para este juego") }
        GmButton(false, onClick = a.saveGlobalLayout) { Text("Guardar global") }
        GmButton(false, onClick = a.resetLayout) { Text("Restablecer") }
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
        GmButton(true, onClick = { a.save("quick"); tick++ }) { Text("Guardar") }
        GmButton(false, onClick = { a.load("quick") }) { Text("Cargar") }
    }
    GmLabel("Estados (3 ranuras)")
    (1..3).forEach { slot ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(110.dp)) {
                Text("Ranura $slot", color = LocalPalette.current.text)
                Text(when_("slot$slot"), color = LocalPalette.current.dim, fontSize = 10.sp)
            }
            GmButton(true, onClick = { a.save("slot$slot"); tick++ }) { Text("Guardar") }
            GmButton(false, onClick = { a.load("slot$slot") }) { Text("Cargar") }
        }
    }
    GmLabel("Velocidad")
    GmChips(listOf("x1", "Fast-Forward x${Settings.ffSpeed.intValue}"), if (speed > 1) 1 else 0) {
        a.setSpeed(if (it == 0) 1 else Settings.ffSpeed.intValue)
    }
    if (Settings.musicInGame.value && Music.hasSource()) {
        GmLabel("Música")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GmButton(true, onClick = a.toggleMusic) { Text(if (Music.playing.value) "Pausar música" else "Reanudar música") }
            GmButton(false, onClick = { Music.next() }) { Text("Siguiente") }
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
        GmButton(false, onClick = {
            if (confirmReset) { confirmReset = false; a.reset() } else confirmReset = true
        }) { Text(if (confirmReset) "¿Seguro? Reiniciar" else "Reiniciar juego", color = if (confirmReset) Color(0xFFFF8A80) else Color.Unspecified) }
        GmButton(false, onClick = a.screenshot) { Text("Captura de pantalla") }
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


/**
 * Barra deslizable estilo "pastilla": pista blanca con contorno fino y relleno negro con sombra suave.
 * El relleno nunca baja de una pastilla redonda (como en la referencia con el valor al minimo).
 */
@Composable
fun PillSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val onC by androidx.compose.runtime.rememberUpdatedState(onChange)
    val r by androidx.compose.runtime.rememberUpdatedState(range)
    fun setFrom(x: Float, w: Int) {
        val f = (x / w.coerceAtLeast(1)).coerceIn(0f, 1f)
        onC(r.start + f * (r.endInclusive - r.start))
    }
    val h = 20.dp
    val inset = 3.dp
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(h + 8.dp)
            .androidxPointer { x, w -> setFrom(x, w) },
        contentAlignment = Alignment.CenterStart,
    ) {
        val inner = maxWidth - inset * 2
        val fillW = maxOf(h - inset * 2, inner * frac)
        Box(Modifier.fillMaxWidth().height(h).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = 0.95f)).border(1.dp, Color(0xFF111111), androidx.compose.foundation.shape.CircleShape))
        // sombra suave hacia abajo-izquierda
        Box(Modifier.padding(start = inset).offset((-3).dp, 4.dp).width(fillW).height(h - inset * 2).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.22f)))
        Box(Modifier.padding(start = inset).width(fillW).height(h - inset * 2).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF111111)))
    }
}

private fun Modifier.androidxPointer(onX: (Float, Int) -> Unit): Modifier = this
    .pointerInput(Unit) { detectTapGestures { onX(it.x, size.width) } }
    .pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { onX(it.x, size.width) },
        ) { c, _ -> c.consume(); onX(c.position.x, size.width) }
    }
