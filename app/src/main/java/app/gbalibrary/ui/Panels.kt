package app.gbalibrary.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.gbalibrary.data.Settings

/**
 * Escala de la interfaz (Ajustes > Escala UI, 0.7x a 1.25x, por defecto 0.85x).
 * Multiplica la densidad, asi que escala TODO lo que usa dp y sp (tarjetas, textos, barras, botones)
 * y los diseños se reacomodan solos porque el espacio disponible en dp cambia con la escala.
 * Solo se aplica a biblioteca y ajustes; los controles dentro del juego no se tocan.
 */
@Composable
fun UiScale(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scale = Settings.uiScale.floatValue
    val scaled = remember(base.density, base.fontScale, scale) { Density(base.density * scale, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides scaled) { content() }
}

/**
 * Barra superior / lateral: color solido oscuro del tema con la opacidad elegida en
 * Ajustes > Apariencia (40%..100%). Sin glass, sin blur: solo un fondo con alpha.
 */
@Composable
fun Modifier.opacityPanel(base: Color, shape: Shape = RectangleShape): Modifier =
    this.clip(shape).background(base.copy(alpha = Settings.boxOpacity.floatValue))

/** Tarjetas de juegos y paneles de Ajustes: mismo fondo solido con la opacidad elegida. */
@Composable
fun Modifier.cardPanel(base: Color, shape: Shape): Modifier =
    this.clip(shape).background(base.copy(alpha = Settings.boxOpacity.floatValue))

/**
 * Foco estilo Steam Big Picture: el elemento enfocado crece suavemente (zoom), proyecta sombra,
 * pasa por encima de sus vecinos y lleva un borde de acento bien visible. Solo usa graphicsLayer
 * (no recompone ni reacomoda), asi que es barato.
 */
@Composable
fun Modifier.bigPictureFocus(focused: Boolean, shape: Shape, accent: Color, maxScale: Float = 1.08f): Modifier {
    val t by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "bpFocus")
    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            val sc = 1f + (maxScale - 1f) * t
            scaleX = sc
            scaleY = sc
            shadowElevation = 28.dp.toPx() * t
            this.shape = shape
            clip = false
        }
        .then(if (focused) Modifier.border(3.dp, accent, shape) else Modifier)
}
