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
        }
    }
}
