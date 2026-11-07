package app.gbalibrary

import android.content.Intent
import android.net.Uri
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
import app.gbalibrary.ui.Music

class MainActivity : ComponentActivity() {
    private val vm: LibraryViewModel by viewModels()

    // Juego al que se le va a poner portada/fondo cuando el selector devuelva la imagen.
    private var pendingGameId: String? = null

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
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val id = pendingGameId
            if (uri != null && id != null) vm.importCover(id, uri)
        }

    private val pickGameBgImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val id = pendingGameId
            if (uri != null && id != null) vm.importGameBackground(id, uri)
        }

    private val pickAvatarImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importAvatar(uri)
        }

    private val pickLibraryBgImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importLibraryBackground(uri)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        PadInput.init(this)
        Archive.clearCache(this) // restos de una sesion anterior
        val actions = LibraryActions(
            addFolder = { pickFolder.launch(null) },
            addFiles = { pickFiles.launch(arrayOf("*/*")) },
            pickCover = { id -> pendingGameId = id; pickCoverImage.launch(arrayOf("image/*")) },
            pickGameBackground = { id -> pendingGameId = id; pickGameBgImage.launch(arrayOf("image/*")) },
            pickAvatar = { pickAvatarImage.launch(arrayOf("image/*")) },
            pickLibraryBackground = { pickLibraryBgImage.launch(arrayOf("image/*")) },
            pickMusic = { pickMusicFile.launch(arrayOf("audio/*")) },
            pickMusicFolder = { pickMusicFolder.launch(null) },
        )
        setContent {
            GbaTheme {
                LibraryApp(
                    vm = vm,
                    actions = actions,
                    onPlay = { game ->
                        Music.pause()
                        startActivity(
                            Intent(this, EmulatorActivity::class.java)
                                .putExtra(EmulatorActivity.EXTRA_GAME_ID, game.id)
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Music.sync(this)
    }

    override fun onPause() {
        super.onPause()
        Music.pause()
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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (PadInput.isGamepad(event.device) && PadInput.feedMotion(event)) return true
        return super.onGenericMotionEvent(event)
    }
}
