@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.gbalibrary.ui

import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import app.gbalibrary.input.PadInput
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
    val pickPipVideo: () -> Unit = {},
    val openDataFolder: () -> Unit = {},
    val requestStorage: () -> Unit = {},
    val exportBackup: (Boolean) -> Unit = {},
    val importBackup: () -> Unit = {},
)

@Composable
fun LibraryApp(vm: LibraryViewModel, actions: LibraryActions, onPlay: (Game) -> Unit, onPlaySlot: (Game, String) -> Unit = { g, _ -> onPlay(g) }) {
    val ctx = LocalContext.current
    val all by vm.games.collectAsState()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    var showNews by rememberSaveable { mutableStateOf(false) }
    val detail = all.firstOrNull { it.id == detailId }

    // Modo de navegacion con mando segun la pantalla visible.
    val navMode = when {
        showSettings || showGallery || showProfile || showNews -> MenuNav.Mode.FOCUS
        detail != null -> MenuNav.Mode.DETAIL
        else -> MenuNav.Mode.HOME
    }
    SideEffect { MenuNav.mode = navMode }
    // En Detalle: A = jugar, B = volver.
    LaunchedEffect(navMode) {
        if (navMode == MenuNav.Mode.DETAIL) {
            MenuNav.events.collect { k ->
                val g = all.firstOrNull { it.id == detailId }
                when (k) {
                    NavKey.OK -> if (g != null) { Sfx.enter(); onPlay(g) }
                    NavKey.BACK -> { Sfx.click(); detailId = null }
                    else -> {}
                }
            }
        }
    }

    BackHandler(enabled = detail != null || showSettings || showProfile || showGallery || showNews) {
        when {
            showNews -> showNews = false
            showGallery -> showGallery = false
            showSettings -> showSettings = false
            showProfile -> showProfile = false
            else -> detailId = null
        }
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

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Tocar la pantalla apaga el foco de mando (si no hay mando conectado).
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!PadInput.connected.value) MenuNav.active.value = false
            }
        },
    ) {
        LibraryBackground()
        IncomingHost()
        run {
            val shots = remember(all) { app.gbalibrary.data.Shots.list(ctx).size }
            val saves = remember(all) { val d = app.gbalibrary.data.DataDir.saves(ctx); all.count { g -> java.io.File(d, "${g.id}.sav").let { it.exists() && it.length() > 0 } } }
            LaunchedEffect(all, shots, saves) {
                AchSound.init(ctx)
                if (all.isNotEmpty()) Achievements.check(ProfileStats.of(all, shots, saves))
            }
        }
        // El contenido respeta barra de estado, notch y barra de navegacion (el fondo ocupa todo).
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Settings.uiMargin.intValue.dp)) {
            when {
                showSettings -> SettingsScreen(vm, actions, onBack = { showSettings = false })
                showGallery -> GalleryScreen(onBack = { showGallery = false })
                showNews -> NewsScreen(vm, onBack = { showNews = false })
                showProfile -> ProfileScreen(
                    vm = vm,
                    onPickAvatar = actions.pickAvatar,
                    onBack = { showProfile = false },
                    onOpenGame = { Sfx.click(); showProfile = false; detailId = it.id },
                    onGallery = { showGallery = true },
                )
                detail != null -> GameDetail(
                    game = detail,
                    vm = vm,
                    actions = actions,
                    onBack = { Sfx.click(); detailId = null },
                    onPlay = { Sfx.enter(); onPlay(detail) },
                    onPlaySlot = { slot -> Sfx.enter(); onPlaySlot(detail, slot) },
                )
                else -> LibraryHome(
                    vm = vm,
                    actions = actions,
                    onOpen = { Sfx.click(); detailId = it.id },
                    onPlay = { Sfx.enter(); onPlay(it) },
                    onSettings = { Sfx.click(); showSettings = true },
                    onProfile = { Sfx.click(); showProfile = true },
                    onGallery = { Sfx.click(); showGallery = true },
                    onNews = { Sfx.click(); showNews = true },
                )
            }
        }
        PipOverlay()
        AchievementToastHost()
        // Vinilo abajo a la derecha (biblioteca y ajustes; nunca dentro del juego)
        VinylOverlay(Modifier.align(Alignment.BottomEnd))
    }
}

