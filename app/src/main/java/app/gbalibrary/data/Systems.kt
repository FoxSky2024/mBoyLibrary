package app.gbalibrary.data

import androidx.compose.ui.graphics.Color

/** Emulador externo al que se le entrega el juego. [activity] es opcional (si es null, lo resuelve Android dentro de [pkg]). */
data class EmulatorDef(val label: String, val pkg: String, val activity: String? = null)

/**
 * Una consola. Los sistemas integrados (GBA/GBC/GB/DS) los ejecuta el motor propio de la app (mGBA / melonDS);
 * los demas ([builtIn] = false) se lanzan con el emulador real instalado en el telefono.
 */
data class ConsoleDef(
    val id: String,
    /** Etiqueta corta: la que usan [app.gbalibrary.ui.systemOf], los chips de filtro y las insignias. */
    val short: String,
    val name: String,
    val maker: String,
    val extensions: Set<String>,
    val accent: Color,
    val bgTop: Color,
    val bgBottom: Color,
    val emulators: List<EmulatorDef>,
    val boxRatio: Float = 0.74f,
    /** Fragmentos (solo a-z0-9, minusculas) de nombre de carpeta para distinguir .iso compartidos. */
    val folderHints: List<String> = listOf(id),
    /** Carpeta de miniaturas en thumbnails.libretro.com (para las portadas automaticas). */
    val libretroDir: String = "",
    val builtIn: Boolean = false,
    /** Texto de "Starting in ..." para los integrados. */
    val engineLabel: String = "",
)

object Systems {
    private val dolphin = EmulatorDef("Dolphin", "org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.MainActivity")
    private val ppsspp = EmulatorDef("PPSSPP", "org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity")
    private val ppssppGold = EmulatorDef("PPSSPP Gold", "org.ppsspp.ppssppgold", "org.ppsspp.ppssppgold.PpssppActivity")

    /** Sistemas que ya ejecuta la app por dentro. */
    val builtIn: List<ConsoleDef> = listOf(
        ConsoleDef("gba", "GBA", "Game Boy Advance", "Nintendo", setOf("gba", "agb"),
            Color(0xFF5C6BC0), Color(0xFF1F2459), Color(0xFF070816), emptyList(), 0.80f, emptyList(),
            "Nintendo - Game Boy Advance", true, "mGBA"),
        ConsoleDef("gbc", "GBC", "Game Boy Color", "Nintendo", setOf("gbc"),
            Color(0xFF26A69A), Color(0xFF0F4A44), Color(0xFF041412), emptyList(), 0.80f, emptyList(),
            "Nintendo - Game Boy Color", true, "mGBA"),
        ConsoleDef("gb", "GB", "Game Boy", "Nintendo", setOf("gb"),
            Color(0xFF9BBC0F), Color(0xFF3B4A12), Color(0xFF0B1004), emptyList(), 0.80f, emptyList(),
            "Nintendo - Game Boy", true, "mGBA"),
        ConsoleDef("nds", "DS", "Nintendo DS", "Nintendo", setOf("nds"),
            Color(0xFFAB47BC), Color(0xFF3B1A47), Color(0xFF0D0610), emptyList(), 0.72f, emptyList(),
            "Nintendo - Nintendo DS", true, "melonDS"),
    )

