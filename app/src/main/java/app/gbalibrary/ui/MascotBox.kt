package app.gbalibrary.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.gbalibrary.R

/** Mascota en su caja (res/drawable-nodpi/mascot_box.png) con un suave balanceo. Usada en el aviso de permiso de almacenamiento. */
@Composable
fun MascotBox(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "mascot")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    Image(
        painterResource(R.drawable.mascot_box), null,
        modifier.height(120.dp).graphicsLayer { translationY = -6f * bob * density; rotationZ = (bob - 0.5f) * 3f },
        contentScale = ContentScale.Fit,
    )
}
