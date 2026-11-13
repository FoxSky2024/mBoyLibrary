package app.gbalibrary.emu

import app.gbalibrary.data.Settings

/**
 * Abstraccion comun de los nucleos. [Native] delega en uno u otro segun la ROM:
 * GBA / GB / GBC -> mGBA integrado, Nintendo DS -> melonDS (libretro). El modulo DS vive aqui.
 */
interface EmulatorCore {
    val id: String
    fun setKeys(keys: Int)
    fun setTouch(nx: Float, ny: Float, down: Boolean)
}

/** Modulo Nintendo DS: distribucion de pantallas, calidad y opciones del nucleo melonDS. */
object DsEmulator : EmulatorCore {
    override val id = "melonds"

    /** Distribuciones: 0 arriba/abajo, 1 lado a lado, 2 solo arriba, 3 solo abajo, 4 grande + pequena. */
    const val LAYOUT_COUNT = 5

    /** Calidad: 0 automatica, 1 alta, 2 rendimiento (gama media a 60 fps). */
    const val QUALITY_AUTO = 0
    const val QUALITY_HIGH = 1
    const val QUALITY_FAST = 2

    override fun setKeys(keys: Int) = Retro.setKeys(keys and GbaKey.DS_MASK)
    override fun setTouch(nx: Float, ny: Float, down: Boolean) = Retro.setTouch(nx, ny, down)

    private fun layoutValue(layout: Int, swap: Boolean): String = when (layout) {
        1 -> if (swap) "rightleft" else "leftright"
        2 -> if (swap) "bottomonly" else "toponly"
        3 -> if (swap) "toponly" else "bottomonly"
        4 -> if (swap) "hybridbottom" else "hybridtop"
        else -> if (swap) "bottomtop" else "topbottom"
    }

    /** Aplica distribucion + intercambio. Seguro de llamar con el juego en marcha (desde el hilo del motor). */
    fun applyLayout() {
        Retro.setOption("melonds_screen_layout", layoutValue(Settings.dsLayout.intValue, Settings.dsSwap.value))
    }

    /** Opciones de rendimiento. Los nombres que el nucleo no tenga se ignoran sin error. */
    fun applyQuality() {
        val fast = Settings.dsQuality.intValue == QUALITY_FAST || (Settings.dsQuality.intValue == QUALITY_AUTO && Settings.frameSkip.value)
        Retro.setOption("melonds_threaded_renderer", "enabled")
        Retro.setOption("melonds_opengl_resolution", if (fast) "1x native" else "2x native")
        Retro.setOption("melonds_audio_interpolation", if (fast) "None" else "Cosine")
    }

    fun applyAll() { applyLayout(); applyQuality() }

    /** Bajada automatica de calidad: si el tiempo medio por fotograma supera ~17 ms se pasa a rendimiento. */
    @Volatile private var slowFrames = 0
    fun onFrameTime(ms: Float) {
        if (Settings.dsQuality.intValue != QUALITY_AUTO || Settings.frameSkip.value) return
        slowFrames = if (ms > 18f) slowFrames + 1 else 0
        if (slowFrames > 120) { slowFrames = 0; Settings.setFrameSkip(true); applyQuality() }
    }
}
