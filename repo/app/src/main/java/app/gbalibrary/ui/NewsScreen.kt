@file:OptIn(ExperimentalLayoutApi::class)

package app.gbalibrary.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.FeedItem
import app.gbalibrary.data.FeedSource
import app.gbalibrary.data.Feeds
import app.gbalibrary.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WIDGET_TYPES = listOf(
    "stats" to "Estadísticas",
    "last" to "Último jugado",
    "badges" to "Logros",
    "clock" to "Reloj",
    "note" to "Nota rápida",
)

/** Noticias estilo "Comunidad de Steam": fuentes RSS/Atom (cualquier pagina con feed) + widgets. */
@Composable
fun NewsScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by vm.games.collectAsState()
    var selected by remember { mutableIntStateOf(-1) } // -1 = todas
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val posts = remember { mutableStateListOf<FeedItem>() }
    var adding by remember { mutableStateOf(false) }
    var addingWidget by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(selected, Feeds.sources.size, reload) {
        loading = true; error = ""
        val srcs = if (selected in Feeds.sources.indices) listOf(Feeds.sources[selected]) else Feeds.sources.toList()
        val res = withContext(Dispatchers.IO) {
            val list = ArrayList<FeedItem>()
            val fails = ArrayList<String>()
            srcs.forEach { s -> try { list.addAll(fetchSafe(s)) } catch (e: Exception) { fails.add(s.name) } }
            list to fails
        }
        posts.clear(); posts.addAll(res.first)
        if (res.second.isNotEmpty()) error = "No se pudo leer: " + res.second.joinToString()
        else if (srcs.isNotEmpty() && res.first.isEmpty()) error = "No se encontraron publicaciones (¿la página tiene RSS/Atom?)"
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Noticias", onBack) {
            Ic(Icons.Filled.Refresh, tint = p.accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { reload++ }.padding(8.dp))
            Ic(Icons.Filled.Add, tint = p.accent, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { adding = true }.padding(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ---- Widgets ----
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Feeds.widgets.forEachIndexed { i, t -> WidgetCard(t, all, onRemove = { Feeds.removeWidget(i) }) }
                    Box(
                        Modifier.width(120.dp).height(110.dp).clip(RoundedCornerShape(12.dp)).background(p.card).clickable { addingWidget = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Ic(Icons.Filled.Add, size = 28.dp, tint = p.accent)
                            Text("Widget", color = p.dim, fontSize = 12.sp)
                        }
                    }
                }
            }
            // ---- Fuentes ----
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceChip("Todas", selected == -1, null) { selected = -1 }
                    Feeds.sources.forEachIndexed { i, s -> SourceChip(s.name, selected == i, { Feeds.remove(s); selected = -1 }) { selected = i } }
                }
            }
            if (Feeds.sources.isEmpty()) item {
                Text("Pulsa + y pega el enlace de una página (blog, canal, Steam app, repositorio de GitHub, subreddit…). Se leerá su RSS/Atom y verás sus publicaciones aquí.", color = p.dim, fontSize = 13.sp)
            }
            if (loading) item { Text("Cargando…", color = p.accent) }
            if (error.isNotEmpty()) item { Text(error, color = p.dim, fontSize = 12.sp) }
            items(posts.toList()) { post -> PostCard(post) { link -> if (link.isNotBlank()) try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { } } }
        }
    }

    if (adding) AddSourceDialog(onClose = { adding = false }) { n, u -> Feeds.add(n, u); selected = -1; adding = false }
    if (addingWidget) {
        AlertDialog(
            onDismissRequest = { addingWidget = false },
            title = { Text("Agregar widget") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { WIDGET_TYPES.forEach { (k, n) -> AppButton(n, { Feeds.addWidget(k); addingWidget = false }, Modifier.fillMaxWidth()) } } },
            confirmButton = { TextButton(onClick = { addingWidget = false }) { Text("Cerrar") } },
        )
    }
}

private fun fetchSafe(s: FeedSource): List<FeedItem> = Feeds.fetch(s)

@Composable
private fun SourceChip(text: String, sel: Boolean, onRemove: (() -> Unit)?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (sel) p.accent else p.card).clickable(onClick = onClick).padding(start = 12.dp, end = if (onRemove != null && sel) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = if (sel) p.onAccent else p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (onRemove != null && sel) Ic(Icons.Filled.Close, size = 18.dp, tint = p.onAccent, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onRemove).padding(4.dp))
    }
}

@Composable
private fun PostCard(it: FeedItem, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, it.image) {
        value = if (it.image.isBlank()) null else withContext(Dispatchers.IO) { Feeds.loadImage(it.image)?.asImageBitmap() }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).opacityPanel(p.panel, RoundedCornerShape(10.dp)).clickable { onOpen(it.link) }.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        img?.let { Image(it, null, Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(it.source.uppercase() + if (it.date.isNotBlank()) "  ·  ${it.date}" else "", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(it.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (it.summary.isNotBlank()) Text(it.summary, color = p.dim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AddSourceDialog(onClose: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Agregar fuente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("Enlace de la página") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Funciona con páginas que tengan RSS/Atom, tiendas de Steam (store.steampowered.com/app/ID), GitHub (releases) y subreddits.", fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onAdd(name, url) }) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
}

@Composable
private fun WidgetCard(type: String, all: List<app.gbalibrary.data.Game>, onRemove: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(190.dp).height(110.dp).clip(RoundedCornerShape(12.dp)).background(p.card).clickable { }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(WIDGET_TYPES.firstOrNull { it.first == type }?.second ?: type, color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Ic(Icons.Filled.Close, size = 16.dp, tint = p.dim, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRemove))
        }
        when (type) {
            "stats" -> {
                Text("${all.size} juegos", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(formatPlayTime(all.sumOf { it.playSeconds }) + " jugadas", color = p.accent, fontSize = 13.sp)
            }
            "last" -> {
                val g = all.filter { it.lastPlayed > 0 }.maxByOrNull { it.lastPlayed }
                Text(g?.title ?: "Sin jugar aún", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (g != null) Text(formatAgo(g.lastPlayed), color = p.dim, fontSize = 12.sp)
            }
            "badges" -> {
                Text("${Profile.unlocked.size} / ${AllBadges.size}", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(if (Profile.platinumDone) "Platino conseguido" else "logros desbloqueados", color = p.accent, fontSize = 12.sp)
            }
            "clock" -> {
                val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
                Text(java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(now)), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(now)), color = p.dim, fontSize = 12.sp)
            }
            "note" -> {
                var t by remember { mutableStateOf(Feeds.quickNote) }
                androidx.compose.foundation.text.BasicTextField(
                    t, { t = it.take(160); Feeds.quickNote = t },
                    textStyle = androidx.compose.ui.text.TextStyle(color = p.text, fontSize = 13.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
