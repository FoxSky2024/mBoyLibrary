package app.gbalibrary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import app.gbalibrary.ui.GbaTheme
import app.gbalibrary.ui.LibraryApp
import app.gbalibrary.ui.LibraryViewModel

class MainActivity : ComponentActivity() {
    private val vm: LibraryViewModel by viewModels()

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Sin permiso persistente: funciona esta sesion, habra que volver a elegirla despues.
                }
                vm.addFolder(uri)
            }
        }

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach {
                    try {
                        contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: SecurityException) {
                        // Igual que arriba.
                    }
                }
                vm.addFiles(uris)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GbaTheme {
                LibraryApp(
                    vm = vm,
                    onPlay = { game ->
                        startActivity(
                            Intent(this, EmulatorActivity::class.java)
                                .putExtra(EmulatorActivity.EXTRA_GAME_ID, game.id)
                        )
                    },
                    onAddFolder = { pickFolder.launch(null) },
                    onAddFiles = { pickFiles.launch(arrayOf("*/*")) },
                )
            }
        }
    }
}
