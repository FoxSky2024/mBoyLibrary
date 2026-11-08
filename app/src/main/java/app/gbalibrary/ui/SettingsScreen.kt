@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import app.gbalibrary.data.DataDir
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.input.PadInput
import java.net.URLDecoder
import kotlin.math.roundToInt

private val SolidColors = listOf(
    0xFF1B2838, 0xFF000000, 0xFF263238, 0xFF0D2B3E, 0xFF1B3A1F, 0xFF3A1F1F, 0xFF2E1F47, 0xFF3E2C1C,
).map { it.toInt() }

/**
 * Ajustes (diseno clasico): barra superior "← Volver" y DOS columnas de secciones con un solo
 * scroll, como antes de la version con menu lateral. Conserva todas las opciones nuevas
 * (imagen, sonido del juego, rendimiento, ventana PiP, interfaz, animaciones...).
 */
@Composable
fun SettingsScreen(vm: LibraryViewModel, actions: LibraryActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    var editingPad by remember { mutableStateOf(false) }
    val globalLayout = remember { ControlLayout().also { it.load("global") } }

    BackHandler(enabled = editingPad) { globalLayout.save("global"); editingPad = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).opacityPanel(p.panel).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("← Volver", color = p.accent, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
                Text("Ajustes y personalización", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProfileSection(actions)
                    ScaleSection()
                    AppearanceSection(actions)
                    InterfaceSection()
                    AnimationsSection()
                    Section("Imagen") { VideoSettings() }
                    PipSection(actions)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MusicSection(actions)
                    Section("Sonido del juego") { AudioSettings() }
                    ControlsSection(globalLayout) { editingPad = true }
                    PerfSection()
                    LibrarySection(vm, actions)
                    CoversSection(vm)
                    DataSection(actions)
                }
            }
        }

        if (editingPad) {
            Box(Modifier.fillMaxSize().background(Color(0xFF05070A))) {
                Box(
                    Modifier.align(Alignment.Center).fillMaxHeight(0.8f).aspectRatioCompat(1.5f)
                        .border(2.dp, Color(0xFF2A3440), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("Pantalla del juego (vista previa)", color = Color(0xFF8795A4), fontSize = 12.sp) }
                ControlsCanvas(globalLayout, null, editing = true, showPad = true, onMenu = {}, onQuick = {})
                ControlsEditorPanel(
                    layout = globalLayout,
                    gameName = null,
                    onSaveGlobal = { globalLayout.save("global") },
                    onSaveGame = null,
                    onReset = { globalLayout.reset() },
                    onDone = { globalLayout.save("global"); editingPad = false },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                )
            }
        }
    }
}

private fun Modifier.aspectRatioCompat(r: Float): Modifier = this.then(Modifier.aspectRatio(r))

// ---------- Secciones ----------

