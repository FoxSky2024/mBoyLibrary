package app.gbalibrary.emu

import java.nio.ByteBuffer

/** Puente JNI hacia libgbalib.so (ver cpp/native-lib.c). */
object Native {
    init {
        System.loadLibrary("gbalib")
    }

    /** [saveType]: 0 auto, 1 SRAM, 2 FLASH 64K, 3 FLASH 128K, 4 EEPROM. */
    external fun load(rom: ByteArray, savePath: String, saveType: Int): Boolean
    external fun unload()
    external fun reset()
    external fun setKeys(keys: Int)

    /** Ejecuta un frame y devuelve cuantos frames de audio estereo se escribieron en [audio]. */
    external fun step(audio: ShortArray, render: Boolean): Int
    /** Copia el ultimo frame (RGBA compacto) al buffer directo. 0 = sin frame nuevo; si no, (ancho shl 16) or alto. */
    external fun copyFrame(dst: ByteBuffer): Int
    external fun videoWidth(): Int
    external fun videoHeight(): Int
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

    // Acciones extra (no existen en la GBA; el motor las resuelve antes de llamar a mGBA)
    const val TURBO_A = 1 shl 10
    const val TURBO_B = 1 shl 11
    const val MENU = 1 shl 12 // solo mando fisico
    const val QUICK = 1 shl 13 // solo mando fisico
    const val GBA_MASK = 0x3FF
}
