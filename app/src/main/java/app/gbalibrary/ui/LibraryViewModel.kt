package app.gbalibrary.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import app.gbalibrary.data.Archive
import app.gbalibrary.data.CoverFetcher
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Persist
import app.gbalibrary.data.Game
import app.gbalibrary.data.Settings
import app.gbalibrary.data.GameStore
import app.gbalibrary.data.RomScanner
import app.gbalibrary.data.ExternalScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un .zip/.7z con varios .gba: el usuario elige cual abrir. */
class ArchivePick(val uri: Uri, val name: String, val entries: List<Archive.Entry>, val autoPlay: Boolean)

const val CAT_ALL = "__all__"
const val CAT_FAV = "__fav__"
const val CAT_RECENT = "__recent__"
const val CAT_TOP = "__top__"

enum class SortMode(val label: String) {
    NAME("Nombre"),
    RECENT("Jugado reciente"),
    PLAYTIME("Tiempo de juego"),
    ADDED("Agregado"),
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: Application = application
    private val store = GameStore.get(application)
    private val prefs = application.getSharedPreferences("library", Context.MODE_PRIVATE)

    val games: StateFlow<List<Game>> = store.games
    val query = MutableStateFlow("")
    // El orden elegido se recuerda (Settings.sortMode -> config.json).
    val sort = MutableStateFlow(
        try { SortMode.valueOf(Settings.sortMode.value) } catch (e: Exception) { SortMode.RECENT }
    )
    val category = MutableStateFlow(CAT_ALL)

    /** Filtro por sistema: "" = todos, o "GBA" / "GBC" / "GB". */
    val systemFilter = MutableStateFlow("")
    val scanning = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    /** Progreso de la busqueda de portadas ("Buscando portadas 3/10"), null si no hay busqueda. */
    val coverStatus = MutableStateFlow<String?>(null)
    private var coverJob: Job? = null

    init {
        viewModelScope.launch { sort.collect { Settings.setSortMode(it.name) } }
    }

    /** Selector de ROM cuando un archivo comprimido trae varios .gba. */
    val archivePick = MutableStateFlow<ArchivePick?>(null)
    private val pickQueue = ArrayList<ArchivePick>()

    /** Juego que se debe abrir ya (por ejemplo, el .gba de un .zip recien elegido). */
    val playRequest = MutableStateFlow<Game?>(null)