@Composable
private fun ProfileSection(actions: LibraryActions) {
    Section("Perfil") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { actions.pickAvatar() }) { Avatar(64) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = Settings.profileName.value,
                    onValueChange = { Settings.setProfileName(it.take(24)) },
                    label = { Text("Nombre de perfil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton("Elegir foto", actions.pickAvatar)
            }
        }
        OutlinedTextField(
            value = Settings.appName.value,
            onValueChange = { Settings.setAppName(it.take(24)) },
            label = { Text("Nombre de la app (dentro)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppearanceSection(actions: LibraryActions) {
    val p = LocalPalette.current
    Section("Tema de color") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Ds.GapL), verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
            Palettes.forEachIndexed { i, pal -> ThemeTile(pal, Settings.theme.intValue == i) { Sfx.click(); Settings.setTheme(i) } }
        }
        Hint("Todos los temas cumplen contraste de lectura (texto 7:1, acento y texto tenue 4.5:1).")
    }
    Section("Opacidad de paneles") {
        Text("Opacidad ${(Settings.boxOpacity.floatValue * 100).roundToInt()}%", color = p.text, fontSize = 13.sp)
        Slider(
            value = Settings.boxOpacity.floatValue,
            onValueChange = { Settings.setBoxOpacity((it * 100).roundToInt() / 100f) },
            valueRange = 0.4f..1f,
        )
        Text("Aplica a las barras y tarjetas de la biblioteca. Los paneles de Ajustes son siempre sólidos.", color = p.dim, fontSize = 12.sp)
    }
    Section("Fondo de la biblioteca") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Por defecto", "Imagen", "Color sólido").forEachIndexed { i, name ->
                AppChip(name, Settings.bgMode.intValue == i, { Settings.setBgMode(i) })
            }
        }
        if (Settings.bgMode.intValue == 1) {
            AppButton("Elegir imagen de la galería", actions.pickLibraryBackground)
        }
        if (Settings.bgMode.intValue == 2) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SolidColors.forEach { c ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .border(if (Settings.bgColor.intValue == c) 3.dp else 1.dp, if (Settings.bgColor.intValue == c) p.accent else p.dim, CircleShape)
                            .clickable { Settings.setBgColor(c) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsSection(globalLayout: ControlLayout, onEdit: () -> Unit) {
    val p = LocalPalette.current
    Section("Botones táctiles") {
        ToggleRow("Mostrar botones táctiles", Settings.padVisible.value) { Settings.setPadVisible(it) }
        Text("Tamaño general ${(Settings.padSize.floatValue * 100).roundToInt()}%", color = p.text, fontSize = 13.sp)
        Slider(value = Settings.padSize.floatValue, onValueChange = { Settings.setPadSize(it) }, valueRange = 0.6f..1.6f)
        Text("Opacidad general ${(Settings.padOpacity.floatValue * 100).roundToInt()}%", color = p.text, fontSize = 13.sp)
        Slider(value = Settings.padOpacity.floatValue, onValueChange = { Settings.setPadOpacity(it) }, valueRange = 0.15f..1f)
        Text("Tipo de movimiento (global)", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppChip("Cruceta clásica", globalLayout.moveType == MoveType.CROSS, { globalLayout.moveType = MoveType.CROSS; globalLayout.save("global") })
            AppChip("Joystick analógico", globalLayout.moveType == MoveType.STICK, { globalLayout.moveType = MoveType.STICK; globalLayout.save("global") })
        }
        if (globalLayout.moveType == MoveType.STICK) {
            Text("Zona muerta ${(globalLayout.deadzone * 100).roundToInt()}%", color = p.text, fontSize = 13.sp)
            Slider(
                value = globalLayout.deadzone,
                onValueChange = { globalLayout.deadzone = it },
                onValueChangeFinished = { globalLayout.save("global") },
                valueRange = 0.05f..0.7f,
            )
        }
        Text(
            "Se puede cambiar entre cruceta y joystick en cualquier momento sin perder el resto de botones. " +
                "Cada juego puede tener su propio layout desde el menú del juego (Controles).",
            color = p.dim, fontSize = 12.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton("Editar controles", onEdit)
            AppButton("Restablecer layout global", { globalLayout.reset(); globalLayout.save("global") })
        }
    }
    Section("Mando") { PadSettingsSection() }
}

@Composable
private fun MusicSection(actions: LibraryActions) {
    val p = LocalPalette.current
    Section("Música de fondo (biblioteca)") {
        ToggleRow("Activar música", Settings.musicOn.value) { Settings.setMusicOn(it) }
        ToggleRow("Seguir escuchando música dentro del juego", Settings.musicInGame.value) { Settings.setMusicInGame(it) }
        Text(
            if (Settings.musicInGame.value) "ON: la música sigue al entrar a jugar; pausa con el botón ♪ flotante o desde el menú de pausa."
            else "OFF: la música se pausa al entrar al juego y vuelve al salir.",
            color = LocalPalette.current.dim, fontSize = 12.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton("Elegir carpeta (playlist)", actions.pickMusicFolder)
            AppButton("Un solo archivo", actions.pickMusic)
        }
        val folder = Settings.musicFolder.value
        Text(
            when {
                folder.isNotEmpty() -> "Carpeta: " + folderLabel(folder)
                Settings.musicUri.value.isNotEmpty() -> "Archivo de música elegido ✓"
                else -> "Ninguna carpeta ni archivo elegido"
            },
            color = p.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (folder.isNotEmpty()) {
            AppButton("Quitar carpeta", { Settings.setMusicFolder("") })
        }
        Text("Orden de reproducción (en bucle)", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppChip("En orden", !Settings.musicShuffle.value, { Settings.setMusicShuffle(false) })
            AppChip("Aleatorio", Settings.musicShuffle.value, { Settings.setMusicShuffle(true) })
            AppButton("Siguiente ⏭", { Music.next() })
        }
        ToggleRow("Silenciar música", Settings.musicMuted.value) { Settings.setMusicMuted(it) }
        Text("Volumen de la música", color = p.text, fontSize = 13.sp)
        Slider(value = Settings.musicVolume.floatValue, onValueChange = { Settings.setMusicVolume(it) })
        Text("Formatos: mp3, m4a, ogg (y aac, wav, flac). Suena solo en la biblioteca, nunca dentro del juego.", color = p.dim, fontSize = 12.sp)
    }
    Section("Sonidos de interfaz") {
        ToggleRow("Click al navegar y al entrar a un juego", Settings.uiSounds.value) { Settings.setUiSounds(it) }
    }
}

private fun folderLabel(uri: String): String = try {
    URLDecoder.decode(uri.substringAfterLast("/tree/"), "UTF-8").substringAfterLast(':').ifBlank { "carpeta elegida" }
} catch (e: Exception) {
    "carpeta elegida"
}

@Composable
private fun LibrarySection(vm: LibraryViewModel, actions: LibraryActions) {
    val p = LocalPalette.current
    Section("Biblioteca") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton("Agregar carpeta", actions.addFolder)
            AppButton("Agregar archivos", actions.addFiles)
            AppButton("Volver a escanear", { vm.rescan() })
        }
        Text("Acepta .gba, .gbc, .gb, .zip y .7z (se descomprimen solo en la caché y se limpian al salir).", color = p.dim, fontSize = 12.sp)
        Text("Esta app no incluye ROMs ni BIOS. Usa solo juegos que poseas legalmente.", color = p.dim, fontSize = 12.sp)
    }
}


// ---------- v0.3: escala, portadas, datos ----------

private val ScaleChips = listOf(0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.25f)

@Composable
private fun ScaleSection() {
    val p = LocalPalette.current
    Section("Escala de la interfaz") {
        Text("Tamaño ${"%.2f".format(Settings.uiScale.floatValue)}x (por defecto 0.85x)", color = p.text, fontSize = 13.sp)
        Slider(value = Settings.uiScale.floatValue, onValueChange = { Settings.setUiScale((it * 100).roundToInt() / 100f) }, valueRange = 0.7f..1.25f)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ScaleChips.forEach { v ->
                AppChip("${v}x", kotlin.math.abs(Settings.uiScale.floatValue - v) < 0.005f, { Settings.setUiScale(v) })
            }
            AppButton("Por defecto", { Settings.setUiScale(0.85f) })
        }
        Text("Afecta tarjetas, títulos, barras, botones y texto de la biblioteca y Ajustes. Los controles dentro del juego no cambian.", color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun CoversSection(vm: LibraryViewModel) {
    val p = LocalPalette.current
    val status = vm.coverStatus.collectAsState().value ?: ""
    Section("Portadas") {
        ToggleRow("Buscar portadas automáticamente al agregar juegos", Settings.autoCovers.value) { Settings.setAutoCovers(it) }
        AppButton("Buscar portadas faltantes", { vm.fetchCovers(true) })
        if (status.isNotBlank()) Text(status, color = p.accent, fontSize = 12.sp)
        OutlinedTextField(
            value = Settings.sgdbKey.value,
            onValueChange = { Settings.setSgdbKey(it.trim()) },
            label = { Text("Clave SteamGridDB (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Fuentes: Libretro, SteamGridDB (si hay clave) y Wikipedia. Se guardan en GBALibrary/Covers; si no hay, queda la letra. Mantén pulsado un juego para cambiar su portada.", color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun DataSection(actions: LibraryActions) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var withRoms by remember { mutableStateOf(false) }
    val perm = DataDir.hasStoragePermission(ctx)
    Section("Datos (carpeta persistente)") {
        Text(DataDir.root(ctx).path, color = p.text, fontSize = 12.sp)
        Text(
            if (perm) "Permiso de almacenamiento: concedido ✓ (se conserva al reinstalar)"
            else "Sin permiso de almacenamiento: se usa una carpeta que Android borra al desinstalar.",
            color = if (perm) p.dim else p.accent, fontSize = 12.sp,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!perm) AppButton("Conceder permiso", actions.requestStorage)
            AppButton("Abrir carpeta de datos", actions.openDataFolder)
            AppButton("Exportar copia de seguridad", { actions.exportBackup(withRoms) })
            AppButton("Importar copia", actions.importBackup)
        }
        ToggleRow("Incluir ROMs en la copia", withRoms) { withRoms = it }
        Text("Games, Saves, States, Covers, config.json y library.json viven ahí y nunca se borran.", color = p.dim, fontSize = 12.sp)
    }
}

// ---------- Piezas ----------

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    SectionCard(title) { content() }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = SwitchRow(label, checked, onChange)


// ---------- v0.3.3: animaciones, interfaz, optimizacion ----------

@Composable
private fun AnimationsSection() {
    val p = LocalPalette.current
    Section("Animaciones") {
        ToggleRow("Animación al entrar al juego", Settings.entryAnim.value) { Settings.setEntryAnim(it) }
        Text("Fade + zoom del logo y el nombre del juego (~0,7 s). El juego carga por detrás, no se retrasa.", color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun InterfaceSection() {
    val p = LocalPalette.current
    Section("Interfaz") {
        ToggleRow("Mostrar batería e internet", Settings.showStatus.value) { Settings.setShowStatus(it) }
        Text("Indicadores pequeños en la esquina del menú principal y de la pantalla de pausa.", color = p.dim, fontSize = 12.sp)
        Text("Márgenes de la interfaz: ${Settings.uiMargin.intValue} dp", color = p.text, fontSize = 13.sp)
        Slider(
            value = Settings.uiMargin.intValue.toFloat(),
            onValueChange = { Settings.setUiMargin(it.roundToInt()) },
            valueRange = 0f..48f, steps = 11,
        )
        Text("Separa de los bordes (notch, esquinas redondeadas) el menú y los botones táctiles del juego.", color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun PerfSection() {
    val p = LocalPalette.current
    Section("Optimización / Rendimiento") {
        PerfControls()
        Text("Se aplican al instante (también dentro del juego, en el menú de pausa).", color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun PipSection(actions: LibraryActions) {
    Section("Video en ventana (PiP)") {
        PipControls(pickVideo = actions.pickPipVideo)
    }
}

/** Muestra de un tema: fondo, panel y acento, con su nombre completo debajo (nunca se corta). */
@Composable
private fun ThemeTile(pal: Palette, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(112.dp).clip(RoundedCornerShape(Ds.RadiusM)).clickable(onClick = onClick)
            .border(if (selected) 3.dp else 1.dp, if (selected) p.accent else p.card, RoundedCornerShape(Ds.RadiusM))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(pal.bg)) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(14.dp).background(pal.panel))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(16.dp).clip(CircleShape).background(pal.accent))
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(width = 26.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(pal.play))
        }
        Text(pal.name, color = p.text, fontSize = Ds.LabelSp.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
