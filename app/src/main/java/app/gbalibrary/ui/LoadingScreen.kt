package app.gbalibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import app.gbalibrary.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Marca de que la pantalla de carga del arranque de la app ya se mostro en este proceso. */
object Boot { var shown = false }

/**
 * Pantalla de carga: negra, con la mascota pequena abajo a la izquierda corriendo sobre una barra fina.
 * [durationMs] = duracion; [preload] se ejecuta en segundo plano mientras tanto (precarga de objetos).
 */
@Composable
fun LoadingScreen(durationMs: Int, onDone: () -> Unit, onCancel: (() -> Unit)? = null, preload: (suspend () -> Unit)? = null) {
    val progress = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    var pose by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (preload != null) launch(Dispatchers.IO) { runCatching { preload() } }
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        done()
    }
    LaunchedEffect(Unit) { while (true) { delay(200); pose = !pose } }
    if (onCancel != null) BackHandler(onBack = onCancel)

    val barW = 170.dp
    val spriteW = 30.dp
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Box(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 30.dp).width(barW)) {
            // pista y relleno
            Box(Modifier.align(Alignment.BottomStart).width(barW).height(2.dp).background(Color.White.copy(alpha = 0.3f)))
            Box(Modifier.align(Alignment.BottomStart).width(barW * progress.value).height(2.dp).background(Color.White))
            // mascota sobre la barra, avanza con el progreso
            Image(
                painterResource(if (pose) R.drawable.load_b else R.drawable.load_a), null,
                Modifier.align(Alignment.BottomStart).offset(x = (barW - spriteW) * progress.value, y = (-5).dp).size(spriteW, 22.dp),
                contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
            )
        }
    }
}
