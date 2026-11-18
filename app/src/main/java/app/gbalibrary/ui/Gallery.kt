package app.gbalibrary.ui

import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.Icons
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.gbalibrary.data.Settings
import app.gbalibrary.data.Shots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ThumbCache {
    val cache = object : LruCache<String, ImageBitmap>(48) {}
}

/** Carga una captura reducida (miniatura o vista grande) sin bloquear la UI. */
@Composable
private fun rememberShotBitmap(shot: Shots.Shot, maxSide: Int): ImageBitmap? {
    val key = "${shot.file.path}@$maxSide@${shot.file.lastModified()}"
    val bmp by produceState<ImageBitmap?>(ThumbCache.cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(shot.file.path, bounds)
                    var s = 1
                    while (bounds.outWidth / (s * 2) >= maxSide) s *= 2
                    val o = BitmapFactory.Options().apply { inSampleSize = s }
                    BitmapFactory.decodeFile(shot.file.path, o)?.asImageBitmap()?.also { ThumbCache.cache.put(key, it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    return bmp
}

@Composable
private fun ShotThumb(shot: Shots.Shot, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bmp = rememberShotBitmap(shot, 320)
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(p.card).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

private fun share(ctx: Context, shot: Shots.Shot) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", shot.file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Compartir captura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(ctx, "No se pudo compartir", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Visor a pantalla completa: anterior / siguiente, compartir y borrar. */
@Composable
fun ShotViewer(shots: List<Shots.Shot>, start: Int, onClose: () -> Unit, onDelete: (Shots.Shot) -> Unit) {
    val ctx = LocalContext.current
    var idx by remember { mutableIntStateOf(start) }
    var confirm by remember { mutableStateOf(false) }
    if (shots.isEmpty()) { onClose(); return }
    if (idx > shots.lastIndex) idx = shots.lastIndex
    val shot = shots[idx]
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xF2000000)), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(shot.gameName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatDate(shot.time)}  ·  ${idx + 1}/${shots.size}", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                }
                TextButton(onClick = onClose) { Text("Cerrar", color = Color.White) }
            }
            val bmp = rememberShotBitmap(shot, 1600)
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
            }
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (idx > 0) idx-- }, enabled = idx > 0) { Ic(Icons.Filled.ChevronLeft, tint = p.accent) }
                Button(onClick = { share(ctx, shot) }) { Text("Compartir") }
                OutlinedButton(onClick = { confirm = true }) { Text("Borrar", color = Color(0xFFFF8A80)) }
                OutlinedButton(onClick = { if (idx < shots.lastIndex) idx++ }, enabled = idx < shots.lastIndex) { Ic(Icons.Filled.ChevronRight, tint = p.accent) }
            }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("¿Borrar esta captura?") },
                text = { Text("Se elimina el archivo del teléfono.") },
                confirmButton = { TextButton(onClick = { confirm = false; onDelete(shot) }) { Text("Borrar") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
            )
        }
    }
}

/** Galeria de un juego (fila de miniaturas) para el detalle. */
@Composable
fun GameGallerySection(gameId: String) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var stamp by remember { mutableIntStateOf(0) }
    val shots by produceState(emptyList<Shots.Shot>(), gameId, stamp) {
        value = withContext(Dispatchers.IO) { Shots.list(ctx, gameId) }
    }
    var open by remember { mutableStateOf<Int?>(null) }
    Text("GALERÍA${if (shots.isNotEmpty()) " (${shots.size})" else ""}", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    if (shots.isEmpty()) {
        Text("Aún no hay capturas. Tómalas desde el menú de pausa > Captura de pantalla.", color = p.dim, fontSize = 12.sp)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shots.size) { i -> ShotThumb(shots[i], Modifier.width(150.dp).height(100.dp)) { open = i } }
        }
    }
    open?.let { start ->
        ShotViewer(shots, start, onClose = { open = null }, onDelete = { Shots.delete(it); stamp++ })
    }
}

/** Galeria global (todos los juegos, por fecha). */
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var stamp by remember { mutableIntStateOf(0) }
    val shots by produceState(emptyList<Shots.Shot>(), stamp) { value = withContext(Dispatchers.IO) { Shots.list(ctx) } }
    var open by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(p.panel.copy(alpha = Settings.boxOpacity.floatValue)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("← Volver", color = p.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
            Text("Galería de capturas (${shots.size})", color = p.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (shots.isEmpty()) {
            Text("Todavía no hay capturas.", color = p.dim, modifier = Modifier.padding(24.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shots.size) { i ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ShotThumb(shots[i], Modifier.fillMaxWidth().height(100.dp)) { open = i }
                        Text(shots[i].gameName, color = p.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDate(shots[i].time), color = p.dim, fontSize = 10.sp)
                    }
                }
            }
        }
    }
    open?.let { start -> ShotViewer(shots, start, onClose = { open = null }, onDelete = { Shots.delete(it); stamp++ }) }
}