    val visible: StateFlow<List<Game>> =
        combine(store.games, query, sort, category, systemFilter) { list0, q, s, c, sysF ->
            val list = if (sysF.isEmpty()) list0 else list0.filter { systemOf(it) == sysF }
            val base = when (c) {
                CAT_ALL -> list
                CAT_FAV -> list.filter { it.favorite }
                CAT_RECENT -> list.filter { it.lastPlayed > 0 }
                CAT_TOP -> list.filter { it.playSeconds > 0 }
                else -> list.filter { c in it.collections }
            }
            val needle = q.trim()
            val filtered = if (needle.isEmpty()) base else base.filter { it.title.contains(needle, ignoreCase = true) }
            when {
                c == CAT_RECENT -> filtered.sortedByDescending { it.lastPlayed }
                c == CAT_TOP -> filtered.sortedByDescending { it.playSeconds }
                s == SortMode.NAME -> filtered.sortedBy { it.title.lowercase() }
                s == SortMode.RECENT -> filtered.sortedWith(
                    compareByDescending<Game> { it.lastPlayed }.thenBy { it.title.lowercase() }
                )
                s == SortMode.PLAYTIME -> filtered.sortedWith(
                    compareByDescending<Game> { it.playSeconds }.thenBy { it.title.lowercase() }
                )
                else -> filtered.sortedByDescending { it.addedAt }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val collections: StateFlow<List<String>> =
        store.games
            .map { list -> list.flatMap { it.collections }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Importar ----

    fun addFolder(uri: Uri) {
        val folders = savedFolders().toMutableSet()
        folders.add(uri.toString())
        prefs.edit().putStringSet(KEY_FOLDERS, folders).apply()
        scan(listOf(uri))
    }

    fun rescan() {
        val folders = savedFolders().map { Uri.parse(it) }
        if (folders.isEmpty()) {
            message.value = "Aun no has agregado ninguna carpeta"
        } else {
            scan(folders)
        }
    }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            scanning.value = true
            val single = uris.size == 1
            val found = ArrayList<Game>()
            val queue = ArrayList<ArchivePick>()
            var fromArchive = false
            var emptyArchives = 0
            withContext(Dispatchers.IO) {
                for (u in uris) {
                    if (Archive.kind(appContext, u) != Archive.Kind.NONE) {
                        val entries = Archive.listRoms(appContext, u)
                        when {
                            entries.isEmpty() -> emptyArchives++
                            entries.size == 1 -> {
                                fromArchive = true
                                RomScanner.fromArchiveEntry(appContext, u, entries[0].name)?.let { found.add(it) }
                            }
                            else -> {
                                val name = try { DocumentFile.fromSingleUri(appContext, u)?.name } catch (e: Exception) { null }
                                queue.add(ArchivePick(u, name ?: "archivo comprimido", entries, single))
                            }
                        }
                    } else {
                        RomScanner.fromUri(appContext, u)?.let { found.add(it) }
                    }
                }
            }
            val added = store.addScanned(found)
            scanning.value = false
            if (added > 0) autoCovers()
            pickQueue.addAll(queue)
            showNextPick()
            when {
                queue.isNotEmpty() -> message.value = if (found.isEmpty()) "Elige cual ROM abrir" else "$added juego(s) nuevo(s). Elige cual ROM abrir"
                found.isEmpty() -> message.value =
                    if (emptyArchives > 0) "El archivo comprimido no trae ninguna ROM (.gba .gbc .gb .nds)" else "Ninguno de los archivos parece una ROM de GBA/GBC/DS"
                else -> message.value = "$added juego(s) nuevo(s)"
            }
            // Un solo .zip/.7z con un solo .gba: se abre directamente.
            if (single && fromArchive && found.size == 1) playRequest.value = store.get(found[0].id)
        }
    }

    private fun showNextPick() {
        if (archivePick.value == null && pickQueue.isNotEmpty()) archivePick.value = pickQueue.removeAt(0)
    }

    /** [entry] = null cancela esa seleccion. */
    fun chooseEntry(pick: ArchivePick, entry: Archive.Entry?) {
        archivePick.value = null
        if (entry == null) {
            showNextPick()
            return
        }
        viewModelScope.launch {
            scanning.value = true
            val game = withContext(Dispatchers.IO) { RomScanner.fromArchiveEntry(appContext, pick.uri, entry.name) }
            if (game != null) {
                val added = store.addScanned(listOf(game))
                if (added > 0) autoCovers()
                if (pick.autoPlay) playRequest.value = store.get(game.id)
            } else {
                message.value = "No se pudo descomprimir esa ROM"
            }
            scanning.value = false
            showNextPick()
        }
    }

    /** Escaneo en segundo plano: los juegos aparecen en la lista a medida que se encuentran. */
    private fun scan(folders: List<Uri>) {
        viewModelScope.launch {
            scanning.value = true
            var added = 0
            var removed = 0
            for (f in folders) {
                val r = RomScanner.scanTree(appContext, f) { batch ->
                    withContext(Dispatchers.Main) { added += store.addScanned(batch) }
                }
                // Archivos borrados: solo se quitan los juegos sin copia propia en GBALibrary/Games.
                for (id in r.removedIds) {
                    val g = store.get(id)
                    if (g != null && g.stored.isEmpty()) {
                        store.remove(id)
                        removed++
                    }
                }
                // Juegos de sistemas con emulador externo (GameCube, Wii, PSP, 3DS...): solo se referencian.
                val ext = ExternalScanner.scanTree(appContext, f, store.games.value)
                added += store.addScanned(ext.games)
                for (id in ext.removedIds) {
                    store.remove(id)
                    removed++
                }
            }
            scanning.value = false
            if (added > 0) autoCovers()
            message.value = when {
                added > 0 && removed > 0 -> "$added nuevo(s), $removed quitado(s) (archivo borrado)"
                added > 0 -> "$added juego(s) nuevo(s)"
                removed > 0 -> "$removed juego(s) quitado(s) (archivo borrado)"
                else -> "Sin cambios: no hay archivos nuevos"
            }
        }
    }

    private fun savedFolders(): Set<String> = prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()

    // ---- Edicion de un juego ----

    fun toggleFavorite(id: String) {
        val g = store.get(id) ?: return
        store.setFavorite(id, !g.favorite)
    }

    fun rename(id: String, title: String) = store.rename(id, title)

    fun setInCollection(id: String, name: String, inCollection: Boolean) =
        store.setInCollection(id, name.trim(), inCollection)

    fun remove(id: String) {
        Covers.deleteFor(appContext, id)
        store.remove(id)
    }

    fun setNote(id: String, note: String) = store.setNote(id, note)

    fun setProgress(id: String, pct: Int) = store.setProgress(id, pct)

    /** Compatibilidad por juego: tipo de guardado (-1 auto) y RTC (0 auto, 1 si, 2 no). */
    fun setCompat(id: String, saveOverride: Int, rtcMode: Int) = store.setCompat(id, saveOverride, rtcMode)

    // ---- Portadas desde internet ----

    /** Tras agregar juegos: busca las portadas de los que no tienen (si esta activado en Ajustes). */
    private fun autoCovers() {
        if (Settings.autoCovers.value) fetchCovers(force = false)
    }

    /**
     * Busca portadas de los juegos que no tienen. Sin [force] se salta los que ya se intentaron hace
     * menos de 7 dias; con [force] ("Buscar portadas faltantes") reintenta todos.
     */
    fun fetchCovers(force: Boolean) {
        if (coverJob?.isActive == true) {
            if (force) message.value = "Ya se estan buscando portadas"
            return
        }
        coverJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val pending = store.games.value.filter { g ->
                !g.hasCover && (force || g.coverTried == 0L || now - g.coverTried > CoverFetcher.RETRY_AFTER_MS)
            }
            if (pending.isEmpty()) {
                if (force) message.value = "No faltan portadas"
                return@launch
            }
            var found = 0
            var offline = false
            for ((i, g) in pending.withIndex()) {
                coverStatus.value = "Buscando portadas ${i + 1}/${pending.size}"
                val ok = CoverFetcher.fetchFor(appContext, g)
                if (ok) {
                    store.setCover(g.id, true)
                    found++
                    CoverFetcher.fetchBackExtras(appContext, g) // lomo/atras de la caja 3D
                }
                if (CoverFetcher.networkFailed) {
                    offline = true
                    break // sin conexion: no se marca nada para reintentar luego
                }
                store.markCoverTried(g.id, System.currentTimeMillis())
            }
            coverStatus.value = null
            message.value = when {
                offline && found == 0 -> "Sin conexion: no se pudieron buscar portadas"
                force || found > 0 -> "Portadas encontradas: $found de ${pending.size}"
                else -> null
            }
        }
    }

