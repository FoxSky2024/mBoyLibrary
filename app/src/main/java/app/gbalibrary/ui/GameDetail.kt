package app.gbalibrary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gbalibrary.data.Game

/** Pagina de un juego, al estilo de la ficha de Steam. */
@Composable
fun GameDetail(
    game: Game,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    val collections by vm.collections.collectAsState()
    var showRename by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text("← Biblioteca") }

            CoverImage(game, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).aspectRatio(1.5f))

            Text(game.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = SteamGreenLight, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                ) { Text("▶  JUGAR", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { vm.toggleFavorite(game.id) }) {
                    Text(if (game.favorite) "★ Favorito" else "☆ Favorito")
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SteamPanel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("TIEMPO DE JUEGO", formatPlayTime(game.playSeconds))
                    InfoRow("ULTIMA VEZ", formatDate(game.lastPlayed))
                    InfoRow("AGREGADO", formatDate(game.addedAt))
                    InfoRow("TAMANO", formatSize(game.sizeBytes))
                    if (game.code.isNotBlank()) InfoRow("CODIGO", game.code)
                    InfoRow("ARCHIVO", game.fileName)
                    if (game.collections.isNotEmpty()) {
                        InfoRow("COLECCIONES", game.collections.sorted().joinToString(", "))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showCollections = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp),
                ) { Text("Colecciones") }
                OutlinedButton(
                    onClick = { showRename = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp),
                ) { Text("Renombrar") }
                OutlinedButton(
                    onClick = { showRemove = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(4.dp),
                ) { Text("Quitar") }
            }
        }
    }

    if (showRename) {
        var text by remember { mutableStateOf(game.title) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Renombrar") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(game.id, text)
                    showRename = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancelar") } },
        )
    }

    if (showCollections) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCollections = false },
            title = { Text("Colecciones") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    collections.forEach { name ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.setInCollection(game.id, name, name !in game.collections) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = name in game.collections,
                                onCheckedChange = { vm.setInCollection(game.id, name, it) },
                            )
                            Text(name)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Nueva coleccion") },
                        )
                        TextButton(onClick = {
                            if (newName.isNotBlank()) {
                                vm.setInCollection(game.id, newName, true)
                                newName = ""
                            }
                        }) { Text("Crear") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCollections = false }) { Text("Listo") } },
        )
    }

    if (showRemove) {
        AlertDialog(
            onDismissRequest = { showRemove = false },
            title = { Text("Quitar de la biblioteca") },
            text = { Text("Se quita solo la entrada de la biblioteca. El archivo de la ROM no se borra.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemove = false
                    onBack()
                    vm.remove(game.id)
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { showRemove = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SteamAccent)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
