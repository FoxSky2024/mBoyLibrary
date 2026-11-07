package app.gbalibrary.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/** Portada del juego: su ultima captura (pixel art sin suavizar) o un degradado con el titulo. */
@Composable
fun CoverImage(game: Game, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, game.id, game.coverStamp) {
        value = withContext(Dispatchers.IO) { Covers.load(ctx, game.id) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = game.title,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
        )
    } else {
        val hue = (game.id.hashCode().absoluteValue % 360).toFloat()
        Box(
            modifier = modifier
                .clipToBounds()
                .background(Color.hsv(hue, 0.45f, 0.38f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize().padding(10.dp),
            ) {
                Box(Modifier.weight(1f))
                Text(
                    game.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.code.isNotBlank()) {
                    Text(game.code, style = MaterialTheme.typography.labelSmall, color = Color(0xCCFFFFFF))
                }
                Box(Modifier.weight(1f))
            }
        }
    }
}
