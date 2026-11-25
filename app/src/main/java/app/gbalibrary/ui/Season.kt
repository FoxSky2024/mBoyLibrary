package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * TEMPORADA (particulas + banner). Para QUITARLO todo: pon ENABLED = false.
 * Para quitar solo una parte: SNOW = false o BANNER = false.
 * Para cambiar de temporada: cambia THEME (CHRISTMAS, NEW_YEAR, HALLOWEEN) o edita sus textos/colores abajo.
 */
object Season {
    const val ENABLED = true
    const val SNOW = false // particulas desactivadas (el codigo sigue aqui por si se quieren volver a activar)
    const val BANNER = true

    /** true: la temporada se elige sola por fecha (y no hay nada fuera de temporada). false: siempre [THEME]. */
    const val AUTO = false
    val THEME: SeasonTheme = SeasonTheme.CHRISTMAS

    /** Tema activo hoy, o null si no toca ninguno. */
    fun active(): SeasonTheme? {
        if (!ENABLED) return null
        if (!AUTO) return THEME
        val c = java.util.Calendar.getInstance()
        val m = c.get(java.util.Calendar.MONTH) // 0 = enero
        val d = c.get(java.util.Calendar.DAY_OF_MONTH)
        return when {
            m == 11 && d <= 26 -> SeasonTheme.CHRISTMAS
            (m == 11 && d >= 27) || (m == 0 && d <= 6) -> SeasonTheme.NEW_YEAR
            (m == 9 && d >= 15) || (m == 10 && d <= 2) -> SeasonTheme.HALLOWEEN
            else -> null
        }
    }
}

class SeasonTheme(
    val title: String,
    val subtitle: String,
    val tag: String,
    val colors: List<Color>,       // degradado del banner (izquierda -> derecha)
    val particle: Color,           // color de las particulas
    val lights: List<Color>,       // bombillas de la guirnalda
    val particleCount: Int,
    val fall: Float,               // velocidad de caida (1 = normal)
) {
    companion object {
        val CHRISTMAS = SeasonTheme(
            "FELIZ NAVIDAD", "Tu biblioteca de GBA y DS, lista para las fiestas", "TEMPORADA NAVIDEÑA",
            listOf(Color(0xFF7A0F1E), Color(0xFF1B3A2A), Color(0xFF0E2236)), Color.White,
            listOf(Color(0xFFFF4D4D), Color(0xFFFFD54F), Color(0xFF66E08A), Color(0xFF66C0F4)), 70, 1f,
        )
        val NEW_YEAR = SeasonTheme(
            "FELIZ AÑO NUEVO", "Que empiece una nueva partida", "FIN DE AÑO",
            listOf(Color(0xFF1A1033), Color(0xFF3B1F6B), Color(0xFF0E2236)), Color(0xFFFFE08A),
            listOf(Color(0xFFFFD54F), Color(0xFFFF80AB), Color(0xFF80D8FF)), 55, 0.7f,
        )
        val HALLOWEEN = SeasonTheme(
            "NOCHE DE BRUJAS", "Juegos para una noche de miedo", "HALLOWEEN",
            listOf(Color(0xFF2B0F00), Color(0xFF7A3500), Color(0xFF14091F)), Color(0xFFFFB74D),
            listOf(Color(0xFFFF9800), Color(0xFFB388FF), Color(0xFF9CCC65)), 30, 0.5f,
        )
    }
}

private class Flake(var x: Float, var y: Float, val r: Float, val v: Float, val sway: Float, val ph: Float)

/** Particulas (nieve) sobre toda la app. No intercepta toques. */
@Composable
fun SeasonParticles() {
    if (!Season.SNOW) return
    val th = Season.active() ?: return
    val flakes = remember { List(th.particleCount) { Flake(Random.nextFloat(), Random.nextFloat(), 1.2f + Random.nextFloat() * 3.2f, 0.03f + Random.nextFloat() * 0.07f, 0.01f + Random.nextFloat() * 0.03f, Random.nextFloat() * 6.28f) } }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                val dt = if (last == 0L) 0f else ((n - last) / 1e9f).coerceAtMost(0.1f)
                last = n
                flakes.forEach { f ->
                    f.y += f.v * th.fall * dt * 2.2f
                    f.x += sin(n / 1e9f * 0.8f + f.ph) * f.sway * dt
                    if (f.y > 1.03f) { f.y = -0.03f; f.x = Random.nextFloat() }
                }
                tick = n
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        if (tick < 0L) return@Canvas
        flakes.forEach { f ->
            val c = Offset(f.x * size.width, f.y * size.height)
            drawCircle(th.particle.copy(alpha = 0.18f), f.r * 2.2f, c)
            drawCircle(th.particle.copy(alpha = 0.85f), f.r, c)
        }
    }
}

/** Banner de temporada (estilo Steam) para la pantalla de inicio. */
@Composable
fun SeasonBanner(modifier: Modifier = Modifier, compact: Boolean = false) {
    if (!Season.BANNER) return
    val th = Season.active() ?: return
    Box(modifier.fillMaxWidth().height(if (compact) 52.dp else 104.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(th.colors))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // brillo diagonal
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), start = Offset(w * 0.1f, 0f), end = Offset(w * 0.5f, h)))
            // colinas nevadas
            val hills = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) { lineTo(x, h - h * 0.16f - h * 0.07f * sin(x / w * 3f * PI.toFloat())); x += 8f }
                lineTo(w, h); close()
            }
            drawPath(hills, Color.White.copy(alpha = 0.85f))
            // guirnalda con bombillas
            val wire = Path()
            val n = 14
            for (i in 0..n) {
                val x = w * i / n
                val y = 6f + 10f * sin(i / n.toFloat() * PI.toFloat() * 4f).let { kotlin.math.abs(it) }
                if (i == 0) wire.moveTo(x, y) else wire.lineTo(x, y)
            }
            drawPath(wire, Color.Black.copy(alpha = 0.45f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            for (i in 0..n) {
                val x = w * i / n
                val y = 6f + 10f * kotlin.math.abs(sin(i / n.toFloat() * PI.toFloat() * 4f))
                val c = th.lights[i % th.lights.size]
                drawCircle(c.copy(alpha = 0.30f), 11f, Offset(x, y + 6f))
                drawCircle(c, 4.5f, Offset(x, y + 6f))
            }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, top = 8.dp)) {
            Text(th.tag, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, fontFamily = AppFonts.Display)
            Text(th.title, color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.Display)
            if (!compact) Text(th.subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontFamily = AppFonts.Body)
        }
    }
}
