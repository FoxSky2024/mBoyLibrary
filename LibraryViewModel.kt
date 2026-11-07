package app.gbalibrary.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gbalibrary.data.Covers
import app.gbalibrary.data.Game
import app.gbalibrary.data.Settings
import app.gbalibrary.data.GameStore
import app.gbalibrary.data.RomScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val sort = MutableStateFlow(SortMode.RECENT)
    val category = MutableStateFlow(CAT_ALL)
    val scanning = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    val visible: StateFlow<List<Game>> =
        combine(store.games, query, sort, category) { list, q, s, c ->
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
            val found = RomScanner.fromUris(appContext, uris)
            val added = store.addScanned(found)
            scanning.value = false
            message.value = when {
                found.isEmpty() -> "Ninguno de los archivos parece una ROM de GBA"
                else -> "$added juego(s) nuevo(s)"
            }
        }
    }

    private fun scan(folders: List<Uri>) {
        viewModelScope.launch {
            scanning.value = true
            var added = 0
            var total = 0
            for (f in folders) {
                val found = RomScanner.scanTree(appContext, f)
                total += found.size
                added += store.addScanned(found)
            }
            scanning.value = false
            message.value = if (total == 0) "No se encontraron archivos .gba" else "$added juego(s) nuevo(s) de $total encontrados"
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
