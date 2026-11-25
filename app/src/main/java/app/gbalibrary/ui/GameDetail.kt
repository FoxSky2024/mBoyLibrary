@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Covers
import app.gbalibrary.data.DataDir
import java.io.File
import app.gbalibrary.data.Game
import app.gbalibrary.data.SaveType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class DetailPanel { INFO, SAVES, SETTINGS }

private val SAVE_SLOTS = listOf("auto" to "Partida automática", "quick" to "Guardado rápido", "slot1" to "Ranura 1", "slot2" to "Ranura 2", "slot3" to "Ranura 3")

/**
 * Ficha del juego: portada grande, nombre, sistema y tamano; boton grande JUGAR y debajo
 * Continuar / Guardados / Ajustes del juego. Cada panel se muestra debajo, sin encimar texto.
 */
@Composable
fun GameDetail(
    game: Game,
    vm: LibraryViewModel,
    actions: LibraryActions,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlaySlot: (String) -> Unit = { onPlay() },
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val collections by vm.collections.collectAsState()
    var note by remember(game.id) { mutableStateOf(game.note) }
    var title by remember(game.id) { mutableStateOf(game.title) }
    var newCollection by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var panel by remember(game.id) { mutableStateOf(DetailPanel.INFO) }
    val hasAuto = remember(game.id, panel) { File(DataDir.states(ctx), "${game.id}.auto.ss").exists() }

    val art by produceState<ImageBitmap?>(null, game.id, game.coverStamp, game.hasBackground) {
        value = if (game.hasBackground) withContext(Dispatchers.IO) { Covers.load(Covers.backgroundFile(ctx, game.id)) } else null
    }

    val front = rememberBitmap(game, "front")
    val steamGreen = Color(0xFF5BA32B)
    Box(Modifier.fillMaxSize()) {
        art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = if (art != null) 0.9f else 1f)))

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "Ficha del juego", onBack = onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Cabecera tipo Steam: arte desenfocado de fondo, portada a la izquierda y titulo a la derecha
                Box(Modifier.fillMaxWidth().height(200.dp).clipToBounds()) {
                    front?.let { Image(it, null, Modifier.fillMaxSize().blur(28.dp), contentScale = ContentScale.Crop) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), p.bg))))
                    Row(Modifier.fillMaxSize().padding(Ds.Pad), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Cover3D(game, Modifier.width(110.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(game.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ds.GapL)) {
                                SystemBadge(systemOf(game))
                                Text(formatSize(game.sizeBytes), color = Color.White.copy(alpha = 0.7f), fontSize = Ds.LabelSp.sp, maxLines = 1)
                            }
                        }
                    }
                }
                // Barra de juego: boton verde grande + estadisticas
                Row(
                    Modifier.fillMaxWidth().background(p.card).padding(Ds.Pad),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(steamGreen).clickable(onClick = onPlay).padding(horizontal = 26.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("▶  JUGAR", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1) }
                    if (hasAuto) AppButton("Continuar", { onPlaySlot("auto") })
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        StatBlock("TIEMPO JUGADO", formatPlayTime(game.playSeconds))
                        StatBlock("ÚLTIMA VEZ", formatAgo(game.lastPlayed))
                        StatBlock("ABIERTO", "${game.launches}")
                    }
                }
                // Pestañas
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Ds.Pad, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppChip("Resumen", panel == DetailPanel.INFO, { panel = DetailPanel.INFO })
                    AppChip("Guardados", panel == DetailPanel.SAVES, { panel = DetailPanel.SAVES })
                    AppChip("Ajustes", panel == DetailPanel.SETTINGS, { panel = DetailPanel.SETTINGS })
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = Ds.Pad), verticalArrangement = Arrangement.spacedBy(Ds.GapL)) {
                    if (!hasAuto && panel == DetailPanel.INFO) Hint("Aún no hay partida automática: JUGAR empieza desde el principio y se guarda solo al salir.")
                    when (panel) {
                        DetailPanel.INFO -> InfoPanel(game)
                        DetailPanel.SAVES -> SavesPanel(game, onPlaySlot)
                        DetailPanel.SETTINGS -> SettingsPanel(
                            game, vm, actions, collections, note, { note = it }, newCollection, { newCollection = it },
                            onRename = { renaming = true }, onUrl = { urlDialog = true }, onRemove = { confirmRemove = true },
                        )
                    }
                    Box(Modifier.height(24.dp))
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.rename(game.id, title); renaming = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }
    if (urlDialog) {
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("Pegar URL de portada") },
            text = { OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true, label = { Text("https://…/imagen.jpg") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.setCoverFromUrl(game.id, url); urlDialog = false }) { Text("Descargar") } },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancelar") } },
        )
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
private fun InfoPanel(game: Game) {
    val p = LocalPalette.current
    val sys = systemOf(game)
    SectionCard("Información") {
        InfoRow("Sistema", if (game.external.isNotEmpty()) consoleOf(game).name else when (sys) { "GBA" -> "Game Boy Advance"; "GBC" -> "Game Boy Color"; "DS" -> "Nintendo DS"; else -> "Game Boy" })
        InfoRow("Tamaño", formatSize(game.sizeBytes))
        InfoRow("Tiempo jugado", formatPlayTime(game.playSeconds))
        InfoRow("Última vez", formatAgo(game.lastPlayed))
        InfoRow("Veces abierto", game.launches.toString())
        if (game.external.isEmpty()) InfoRow("Guardado detectado", if (game.detSave >= 0) SaveType.label(game.detSave) else "— (se detecta al abrir)")
        if (sys == "GBA") InfoRow("Código del juego", game.code.ifBlank { "—" })
        InfoRow("Archivo", game.fileName)
    }
    if (game.progress >= 0) {
        SectionCard("Progreso") {
            Text("${game.progress}%", color = p.text, fontSize = Ds.BodySp.sp)
        }
    }
    GameGallerySection(game.id)
}

