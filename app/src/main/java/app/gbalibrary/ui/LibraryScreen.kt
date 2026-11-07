@file:OptIn(ExperimentalMaterial3Api::class)

package app.gbalibrary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gbalibrary.data.Game

@Composable
fun LibraryApp(
    vm: LibraryViewModel,
    onPlay: (Game) -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
) {
    val all by vm.games.collectAsState()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = all.firstOrNull { it.id == detailId }

    BackHandler(enabled = detail != null) { detailId = null }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (detail != null) {
            GameDetail(
                game = detail,
                vm = vm,
                onBack = { detailId = null },
                onPlay = { onPlay(detail) },
            )
        } else {
            LibraryHome(
                vm = vm,
                onOpen = { detailId = it.id },
                onPlay = onPlay,
                onAddFolder = onAddFolder,
                onAddFiles = onAddFiles,
            )
        }
    }
}

@Composable
private fun LibraryHome(
    vm: LibraryViewModel,
    onOpen: (Game) -> Unit,
    onPlay: (Game) -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
) {
    val all by vm.games.collectAsState()
    val games by vm.visible.collectAsState()
    val query by vm.query.collectAsState()
    val sort by vm.sort.collectAsState()
    val category by vm.category.collectAsState()
    val collections by vm.collections.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val message by vm.message.collectAsState()
    val wide = LocalConfiguration.current.screenWidthDp >= 720

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (wide) {
                SidePanel(
                    all = all,
                    collections = collections,
                    selected = category,
                    onSelect = { vm.category.value = it },
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopControls(
                    query = query,
                    onQuery = { vm.query.value = it },
                    sort = sort,
                    onSort = { vm.sort.value = it },
                    onAddFolder = onAddFolder,
                    onAddFiles = onAddFiles,
                    onRescan = { vm.rescan() },
                )
                if (!wide) {
                    CategoryChips(
                        collections = collections,
                        selected = category,
                        onSelect = { vm.category.value = it },
                    )
                }
                if (scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (all.isEmpty()) {
                    EmptyState(onAddFolder = onAddFolder, onAddFiles = onAddFiles)
                } else if (games.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay juegos en esta vista", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(170.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(games, key = { it.id }) { game ->
                            GameTile(
                                game = game,
                                onClick = { onOpen(game) },
                                onPlay = { onPlay(game) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopControls(
    query: String,
    onQuery: (String) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onRescan: () -> Unit,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Buscar en la biblioteca") },
        )
        Box {
            TextButton(onClick = { sortOpen = true }) { Text("${sort.label} ▾") }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            onSort(mode)
                            sortOpen = false
                        },
                    )
                }
            }
        }
        Box {
            Button(onClick = { addOpen = true }) { Text("+") }
            DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Agregar carpeta de ROMs") },
                    onClick = {
                        addOpen = false
                        onAddFolder()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Agregar archivos .gba") },
                    onClick = {
                        addOpen = false
                        onAddFiles()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Volver a escanear carpetas") },
                    onClick = {
                        addOpen = false
                        onRescan()
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryChips(
    collections: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val entries = buildList {
        add(CAT_ALL to "Todos")
        add(CAT_FAV to "Favoritos")
        add(CAT_RECENT to "Recientes")
        collections.forEach { add(it to it) }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries) { (key, label) ->
            FilterChip(
                selected = key == selected,
                onClick = { onSelect(key) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SidePanel(
    all: List<Game>,
    collections: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(SteamPanel)
            .padding(vertical = 16.dp),
    ) {
        Text(
            "BIBLIOTECA",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = SteamAccent,
            fontWeight = FontWeight.Bold,
        )
        CategoryRow("Todos", all.size, selected == CAT_ALL) { onSelect(CAT_ALL) }
        CategoryRow("Favoritos", all.count { it.favorite }, selected == CAT_FAV) { onSelect(CAT_FAV) }
        CategoryRow("Jugados recientemente", all.count { it.lastPlayed > 0 }, selected == CAT_RECENT) {
            onSelect(CAT_RECENT)
        }
        if (collections.isNotEmpty()) {
            Text(
                "COLECCIONES",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = SteamAccent,
                fontWeight = FontWeight.Bold,
            )
            collections.forEach { name ->
                CategoryRow(name, all.count { name in it.collections }, selected == name) { onSelect(name) }
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) SteamCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
private fun GameTile(game: Game, onClick: () -> Unit, onPlay: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SteamCard.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
    ) {
        Box {
            CoverImage(game, Modifier.fillMaxWidth().aspectRatio(1.5f))
            if (game.favorite) {
                Text(
                    "★",
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SteamGreenLight)
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("▶", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                game.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                formatPlayTime(game.playSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun EmptyState(onAddFolder: () -> Unit, onAddFiles: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Tu biblioteca esta vacia",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Agrega la carpeta donde guardas tus ROMs (.gba) o elige archivos sueltos. " +
                "Usa solo copias de juegos que tengas legitimamente.",
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Button(
            onClick = onAddFolder,
            colors = ButtonDefaults.buttonColors(containerColor = SteamGreenLight, contentColor = Color.White),
        ) { Text("Agregar carpeta de ROMs") }
        TextButton(onClick = onAddFiles) { Text("o elegir archivos .gba") }
    }
}
