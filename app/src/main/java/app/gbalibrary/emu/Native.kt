package app.gbalibrary.emu

import java.io.File
import java.nio.ByteBuffer

/**
 * Fachada de los nucleos de emulacion. Cada juego se carga con el nucleo que le toca:
 *  - GBA / GB / GBC: mGBA integrado ([Mgba]).
 *  - Nintendo DS: nucleo libretro melonDS (libmelonds.so) a traves del host libretro ([Retro]).
 * El resto de la app (Engine, capturas, estados) solo habla con [Native].
 */
object Native {
    /** Nombre de la libreria del nucleo DS dentro del APK (jniLibs/<abi>/libmelonds.so). */
    const val DS_CORE_LIB = "libmelonds.so"

    @Volatile
    var isDs = false
        private set

    /** Ultimo motivo de error al cargar (para mostrarlo al usuario). */
    @Volatile
    var lastError = ""
        private set

    /** Cabecera de una ROM de DS: logo de Nintendo con CRC 0xCF56 en 0x15C. */
    fun looksLikeDs(rom: ByteArray): Boolean =
        rom.size > 0x200 && (rom[0x15C].toInt() and 0xFF) == 0x56 && (rom[0x15D].toInt() and 0xFF) == 0xCF

    /** null si el nucleo DS esta incluido en esta instalacion; si no, el motivo. */
    fun dsCoreProblem(): String? = Retro.available(DS_CORE_LIB)

    fun load(rom: ByteArray, savePath: String, saveType: Int, rtc: Boolean): Boolean {
        lastError = ""
        if (looksLikeDs(rom)) {
            val problem = dsCoreProblem()
            if (problem != null) {
                lastError = "Falta el n\u00facleo de Nintendo DS (melonDS) en esta versi\u00f3n de la app. Compila con el workflow incluido para a\u00f1adirlo. ($problem)"
                return false
            }
            val root = File(savePath).parentFile?.parentFile
            val sys = File(root, "System").apply { mkdirs() }
            val tmpRom = File(System.getProperty("java.io.tmpdir") ?: ".", "ds_rom.nds").path
            val ok = Retro.load(DS_CORE_LIB, rom, tmpRom, sys.path, savePath.removeSuffix(".sav") + ".dsv")
            isDs = ok
            if (!ok) lastError = "melonDS no pudo cargar esta ROM (revisa que no est\u00e9 cifrada ni da\u00f1ada)"
            return ok
        }
        isDs = false
        return Mgba.load(rom, savePath, saveType, rtc)
    }

    fun unload() { if (isDs) Retro.unload() else Mgba.unload(); isDs = false }
    fun reset() { if (isDs) Retro.reset() else Mgba.reset() }
    fun setKeys(keys: Int) { if (isDs) Retro.setKeys(keys and GbaKey.DS_MASK) else Mgba.setKeys(keys and GbaKey.GBA_MASK) }
    fun step(audio: ShortArray, render: Boolean): Int = if (isDs) Retro.step(audio, render) else Mgba.step(audio, render)
    fun copyFrame(dst: ByteBuffer): Int = if (isDs) Retro.copyFrame(dst) else Mgba.copyFrame(dst)
    fun videoWidth(): Int = if (isDs) Retro.videoWidth() else Mgba.videoWidth()
    fun videoHeight(): Int = if (isDs) Retro.videoHeight() else Mgba.videoHeight()
    fun saveState(path: String): Boolean = if (isDs) Retro.saveState(path) else Mgba.saveState(path)
    fun loadState(path: String): Boolean = if (isDs) Retro.loadState(path) else Mgba.loadState(path)
    fun screenshot(): IntArray? = if (isDs) Retro.screenshot() else Mgba.screenshot()

    /** Toque en la pantalla (solo DS). [nx], [ny]: 0..1 sobre toda la imagen. */
    fun setTouch(nx: Float, ny: Float, down: Boolean) { if (isDs) Retro.setTouch(nx, ny, down) }

    /** Distribucion, intercambio y calidad de DS (ver [DsEmulator]). */
    fun setDsLayout(@Suppress("UNUSED_PARAMETER") layout: Int) { if (isDs) DsEmulator.applyAll() }
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

    // Solo Nintendo DS
    const val X = 1 shl 14
    const val Y = 1 shl 15
    const val DS_MASK = GBA_MASK or X or Y
}
