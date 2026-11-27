package app.gbalibrary.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sobre de cartas con acabado de foil: bordes sellados con relieve, degradado metalico, destello holografico
 * que cruza, medallon con la huella, cinta con el nombre y un rombo por cada objeto que contiene.
 */
@Composable
fun PackArt(name: String, color: Color, count: Int, modifier: Modifier = Modifier, titleSp: Float = 14f) {
    val tr = rememberInfiniteTransition(label = "packart")
    val shine by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "shine")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val crimp = h * 0.085f
            val dark = lerp(color, Color.Black, 0.6f)
            val light = lerp(color, Color.White, 0.4f)
            // silueta del sobre con los bordes superior e inferior en dientes de sierra
            val teeth = 16
            val st = w / teeth
            val body = Path().apply {
                moveTo(0f, crimp * 0.25f)
                for (i in 0 until teeth) { lineTo(st * (i + 0.5f), if (i % 2 == 0) 0f else crimp * 0.25f); lineTo(st * (i + 1), crimp * 0.25f) }
                lineTo(w, h - crimp * 0.25f)
                for (i in teeth - 1 downTo 0) { lineTo(st * (i + 0.5f), if (i % 2 == 0) h else h - crimp * 0.25f); lineTo(st * i, h - crimp * 0.25f) }
                close()
            }
            drawPath(body, Brush.horizontalGradient(listOf(dark, color, light, color, dark)))
            clipPath(body) {
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.4f))))
                // rayos detras del medallon
                val ce = Offset(w / 2f, h * 0.42f)
                for (i in 0 until 14) {
                    val a0 = Math.toRadians(i * 360.0 / 14); val a1 = Math.toRadians(i * 360.0 / 14 + 11.0)
                    val r = h
                    val ray = Path().apply {
                        moveTo(ce.x, ce.y)
                        lineTo(ce.x + (r * Math.cos(a0)).toFloat(), ce.y + (r * Math.sin(a0)).toFloat())
                        lineTo(ce.x + (r * Math.cos(a1)).toFloat(), ce.y + (r * Math.sin(a1)).toFloat()); close()
                    }
                    drawPath(ray, Color.White.copy(alpha = 0.10f))
                }
                // bandas selladas con relieve vertical
                for (band in listOf(0f, h - crimp)) {
                    var x = 0f; var odd = false
                    while (x < w) { drawRect(if (odd) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.2f), Offset(x, band), Size(st * 0.5f, crimp)); x += st * 0.5f; odd = !odd }
                }
                drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, crimp), Offset(w, crimp), 2f)
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, h - crimp), Offset(w, h - crimp), 2f)
                // medallon
                drawCircle(Brush.radialGradient(listOf(light, color, dark), ce, w * 0.3f), w * 0.29f, ce)
                drawCircle(Color.White.copy(alpha = 0.9f), w * 0.29f, ce, style = Stroke(w * 0.022f))
                drawCircle(Color.White.copy(alpha = 0.4f), w * 0.23f, ce, style = Stroke(w * 0.01f))
                // cinta del nombre
                val ry = h * 0.69f; val rh = h * 0.2f
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.75f)), ry, ry + rh), Offset(0f, ry), Size(w, rh))
                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, ry), Offset(w, ry), 2f)
                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, ry + rh), Offset(w, ry + rh), 2f)
                // rombos: uno por objeto del sobre
                val n = count.coerceIn(1, 6)
                val d = w * 0.035f
                for (i in 0 until n) {
                    val x = w / 2f + (i - (n - 1) / 2f) * d * 3.2f; val y = h * 0.935f
                    drawPath(Path().apply { moveTo(x, y - d); lineTo(x + d, y); lineTo(x, y + d); lineTo(x - d, y); close() }, Color.White.copy(alpha = 0.9f))
                }
                // destello holografico que cruza
                val sx = w * (shine * 2.2f - 0.6f)
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color(0xFFB388FF).copy(alpha = 0.2f), Color.Transparent), Offset(sx - w * 0.25f, 0f), Offset(sx + w * 0.25f, h * 0.6f)))
            }
            drawPath(body, Color.White.copy(alpha = 0.6f), style = Stroke(2.5f))
        }
        Box(Modifier.align(androidx.compose.ui.BiasAlignment(0f, -0.16f))) { PawIcon(30.dp, Color.White) }
        Box(Modifier.align(androidx.compose.ui.BiasAlignment(0f, 0.58f)).fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(
                name, color = Color.White, fontWeight = FontWeight.Black, fontSize = titleSp.sp, textAlign = TextAlign.Center, maxLines = 2,
                style = TextStyle(shadow = Shadow(Color.Black, Offset(1f, 2f), 4f)), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
