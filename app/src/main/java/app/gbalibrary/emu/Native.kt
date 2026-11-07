package app.gbalibrary.emu

import android.view.Surface

/** Puente JNI hacia libgbalib.so (ver cpp/native-lib.c). */
object Native {
    init {
        System.loadLibrary("gbalib")
    }

    external fun load(rom: ByteArray, savePath: String): Boolean
    external fun unload()
    external fun reset()
    external fun setKeys(keys: Int)

    /** Ejecuta un frame y devuelve cuantos frames de audio estereo se escribieron en [audio]. */
    external fun step(audio: ShortArray, render: Boolean): Int
    external fun setSurface(surface: Surface?)
    external fun saveState(path: String): Boolean
    external fun loadState(path: String): Boolean
    external fun screenshot(): IntArray?
}

/** Mascara de bits que espera mGBA en setKeys (1 shl GBA_KEY_x). */
object GbaKey {
    const val A = 1
    const val B = 2
    const val SELECT = 4
    const val START = 8
    const val RIGHT = 16
    const val LEFT = 32
    const val UP = 64
    const val DOWN = 128
    const val R = 256
    const val L = 512
    const val DIRECTIONS = RIGHT or LEFT or UP or DOWN
}
