@file:OptIn(ExperimentalMaterial3Api::class)

package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings

private val SolidColors = listOf(
    0xFF1B2838, 0xFF000000, 0xFF263238, 0xFF0D2B3E, 0xFF1B3A1F, 0xFF3A1F1F, 0xFF2E1F47, 0xFF3E2C1C,
).map { it.toInt() }

@Composable
fun SettingsScreen(vm: LibraryViewModel, actions: LibraryActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(p.panel.copy(alpha = 0.95f)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("← Volver", color = p.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
            Text("Ajustes y personalización", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Section("Perfil") {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable { actions.pickAvatar() }) { Avatar(64) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = Settings.profileName.value,
                                onValueChange = { Settings.setProfileName(it.take(24)) },
                                label = { Text("Nombre de perfil") },
                                singleLine = true,
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

                Section("Tema") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Palettes.forEachIndexed { i, pal ->
                            FilterChip(
                                selected = Settings.theme.intValue == i,
                                onClick = { Sfx.click(); Settings.setTheme(i) },
                                label = { Text(pal.name) },
                                leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(pal.accent)) },
                            )
                        }
                    }
                }

                Section("Fondo de la biblioteca") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Por defecto", "Imagen", "Color sólido").forEachIndexed { i, name ->
                            FilterChip(selected = Settings.bgMode.intValue == i, onClick = { Settings.setBgMode(i) }, label = { Text(name) })
                        }
                    }
                    if (Settings.bgMode.intValue == 1) {
                        OutlinedButton(onClick = actions.pickLibraryBackground) { Text("Elegir imagen de la galería") }
                        ToggleRow("Desenfocar (blur)", Settings.bgBlur.value) { Settings.setBgBlur(it) }
                    }
                    if (Settings.bgMode.intValue == 2) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Section("Música de fondo (biblioteca)") {
                    ToggleRow("Activar música", Settings.musicOn.value) { Settings.setMusicOn(it) }
                    OutlinedButton(onClick = actions.pickMusic) { Text("Elegir archivo de música (mp3)") }
                    Text(
                        if (Settings.musicUri.value.isEmpty()) "Ningún archivo elegido" else "Archivo de música elegido ✓",
                        color = p.dim, fontSize = 12.sp,
                    )
                    ToggleRow("Silenciar música", Settings.musicMuted.value) { Settings.setMusicMuted(it) }
                    Text("Volumen de la música", color = p.text, fontSize = 13.sp)
                    Slider(value = Settings.musicVolume.floatValue, onValueChange = { Settings.setMusicVolume(it) })
                }

                Section("Sonidos de interfaz") {
                    ToggleRow("Click al navegar y al entrar a un juego", Settings.uiSounds.value) { Settings.setUiSounds(it) }
                }

                Section("Biblioteca") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = actions.addFolder) { Text("Agregar carpeta") }
                        OutlinedButton(onClick = actions.addFiles) { Text("Agregar archivos") }
                    }
                    OutlinedButton(onClick = { vm.rescan() }) { Text("Volver a escanear carpetas") }
                    Text("Esta app no incluye ROMs ni BIOS. Usa solo juegos que poseas legalmente.", color = p.dim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.panel.copy(alpha = 0.85f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title.uppercase(), color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = LocalPalette.current.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
