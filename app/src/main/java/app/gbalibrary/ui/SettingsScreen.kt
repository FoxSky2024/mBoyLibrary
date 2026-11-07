@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
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
 * Ajustes (diseno de la 0.2.0): cabecera + dos columnas de tarjetas con scroll. Las tarjetas son
 * semi-opacas para que se lean sobre cualquier fondo. Botones y chips usan FlowRow (nada se encima).
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
                Modifier.fillMaxWidth().height(52.dp).background(p.panel.copy(alpha = 0.97f)).padding(horizontal = 16.dp),
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
                    AppearanceSection(actions)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MusicSection(actions)
                    ControlsSection(globalLayout) { editingPad = true }
                    LibrarySection(vm, actions)
                }
            }
        }

        if (editingPad) {
            Box(Modifier.fillMaxSize().background(Color(0xFF05070A))) {
                Box(
                    Modifier.align(Alignment.Center).fillMaxHeight(0.8f).aspectRatioCompat(1.5f)
                        .border(2.dp, Color(0xFF2A3440), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("Pantalla del juego (vista previa)", color = Color(0xFF3A4654), fontSize = 12.sp) }
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
                OutlinedButton(onClick = actions.pickAvatar) { Text("Elegir foto") }
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
    Section("Tema") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Palettes.forEachIndexed { i, pal ->
                FilterChip(
                    selected = Settings.theme.intValue == i,
                    onClick = { Sfx.click(); Settings.setTheme(i) },
                    label = { Text(pal.name, maxLines = 1) },
                    leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(pal.accent)) },
                )
            }
        }
    }
    Section("Fondo de la biblioteca") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Por defecto", "Imagen", "Color sólido").forEachIndexed { i, name ->
                FilterChip(selected = Settings.bgMode.intValue == i, onClick = { Settings.setBgMode(i) }, label = { Text(name) })
            }
        }
        if (Settings.bgMode.intValue == 1) {
            OutlinedButton(onClick = actions.pickLibraryBackground) { Text("Elegir imagen de la galería") }
            ToggleRow("Desenfocar (blur)", Settings.bgBlur.value) { Settings.setBgBlur(it) }
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
            FilterChip(
                selected = globalLayout.moveType == MoveType.CROSS,
                onClick = { globalLayout.moveType = MoveType.CROSS; globalLayout.save("global") },
                label = { Text("Cruceta clásica") },
            )
            FilterChip(
                selected = globalLayout.moveType == MoveType.STICK,
                onClick = { globalLayout.moveType = MoveType.STICK; globalLayout.save("global") },
                label = { Text("Joystick analógico") },
            )
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
            OutlinedButton(onClick = onEdit) { Text("Editar controles") }
            OutlinedButton(onClick = { globalLayout.reset(); globalLayout.save("global") }) { Text("Restablecer layout global") }
        }
    }
    Section("Mando") { PadSettingsSection() }
}

@Composable
private fun MusicSection(actions: LibraryActions) {
    val p = LocalPalette.current
    Section("Música de fondo (biblioteca)") {
        ToggleRow("Activar música", Settings.musicOn.value) { Settings.setMusicOn(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = actions.pickMusicFolder) { Text("Elegir carpeta (playlist)") }
            OutlinedButton(onClick = actions.pickMusic) { Text("Un solo archivo") }
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
            OutlinedButton(onClick = { Settings.setMusicFolder("") }) { Text("Quitar carpeta") }
        }
        Text("Orden de reproducción (en bucle)", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !Settings.musicShuffle.value, onClick = { Settings.setMusicShuffle(false) }, label = { Text("En orden") })
            FilterChip(selected = Settings.musicShuffle.value, onClick = { Settings.setMusicShuffle(true) }, label = { Text("Aleatorio") })
            OutlinedButton(onClick = { Music.next() }) { Text("Siguiente ⏭") }
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
            OutlinedButton(onClick = actions.addFolder) { Text("Agregar carpeta") }
            OutlinedButton(onClick = actions.addFiles) { Text("Agregar archivos") }
            OutlinedButton(onClick = { vm.rescan() }) { Text("Volver a escanear") }
        }
        Text("Acepta .gba, .gbc, .gb, .zip y .7z (se descomprimen solo en la caché y se limpian al salir).", color = p.dim, fontSize = 12.sp)
        Text("Esta app no incluye ROMs ni BIOS. Usa solo juegos que poseas legalmente.", color = p.dim, fontSize = 12.sp)
    }
}

// ---------- Piezas ----------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.panel.copy(alpha = 0.94f))
            .border(1.dp, p.card, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title.uppercase(), color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = LocalPalette.current.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
