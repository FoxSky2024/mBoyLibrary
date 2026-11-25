package app.gbalibrary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PawGold = Color.White

/** Icono vectorial de la huella Pawb (sustituye al emoji). */
@Composable
fun PawIcon(size: Dp = 16.dp, tint: Color = PawGold, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = size.toPx()
        fun toe(cx: Float, cy: Float, rx: Float, ry: Float) =
            drawOval(tint, Offset((cx - rx) * w, (cy - ry) * w), Size(rx * 2 * w, ry * 2 * w))
        toe(0.16f, 0.46f, 0.10f, 0.13f)
        toe(0.38f, 0.24f, 0.11f, 0.15f)
        toe(0.62f, 0.24f, 0.11f, 0.15f)
        toe(0.84f, 0.46f, 0.10f, 0.13f)
        // almohadilla
        toe(0.50f, 0.70f, 0.27f, 0.23f)
    }
}

/** Huella + cantidad, en una fila. */
@Composable
fun PawAmount(text: String, color: Color = PawGold, fontSize: TextUnit = 14.sp, iconSize: Dp = 14.dp, bold: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PawIcon(iconSize, color)
        Text(text, color = color, fontSize = fontSize, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
