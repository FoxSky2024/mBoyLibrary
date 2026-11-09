package app.gbalibrary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Game
import kotlinx.coroutines.launch

/** Caratula en 3D: arrastra para girarla (horizontal = vuelta completa, vertical = inclinar); al soltar se acomoda. */
@Composable
fun Cover3D(game: Game, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val rotY = remember { Animatable(-18f) }
    val rotX = remember { Animatable(0f) }
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(Ds.RadiusM)
    Box(
        modifier.aspectRatio(3f / 4f).pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    scope.launch {
                        val target = Math.round(rotY.value / 180f) * 180f
                        launch { rotY.animateTo(target + if (target % 360f == 0f) -12f else 12f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                        rotX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
                    }
                },
            ) { change, drag ->
                change.consume()
                scope.launch {
                    rotY.snapTo(rotY.value + drag.x * 0.6f)
                    rotX.snapTo((rotX.value - drag.y * 0.3f).coerceIn(-25f, 25f))
                }
            }
        },
    ) {
        val back = ((rotY.value % 360f) + 360f) % 360f
        val showBack = back in 90f..270f
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { rotationY = rotY.value; rotationX = rotX.value; cameraDistance = 14f * density }
                .shadow(14.dp, shape)
                .clip(shape).border(2.dp, p.card, shape),
        ) {
            if (!showBack) {
                CoverImage(game, Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }
                        .background(Brush.verticalGradient(listOf(p.card, p.panel))).padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(game.title, color = p.text, fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 4)
                    Text(systemOf(game), color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(formatPlayTime(game.playSeconds), color = p.dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
