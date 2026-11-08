@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.VideoFilter

/** Filtro de video efectivo segun la calidad de renderizado (Bajo = pixel, Alto = suave, Medio = el elegido). */
fun effectiveFilter(): Int = when (Settings.renderQuality.intValue) {
    0 -> VideoFilter.PIXEL
    2 -> VideoFilter.SMOOTH
    else -> Settings.filter.intValue
}

@Composable
private fun PerfLabel(text: String) {
    Text(text, color = LocalPalette.current.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun PerfChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) = ChipRow(options, selected, onSelect)

@Composable
private fun PerfSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = SwitchRow(label, checked, onChange)

/**
 * Controles de rendimiento. Se usan en Ajustes > Optimizacion y en el menu de pausa; guardan en
 * Settings y el emulador los aplica al instante (sin reiniciar la app).
 */
@Composable
fun PerfControls() {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PerfLabel("Calidad de renderizado")
        PerfChips(listOf("Bajo", "Medio", "Alto"), Settings.renderQuality.intValue) { Settings.setRenderQuality(it) }
        Text(
            when (Settings.renderQuality.intValue) {
                0 -> "Bajo: píxeles nítidos, el más ligero."
                2 -> "Alto: imagen suavizada."
                else -> "Medio: usa el filtro elegido en Pantalla."
            },
            color = p.dim, fontSize = 11.sp,
        )
        PerfLabel("Límite de FPS")
        PerfChips(listOf("30", "60", "Sin límite"), when (Settings.fpsLimit.intValue) { 30 -> 0; 0 -> 2; else -> 1 }) {
            Settings.setFpsLimit(when (it) { 0 -> 30; 2 -> 0; else -> 60 })
        }
        Text("30 dibuja un cuadro de cada dos (ahorra batería). Sin límite corre sin freno de velocidad.", color = p.dim, fontSize = 11.sp)
        PerfLabel("Fast-Forward")
        PerfChips(listOf("x2", "x3", "x4"), Settings.ffSpeed.intValue - 2) { Settings.setFfSpeed(it + 2) }
        PerfSwitch("Saltar frames (solo si el teléfono no alcanza)", Settings.frameSkip.value) { Settings.setFrameSkip(it) }
        PerfSwitch("Sincronización de audio", Settings.audioSync.value) { Settings.setAudioSync(it) }
    }
}
