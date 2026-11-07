@file:OptIn(ExperimentalMaterial3Api::class)

package app.gbalibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Game
import app.gbalibrary.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Selectores de archivos que vive en MainActivity. */
class LibraryActions(
    val addFolder: () -> Unit,
    val addFiles: () -> Unit,
    val pickCover: (String) -> Unit,
    val pickGameBackground: (String) -> Unit,
    val pickAvatar: () -> Unit,
    val pickLibraryBackground: () -> Unit,
    val pickMusic: () -> Unit,
    val pickMusicFolder: () -> Unit,
)

@Composable
fun LibraryApp(vm: LibraryViewModel, actions: LibraryActions, onPlay: (Game) -> Unit) {
    val ctx = LocalContext.current
    val all by vm.games.collectAsState()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val detail = all.firstOrNull { it.id == detailId }

    BackHandler(enabled = detail != null || showSettings) {
        if (showSettings) showSettings = false else detailId = null
    }

    LaunchedEffect(
        Settings.musicOn.value, Settings.musicUri.value, Settings.musicFolder.value,
        Settings.musicShuffle.value, Settings.musicMuted.value, Settings.musicVolume.floatValue,
    ) {
        Music.sync(ctx)
    }

    // .zip/.7z: abrir el juego recien descomprimido y selector si trae varios .gba
    val playReq by vm.playRequest.collectAsState()
    LaunchedEffect(playReq) {
        playReq?.let { vm.playRequest.value = null; onPlay(it) }
    }
    val pick by vm.archivePick.collectAsState()
    pick?.let { pk ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.chooseEntry(pk, null) },
            title = { Text("Elige el juego de ${pk.name}", maxLines = 2) },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    pk.entries.forEach { e ->
                        Text(
                            e.display,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth().clickable { vm.chooseEntry(pk, e) }.padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.chooseEntry(pk, null) }) { Text("Cancelar") } },
        )
    }

    Box(Modifier.fillMaxSize()) {
        LibraryBackground()
        when {
            showSettings -> SettingsScreen(vm, actions, onBack = { showSettings = false })
            detail != null -> GameDetail(
                game = detail,
                vm = vm,
                actions = actions,
                onBack = { Sfx.click(); detailId = null },
                onPlay = { Sfx.enter(); onPlay(detail) },
            )
            else -> LibraryHome(
                vm = vm,
                actions = actions,
                onOpen = { Sfx.click(); detailId = it.id },
                onPlay = { Sfx.enter(); onPlay(it) },
                onSettings = { Sfx.click(); showSettings = true },
            )
        }
        // Vinilo abajo a la derecha (biblioteca y ajustes; nunca dentro del juego)
        VinylOverlay(Modifier.align(Alignment.BottomEnd))
    }
}

/** Fondo de la biblioteca: degradado del tema, imagen (con blur opcional) o color solido. */
@Composable
fun LibraryBackground() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    when (Settings.bgMode.intValue) {
        2 -> Box(Modifier.fillMaxSize().background(Color(Settings.bgColor.intValue)))
        1 -> {
            val blur = Settings.bgBlur.value
            val bmp by produceState<ImageBitmap?>(null, Settings.bgStamp.intValue, blur) {
                value = withContext(Dispatchers.IO) {
                    val f = Covers.libraryBgFile(ctx)
                    if (blur) Covers.loadBlurred(f) else Covers.load(f)
                }
            }
            Box(Modifier.fillMaxSize().background(p.bg))
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.5f)))
        }
        else -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg, p.panel))))
    }
}

@Composable
private fun LibraryHome(
    vm: LibraryViewModel,
    actions: LibraryActions,
    onOpen: (Game) -> Unit,
    onPlay: (Game) -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val all by vm.games.collectAsState()
    val games by vm.visible.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()
    val category by vm.category.collectAsState()
    val collections by vm.collections.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val message by vm.message.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                query = query,
                onQuery = { vm.query.value = it },
                sort = sort,
                onSort = { vm.sort.value = it },
                actions = actions,
                onRescan = { vm.rescan() },
                onSettings = onSettings,
            )
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = p.accent)

            Row(Modifier.fillMaxSize()) {
                SidePanel(all, collections, category) { vm.category.value = it }
                if (all.isEmpty()) {
                    EmptyLibrary(actions)
                } else {
                    GameGrid(
                        games = games,
                        all = all,
                        showFeatured = category == CAT_ALL && query.isBlank(),
                        onOpen = onOpen,
                        onPlay = onPlay,
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

@Composable
private fun TopBar(
    query: String,
    onQuery: (String) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    actions: LibraryActions,
    onRescan: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    var sortMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(p.panel.copy(alpha = 0.96f)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("◆", color = p.accent, fontSize = 22.sp)
        Text(Settings.appName.value, color = p.text, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)

        Box(
            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(p.bg.copy(alpha = 0.8f)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Buscar juegos...", color = p.dim, fontSize = 14.sp)
                        inner()
                    }
                },
            )
        }

        Box {
            Text("Orden: ${sort.label} ▾", color = p.text, fontSize = 13.sp, modifier = Modifier.clickable { sortMenu = true }.padding(6.dp))
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortMode.entries.forEach { m ->
                    DropdownMenuItem(text = { Text(m.label) }, onClick = { onSort(m); sortMenu = false })
                }
            }
        }

        Box {
            Text("＋", color = p.accent, fontSize = 24.sp, modifier = Modifier.clickable { Sfx.click(); addMenu = true }.padding(horizontal = 8.dp))
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(text = { Text("Agregar carpeta de ROMs") }, onClick = { addMenu = false; actions.addFolder() })
                DropdownMenuItem(text = { Text("Agregar archivos .gba") }, onClick = { addMenu = false; actions.addFiles() })
                DropdownMenuItem(text = { Text("Volver a escanear carpetas") }, onClick = { addMenu = false; onRescan() })
            }
        }

        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onSettings).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(Settings.profileName.value, color = p.text, fontSize = 13.sp, maxLines = 1)
            Avatar(36)
            Text("⚙", color = p.dim, fontSize = 18.sp)
        }
    }
}

