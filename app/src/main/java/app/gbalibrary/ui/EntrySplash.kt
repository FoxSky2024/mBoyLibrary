package app.gbalibrary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Settings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animacion de entrada (~750 ms): fade + zoom suave del logo y el nombre del juego. El juego ya se
 * esta cargando por detras, asi que no retrasa nada. Solo anima alpha y escala (graphicsLayer).
 */
@Composable
fun EntrySplash(gameTitle: String, onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    val s = remember { Animatable(0.82f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { s.animateTo(1.04f, tween(750, easing = FastOutSlowInEasing)) }
            a.animateTo(1f, tween(220))
            delay(330)
            a.animateTo(0f, tween(200))
        }
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (a.value > 0.6f) 1f else a.value / 0.6f * 1f }
            .background(Color(0xFF0B0F14))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("◆", color = Color(0xFF66C0F4), fontSize = 44.sp)
            Text(Settings.appName.value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                gameTitle, color = Color(0xFFB0BEC5), fontSize = 15.sp, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).widthIn(max = 320.dp),
            )
        }
    }
}
