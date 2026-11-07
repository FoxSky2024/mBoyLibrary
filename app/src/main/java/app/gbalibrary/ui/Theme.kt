package app.gbalibrary.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.gbalibrary.data.Settings

/** Paleta de un tema: fondo, paneles, tarjetas, acento, boton JUGAR y textos. */
data class Palette(
    val name: String,
    val bg: Color,
    val panel: Color,
    val card: Color,
    val accent: Color,
    val play: Color,
    val text: Color,
    val dim: Color,
    val light: Boolean = false,
)

val Palettes = listOf(
    Palette("Oscuro Steam", Color(0xFF1B2838), Color(0xFF171A21), Color(0xFF2A475E), Color(0xFF66C0F4), Color(0xFF5BA32B), Color(0xFFC7D5E0), Color(0xFF8FA3B3)),
    Palette("Claro", Color(0xFFEEF1F5), Color(0xFFFFFFFF), Color(0xFFDDE4EC), Color(0xFF1E6FB8), Color(0xFF2E9E3F), Color(0xFF1B2733), Color(0xFF5C6B7A), light = true),
    Palette("Azul Neón", Color(0xFF070B1F), Color(0xFF0B1030), Color(0xFF132050), Color(0xFF00E5FF), Color(0xFF00C2FF), Color(0xFFD6F4FF), Color(0xFF7FA6C9)),
    Palette("Verde GBA", Color(0xFF0F2A12), Color(0xFF0A1F0D), Color(0xFF1E4D22), Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFFDDEFC0), Color(0xFF8FB07A)),
    Palette("Rojo", Color(0xFF260B0E), Color(0xFF1A070A), Color(0xFF4A1519), Color(0xFFFF5252), Color(0xFFE53935), Color(0xFFFFDADA), Color(0xFFC98E8E)),
    Palette("Morado", Color(0xFF1B1030), Color(0xFF130A24), Color(0xFF3A2368), Color(0xFFB388FF), Color(0xFF8E5CF0), Color(0xFFE8DDFF), Color(0xFFA797C9)),
)

val LocalPalette = staticCompositionLocalOf { Palettes[0] }

@Composable
fun GbaTheme(content: @Composable () -> Unit) {
    val p = Palettes[Settings.theme.intValue.coerceIn(0, Palettes.size - 1)]
    val scheme = if (p.light) {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White, secondary = p.play,
            background = p.bg, onBackground = p.text, surface = p.panel, onSurface = p.text,
            surfaceVariant = p.card, onSurfaceVariant = p.text,
        )
    } else {
        darkColorScheme(
            primary = p.accent, onPrimary = Color.Black, secondary = p.play,
            background = p.bg, onBackground = p.text, surface = p.panel, onSurface = p.text,
            surfaceVariant = p.card, onSurfaceVariant = p.text,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
