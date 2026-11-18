package app.gbalibrary.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
) {
    /** Texto sobre el boton JUGAR / acciones principales (blanco o negro segun el color). */
    val onPlay: Color get() = readableOn(play)

    /** Texto sobre el color de acento. */
    val onAccent: Color get() = readableOn(accent)
}

private val RawPalettes = listOf(
    Palette("Oscuro Steam", Color(0xFF1B2838), Color(0xFF171A21), Color(0xFF2A475E), Color(0xFF66C0F4), Color(0xFF5BA32B), Color(0xFFC7D5E0), Color(0xFF8FA3B3)),
    Palette("Claro", Color(0xFFEEF1F5), Color(0xFFFFFFFF), Color(0xFFDDE4EC), Color(0xFF1E6FB8), Color(0xFF2E9E3F), Color(0xFF1B2733), Color(0xFF5C6B7A), light = true),
    Palette("Azul Neón", Color(0xFF070B1F), Color(0xFF0B1030), Color(0xFF132050), Color(0xFF00E5FF), Color(0xFF00C2FF), Color(0xFFD6F4FF), Color(0xFF7FA6C9)),
    Palette("Verde GBA", Color(0xFF0F2A12), Color(0xFF0A1F0D), Color(0xFF1E4D22), Color(0xFF9BBC0F), Color(0xFF8BAC0F), Color(0xFFDDEFC0), Color(0xFF8FB07A)),
    Palette("Rojo", Color(0xFF260B0E), Color(0xFF1A070A), Color(0xFF4A1519), Color(0xFFFF5252), Color(0xFFE53935), Color(0xFFFFDADA), Color(0xFFC98E8E)),
    Palette("Morado", Color(0xFF1B1030), Color(0xFF130A24), Color(0xFF3A2368), Color(0xFFB388FF), Color(0xFF8E5CF0), Color(0xFFE8DDFF), Color(0xFFA797C9)),
)

/** Relacion de contraste WCAG entre dos colores (1..21). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** Negro o blanco, el que mejor se lee sobre [bg]. */
fun readableOn(bg: Color): Color =
    if (contrastRatio(Color.White, bg) >= contrastRatio(Color.Black, bg)) Color.White else Color.Black

/** Acerca [this] a blanco/negro hasta cumplir [min] de contraste con todos los fondos de [bgs]. */
fun Color.ensureContrast(bgs: List<Color>, min: Float = 4.5f): Color {
    val target = if (bgs.all { it.luminance() < 0.5f }) Color.White else Color.Black
    var c = this
    var i = 0
    while (i < 20 && bgs.any { contrastRatio(c, it) < min }) {
        c = lerp(c, target, 0.12f)
        i++
    }
    return c
}

/** Todos los temas con contraste correcto: texto 7:1 aprox., texto tenue y acento 4.5:1 sobre fondo, panel y tarjeta. */
val Palettes: List<Palette> = RawPalettes.map { r ->
    val bgs = listOf(r.bg, r.panel, r.card)
    r.copy(
        text = r.text.ensureContrast(bgs, 7f),
        dim = r.dim.ensureContrast(bgs, 4.5f),
        accent = r.accent.ensureContrast(listOf(r.bg, r.panel), 4.5f),
    )
}

val LocalPalette = staticCompositionLocalOf { Palettes[0] }

@Composable
fun GbaTheme(content: @Composable () -> Unit) {
    val p = Palettes[Settings.theme.intValue.coerceIn(0, Palettes.size - 1)]
    val scheme = if (p.light) {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.play, onSecondary = p.onPlay, secondaryContainer = p.card, onSecondaryContainer = p.text, outline = p.dim,
            background = p.bg, onBackground = p.text, surface = p.panel, onSurface = p.text,
            surfaceVariant = p.card, onSurfaceVariant = p.text,
        )
    } else {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.play, onSecondary = p.onPlay, secondaryContainer = p.card, onSecondaryContainer = p.text, outline = p.dim,
            background = p.bg, onBackground = p.text, surface = p.panel, onSurface = p.text,
            surfaceVariant = p.card, onSurfaceVariant = p.text,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
