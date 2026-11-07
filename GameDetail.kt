@file:OptIn(ExperimentalMaterial3Api::class)

package app.gbalibrary.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ficha del juego estilo Steam: portada grande, JUGAR, favorito, datos, nota y personalizacion. */
@Composable
fun GameDetail(game: Game, vm: LibraryViewModel, actions: LibraryActions, onBack: () -> Unit, onPlay: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val collections by vm.collections.collectAsState()
    var note by remember(game.id) { mutableStateOf(game.note) }
    var title by remember(game.id) { mutableStateOf(game.title) }
    var newCollection by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }

    val art by produceState<ImageBitmap?>(null, game.id, game.coverStamp, game.hasBackground) {
        value = if (game.hasBackground) withContext(Dispatchers.IO) { Covers.load(Covers.backgroundFile(ctx, game.id)) } else null
    }

    Box(Modifier.fillMaxSize()) {
        art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = if (art != null) 0.78f else 0.92f)))

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(52.dp).background(p.panel.copy(alpha = 0.95f)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("← Biblioteca", color = p.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
                Text(Settings_appName(), color = p.dim, fontSize = 13.sp)
            }

            Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)).border(2.dp, p.card, RoundedCornerShape(10.dp))) {
                        CoverImage(game, Modifier.fillMaxSize())
                    }
                    OutlinedButton(onClick = { actions.pickCover(game.id) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir portada", fontSize = 12.sp) }
                    if (game.hasCover) TextButton(onClick = { vm.clearCover(game.id) }, modifier = Modifier.fillMaxWidth()) { Text("Quitar portada", fontSize = 12.sp) }
                    OutlinedButton(onClick = { actions.pickGameBackground(game.id) }, modifier = Modifier.fillMaxWidth()) { Text("Elegir fondo", fontSize = 12.sp) }
                    if (game.hasBackground) TextButton(onClick = { vm.clearGameBackground(game.id) }, modifier = Modifier.fillMaxWidth()) { Text("Quitar fondo", fontSize = 12.sp) }
                }

                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(game.title, color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onPlay,
                            colors = ButtonDefaults.buttonColors(containerColor = p.play),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(56.dp).width(230.dp),
                        ) { Text("▶  JUGAR", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black) }
                        OutlinedButton(onClick = { vm.toggleFavorite(game.id) }, modifier = Modifier.height(56.dp)) {
                            Text(if (game.favorite) "★ Favorito" else "☆ Favorito", fontSize = 15.sp)
                        }
                    }

                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.panel.copy(alpha = 0.85f)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InfoRow("Tiempo jugado", formatPlayTime(game.playSeconds))
                        InfoRow("Última vez", formatAgo(game.lastPlayed))
                        InfoRow("Tamaño", formatSize(game.sizeBytes))
                        InfoRow("Código del juego", game.code.ifBlank { "—" })
                        InfoRow("Archivo", game.fileName)
                    }

                    Text("DESCRIPCIÓN", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Juego de Game Boy Advance" +
                            (if (game.headerTitle.isNotBlank()) " · título interno \"${game.headerTitle}\"" else "") +
                            (if (game.code.isNotBlank()) " · código ${game.code}" else "") + ".",
                        color = p.text,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Mi nota") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    OutlinedButton(onClick = { vm.setNote(game.id, note) }) { Text("Guardar nota") }

                    Text("NOMBRE", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.rename(game.id, title) }) { Text("Renombrar") }
                    }

                    Text("COLECCIONES", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        collections.forEach { c ->
                            val on = c in game.collections
                            FilterChip(selected = on, onClick = { vm.setInCollection(game.id, c, !on) }, label = { Text(c) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCollection,
                            onValueChange = { newCollection = it },
                            label = { Text("Nueva colección") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = {
                            if (newCollection.isNotBlank()) {
                                vm.setInCollection(game.id, newCollection, true)
                                newCollection = ""
                            }
                        }) { Text("Agregar") }
                    }

                    TextButton(onClick = { confirmRemove = true }) { Text("Quitar de la biblioteca", color = Color(0xFFE57373)) }
                    Box(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("¿Quitar de la biblioteca?") },
            text = { Text("No se borra el archivo de la ROM, solo se quita de la lista (tus partidas guardadas se conservan).") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.remove(game.id); onBack() }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = p.dim, modifier = Modifier.width(150.dp), fontSize = 13.sp)
        Text(value, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

private fun Settings_appName(): String = app.gbalibrary.data.Settings.appName.value