    /** Busca (de nuevo) la portada de un solo juego, aunque ya tenga una. */
    fun fetchCoverFor(id: String) {
        val g = store.get(id) ?: return
        viewModelScope.launch {
            coverStatus.value = "Buscando portada de ${g.title}"
            val ok = CoverFetcher.fetchFor(appContext, g)
            coverStatus.value = null
            if (ok) {
                store.setCover(id, true)
                CoverFetcher.fetchBackExtras(appContext, g)
                message.value = "Portada encontrada"
            } else {
                message.value = if (CoverFetcher.networkFailed) "Sin conexion" else "No se encontro portada para ${g.title}"
            }
            if (!CoverFetcher.networkFailed) store.markCoverTried(id, System.currentTimeMillis())
        }
    }

    /** Portada desde una URL pegada por el usuario. */
    fun setCoverFromUrl(id: String, url: String) {
        viewModelScope.launch {
            coverStatus.value = "Descargando portada"
            val ok = CoverFetcher.fetchFromUrl(appContext, id, url)
            coverStatus.value = null
            if (ok) {
                store.setCover(id, true)
                message.value = "Portada actualizada"
            } else {
                message.value = "No se pudo descargar esa imagen (usa un enlace https directo a una imagen)"
            }
        }
    }

    // ---- Carpeta de datos / copias de seguridad ----

    /** Vuelve a leer biblioteca y ajustes despues de importar una copia o de migrar la carpeta de datos. */
    fun reloadFromDisk() {
        store.reload()
        Settings.reload(appContext)
        try { sort.value = SortMode.valueOf(Settings.sortMode.value) } catch (e: Exception) { /* se queda el actual */ }
    }

    fun exportBackup(uri: Uri, includeRoms: Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.openOutputStream(uri)?.let { Persist.exportBackup(appContext, it, includeRoms); true } ?: false
                } catch (e: Exception) { false }
            }
            message.value = if (ok) "Copia de seguridad guardada" else "No se pudo guardar la copia"
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.openInputStream(uri)?.use { Persist.importBackup(appContext, it) } ?: 0
                } catch (e: Exception) { 0 }
            }
            if (count > 0) {
                reloadFromDisk()
                message.value = "Copia restaurada ($count archivos)"
            } else {
                message.value = "No se pudo importar esa copia"
            }
        }
    }

    // ---- Imagenes personalizadas (se copian reducidas a la app) ----

    fun importCover(id: String, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Covers.import(appContext, uri, Covers.coverFile(appContext, id), 900) }
            if (ok) store.setCover(id, true) else message.value = "No se pudo usar esa imagen"
        }
    }

    fun clearCover(id: String) {
        Covers.coverFile(appContext, id).delete()
        store.setCover(id, false)
    }

    fun importGameBackground(id: String, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Covers.import(appContext, uri, Covers.backgroundFile(appContext, id), 1600) }
            if (ok) store.setBackground(id, true) else message.value = "No se pudo usar esa imagen"
        }
    }

    fun clearGameBackground(id: String) {
        Covers.backgroundFile(appContext, id).delete()
        store.setBackground(id, false)
    }

    fun importAvatar(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Covers.import(appContext, uri, Covers.avatarFile(appContext), 256) }
            if (ok) Settings.bumpAvatar() else message.value = "No se pudo usar esa imagen"
        }
    }

    fun importLibraryBackground(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Covers.import(appContext, uri, Covers.libraryBgFile(appContext), 1600) }
            if (ok) {
                Settings.bumpBg()
                Settings.setBgMode(1)
            } else {
                message.value = "No se pudo usar esa imagen"
            }
        }
    }

    companion object {
        private const val KEY_FOLDERS = "folders"
    }
}
