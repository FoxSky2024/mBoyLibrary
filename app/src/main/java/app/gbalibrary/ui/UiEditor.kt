package app.gbalibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import kotlin.math.roundToInt

/** Abre el editor de interfaz en vivo (lo atiende la biblioteca: cierra Ajustes y muestra el panel sobre ella). */
object UiEditNav {
    val show = mutableStateOf(false)
    val request = mutableStateOf(false)
}

/** Panel compacto abajo: todo lo que cambias se ve al instante en la biblioteca de detras. */
@Composable
fun UiEditorOverlay(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var collapsed by remember { mutableStateOf(false) }
    Column(
        modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.panel.copy(alpha = 0.94f))
            .border(1.dp, p.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Editar interfaz · en vivo", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            AppButton(if (collapsed) "Abrir" else "Plegar", { collapsed = !collapsed })
            AppButton("Listo", { UiEditNav.show.value = false }, kind = BtnKind.PRIMARY)
        }
        if (!collapsed) Column(
            Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Tema", color = p.dim, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palettes.forEachIndexed { i, pal ->
                    val sel = Settings.theme.intValue == i
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(pal.bg).border(if (sel) 3.dp else 1.dp, if (sel) p.accent else p.dim, CircleShape)
                            .clickable { Sfx.click(); Settings.setTheme(i) },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size(16.dp).clip(CircleShape).background(pal.accent)) }
                }
            }
            Text("Tamaño de la interfaz  ${"%.2f".format(Settings.uiScale.floatValue)}x", color = p.dim, fontSize = 11.sp)
            Slider(Settings.uiScale.floatValue, { Settings.setUiScale((it * 100).roundToInt() / 100f) }, valueRange = 0.5f..1.25f)
            Text("Margen de pantalla  ${Settings.uiMargin.intValue}", color = p.dim, fontSize = 11.sp)
            Slider(Settings.uiMargin.intValue.toFloat(), { Settings.setUiMargin(it.roundToInt()) }, valueRange = 0f..48f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip("Estantería", Settings.shelfView.value, { Settings.setShelfView(true) })
                AppChip("Cuadrícula", !Settings.shelfView.value, { Settings.setShelfView(false) })
                AppButton("Por defecto", { Settings.setUiScale(0.6f); Settings.setUiMargin(0) })
            }
        }
    }
}
