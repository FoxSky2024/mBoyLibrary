package app.gbalibrary

import android.content.Intent
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gbalibrary.data.DataDir
import app.gbalibrary.data.Persist
import app.gbalibrary.ui.UiScale
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.view.KeyEvent
import android.view.MotionEvent
import app.gbalibrary.data.Archive
import app.gbalibrary.data.Settings
import app.gbalibrary.input.PadInput
import app.gbalibrary.ui.GbaTheme
import app.gbalibrary.ui.LibraryActions
import app.gbalibrary.ui.LibraryApp
import app.gbalibrary.ui.LibraryViewModel
import app.gbalibrary.ui.MenuNav
import app.gbalibrary.ui.Pip
import app.gbalibrary.stream.StreamClient
import app.gbalibrary.ui.Music
import app.gbalibrary.ui.NavKey
import android.os.SystemClock

class MainActivity : ComponentActivity() {
    private val vm: LibraryViewModel by viewModels()

    // Juego al que se le va a poner portada/fondo cuando el selector devuelva la imagen.
    private var pendingGameId: String? = null

    /** True al abrir un juego con la musica local activada: onPause no la corta. */
    private var keepMusic = false

    private fun keep(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Sin permiso persistente: funciona esta sesion, habra que volver a elegirlo despues.
        }
    }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                keep(uri)
                vm.addFolder(uri)
            }
        }

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { keep(it) }
                vm.addFiles(uris)
            }
        }

    private val pickCoverImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val id = pendingGameId
            if (uri != null && id != null) vm.importCover(id, uri)
        }

    private val pickGameBgImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val id = pendingGameId
            if (uri != null && id != null) vm.importGameBackground(id, uri)
        }

    private val pickAvatarImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.importAvatar(uri)
        }

    private val pickLibraryBgImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.importLibraryBackground(uri)
        }

    /** Selector de fotos del sistema: abre en Imagenes / Galeria, no en Descargas. */
    private fun launchImage(l: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest>) =
        l.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    private val pickPipVideoFile =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) Pip.showVideo(uri.toString())
        }

    private val pickMusicFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                keep(uri)
                Settings.setMusicUri(uri.toString())
                Settings.setMusicOn(true)
            }
        }

    private val pickMusicFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                keep(uri)
                Settings.setMusicFolder(uri.toString())
                Settings.setMusicOn(true)
            }
        }

    private var includeRomsPending = false
    private var askStorage by mutableStateOf(false)

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) vm.exportBackup(uri, includeRomsPending)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importBackup(uri)
        }

    private val legacyPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { onStorageMaybeGranted() }

    private val manageAllLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { onStorageMaybeGranted() }

    private fun onStorageMaybeGranted() {
        if (DataDir.refresh(this)) vm.reloadFromDisk()
    }

    private fun requestStorage() {
        if (DataDir.hasStoragePermission(this)) { onStorageMaybeGranted(); return }
        if (Build.VERSION.SDK_INT >= 30) {
            val i = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            try { manageAllLauncher.launch(i) } catch (e: Exception) {
                try { manageAllLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (e2: Exception) {
                    Toast.makeText(this, "No se pudo abrir la pantalla de permisos", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            legacyPermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun openDataFolder() {
        val path = DataDir.root(this).path
        try {
            val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/GBALibrary")
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Carpeta de datos: $path", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleJoinLink(i: android.content.Intent?) {
        val d = i?.data ?: return
        if (d.scheme == "mboy" && d.host == "join") {
            d.lastPathSegment?.filter { it.isDigit() }?.takeIf { it.length == 6 }?.let { StreamClient.pendingCode.value = it }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleJoinLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleJoinLink(intent)
        Settings.init(this) // GbaApp ya lo hizo; es idempotente
        PadInput.init(this)
        Archive.clearCache(this) // restos de una sesion anterior
        val actions = LibraryActions(
            addFolder = { pickFolder.launch(null) },
            addFiles = { pickFiles.launch(arrayOf("*/*")) },
            pickCover = { id -> pendingGameId = id; launchImage(pickCoverImage) },
            pickGameBackground = { id -> pendingGameId = id; launchImage(pickGameBgImage) },
            pickAvatar = { launchImage(pickAvatarImage) },
            pickLibraryBackground = { launchImage(pickLibraryBgImage) },
            pickMusic = { pickMusicFile.launch(arrayOf("audio/*")) },
            pickPipVideo = { pickPipVideoFile.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            pickMusicFolder = { pickMusicFolder.launch(null) },
            openDataFolder = { openDataFolder() },
            requestStorage = { requestStorage() },
            exportBackup = { withRoms ->
                includeRomsPending = withRoms
                exportLauncher.launch("GBALibrary-backup.zip")
            },
            importBackup = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        )
        val flags = getSharedPreferences("app_flags", MODE_PRIVATE)
        if (!DataDir.hasStoragePermission(this) && !flags.getBoolean("storage_asked", false)) askStorage = true
        setContent {
            GbaTheme {
              UiScale {
                if (askStorage) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Guardar tus datos de forma permanente") },
                        text = { Text("Para que juegos, partidas, estados y portadas sobrevivan a una reinstalación, la app usa la carpeta Documentos/GBALibrary. Android pide el permiso de acceso a archivos. Puedes hacerlo después en Ajustes.") },
                        confirmButton = { TextButton(onClick = { askStorage = false; flags.edit().putBoolean("storage_asked", true).apply(); requestStorage() }) { Text("Conceder") } },
                        dismissButton = { TextButton(onClick = { askStorage = false; flags.edit().putBoolean("storage_asked", true).apply() }) { Text("Más tarde") } },
                    )
                }
                LibraryApp(
                    vm = vm,
                    actions = actions,
                    onPlay = { game ->
                        // Con "seguir escuchando dentro del juego" la musica no se pausa al entrar.
                        if (Settings.musicInGame.value) keepMusic = true else Music.pause()
                        startActivity(
                            Intent(this, EmulatorActivity::class.java)
                                .putExtra(EmulatorActivity.EXTRA_GAME_ID, game.id)
                        )
                    },
                )
              }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (DataDir.refresh(this)) vm.reloadFromDisk()
        Music.userPaused = false
        Music.sync(this)
    }

    override fun onPause() {
        super.onPause()
        Persist.flush(this)
        if (keepMusic) keepMusic = false else Music.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            Music.release()
            Archive.clearCache(this) // limpia la cache de ROMs descomprimidas al salir
        }
    }

    // Ajustes > Mando: captura de botones (remapear) y modo "Probar mando".
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (PadInput.feedKey(keyCode, true)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (PadInput.feedKey(keyCode, false)) return true
        return super.onKeyUp(keyCode, event)
    }

    // ---- Navegacion de menus con mando / teclado (estilo Steam Big Picture) ----

    /** Pulsa una tecla "falsa" en la ventana principal (sin pasar de nuevo por dispatchKeyEvent). */
    private fun synthKey(code: Int, down: Boolean) {
        val t = SystemClock.uptimeMillis()
        window.superDispatchKeyEvent(KeyEvent(t, t, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0))
    }

    private fun navToDpad(k: NavKey) = when (k) {
        NavKey.UP -> KeyEvent.KEYCODE_DPAD_UP
        NavKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        NavKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        else -> KeyEvent.KEYCODE_DPAD_RIGHT
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (PadInput.busy) return super.dispatchKeyEvent(event) // remapeo / prueba de mando
        val k = MenuNav.fromKey(event.keyCode)
        if (k == NavKey.NONE) return super.dispatchKeyEvent(event)
        val pad = PadInput.isPadKey(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        // Con el cursor en un campo de texto, el teclado fisico escribe (el mando siempre navega).
        if (!pad && MenuNav.textFocus && MenuNav.mode == MenuNav.Mode.HOME) return super.dispatchKeyEvent(event)
        if (down) MenuNav.active.value = true

        return when (MenuNav.mode) {
            MenuNav.Mode.HOME, MenuNav.Mode.DETAIL -> {
                // En Detalle las flechas siguen el foco normal de Compose; todo lo demas lo maneja la pantalla.
                if (MenuNav.mode == MenuNav.Mode.DETAIL && MenuNav.isDirection(k)) return super.dispatchKeyEvent(event)
                if (down && (event.repeatCount == 0 || (MenuNav.isDirection(k) && MenuNav.repeatAllowed(SystemClock.uptimeMillis())))) {
                    MenuNav.send(k)
                }
                true
            }
            MenuNav.Mode.FOCUS -> {
                if (!pad) return super.dispatchKeyEvent(event)
                when (k) {
                    NavKey.OK -> synthKey(KeyEvent.KEYCODE_DPAD_CENTER, down)
                    NavKey.BACK -> if (down && event.repeatCount == 0) onBackPressedDispatcher.onBackPressed()
                    NavKey.START -> {}
                    else -> return super.dispatchKeyEvent(event) // D-Pad nativo: mueve el foco de Compose
                }
                true
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (PadInput.isGamepad(event.device)) {
            if (PadInput.feedMotion(event)) return true
            // Stick izquierdo / hat = mover el foco del menu.
            if (event.action == MotionEvent.ACTION_MOVE) {
                var x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                var y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                if (x == 0f && y == 0f) {
                    x = event.getAxisValue(MotionEvent.AXIS_X)
                    y = event.getAxisValue(MotionEvent.AXIS_Y)
                }
                val k = MenuNav.stick(x, y, SystemClock.uptimeMillis())
                if (k != NavKey.NONE) {
                    MenuNav.active.value = true
                    if (MenuNav.mode == MenuNav.Mode.HOME) {
                        MenuNav.send(k)
                    } else {
                        val code = navToDpad(k)
                        synthKey(code, true)
                        synthKey(code, false)
                    }
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
}
