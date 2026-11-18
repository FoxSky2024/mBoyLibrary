package app.gbalibrary.emu

import android.util.Log
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

    /** Aplica una opcion del nucleo y deja constancia en logcat (etiqueta DsEmulator) si el nucleo la rechaza. */
    private fun set(key: String, value: String): Boolean {
        val ok = Retro.setOption(key, value)
        if (!ok) Log.w("DsEmulator", "El nucleo rechazo $key=$value (clave o valor desconocido; ver las opciones listadas por RetroCore)")
        return ok
    }

    /**
     * El nucleo SIEMPRE entrega "arriba sobre abajo" sin separacion (256x384): [DsScreens] y [GbaGlView]
     * recortan cada mitad y la colocan segun la distribucion elegida, asi el toque se calcula desde la
     * pantalla inferior real en cualquier modo.
     */
    fun applyLayout() {
        set("melonds_screen_layout", "topbottom")
        set("melonds_screen_gap", "0")
        // Imprescindible: por defecto el nucleo lee el toque como "Mouse"; la app manda puntero (Touch).
        set("melonds_touch_mode", "touch")
    }

    private var autoSkipped = false
    private var fastFrames = 0

    /** Opciones de rendimiento. Los nombres que el nucleo no tenga se ignoran (y se registran en logcat). */
    fun applyQuality() {
        val fast = Settings.dsQuality.intValue == QUALITY_FAST || (Settings.dsQuality.intValue == QUALITY_AUTO && Settings.frameSkip.value)
        set("melonds_threaded_renderer", "enabled")
        set("melonds_opengl_resolution", if (fast) "1xnative" else "2xnative") // solo aplica con render OpenGL; en software se ignora
        set("melonds_audio_interpolation", if (fast) "None" else "Cosine")
    }

    fun applyAll() { applyLayout(); applyQuality() }

    /**
     * Saltar fotogramas automaticamente: si el tiempo medio por fotograma supera ~18 ms durante 2 s se activa
     * el salto de dibujo y la calidad de rendimiento; cuando vuelve a ~14 ms durante 5 s se recupera.
     */
    @Volatile private var slowFrames = 0
    fun onFrameTime(ms: Float) {
        if (Settings.dsQuality.intValue != QUALITY_AUTO) return
        if (!autoSkipped) {
            slowFrames = if (ms > 18f) slowFrames + 1 else 0
            if (slowFrames > 120) { slowFrames = 0; autoSkipped = true; Settings.setFrameSkip(true); applyQuality() }
        } else {
            fastFrames = if (ms < 14f) fastFrames + 1 else 0
            if (fastFrames > 300) { fastFrames = 0; autoSkipped = false; Settings.setFrameSkip(false); applyQuality() }
        }
    }
}
