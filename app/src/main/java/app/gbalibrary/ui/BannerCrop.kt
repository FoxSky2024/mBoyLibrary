package app.gbalibrary.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Matriz 4x5 (brillo -0.5..0.5, saturacion 0..2) compartida por la vista previa y la exportacion. */
private fun adjustMatrix(brightness: Float, saturation: Float): FloatArray {
    val s = saturation; val k = 1f + brightness
    val rw = 0.213f; val gw = 0.715f; val bw = 0.072f
    return floatArrayOf(
        (rw * (1 - s) + s) * k, gw * (1 - s) * k, bw * (1 - s) * k, 0f, 0f,
        rw * (1 - s) * k, (gw * (1 - s) + s) * k, bw * (1 - s) * k, 0f, 0f,
        rw * (1 - s) * k, gw * (1 - s) * k, (bw * (1 - s) + s) * k, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** Editor de imagen del banner: arrastra y pellizca para encuadrar (marco 3:1), brillo y saturacion. Devuelve base64 JPEG 480x160. */
@Composable
fun BannerCropDialog(
    src: Bitmap, onDone: (ByteArray) -> Unit, onCancel: () -> Unit,
    aspect: Float = 3f, outW: Int = 1200, outH: Int = 400, png: Boolean = false, round: Boolean = false,
    title: String = "Recortar y editar",
) {
    val p = LocalPalette.current
    var frame by remember { mutableStateOf(IntSize(1, 1)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) } // desplazamiento extra respecto al centrado
    var bright by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(1f) }
    val img = remember(src) { src.asImageBitmap() }

    // devuelve (escala total, x, y) de la imagen dentro del marco, con limites para que siempre lo cubra
    fun place(fw: Float, fh: Float, z: Float, o: Offset): Triple<Float, Float, Float> {
        val cover = maxOf(fw / src.width, fh / src.height)
        val sc = cover * z
        val iw = src.width * sc; val ih = src.height * sc
        val x = ((fw - iw) / 2f + o.x).coerceIn(minOf(0f, fw - iw), maxOf(0f, fw - iw))
        val y = ((fh - ih) / 2f + o.y).coerceIn(minOf(0f, fh - ih), maxOf(0f, fh - ih))
        return Triple(sc, x, y)
    }

    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.clip16().background(p.panel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = p.text, fontSize = 16.sp)
            Text("Arrastra para mover · pellizca para acercar", color = p.dim, fontSize = 11.sp)
            Canvas(
                (if (aspect >= 2f) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally)).aspectRatio(aspect).clipToBounds().background(if (png) Color(0xFF2A2F3A) else Color.Black)
                    .onSizeChanged { frame = it }
                    .pointerInput(src) {
                        detectTransformGestures { _, pan, g, _ ->
                            val fw = frame.width.toFloat(); val fh = frame.height.toFloat()
                            val nz = (zoom * g).coerceIn(1f, 6f)
                            val (_, x, y) = place(fw, fh, nz, off + pan)
                            val cover = maxOf(fw / src.width, fh / src.height)
                            val base = Offset((fw - src.width * cover * nz) / 2f, (fh - src.height * cover * nz) / 2f)
                            zoom = nz; off = Offset(x - base.x, y - base.y)
                        }
                    },
            ) {
                val (sc, x, y) = place(size.width, size.height, zoom, off)
                withTransform({ translate(x, y); scale(sc, sc, Offset.Zero) }) {
                    drawImage(img, colorFilter = ColorFilter.colorMatrix(ColorMatrix(adjustMatrix(bright, sat))))
                }
                if (round) {
                    // lo que queda fuera del circulo no se guarda
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                    }
                    drawPath(path, Color(0xCC000000))
                }
            }
            Text("Brillo", color = p.dim, fontSize = 11.sp)
            Slider(bright, { bright = it }, valueRange = -0.5f..0.5f)
            Text("Saturación", color = p.dim, fontSize = 11.sp)
            Slider(sat, { sat = it }, valueRange = 0f..2f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Restablecer", color = p.text, fontSize = 13.sp,
                    modifier = Modifier.clip16().background(p.card).clickable0 { zoom = 1f; off = Offset.Zero; bright = 0f; sat = 1f }.padding(horizontal = 14.dp, vertical = 8.dp))
                Text("Cancelar", color = p.text, fontSize = 13.sp,
                    modifier = Modifier.clip16().background(p.card).clickable0 { onCancel() }.padding(horizontal = 14.dp, vertical = 8.dp))
                Text("Aplicar", color = p.onPlay, fontSize = 13.sp,
                    modifier = Modifier.clip16().background(p.play).clickable0 {
                        val fw = frame.width.toFloat().coerceAtLeast(1f); val fh = frame.height.toFloat().coerceAtLeast(1f)
                        val (sc, x, y) = place(fw, fh, zoom, off)
                        val f = outW / fw
                        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        val m = android.graphics.Matrix().apply { postScale(sc, sc); postTranslate(x, y); postScale(f, f) }
                        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(adjustMatrix(bright, sat)))
                        }
                        val cv = android.graphics.Canvas(out)
                        if (round) {
                            val layer = cv.saveLayer(0f, 0f, outW.toFloat(), outH.toFloat(), null)
                            cv.drawBitmap(src, m, paint)
                            val mask = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                            }
                            cv.drawOval(0f, 0f, outW.toFloat(), outH.toFloat(), mask)
                            cv.restoreToCount(layer)
                        } else cv.drawBitmap(src, m, paint)
                        val bos = java.io.ByteArrayOutputStream()
                        if (png) out.compress(Bitmap.CompressFormat.PNG, 100, bos) else out.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                        onDone(bos.toByteArray())
                    }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

private fun Modifier.clip16(): Modifier = this.then(Modifier.clip(RoundedCornerShape(14.dp)))
private fun Modifier.clickable0(onClick: () -> Unit): Modifier = this.then(Modifier.clickable { Sfx.click(); onClick() })

/** Decodifica la imagen elegida a un bitmap manejable (lado mayor <= 1600). */
fun decodeForCrop(ctx: android.content.Context, uri: android.net.Uri): Bitmap? = try {
    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
    var ss = 1
    while (maxOf(opts.outWidth, opts.outHeight) / ss > 1600) ss *= 2
    ctx.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = ss })
    }
} catch (e: Exception) { null }