/** Fondo de la biblioteca: degradado del tema, imagen o color solido. */
@Composable
fun LibraryBackground() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    when (Settings.bgMode.intValue) {
        2 -> Box(Modifier.fillMaxSize().background(Color(Settings.bgColor.intValue)))
        1 -> {
            val bmp by produceState<ImageBitmap?>(null, Settings.bgStamp.intValue) {
                value = withContext(Dispatchers.IO) { Covers.load(Covers.libraryBgFile(ctx)) }
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
    onProfile: () -> Unit,
    onGallery: () -> Unit,
    onNews: () -> Unit,
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
    val systemFilter by vm.systemFilter.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    var menuGame by remember { mutableStateOf<Game?>(null) }

    // ---- Navegacion con mando (Big Picture) ----
    val nav = remember { HomeNav() }
    val gridState = rememberLazyGridState()
    val featState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val featured = remember(all) { featuredOf(all) }
    val showFeatured = category == CAT_ALL && query.isBlank() && featured.isNotEmpty()
    val sideKeys = remember(collections) { listOf(CAT_ALL, CAT_FAV, CAT_RECENT, CAT_TOP) + collections }
    val showFocus = MenuNav.active.value || PadInput.connected.value
    DisposableEffect(Unit) { onDispose { MenuNav.textFocus = false } }

    val handleNav: (NavKey) -> Unit = handle@{ k ->
        if (MenuNav.textFocus) focusManager.clearFocus()
        val n = games.size
        // El foco nunca se pierde: si la zona ya no existe, se pasa a otra valida.
        if (nav.zone == Zone.FEATURED && !showFeatured) nav.zone = Zone.GRID
        if (nav.zone == Zone.GRID && n == 0) nav.zone = Zone.SIDE
        nav.grid = nav.grid.coerceIn(0, maxOf(0, n - 1))
        nav.feat = nav.feat.coerceIn(0, maxOf(0, featured.size - 1))
        val cols = nav.cols.coerceAtLeast(1)
        fun enterSide() {
            nav.side = sideKeys.indexOf(category).coerceAtLeast(0)
            nav.zone = Zone.SIDE
        }
        when (k) {
            NavKey.START -> { Sfx.click(); onSettings() }
            NavKey.BACK -> if (nav.zone != Zone.GRID && n > 0) nav.zone = Zone.GRID
            NavKey.OK -> when (nav.zone) {
                Zone.GRID -> games.getOrNull(nav.grid)?.let(onOpen)
                Zone.FEATURED -> featured.getOrNull(nav.feat)?.let(onOpen)
                Zone.SIDE -> { Sfx.click(); sideKeys.getOrNull(nav.side)?.let { vm.category.value = it } }
            }
            else -> when (nav.zone) {
                Zone.GRID -> when (k) {
                    NavKey.LEFT -> if (nav.grid % cols == 0) enterSide() else nav.grid--
                    NavKey.RIGHT -> if (nav.grid < n - 1 && nav.grid % cols != cols - 1) nav.grid++
                    NavKey.UP -> if (nav.grid >= cols) nav.grid -= cols else if (showFeatured) nav.zone = Zone.FEATURED
                    NavKey.DOWN -> if (nav.grid + cols < n) nav.grid += cols else if (nav.grid / cols < (n - 1) / cols) nav.grid = n - 1
                    else -> {}
                }
                Zone.FEATURED -> when (k) {
                    NavKey.LEFT -> if (nav.feat == 0) enterSide() else nav.feat--
                    NavKey.RIGHT -> if (nav.feat < featured.size - 1) nav.feat++
                    NavKey.DOWN -> if (n > 0) { nav.grid = nav.feat.coerceAtMost(minOf(cols, n) - 1); nav.zone = Zone.GRID }
                    else -> {}
                }
                Zone.SIDE -> when (k) {
                    NavKey.UP -> nav.side = maxOf(0, nav.side - 1)
                    NavKey.DOWN -> nav.side = minOf(sideKeys.size - 1, nav.side + 1)
                    NavKey.RIGHT -> if (showFeatured) nav.zone = Zone.FEATURED else if (n > 0) nav.zone = Zone.GRID
                    else -> {}
                }
            }
        }
        Unit
    }
    val currentNav by rememberUpdatedState(handleNav)
    LaunchedEffect(Unit) { MenuNav.events.collect { currentNav(it) } }

    // Mantiene el elemento enfocado a la vista (desplazamiento suave).
    LaunchedEffect(nav.zone, nav.grid, nav.feat, showFeatured, showFocus) {
        if (!showFocus) return@LaunchedEffect
        when (nav.zone) {
            Zone.GRID -> {
                val idx = nav.grid + (if (showFeatured) 1 else 0)
                val info = gridState.layoutInfo
                val vh = info.viewportSize.height
                val item = info.visibleItemsInfo.firstOrNull { it.index == idx }
                if (item == null) gridState.animateScrollToItem(idx, -(vh / 3))
                else if (item.offset.y < 0 || item.offset.y + item.size.height > vh) {
                    gridState.animateScrollToItem(idx, -((vh - item.size.height) / 2))
                }
            }
            Zone.FEATURED -> {
                if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) gridState.animateScrollToItem(0)
                val li = featState.layoutInfo
                val it = li.visibleItemsInfo.firstOrNull { v -> v.index == nav.feat }
                if (it == null || it.offset < 0 || it.offset + it.size > li.viewportEndOffset) featState.animateScrollToItem(nav.feat)
            }
            Zone.SIDE -> {}
        }
    }
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
                onProfile = onProfile,
                onGallery = onGallery,
                onNews = onNews,
            )
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = p.accent)
            if (all.isNotEmpty()) SystemFilterRow(all, systemFilter) { vm.systemFilter.value = it }

            Row(Modifier.fillMaxSize()) {
                SidePanel(all, collections, category, if (showFocus && nav.zone == Zone.SIDE) nav.side else -1, sideKeys) { vm.category.value = it }
                if (all.isEmpty()) {
                    EmptyLibrary(actions)
                } else {
                    GameGrid(
                        games = games,
                        featured = featured,
                        showFeatured = showFeatured,
                        nav = nav,
                        showFocus = showFocus,
                        gridState = gridState,
                        featState = featState,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        onLong = { menuGame = it },
                    )
                }
            }
        }
        menuGame?.let { g -> CoverMenu(g, vm, actions, onOpen) { menuGame = null } }
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
    onProfile: () -> Unit,
    onGallery: () -> Unit,
    onNews: () -> Unit,
) {
    val p = LocalPalette.current
    var sortMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(48.dp).opacityPanel(p.panel).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ic(Icons.Filled.SportsEsports, size = 22.dp, tint = p.accent)
        Text(Settings.appName.value, color = p.text, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, softWrap = false, modifier = Modifier.widthIn(max = 150.dp), overflow = TextOverflow.Ellipsis)

        Box(
            Modifier.weight(1f).widthIn(min = 100.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(p.bg.copy(alpha = 0.8f)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 14.sp),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().onFocusChanged { MenuNav.textFocus = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Buscar juegos...", color = p.dim, fontSize = 14.sp)
                        inner()
                    }
                },
            )
        }

        Box {
            Row(Modifier.clickable { sortMenu = true }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Orden: ${sort.label}", color = p.text, fontSize = 13.sp, maxLines = 1, softWrap = false)
                Ic(Icons.Filled.ArrowDropDown, size = 18.dp)
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortMode.entries.forEach { m ->
                    DropdownMenuItem(text = { Text(m.label) }, onClick = { onSort(m); sortMenu = false })
                }
            }
        }

        Box {
            Ic(Icons.Filled.Add, size = 26.dp, tint = p.accent, modifier = Modifier.clickable { Sfx.click(); addMenu = true }.padding(horizontal = 10.dp, vertical = 4.dp))
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                DropdownMenuItem(text = { Text("Agregar carpeta de ROMs") }, onClick = { addMenu = false; actions.addFolder() })
                DropdownMenuItem(text = { Text("Agregar archivos (.gba .gbc .gb .zip .7z)") }, onClick = { addMenu = false; actions.addFiles() })
                DropdownMenuItem(text = { Text("Volver a escanear carpetas") }, onClick = { addMenu = false; onRescan() })
            }
        }

        StatusIndicators()

        Ic(Icons.Filled.RssFeed, size = 22.dp, tint = p.dim, modifier = Modifier.clickable { onNews() }.padding(horizontal = 6.dp, vertical = 4.dp))
        Ic(Icons.Filled.PhotoLibrary, size = 22.dp, tint = p.dim, modifier = Modifier.clickable { onGallery() }.padding(horizontal = 6.dp, vertical = 4.dp))
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onProfile).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(Settings.profileName.value, color = p.text, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp))
            Avatar(32)
        }
        Ic(Icons.Filled.Settings, size = 22.dp, tint = p.dim, modifier = Modifier.clickable(onClick = onSettings).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}