    /** Sistemas que se lanzan con un emulador externo. */
    val external: List<ConsoleDef> = listOf(
        ConsoleDef("gamecube", "GCN", "GameCube", "Nintendo", setOf("iso", "gcm", "rvz", "gcz", "ciso"),
            Color(0xFF8C6CFF), Color(0xFF2A2170), Color(0xFF080616), listOf(dolphin), 0.80f,
            listOf("gamecube", "gc", "ngc"), "Nintendo - GameCube"),
        ConsoleDef("wii", "Wii", "Wii", "Nintendo", setOf("iso", "wbfs", "rvz", "wad"),
            Color(0xFF5BC8F5), Color(0xFF2B4A63), Color(0xFF0A1118), listOf(dolphin), 0.74f,
            listOf("wii"), "Nintendo - Wii"),
        ConsoleDef("wiiu", "Wii U", "Wii U", "Nintendo", setOf("wud", "wux", "wua", "rpx"),
            Color(0xFF29B6F6), Color(0xFF4A6075), Color(0xFF0C141B), emptyList(), 0.74f,
            listOf("wiiu"), "Nintendo - Wii U"),
        ConsoleDef("switch", "Switch", "Switch", "Nintendo", setOf("nsp", "xci", "nca"),
            Color(0xFFFF4D5A), Color(0xFF3A1117), Color(0xFF0D0507),
            listOf(EmulatorDef("Eden", "dev.eden.eden_emulator")), 0.62f,
            listOf("switch", "nx"), "Nintendo - Nintendo Switch"),
        ConsoleDef("psp", "PSP", "PlayStation Portable", "Sony", setOf("iso", "cso", "pbp"),
            Color(0xFF6C7BFF), Color(0xFF1E1B5E), Color(0xFF05041A), listOf(ppsspp, ppssppGold), 0.57f,
            listOf("psp"), "Sony - PlayStation Portable"),
        ConsoleDef("ps2", "PS2", "PlayStation 2", "Sony", setOf("iso", "chd", "cso"),
            Color(0xFF3D6BFF), Color(0xFF111C52), Color(0xFF04060F),
            listOf(EmulatorDef("NetherSX2", "xyz.aethersx2.android")), 0.72f,
            listOf("ps2", "playstation2"), "Sony - PlayStation 2"),
        ConsoleDef("xbox360", "X360", "Xbox 360", "Microsoft", setOf("xex", "god"),
            Color(0xFF5CFF4A), Color(0xFF14661C), Color(0xFF030A04), emptyList(), 0.72f,
            listOf("xbox360", "x360"), "Microsoft - Xbox 360"),
        ConsoleDef("3ds", "3DS", "Nintendo 3DS", "Nintendo", setOf("3ds", "cci", "cxi", "cia"),
            Color(0xFFFF5A5A), Color(0xFF47202A), Color(0xFF0C0608),
            listOf(EmulatorDef("Azahar", "org.azahar_emu.azahar")), 0.88f,
            listOf("3ds", "n3ds"), "Nintendo - Nintendo 3DS"),
        ConsoleDef("vita", "Vita", "PlayStation Vita", "Sony", setOf("vpk"),
            Color(0xFF4FA3FF), Color(0xFF14284A), Color(0xFF05080F),
            listOf(EmulatorDef("Vita3K", "org.vita3k.emulator")), 0.78f,
            listOf("vita", "psvita"), "Sony - PlayStation Vita"),
    )

    val all: List<ConsoleDef> get() = builtIn + external

    fun externalById(id: String): ConsoleDef? = external.firstOrNull { it.id == id }

    /** Busca por etiqueta corta ("GBA", "GCN"...). */
    fun byShort(short: String): ConsoleDef? = all.firstOrNull { it.short == short }

    /** Extensiones que el escaner de sistemas externos tiene que mirar. */
    val externalExtensions: Set<String> by lazy { external.flatMap { it.extensions }.toSet() }

    /**
     * Elige la consola externa de un archivo: por extension y, si varias comparten extension (.iso),
     * por el nombre de las carpetas. Sin pista y con ambiguedad devuelve null (el archivo se ignora).
     */
    fun detectExternal(ext: String, pathHint: String): ConsoleDef? {
        val cands = external.filter { ext in it.extensions }
        if (cands.size <= 1) return cands.firstOrNull()
        val hint = pathHint.lowercase().filter { it.isLetterOrDigit() }
        // gana la pista mas larga: "wiiu" antes que "wii"
        return cands.mapNotNull { c ->
            c.folderHints.filter { it in hint }.maxOfOrNull { it.length }?.let { c to it }
        }.maxByOrNull { it.second }?.first
    }
}
