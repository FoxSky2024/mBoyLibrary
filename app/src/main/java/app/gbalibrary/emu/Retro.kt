package app.gbalibrary.emu

import java.nio.ByteBuffer

/** Puente JNI del host libretro (cpp/retro_host.c). */
object Retro {
    init {
        System.loadLibrary("gbalib")
    }

    /** null = el nucleo existe y se puede abrir; si no, el error de dlopen. */
    external fun available(lib: String): String?
    external fun load(coreLib: String, rom: ByteArray, romPath: String, sysDir: String, savePath: String): Boolean
    external fun unload()
    external fun reset()
    external fun setKeys(keys: Int)
    external fun setTouch(nx: Float, ny: Float, down: Boolean)
    external fun setOption(key: String, wanted: String): Boolean
    external fun step(audio: ShortArray, render: Boolean): Int
    external fun copyFrame(dst: ByteBuffer): Int
    external fun videoWidth(): Int
    external fun videoHeight(): Int
    external fun saveState(path: String): Boolean
    external fun loadState(path: String): Boolean
    external fun screenshot(): IntArray?
    external fun flushSave()
}