/** Menu al mantener pulsado un juego: cambiar portada (galeria / pegar URL / buscar en internet / quitar). */
@Composable
private fun CoverMenu(g: Game, vm: LibraryViewModel, actions: LibraryActions, onOpen: (Game) -> Unit, onClose: () -> Unit) {
    var urlMode by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    if (urlMode) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Pegar URL de portada") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text("https://…/imagen.jpg") }, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.setCoverFromUrl(g.id, url); onClose() }) { Text("Descargar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Cancelar") } },
        )
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(g.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                @Composable
                fun Item(label: String, act: () -> Unit) = Text(
                    label, color = LocalPalette.current.text,
                    modifier = Modifier.fillMaxWidth().clickable { act() }.padding(vertical = 11.dp, horizontal = 4.dp),
                )
                Item("Cambiar portada: galería") { actions.pickCover(g.id); onClose() }
                Item("Cambiar portada: pegar URL") { urlMode = true }
                Item("Buscar portada en internet") { vm.fetchCoverFor(g.id); onClose() }
                if (g.hasCover) Item("Quitar portada") { vm.clearCover(g.id); onClose() }
                Item("Ver ficha del juego") { onClose(); onOpen(g) }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Cerrar") } },
    )
}

@Composable
fun Avatar(sizeDp: Int, ring: Color? = null, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, Settings.avatarStamp.intValue) {
        value = withContext(Dispatchers.IO) { Covers.load(Covers.avatarFile(ctx)) }
    }
    Box(
        Modifier.size(sizeDp.dp).clip(shape).background(p.card).border(2.dp, ring ?: p.accent, shape),
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
private fun SidePanel(
    all: List<Game>,
    collections: List<String>,
    selected: String,
    focusIndex: Int,
    sideKeys: List<String>,
    onSelect: (String) -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier.width(160.dp).fillMaxHeight().opacityPanel(p.panel).verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
    ) {
        Text("BIBLIOTECA", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        SideItem("Todos", all.size, selected == CAT_ALL, focusIndex >= 0 && sideKeys.getOrNull(focusIndex) == CAT_ALL) { onSelect(CAT_ALL) }
        SideItem("Favoritos", all.count { it.favorite }, selected == CAT_FAV, focusIndex >= 0 && sideKeys.getOrNull(focusIndex) == CAT_FAV) { onSelect(CAT_FAV) }
        SideItem("Recientes", all.count { it.lastPlayed > 0 }, selected == CAT_RECENT, focusIndex >= 0 && sideKeys.getOrNull(focusIndex) == CAT_RECENT) { onSelect(CAT_RECENT) }
        SideItem("Más jugados", all.count { it.playSeconds > 0 }, selected == CAT_TOP, focusIndex >= 0 && sideKeys.getOrNull(focusIndex) == CAT_TOP) { onSelect(CAT_TOP) }
        if (collections.isNotEmpty()) {
            Text("COLECCIONES", color = p.dim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
            collections.forEach { c -> SideItem(c, all.count { c in it.collections }, selected == c, focusIndex >= 0 && sideKeys.getOrNull(focusIndex) == c) { onSelect(c) } }
        }
    }
}

@Composable
private fun SideItem(label: String, count: Int, selected: Boolean, focused: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (focused) p.accent.copy(alpha = 0.30f) else if (selected) p.card else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, p.accent) else Modifier)
            .clickable { Sfx.click(); onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = if (selected) p.accent else p.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count", color = p.dim, fontSize = 12.sp)
    }
}

