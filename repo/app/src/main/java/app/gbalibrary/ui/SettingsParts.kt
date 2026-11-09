package app.gbalibrary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import app.gbalibrary.emu.VideoFilter
import app.gbalibrary.emu.VideoScale

/**
 * Bloques de ajustes compartidos: se usan tal cual en Ajustes (biblioteca) y en el menu de pausa,
 * asi que ambos se ven y se comportan igual.
 */

@Composable
fun VideoSettings() {
    Column(verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
        Subtitle("Calidad de imagen")
        ChipRow(listOf("Pixel Perfect", "Suave", "CRT"), Settings.filter.intValue) { Settings.setFilter(it) }
        Hint(
            when (Settings.filter.intValue) {
                VideoFilter.PIXEL -> "Pixeles nitidos, como en la consola real."
                VideoFilter.SMOOTH -> "Imagen suavizada (lineal)."
                else -> "Pixeles nitidos con lineas de barrido tipo CRT."
            },
        )
        SliderRow("Brillo", Settings.brightness.floatValue, 0.5f..1.5f, "${(Settings.brightness.floatValue * 100).toInt()}%") { Settings.setBrightness(it) }
        Subtitle("Escala")
        ChipRow(listOf("Ajustar", "Original", "Estirar", "Llenar"), Settings.scale.intValue) { Settings.setScale(it) }
        Hint(
            when (Settings.scale.intValue) {
                VideoScale.FIT -> "Llena la pantalla manteniendo la proporcion de la consola."
                VideoScale.ORIGINAL -> "Multiplo entero del tamano original (pixeles perfectos)."
                VideoScale.STRETCH -> "Estira la imagen a toda la pantalla."
                else -> "Llena toda la pantalla recortando los bordes."
            },
        )
        Subtitle("Nintendo DS: pantallas")
        ChipRow(listOf("Arriba / abajo", "Lado a lado", "Solo arriba", "Solo abajo"), Settings.dsLayout.intValue) { Settings.setDsLayout(it) }
        Hint("Se aplica al abrir un juego de DS. La pantalla inferior es táctil: toca la imagen directamente. 'Lado a lado' aprovecha mejor el teléfono en horizontal.")
        SwitchRow("Mostrar FPS", Settings.showFps.value) { Settings.setShowFps(it) }
    }
}

@Composable
fun AudioSettings() {
    Column(verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
        SwitchRow("Sonido del juego", !Settings.muted.value) { Settings.setMuted(!it) }
        SliderRow("Volumen del juego", Settings.volume.intValue.toFloat(), 0f..100f, "${Settings.volume.intValue}%") { Settings.setVolume(it.toInt()) }
        Hint("Si el sonido se corta, cambia de salida de audio o vuelve a abrir el juego: la app recrea el audio sola.")
    }
}

/** Subtitulo dentro de una seccion. */
@Composable
fun Subtitle(text: String) {
    androidx.compose.material3.Text(
        text, color = LocalPalette.current.text, fontSize = Ds.BodySp.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    )
}
