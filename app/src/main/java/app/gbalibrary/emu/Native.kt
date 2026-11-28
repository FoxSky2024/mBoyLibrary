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

    /** Nucleos libretro de NES y SNES (jniLibs/<abi>/libfceumm.so y libsnes9x.so). */
    const val NES_CORE_LIB = "libfceumm.so"
    const val SNES_CORE_LIB = "libsnes9x.so"
    const val PS1_CORE_LIB = "libpcsx_rearmed.so"
    const val PCE_CORE_LIB = "libmednafen_pce_fast.so"

    /** Extension del juego que se va a abrir (la pone EmulatorActivity); ayuda a elegir nucleo (.pce, .sfc). */
    @Volatile
    var hintExt = ""

    /** 0 = mGBA, 1 = DS, 2 = NES, 3 = SNES, 4 = PS1, 5 = PC Engine. */
    @Volatile
    private var core = 0

    val isDs: Boolean get() = core == 1

    /** true si el juego corre en el host libretro (DS, NES, SNES). */
    val isRetro: Boolean get() = core != 0

    /** true si el sistema tiene botones X e Y (DS y SNES). */
    val hasXY: Boolean get() = core == 1 || core == 3 || core == 4

    /** Relacion de aspecto en pantalla (los pixeles de NES/SNES no son cuadrados); 0 = la de la imagen. */
    val displayAspect: Float get() = if (core >= 2) 4f / 3f else 0f

    /** Ultimo motivo de error al cargar (para mostrarlo al usuario). */
    @Volatile
    var lastError = ""
        private set

    /** Cabecera de una ROM de DS: logo de Nintendo con CRC 0xCF56 en 0x15C. */
    fun looksLikeDs(rom: ByteArray): Boolean =
        rom.size > 0x200 && (rom[0x15C].toInt() and 0xFF) == 0x56 && (rom[0x15D].toInt() and 0xFF) == 0xCF

    /** null si el nucleo DS esta incluido en esta instalacion; si no, el motivo. */
    fun dsCoreProblem(): String? = Retro.available(DS_CORE_LIB)

    /** Cabecera iNES: "NES" 1A. */
    fun looksLikeNes(rom: ByteArray): Boolean =
        rom.size > 16 && rom[0] == 0x4E.toByte() && rom[1] == 0x45.toByte() && rom[2] == 0x53.toByte() && rom[3] == 0x1A.toByte()

    private fun u16(rom: ByteArray, o: Int) = (rom[o].toInt() and 0xFF) or ((rom[o + 1].toInt() and 0xFF) shl 8)

    /** SNES: suma de comprobacion + complemento = 0xFFFF en la cabecera interna (LoROM 0x7FC0 / HiROM 0xFFC0, con o sin 512 B de copiador). */
    fun looksLikeSnes(rom: ByteArray): Boolean {
        if (rom.size < 0x8000) return false
        if ((rom[0xB2].toInt() and 0xFF) == 0x96) return false // GBA
        if (rom.size > 0x108 && (rom[0x104].toInt() and 0xFF) == 0xCE && (rom[0x105].toInt() and 0xFF) == 0xED) return false // Game Boy
        val extra = if (rom.size % 1024 == 512) 512 else 0
        for (base in intArrayOf(0x7FC0, 0xFFC0)) {
            val o = base + extra
            if (rom.size >= o + 0x20 && (u16(rom, o + 0x1C) xor u16(rom, o + 0x1E)) == 0xFFFF) return true
        }
        return false
    }

    private fun loadRetro(rom: ByteArray, savePath: String, lib: String, nombre: String, ext: String, id: Int, realPath: String? = null): Boolean {
        val problem = Retro.available(lib)
        if (problem != null) {
            lastError = "Falta el n\u00facleo de $nombre en esta versi\u00f3n de la app. Compila con el workflow incluido para a\u00f1adirlo. ($problem)"
            return false
        }
        val root = File(savePath).parentFile?.parentFile
        val sys = File(root, "System").apply { mkdirs() }
        val tmpRom = realPath ?: File(System.getProperty("java.io.tmpdir") ?: ".", "retro_rom.$ext").path
        val ok = Retro.load(lib, rom, tmpRom, sys.path, savePath.removeSuffix(".sav") + ".srm")
        core = if (ok) id else 0
        if (!ok) lastError = "$nombre no pudo cargar esta ROM (revisa que no est\u00e9 da\u00f1ada)"
        return ok
    }

    /** PS1: el disco se abre por ruta (no se lee a memoria). */
    fun loadDisc(path: String, savePath: String): Boolean {
        lastError = ""
        return loadRetro(ByteArray(0), savePath, PS1_CORE_LIB, "PlayStation (PCSX ReARMed)", path.substringAfterLast('.', "chd"), 4, path)
    }

    fun load(rom: ByteArray, savePath: String, saveType: Int, rtc: Boolean): Boolean {
        lastError = ""
        if (hintExt == "pce" || hintExt == "sgx") return loadRetro(rom, savePath, PCE_CORE_LIB, "PC Engine (Beetle PCE Fast)", "pce", 5)
        if (looksLikeNes(rom)) return loadRetro(rom, savePath, NES_CORE_LIB, "NES (FCEUmm)", "nes", 2)
        if (!looksLikeDs(rom) && looksLikeSnes(rom)) return loadRetro(rom, savePath, SNES_CORE_LIB, "SNES (Snes9x)", "sfc", 3)
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
            core = if (ok) 1 else 0
            if (!ok) lastError = "melonDS no pudo cargar esta ROM (revisa que no est\u00e9 cifrada ni da\u00f1ada)"
            return ok
        }
        core = 0
        return Mgba.load(rom, savePath, saveType, rtc)
    }

    fun unload() { if (isRetro) Retro.unload() else Mgba.unload(); core = 0 }
    fun reset() { if (isRetro) Retro.reset() else Mgba.reset() }
    fun setKeys(keys: Int) { if (isRetro) Retro.setKeys(keys and GbaKey.DS_MASK) else Mgba.setKeys(keys and GbaKey.GBA_MASK) }
    fun step(audio: ShortArray, render: Boolean): Int = if (isRetro) Retro.step(audio, render) else Mgba.step(audio, render)
    fun copyFrame(dst: ByteBuffer): Int = if (isRetro) Retro.copyFrame(dst) else Mgba.copyFrame(dst)
    fun videoWidth(): Int = if (isRetro) Retro.videoWidth() else Mgba.videoWidth()
    fun videoHeight(): Int = if (isRetro) Retro.videoHeight() else Mgba.videoHeight()
    fun saveState(path: String): Boolean = if (isRetro) Retro.saveState(path) else Mgba.saveState(path)
    fun loadState(path: String): Boolean = if (isRetro) Retro.loadState(path) else Mgba.loadState(path)
    fun screenshot(): IntArray? = if (isRetro) Retro.screenshot() else Mgba.screenshot()

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

    // Acciones extra solo de mando fisico (no llegan al nucleo)
    const val QLOAD = 1 shl 16 // cargar el guardado rapido
    const val SPEED = 1 shl 17 // ciclar la velocidad
}
