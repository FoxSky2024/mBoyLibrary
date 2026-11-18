package app.gbalibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Game
import app.gbalibrary.emu.ExternalLauncher

/** "Booting GameCube - Starting in Dolphin": pantalla de arranque con los colores de la consola. */
@Composable
fun BootScreen(game: Game, onDone: () -> Unit, onCancel: () -> Unit) {
    val c = consoleOf(game)
    val ctx = LocalContext.current
    val engine = if (c.builtIn) c.engineLabel
    else (ExternalLauncher.resolve(ctx, c)?.label ?: c.emulators.firstOrNull()?.label ?: "—")
    val progress = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)

    LaunchedEffect(game.id) {
        progress.animateTo(1f, tween(1400, easing = LinearEasing))
        done()
    }
    BackHandler(onBack = onCancel)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(c.bgTop, c.accent.copy(alpha = 0.55f), c.bgBottom)))
            .pointerInput(Unit) { detectTapGestures { } }, // traga los toques: nada por debajo reacciona
    ) {
        Row(Modifier.align(Alignment.Center).padding(40.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(game, Modifier.fillMaxHeight(0.62f).aspectRatio(c.boxRatio).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(44.dp))
            Column {
                Text("BOOTING ${c.name.uppercase()}", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 3.sp)
                Text(c.name, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black)
                Text(game.title, color = Color.White, fontSize = 20.sp, maxLines = 2)
                Text("Starting in $engine", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { progress.value }, color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f), modifier = Modifier.width(240.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "CANCELAR", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCancel() }.padding(vertical = 6.dp),
                )
            }
        }
    }
}