/** Filtro por sistema (Todos / GBA / GBC / GB), con cuantos juegos hay de cada uno. */
@Composable
private fun SystemFilterRow(all: List<Game>, selected: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    val systems = listOf("" to "Todos", "GBA" to "GBA", "GBC" to "GBC", "GB" to "GB")
    Row(
        Modifier.fillMaxWidth().background(p.panel.copy(alpha = Settings.boxOpacity.floatValue))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Ds.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        systems.forEach { (key, name) ->
            val n = if (key.isEmpty()) all.size else all.count { systemOf(it) == key }
            if (key.isEmpty() || n > 0 || selected == key) AppChip("$name  $n", selected == key, { onSelect(key) })
        }
    }
}

@Composable
private fun EmptyLibrary(actions: LibraryActions) {
    EmptyState(
        icon = Icons.Filled.SportsEsports,
        title = "Tu biblioteca está vacía",
        body = "Agrega la carpeta donde guardas tus ROMs (.gba, .gbc, .gb, .zip, .7z). Solo juegos que poseas.",
    ) {
        AppButton("Agregar carpeta de ROMs", actions.addFolder, kind = BtnKind.PRIMARY, big = true, icon = Icons.Filled.FolderOpen)
        AppButton("Agregar archivos", actions.addFiles)
    }
}

