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
            Color(0xFF5C6BC0), Color(0xFF1F2459), Color(0xFF070816), emptyList(), 0.637f, emptyList(),
            "Nintendo - Game Boy Advance", true, "mGBA"),
        ConsoleDef("gbc", "GBC", "Game Boy Color", "Nintendo", setOf("gbc"),
            Color(0xFF26A69A), Color(0xFF0F4A44), Color(0xFF041412), emptyList(), 0.637f, emptyList(),
            "Nintendo - Game Boy Color", true, "mGBA"),
        ConsoleDef("gb", "GB", "Game Boy", "Nintendo", setOf("gb"),
            Color(0xFF9BBC0F), Color(0xFF3B4A12), Color(0xFF0B1004), emptyList(), 0.637f, emptyList(),
            "Nintendo - Game Boy", true, "mGBA"),
        ConsoleDef("nds", "DS", "Nintendo DS", "Nintendo", setOf("nds"),
            Color(0xFFAB47BC), Color(0xFF3B1A47), Color(0xFF0D0610), emptyList(), 0.72f, emptyList(),
            "Nintendo - Nintendo DS", true, "melonDS"),
        ConsoleDef("nes", "NES", "Nintendo Entertainment System", "Nintendo", setOf("nes"),
            Color(0xFFE53935), Color(0xFF4A1414), Color(0xFF100505), emptyList(), 0.72f, emptyList(),
            "Nintendo - Nintendo Entertainment System", true, "FCEUmm"),
        ConsoleDef("snes", "SNES", "Super Nintendo", "Nintendo", setOf("sfc", "smc"),
            Color(0xFF7E57C2), Color(0xFF2A1F4D), Color(0xFF0A0714), emptyList(), 0.72f, emptyList(),
            "Nintendo - Super Nintendo Entertainment System", true, "Snes9x"),
    )

    /** Sistemas que se lanzan con un emulador externo. */
    val external: List<ConsoleDef> = emptyList()

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
