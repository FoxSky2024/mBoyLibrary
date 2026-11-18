package app.gbalibrary.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR negro sobre blanco (siempre, para que cualquier camara lo lea sin importar el tema). */
@Composable
fun QrImage(text: String, sizeDp: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        try {
            val hints = mapOf<EncodeHintType, Any>(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            null
        }
    }
    val m = matrix
    if (m == null) {
        Text("No se pudo generar el QR (contenido demasiado largo)", color = Color.Red, fontSize = 12.sp, modifier = modifier)
        return
    }
    Canvas(modifier.size(sizeDp).background(Color.White)) {
        val n = m.width
        val cell = size.width / n
        for (y in 0 until n) for (x in 0 until n) {
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

fun shareText(ctx: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    try { ctx.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

@Composable
fun QrDialog(title: String, hint: String, link: String, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = p.text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            QrImage(link, 240.dp, Modifier.clip(RoundedCornerShape(8.dp)))
            Text(hint, color = p.dim, fontSize = 12.sp, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton("Enlace", { shareText(ctx, link) }, Modifier.weight(1f), icon = Icons.Filled.Share)
                AppButton("Cerrar", onClose, Modifier.weight(1f), kind = BtnKind.PRIMARY)
            }
        }
    }
}