/** Destacados: recientes + favoritos (o los ultimos agregados si aun no hay historial). */
private fun featuredOf(all: List<Game>): List<Game> {
    val recent = all.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(8)
    val favs = all.filter { it.favorite && recent.none { r -> r.id == it.id } }
    val list = recent + favs
    return if (list.isEmpty()) all.sortedByDescending { it.addedAt }.take(6) else list.take(12)
}

private enum class Zone { SIDE, FEATURED, GRID }

/** Posicion del foco del mando en la biblioteca. */
private class HomeNav {
    var zone by mutableStateOf(Zone.GRID)
    var grid by mutableIntStateOf(0)
    var feat by mutableIntStateOf(0)
    var side by mutableIntStateOf(0)
    var cols by mutableIntStateOf(1)
}

@Composable
private fun GameGrid(
    games: List<Game>,
    featured: List<Game>,
    showFeatured: Boolean,
    nav: HomeNav,
    showFocus: Boolean,
    gridState: LazyGridState,
    featState: LazyListState,
    onOpen: (Game) -> Unit,
    onPlay: (Game) -> Unit,
    onLong: (Game) -> Unit,
) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Misma cuenta que GridCells.Adaptive(128.dp) con 12.dp de margen y de separacion.
        val cols = ((maxWidth - 24.dp + 12.dp) / (128.dp + 12.dp)).toInt().coerceAtLeast(1)
        SideEffect { nav.cols = cols }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 128.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showFeatured) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text("DESTACADOS", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyRow(state = featState, contentPadding = PaddingValues(vertical = 10.dp, horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(featured, key = { _, g -> g.id }) { i, g ->
                                FeatureCard(g, showFocus && nav.zone == Zone.FEATURED && nav.feat == i, onOpen, onPlay, onLong)
                            }
                        }
                        Text("TODOS LOS JUEGOS", color = p.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
            if (games.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Ic(Icons.Filled.Search, size = 40.dp, tint = p.dim)
                        Text("Ningún juego coincide", color = p.text, fontSize = Ds.TitleSp.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        Text("Prueba con otra búsqueda o cambia el filtro de sistema.", color = p.dim, fontSize = Ds.BodySp.sp)
                    }
                }
            }
            itemsIndexed(games, key = { _, g -> g.id }) { i, g ->
                GameCard(g, showFocus && nav.zone == Zone.GRID && nav.grid == i, onOpen, onPlay, onLong)
            }
        }
    }
}

