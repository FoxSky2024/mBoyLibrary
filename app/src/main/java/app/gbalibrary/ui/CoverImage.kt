package app.gbalibrary.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/** Portada personalizada del juego o, si no hay, un placeholder con degradado, inicial y titulo. */
@Composable
fun CoverImage(game: Game, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, game.id, game.coverStamp, game.hasCover) {
        value = if (game.hasCover) withContext(Dispatchers.IO) { Covers.load(Covers.coverFile(ctx, game.id)) } else null
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = game.title, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        val hue = (game.id.hashCode().absoluteValue % 360).toFloat()
        val top = Color.hsv(hue, 0.55f, 0.55f)
        val bottom = Color.hsv((hue + 35f) % 360f, 0.65f, 0.22f)
        Box(
            modifier = modifier.clipToBounds().background(Brush.verticalGradient(listOf(top, bottom))),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(10.dp),
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        game.title.trim().take(1).uppercase(),
                        color = Color(0x55FFFFFF),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    game.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("GAME BOY ADVANCE", color = Color(0x99FFFFFF), fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
