package app.gbalibrary.emu

import java.nio.ByteBuffer

/** Puente JNI de mGBA (GBA / GB / GBC), ver cpp/native-lib.c. */
object Mgba {
    init {
        System.loadLibrary("gbalib")
    }

    /**
     * [saveType]: 0 auto, 1 SRAM, 2 FLASH 64K, 3 FLASH 128K, 4 EEPROM 8K, 5 EEPROM 512B.
     * [rtc]: forzar el reloj de tiempo real (juegos tipo Pokemon Rubi/Zafiro/Esmeralda y sus hacks).
     */
    external fun load(rom: ByteArray, savePath: String, saveType: Int, rtc: Boolean): Boolean
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