@Composable
fun Avatar(sizeDp: Int) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, Settings.avatarStamp.intValue) {
        value = withContext(Dispatchers.IO) { Covers.load(Covers.avatarFile(ctx)) }
    }
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(p.card).border(2.dp, p.accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(Settings.profileName.value.take(1).uppercase(), color = p.text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SidePanel(all: List<Game>, collections: List<String>, selected: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.width(190.dp).fillMaxHeight().background(p.panel.copy(alpha = 0.85f)).verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
    ) {
        Text("BIBLIOTECA", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        SideItem("Todos", all.size, selected == CAT_ALL) { onSelect(CAT_ALL) }
        SideItem("Favoritos", all.count { it.favorite }, selected == CAT_FAV) { onSelect(CAT_FAV) }
        SideItem("Recientes", all.count { it.lastPlayed > 0 }, selected == CAT_RECENT) { onSelect(CAT_RECENT) }
        SideItem("Más jugados", all.count { it.playSeconds > 0 }, selected == CAT_TOP) { onSelect(CAT_TOP) }
        if (collections.isNotEmpty()) {
            Text("COLECCIONES", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
            collections.forEach { c -> SideItem(c, all.count { c in it.collections }, selected == c) { onSelect(c) } }
        }
    }
}

@Composable
private fun SideItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) p.card else Color.Transparent)
            .clickable { Sfx.click(); onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = if (selected) p.accent else p.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count", color = p.dim, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyLibrary(actions: LibraryActions) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Tu biblioteca está vacía", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Agrega la carpeta donde guardas tus ROMs de GBA (solo juegos que poseas).", color = p.dim, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Button(onClick = actions.addFolder, colors = ButtonDefaults.buttonColors(containerColor = p.play)) { Text("Agregar carpeta de ROMs", color = Color.White) }
        Button(onClick = actions.addFiles, colors = ButtonDefaults.buttonColors(containerColor = p.card), modifier = Modifier.padding(top = 8.dp)) { Text("Agregar archivos .gba", color = p.text) }
    }
}

@Composable
private fun GameGrid(
    games: List<Game>,
    all: List<Game>,
    showFeatured: Boolean,
    onOpen: (Game) -> Unit,
    onPlay: (Game) -> Unit,
) {
    val p = LocalPalette.current
    val featured = remember(all) {
        val recent = all.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(8)
        val favs = all.filter { it.favorite && recent.none { r -> r.id == it.id } }
        val list = recent + favs
        if (list.isEmpty()) all.sortedByDescending { it.addedAt }.take(6) else list.take(12)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 136.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showFeatured && featured.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("DESTACADOS", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(featured, key = { it.id }) { g -> FeatureCard(g, onOpen, onPlay) }
                    }
                    Text("TODOS LOS JUEGOS", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
                }
            }
        }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No hay juegos que coincidan.", color = p.dim, modifier = Modifier.padding(24.dp))
            }
        }
        items(games, key = { it.id }) { g -> GameCard(g, onOpen, onPlay) }
    }
}

@Composable
private fun FeatureCard(g: Game, onOpen: (Game) -> Unit, onPlay: (Game) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.width(300.dp).height(150.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(g) },
    ) {
        CoverImage(g, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth(0.72f)) {
            Text(g.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (g.lastPlayed > 0) "${formatPlayTime(g.playSeconds)} · ${formatAgo(g.lastPlayed)}" else "Sin jugar", color = Color(0xCCFFFFFF), fontSize = 11.sp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(p.play).clickable { onPlay(g) }.padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Text("▶ JUGAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        if (g.favorite) Text("★", color = Color(0xFFFFD54F), fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
private fun GameCard(g: Game, onOpen: (Game) -> Unit, onPlay: (Game) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.clickable { onOpen(g) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)).border(1.dp, p.card, RoundedCornerShape(8.dp))) {
            CoverImage(g, Modifier.fillMaxSize())
            if (g.favorite) Text("★", color = Color(0xFFFFD54F), fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(34.dp).clip(CircleShape).background(p.play).clickable { onPlay(g) },
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 14.sp) }
        }
        Text(
            g.title,
            color = p.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(formatPlayTime(g.playSeconds), color = p.dim, fontSize = 11.sp)
        Text("Última vez: ${formatAgo(g.lastPlayed)}", color = p.dim, fontSize = 11.sp)
    }
}