@Composable
private fun SavesPanel(game: Game, onPlaySlot: (String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    SectionCard("Guardados") {
        SAVE_SLOTS.forEach { (slot, name) ->
            val f = File(DataDir.states(ctx), "${game.id}.$slot.ss")
            val exists = f.exists()
            Row(
                Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(Ds.RadiusM)).padding(start = Ds.Pad, top = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Ds.GapL),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = p.text, fontSize = Ds.BodySp.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (exists) formatAgo(f.lastModified()) else "Vacía", color = p.dim, fontSize = Ds.LabelSp.sp, maxLines = 1)
                }
                AppButton("Cargar", { onPlaySlot(slot) }, Modifier.widthIn(min = 110.dp), kind = BtnKind.PRIMARY, enabled = exists)
            }
        }
        Hint("Las ranuras se crean dentro del juego: Pausa > Guardar.")
    }
}

@Composable
private fun SettingsPanel(
    game: Game,
    vm: LibraryViewModel,
    actions: LibraryActions,
    collections: List<String>,
    note: String,
    onNote: (String) -> Unit,
    newCollection: String,
    onNewCollection: (String) -> Unit,
    onRename: () -> Unit,
    onUrl: () -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard("Juego") {
        ButtonFlow {
            AppButton("Favorito", { vm.toggleFavorite(game.id) }, icon = if (game.favorite) Icons.Filled.Star else Icons.Filled.StarBorder)
            AppButton("Renombrar", onRename)
        }
    }
    SectionCard("Portada y fondo") {
        ButtonFlow {
            AppButton("Portada: galería", { actions.pickCover(game.id) })
            AppButton("Portada: pegar URL", onUrl)
            AppButton("Buscar en internet", { vm.fetchCoverFor(game.id) })
            if (game.hasCover) AppButton("Quitar portada", { vm.clearCover(game.id) })
            AppButton("Elegir fondo", { actions.pickGameBackground(game.id) })
            if (game.hasBackground) AppButton("Quitar fondo", { vm.clearGameBackground(game.id) })
        }
    }
    SectionCard("Nota y progreso") {
        OutlinedTextField(value = note, onValueChange = onNote, label = { Text("Mi nota") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        AppButton("Guardar nota", { vm.setNote(game.id, note) })
        var prog by remember(game.id) { mutableStateOf(game.progress.coerceAtLeast(0).toFloat()) }
        SliderRow(
            "Progreso del juego", prog, 0f..100f, if (game.progress < 0) "Sin definir" else "${game.progress}%", onChange = { prog = it },
            onFinished = { vm.setProgress(game.id, prog.toInt()) },
        )
        if (game.progress >= 0) AppButton("Quitar progreso", { vm.setProgress(game.id, -1) })
    }
    if (systemOf(game) == "GBA") {
        SectionCard("Compatibilidad") {
            Subtitle("Tipo de guardado")
            val saveOpts = listOf(
                -1 to "Auto", SaveType.SRAM to "SRAM", SaveType.FLASH512 to "FLASH 64K",
                SaveType.FLASH1M to "FLASH 128K", SaveType.EEPROM to "EEPROM 8K", SaveType.EEPROM512 to "EEPROM 512B",
            )
            ChipRow(saveOpts.map { it.second }, saveOpts.indexOfFirst { it.first == game.saveOverride || (it.first == -1 && game.saveOverride < 0) }.coerceAtLeast(0)) {
                vm.setCompat(game.id, saveOpts[it].first, game.rtcMode)
            }
            Subtitle("Reloj (RTC)")
            ChipRow(listOf("Auto", "Sí", "No"), game.rtcMode) { vm.setCompat(game.id, game.saveOverride, it) }
            Hint("Se aplica la próxima vez que abras el juego. Déjalo en Auto salvo que un hack no guarde.")
        }
    }
    SectionCard("Colecciones") {
        if (collections.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Ds.Gap), verticalArrangement = Arrangement.spacedBy(Ds.Gap)) {
                collections.forEach { c ->
                    val on = c in game.collections
                    AppChip(c, on, { vm.setInCollection(game.id, c, !on) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Ds.Gap), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newCollection, onValueChange = onNewCollection, label = { Text("Nueva colección") }, singleLine = true, modifier = Modifier.weight(1f))
            AppButton("Agregar", {
                if (newCollection.isNotBlank()) {
                    vm.setInCollection(game.id, newCollection, true)
                    onNewCollection("")
                }
            })
        }
    }
    AppButton("Quitar de la biblioteca", onRemove, Modifier.fillMaxWidth(), kind = BtnKind.DANGER)
}

@Composable
private fun StatBlock(label: String, value: String) {
    val p = LocalPalette.current
    Column {
        Text(label, color = p.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = p.dim, modifier = Modifier.width(150.dp), fontSize = Ds.LabelSp.sp)
        Text(value, color = p.text, fontSize = Ds.LabelSp.sp, modifier = Modifier.weight(1f))
    }
}