@Composable
private fun FeatureCard(g: Game, focused: Boolean, onOpen: (Game) -> Unit, onPlay: (Game) -> Unit, onLong: (Game) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.width(250.dp).height(125.dp).bigPictureFocus(focused, RoundedCornerShape(10.dp), p.accent, 1.06f).cardPanel(p.card, RoundedCornerShape(10.dp)).combinedClickable(onClick = { onOpen(g) }, onLongClick = { onLong(g) }),
    ) {
        CoverImage(g, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth(0.72f)) {
            FitTitle(g.title, Color.White, FontWeight.Bold, maxSp = 15f, minSp = 10f) // sobre degradado oscuro: blanco siempre legible
            Text(if (g.lastPlayed > 0) "${formatPlayTime(g.playSeconds)} · ${formatAgo(g.lastPlayed)}" else "Sin jugar", color = Color(0xCCFFFFFF), fontSize = 11.sp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(p.play).clickable { onPlay(g) }.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(Icons.Filled.PlayArrow, size = 16.dp, tint = p.onPlay)
                        Text("JUGAR", color = p.onPlay, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
        SystemBadge(systemOf(g), Modifier.align(Alignment.TopStart).padding(10.dp))
        if (g.favorite) Ic(Icons.Filled.Star, size = 20.dp, tint = Color(0xFFFFD54F), modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
private fun GameCard(g: Game, focused: Boolean, onOpen: (Game) -> Unit, onPlay: (Game) -> Unit, onLong: (Game) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.bigPictureFocus(focused, RoundedCornerShape(8.dp), p.accent).cardPanel(p.card, RoundedCornerShape(8.dp)).combinedClickable(onClick = { onOpen(g) }, onLongClick = { onLong(g) }).padding(bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp))) {
            CoverImage(g, Modifier.fillMaxSize())
            SystemBadge(systemOf(g), Modifier.align(Alignment.TopStart).padding(6.dp))
            if (g.favorite) Ic(Icons.Filled.Star, size = 20.dp, tint = Color(0xFFFFD54F), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(34.dp).clip(CircleShape).background(p.play).clickable { onPlay(g) },
                contentAlignment = Alignment.Center,
            ) { Ic(Icons.Filled.PlayArrow, size = 18.dp, tint = p.onPlay) }
        }
        FitTitle(g.title, p.text, FontWeight.SemiBold, maxSp = 13f, minSp = 9f, modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp).fillMaxWidth())
        Text(
            if (g.lastPlayed > 0) "Último: ${formatAgo(g.lastPlayed)}" else "Sin jugar",
            color = p.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/**
 * Titulo completo en maximo 2 lineas: empieza en [maxSp] y achica la fuente (hasta [minSp])
 * hasta que el texto quepa sin cortarse con "...".
 */
@Composable
fun FitTitle(
    text: String,
    color: Color,
    weight: FontWeight,
    maxSp: Float,
    minSp: Float,
    modifier: Modifier = Modifier,
) {
    var size by remember(text) { mutableStateOf(maxSp) }
    Text(
        text,
        color = color,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.15f).sp,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { r ->
            if (r.hasVisualOverflow && size > minSp) size = maxOf(minSp, size - 0.5f)
        },
    )
}
