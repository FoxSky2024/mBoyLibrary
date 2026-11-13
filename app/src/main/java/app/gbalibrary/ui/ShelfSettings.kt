package app.gbalibrary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.data.Systems
import app.gbalibrary.emu.ExternalLauncher

/** Ajustes > estanteria 3D, pantalla de arranque y estado de los emuladores externos. */
@Composable
fun ShelfDeckSection() {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    Section("Estantería 3D y sistemas") {
        Column(verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
            ToggleRow("Vista de estantería 3D", Settings.shelfView.value) { Settings.setShelfView(it) }
            Hint("También se cambia con el botón al final de la fila de sistemas. La vista Steam (cuadrícula) sigue disponible.")
            ToggleRow("Pantalla de arranque por consola", Settings.bootScreen.value) { Settings.setBootScreen(it) }
            Subtitle("Emuladores externos")
            Hint("GBA, GBC, GB y DS los ejecuta la propia app. Los demás sistemas se abren con el emulador instalado:")
            Systems.external.forEach { c ->
                val inst = ExternalLauncher.resolve(ctx, c)
                Row(Modifier.fillMaxWidth()) {
                    Text(c.name, color = p.text, fontSize = 13.sp, modifier = Modifier.width(150.dp))
                    val label = when {
                        inst != null -> "✓ ${inst.label}"
                        c.emulators.isEmpty() -> "Sin emulador para Android"
                        else -> "${c.emulators.joinToString { it.label }} (no instalado)"
                    }
                    Text(label, color = if (inst != null) Color(0xFF6BE675) else p.dim, fontSize = 13.sp)
                }
            }
            Hint("Pon cada sistema en su propia carpeta (GameCube/, Wii/, PSP/…) para que los .iso se clasifiquen bien.")
        }
    }
}
