package app.gbalibrary

import android.app.Application
import app.gbalibrary.data.DataDir
import app.gbalibrary.data.Settings

/**
 * Se ejecuta antes que cualquier pantalla: crea/elige la carpeta de datos persistente
 * (GBALibrary) y restaura los ajustes guardados en ella.
 */
class GbaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DataDir.init(this)
        Settings.init(this)
    }
}
