package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import app.gbalibrary.data.Game
import app.gbalibrary.data.Pawb
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Carrusel de banners de la pantalla de inicio (estilo portada de Steam):
 * temporada, "Destacado y recomendado" (seguir jugando), evento semanal, juego del dia y tu actividad.
 * Para quitar o cambiar uno: edita la lista `pages` de [HomeBanners]. BANNERS = false lo oculta todo.
 */
object HomeBannerConfig {
    const val BANNERS = true
    const val AUTO_MS = 6000L
}

private class Slide(val key: String, val content: @Composable () -> Unit)

@Composable
fun HomeBanners(all: List<Game>, onOpen: (Game) -> Unit, onPlay: (Game) -> Unit, modifier: Modifier = Modifier) {
    if (!HomeBannerConfig.BANNERS || all.isEmpty()) return
    val p = LocalPalette.current
    val day = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }
    val last = remember(all) { all.filter { it.lastPlayed > 0 }.maxByOrNull { it.lastPlayed } ?: all.first() }
    val daily = remember(all, day) { all[day % all.size] }
    val season = Season.active()

    val equipped = Pawb.banner.value
    val pages = remember(all, season, last, daily, equipped, Pawb.custom.value) {
        buildList<Slide> {
            if (Pawb.banner.value.isNotEmpty()) add(Slide("mine") { EquippedBannerArt(Modifier.fillMaxSize()) })
            if (season != null) add(Slide("season") { SeasonBanner(Modifier.fillMaxSize()) })
            add(Slide("featured") { FeaturedBanner(last, onOpen, onPlay) })
            add(Slide("event") { EventBanner(all, day) })
            add(Slide("deal") { DealBanner(daily, onOpen) })
            add(Slide("stats") { StatsBanner(all) })
        }
    }
    val pager = rememberPagerState { pages.size }
    LaunchedEffect(pages.size) {
        while (true) {
            delay(HomeBannerConfig.AUTO_MS)
            if (!pager.isScrollInProgress && pages.size > 1) pager.animateScrollToPage((pager.currentPage + 1) % pages.size)
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(104.dp), pageSpacing = 10.dp) { i ->
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) { pages[i].content() }
        }
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(pages.size) { i ->
                Box(Modifier.width(if (i == pager.currentPage) 18.dp else 6.dp).height(4.dp).clip(CircleShape).background(if (i == pager.currentPage) p.accent else p.dim.copy(alpha = 0.4f)))
            }
        }
    }
}

/** "Destacado y recomendado": el ultimo juego con su caratula, tiempo y boton JUGAR. */
@Composable
private fun FeaturedBanner(g: Game, onOpen: (Game) -> Unit, onPlay: (Game) -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF1B2D44), Color(0xFF2A475E), Color(0xFF16202D)))).clickable { onOpen(g) }) {
        Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(84.dp).width(62.dp).clip(RoundedCornerShape(4.dp))) { CoverImage(g, Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("DESTACADO Y RECOMENDADO", color = Color(0xFF66C0F4), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, fontFamily = AppFonts.Display)
                Text(g.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.Display, maxLines = 1)
                Text(
                    if (g.lastPlayed > 0) "Seguir jugando · ${formatPlayTime(g.playSeconds)} · ${formatAgo(g.lastPlayed)}" else "Aún sin jugar · ¡empieza ahora!",
                    color = Color(0xFFC7D5E0), fontSize = 12.sp, maxLines = 1,
                )
            }
            Text(
                "JUGAR", color = p.onPlay, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(p.play).clickable { Sfx.enter(); onPlay(g) }.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

/** Evento semanal tipo "Fest": pergamino naranja con titulo grande; cambia segun el dia de la semana. */
@Composable
private fun EventBanner(all: List<Game>, day: Int) {
    data class Ev(val title: String, val sub: String, val a: Color, val b: Color)
    val evs = listOf(
        Ev("RETRO FEST", "Tu biblioteca clásica", Color(0xFFE8892B), Color(0xFF8A3B12)),
        Ev("SEMANA GBA", "${all.count { systemOf(it) == "GBA" }} juegos de Game Boy Advance", Color(0xFF6C4BD6), Color(0xFF2A1A6B)),
        Ev("PORTÁTILES FEST", "${all.count { systemOf(it) == "GBC" || systemOf(it) == "GB" }} clásicos de Game Boy", Color(0xFF3FA56B), Color(0xFF14432B)),
        Ev("DUAL SCREEN FEST", "${all.count { systemOf(it) == "DS" }} juegos de Nintendo DS", Color(0xFF3B7DD8), Color(0xFF123566)),
    )
    val e = evs[day % evs.size]
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(e.b, e.a, e.b)))) {
        Canvas(Modifier.fillMaxSize()) {
            // rayas diagonales del pergamino
            var x = -size.height
            while (x < size.width) {
                val path = Path().apply { moveTo(x, size.height); lineTo(x + size.height * 0.8f, 0f); lineTo(x + size.height * 0.8f + 22f, 0f); lineTo(x + 22f, size.height); close() }
                drawPath(path, Color.White.copy(alpha = 0.07f))
                x += 70f
            }
            drawRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, 6f))
            drawRect(Color.Black.copy(alpha = 0.25f), topLeft = Offset(0f, size.height - 6f), size = androidx.compose.ui.geometry.Size(size.width, 6f))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(e.title, color = Color(0xFFFFF3D6), fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.Display, letterSpacing = 3.sp)
            Text(e.sub, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("EN CURSO ESTA SEMANA", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = AppFonts.Display)
        }
    }
}

/** "Descuentos y eventos": juego del dia con etiqueta de oferta (-100%, ya es tuyo). */
@Composable
private fun DealBanner(g: Game, onOpen: (Game) -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF2A1033), Color(0xFF4A1F5C), Color(0xFF16202D)))).clickable { onOpen(g) }) {
        Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(84.dp).width(62.dp).clip(RoundedCornerShape(4.dp))) { CoverImage(g, Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("OFERTA DEL DÍA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(Color(0xFF9C2D7E)).padding(horizontal = 8.dp, vertical = 2.dp))
                Text(g.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.Display, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("-100%", color = Color(0xFF1B2D0A), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.background(Color(0xFFA4D007)).padding(horizontal = 8.dp, vertical = 3.dp))
                    Text("Ya está en tu biblioteca", color = Color(0xFFC7D5E0), fontSize = 12.sp)
                }
            }
        }
    }
}

/** Resumen de tu actividad. */
@Composable
private fun StatsBanner(all: List<Game>) {
    val hours = all.sumOf { it.playSeconds }
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0E2236), Color(0xFF1F4E5F), Color(0xFF0E2236))))) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat("${all.size}", "JUEGOS")
            Stat(formatPlayTime(hours), "JUGADO")
            Stat("${all.count { it.playSeconds > 0 }}", "EMPEZADOS")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.Display)
        Text(label, color = Color(0xFF66C0F4), fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = AppFonts.Display)
    }
}
