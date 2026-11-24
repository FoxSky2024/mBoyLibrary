package app.gbalibrary.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Game
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fondo del menu 3D: la portada del juego elegido, muy desenfocada y teñida con el color de la consola (como el video). */
@Composable
fun ShelfBackdrop(game: Game?, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF05050A))) {
        if (game != null) {
            Crossfade(game, animationSpec = tween(450), label = "backdrop") { g ->
                val bmp = rememberBitmap(g, "front")
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().blur(60.dp).alpha(0.55f), contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.75f)))))
    }
}

/** Pastilla de estado centrada arriba: hora, red y bateria. */
@Composable
fun ShelfStatusPill(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(20_000) } }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(fmt.format(now).lowercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        StatusIndicators(tint = Color.White)
    }
}

/** Panel inferior de cristal: icono, titulo, tiempo, boton Jugar blanco, Inspeccionar y "…" (Detalles). */
@Composable
fun ShelfBottomPanel(
    game: Game, index: Int, total: Int,
    onPlay: () -> Unit, onInspect: () -> Unit, onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val console = remember(game.id) { consoleOf(game) }
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.09f)).border(1.dp, Color.White.copy(alpha = 0.14f), shape).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp))) { CoverImage(game, Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(game.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = AppFonts.Display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(console.name); append(" · "); append(formatPlayTime(game.playSeconds))
                        append(" · "); append(if (game.lastPlayed > 0) "Jugado ${formatAgo(game.lastPlayed)}" else "Nunca jugado")
                    },
                    color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "▶  Jugar", color = Color(0xFF14141A), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(Color(0xFFF2F2F5)).clickable { Sfx.enter(); onPlay() }.padding(horizontal = 26.dp, vertical = 11.dp),
            )
            Text(
                "Inspeccionar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { Sfx.click(); onInspect() }.padding(horizontal = 18.dp, vertical = 11.dp),
            )
            Box {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { Sfx.click(); menu = true },
                    contentAlignment = Alignment.Center,
                ) { Text("···", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Detalles del juego") }, onClick = { menu = false; onDetails() })
                }
            }
        }
        // puntos de pagina
        val dots = 5
        val cur = if (total <= 1) 0 else (index * dots / total).coerceIn(0, dots - 1)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(dots) { i -> Box(Modifier.size(if (i == cur) 7.dp else 5.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == cur) 0.9f else 0.35f))) }
        }
    }
}
